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
import com.niro.web.manager.TradeOrderRecordMapperManager;
import com.niro.web.service.UserPlatformSettingsService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class C5OrderSyncJobHandler {

    private final TradeOrderRecordMapperManager tradeOrderRecordMapperManager;
    private final UserPlatformSettingsService userPlatformSettingsService;

    @XxlJob("c5OrderSyncJobHandler")
    public void syncC5OrderStatuses() {
        XxlJobHelper.log("开始同步 C5 订单状态...");
        log.info("开始同步 C5 订单状态...");

        try {
            // 1. 获取最近 48 小时内的活跃 C5 订单
            // 本地状态为 SUCCESS 但远程可能已 CANCELLED 或 COMPLETED 的订单
            LocalDateTime syncThreshold = LocalDateTime.now().minusHours(48);
            List<TradeOrderRecord> activeOrders = tradeOrderRecordMapperManager.selectActiveC5Orders(syncThreshold);

            if (CollUtil.isEmpty(activeOrders)) {
                String msg = "未找到需要同步的活跃 C5 订单。";
                XxlJobHelper.log(msg);
                log.info(msg);
                return;
            }

            int totalProcessed = 0;
            int totalUpdated = 0;

            // 2. 按用户 ID 分组以高效获取设置并复用客户端
            Map<Long, List<TradeOrderRecord>> ordersByUser = activeOrders.stream()
                    .collect(Collectors.groupingBy(TradeOrderRecord::getUserId));

            for (Map.Entry<Long, List<TradeOrderRecord>> entry : ordersByUser.entrySet()) {
                Long userId = entry.getKey();
                List<TradeOrderRecord> userOrders = entry.getValue();

                try {
                    // 获取用户的 C5 证书
                    UserPlatformSettingsDTO settings = userPlatformSettingsService.getByUserId(userId);
                    if (settings == null || StrUtil.isBlank(settings.getC5AppKey())) {
                        log.warn("跳过用户 ID {} 的订单：未找到 C5 证书配置。", userId);
                        continue;
                    }

                    // 初始化 C5 客户端
                    C5Config config = new C5Config()
                            .setAppKey(settings.getC5AppKey());
                    C5ApiClient c5ApiClient = new C5ApiClient(config);

                    // 批量查询状态
                    // C5 API 可能对批量大小有限制，但在 48 小时窗口内每个用户的列表通常较小。
                    // 如有必要，我们可以对 userOrders 进行分区。目前假设 < 50。
                    List<String> orderIds = userOrders.stream()
                            // 这里存储已确认的 orderId
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
                    log.error("处理用户 ID {} 的订单时出错: {}", userId, e.getMessage());
                }
            }

            String summary = StrUtil.format("C5 订单状态同步完成。处理总数: {}, 更新总数: {}",
                    totalProcessed, totalUpdated);
            XxlJobHelper.log(summary);
            log.info(summary);

        } catch (Exception e) {
            String errorMsg = "C5 订单状态同步失败: " + e.getMessage();
            XxlJobHelper.handleFail(errorMsg);
            log.error(errorMsg, e);
        }
    }

    private int updateOrderIfNeeded(List<TradeOrderRecord> userOrders, C5BuyerStatusResponse.OrderBuyDTO statusDTO) {
        // 查找匹配的本地订单
        TradeOrderRecord localOrder = userOrders.stream()
                .filter(o -> StrUtil.equals(o.getOrderId(), statusDTO.getOrderId())
                        || StrUtil.equals(o.getOrderId(), statusDTO.getOrderAssetId()))
                .findFirst()
                .orElse(null);

        if (localOrder == null) {
            return 0;
        }

        // 检查状态是否需要更新
        // C5 状态: 11 = 已取消
        if (statusDTO.getStatus() != null && statusDTO.getStatus() == 11) {
            // 更新本地状态为 已取消
            if (!OrderStatusEnum.CANCELLED.getCode().equals(localOrder.getStatus())) {
                localOrder.setStatus(OrderStatusEnum.CANCELLED.getCode());
                localOrder.setErrorMsg("C5平台自动取消");
                localOrder.setUpdateTime(LocalDateTime.now());
                tradeOrderRecordMapperManager.updateById(localOrder);

                log.info("已将订单 {} ({}) 的状态更新为 已取消", localOrder.getOrderId(),
                        localOrder.getMarketHashName());
                return 1;
            }
        }

        // 也可以处理 '10 = 已完成' -> SUCCESS，如果我们想区分“已下单”和“已完成”。
        // 但根据需求，我们关注已取消的情况。

        return 0;
    }
}
