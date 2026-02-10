package com.niro.web.jobhandler;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.niro.sdk.c5.client.C5ApiClient;
import com.niro.sdk.c5.config.C5Config;
import com.niro.sdk.c5.request.order.C5BuyerStatusRequest;
import com.niro.sdk.c5.response.order.C5BuyerStatusResponse;
import com.niro.web.dto.UserPlatformSettingsDTO;
import com.niro.web.entity.TradeOrderRecord;
import com.niro.web.enums.OrderStatusEnum;
import com.niro.web.manager.TradeOrderRecordManagerMapper;
import com.niro.web.service.UserPlatformSettingsService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class C5OrderSyncJobHandler {

    private final TradeOrderRecordManagerMapper tradeOrderRecordManagerMapper;
    private final UserPlatformSettingsService userPlatformSettingsService;

    @XxlJob("c5OrderSyncJobHandler")
    public void syncC5OrderStatuses() {
        XxlJobHelper.log("Starting C5 order status synchronization...");
        log.info("Starting C5 order status synchronization...");

        try {
            // 1. Fetch active C5 orders from the last 48 hours
            // Orders that are SUCCESS locally but might be CANCELLED or COMPLETED remotely
            LocalDateTime syncThreshold = LocalDateTime.now().minusHours(48);
            List<TradeOrderRecord> activeOrders = tradeOrderRecordManagerMapper.selectActiveC5Orders(syncThreshold);

            if (CollUtil.isEmpty(activeOrders)) {
                String msg = "No active C5 orders found to sync.";
                XxlJobHelper.log(msg);
                log.info(msg);
                return;
            }

            int totalProcessed = 0;
            int totalUpdated = 0;

            // 2. Group orders by userId to efficiently get settings and reuse client
            Map<Long, List<TradeOrderRecord>> ordersByUser = activeOrders.stream()
                    .collect(Collectors.groupingBy(TradeOrderRecord::getUserId));

            for (Map.Entry<Long, List<TradeOrderRecord>> entry : ordersByUser.entrySet()) {
                Long userId = entry.getKey();
                List<TradeOrderRecord> userOrders = entry.getValue();

                try {
                    // Get C5 credentials for the user
                    UserPlatformSettingsDTO settings = userPlatformSettingsService.getByUserId(userId);
                    if (settings == null || StrUtil.isBlank(settings.getC5AppKey())
                            || StrUtil.isBlank(settings.getC5SecretKey())) {
                        log.warn("Skipping orders for userId {}: C5 credentials not found.", userId);
                        continue;
                    }

                    // Initialize C5 Client
                    C5Config config = new C5Config()
                            .setAppKey(settings.getC5AppKey())
                            .setSecretKey(settings.getC5SecretKey());
                    C5ApiClient c5ApiClient = new C5ApiClient(config);

                    // Batch query status
                    // C5 API might have limits on batch size, but list is usually small per user in
                    // 48h window.
                    // If necessary, we can partition userOrders. Assuming < 50 for now.
                    List<String> orderIds = userOrders.stream()
                            // This stores orderId as confirmed
                            .map(TradeOrderRecord::getOrderId)
                            .collect(Collectors.toList());

                    if (CollUtil.isEmpty(orderIds)) {
                        continue;
                    }

                    C5BuyerStatusRequest request = new C5BuyerStatusRequest().setOrderIds(orderIds);
                    C5BuyerStatusResponse response = c5ApiClient.getOrder().batchBuyerStatus(request);

                    if (response != null && CollUtil.isNotEmpty(response.getList())) {
                        for (C5BuyerStatusResponse.OrderBuyDTO statusDTO : response.getList()) {
                            totalUpdated += updateOrderIfNeeded(userOrders, statusDTO);
                        }
                    }

                    totalProcessed += userOrders.size();

                } catch (Exception e) {
                    log.error("Error processing orders for userId {}: {}", userId, e.getMessage());
                }
            }

            String summary = StrUtil.format("C5 order status synchronization completed. Processed: {}, Updated: {}",
                    totalProcessed, totalUpdated);
            XxlJobHelper.log(summary);
            log.info(summary);

        } catch (Exception e) {
            String errorMsg = "C5 order status synchronization failed: " + e.getMessage();
            XxlJobHelper.handleFail(errorMsg);
            log.error(errorMsg, e);
        }
    }

    private int updateOrderIfNeeded(List<TradeOrderRecord> userOrders, C5BuyerStatusResponse.OrderBuyDTO statusDTO) {
        // Find matching local order
        TradeOrderRecord localOrder = userOrders.stream()
                .filter(o -> StrUtil.equals(o.getOrderId(), statusDTO.getOrderId())
                        || StrUtil.equals(o.getOrderId(), statusDTO.getOrderAssetId()))
                .findFirst()
                .orElse(null);

        if (localOrder == null) {
            return 0;
        }

        // Check if status needs update
        // C5 Status: 11 = Cancelled
        if (statusDTO.getStatus() != null && statusDTO.getStatus() == 11) {
            // Update local status to CANCELLED
            if (!OrderStatusEnum.CANCELLED.getCode().equals(localOrder.getStatus())) {
                localOrder.setStatus(OrderStatusEnum.CANCELLED.getCode());
                localOrder.setErrorMsg("C5平台自动取消");
                localOrder.setUpdateTime(LocalDateTime.now());
                tradeOrderRecordManagerMapper.updateById(localOrder);

                log.info("Updated order {} ({}) status to CANCELLED", localOrder.getOrderId(),
                        localOrder.getMarketHashName());
                return 1;
            }
        }

        // We can also handle '10 = Completed' -> SUCCESS if we want to distinguish
        // between "Ordered" and "Completed"
        // But per requirement, we focus on Cancelled.

        return 0;
    }
}
