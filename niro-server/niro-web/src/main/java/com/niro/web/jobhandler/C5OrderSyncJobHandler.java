package com.niro.web.jobhandler;

import com.niro.core.constant.MqConstant;
import com.niro.core.util.RocketMqHelper;
import com.niro.web.dto.C5OrderStatusSyncMessage;
import com.niro.web.entity.TradeOrderRecord;
import com.niro.web.entity.UserPlatformSettings;
import com.niro.web.enums.PlatformEnum;
import com.niro.web.manager.TradeOrderRecordMapperManager;
import com.niro.web.manager.UserPlatformSettingsMapperManager;
import com.niro.web.service.UserPlatformSettingsService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * C5 订单状态同步 Job Handler（MQ 异步版）
 *
 * <p>工作流程：</p>
 * <ol>
 *   <li>查询需要同步状态的订单列表</li>
 *   <li>循环发送 MQ 消息，每个订单一条消息</li>
 * </ol>
 *
 * @author niro
 * @date 2026-02-17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class C5OrderSyncJobHandler {

    private final TradeOrderRecordMapperManager tradeOrderRecordMapperManager;
    private final UserPlatformSettingsMapperManager userPlatformSettingsMapperManager;
    private final UserPlatformSettingsService userPlatformSettingsService;
    private final RocketMqHelper rocketMqHelper;

    /**
     * 同步 C5 订单状态
     *
     * <p>调度参数：无（自动查询 30 分钟内状态有变化的订单）</p>
     */
    @XxlJob("syncC5OrderStatuses")
    public void syncC5OrderStatuses() {
        log.info("开始同步 C5 订单状态（MQ 异步版）");

        try {
            // 查询需要同步的订单（30 分钟内状态可能有变化的）
            LocalDateTime syncTime = LocalDateTime.now().minusMinutes(30);
            List<TradeOrderRecord> orders = tradeOrderRecordMapperManager.lambdaQuery()
                    .eq(TradeOrderRecord::getPlatform, PlatformEnum.C5.getCode())
                    .ge(TradeOrderRecord::getUpdateTime, syncTime)
                    .in(TradeOrderRecord::getStatus, 0, 1)     // 处理中、成功
                    .orderByDesc(TradeOrderRecord::getUpdateTime)
                    .last("LIMIT 1000")
                    .list();

            if (orders.isEmpty()) {
                log.info("没有需要同步状态的 C5 订单");
                XxlJobHelper.handleSuccess("没有需要同步的订单");
                return;
            }

            log.info("查询到 {} 条需要同步状态的订单", orders.size());

            List<Long> userIds = orders.stream()
                    .map(TradeOrderRecord::getUserId)
                    .distinct()
                    .collect(Collectors.toList());

            Map<Long, String> appKeyMap = new HashMap<>();
            if (!userIds.isEmpty()) {
                List<UserPlatformSettings> settingsList = userPlatformSettingsMapperManager.lambdaQuery()
                        .in(UserPlatformSettings::getUserId, userIds)
                        .list();
                for (UserPlatformSettings settings : settingsList) {
                    if (settings != null && settings.getC5AppKeyEncrypted() != null) {
                        appKeyMap.put(settings.getUserId(), userPlatformSettingsService.decryptC5AppKey(settings));
                    }
                }
            }

            int successCount = 0;
            int failCount = 0;

            // 循环发送 MQ 消息
            for (TradeOrderRecord order : orders) {
                try {
                    String appKey = appKeyMap.get(order.getUserId());

                    C5OrderStatusSyncMessage message = C5OrderStatusSyncMessage.builder()
                            .recordId(order.getId())
                            .orderId(order.getOrderId())
                            .orderNo(order.getOutTradeNo())
                            .userId(order.getUserId())
                            .appKey(appKey)
                            .currentStatus(order.getStatus())
                            .build();

                    // 发送 MQ 消息（延迟 5 秒，避免集中调用）
                    rocketMqHelper.topic(MqConstant.TOPIC_C5_ORDER, MqConstant.TAG_C5_ORDER_STATUS_SYNC)
                            .key(order.getOutTradeNo())
                            .timeout(5000L)
                            .send(message);

                    successCount++;
                    log.debug("订单状态同步消息发送成功, recordId={}, orderNo={}",
                            order.getId(), order.getOutTradeNo());

                } catch (Exception e) {
                    failCount++;
                    log.error("订单状态同步消息发送失败, recordId={}, orderNo={}",
                            order.getId(), order.getOutTradeNo(), e);
                }
            }

            log.info("C5 订单状态同步任务完成，总订单数={}, 发送成功={}, 发送失败={}",
                    orders.size(), successCount, failCount);

            if (failCount > 0) {
                XxlJobHelper.handleFail("部分消息发送失败，成功：" + successCount + "，失败：" + failCount);
            } else {
                XxlJobHelper.handleSuccess("成功发送 " + successCount + " 条订单状态同步消息");
            }

        } catch (Exception e) {
            log.error("同步 C5 订单状态任务执行失败", e);
            XxlJobHelper.handleFail("任务执行失败: " + e.getMessage());
        }
    }
}
