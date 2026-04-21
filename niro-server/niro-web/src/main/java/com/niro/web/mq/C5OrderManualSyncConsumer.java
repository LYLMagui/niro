package com.niro.web.mq;

import com.niro.core.constant.MqConstant;
import com.niro.core.util.Assert;
import com.niro.web.dto.C5OrderManualSyncMessage;
import com.niro.web.service.C5OrderSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * C5 手动同步消费者
 *
 * @author niro
 * @since 2026-04-21
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstant.TOPIC_C5_ORDER,
        selectorExpression = MqConstant.TAG_C5_ORDER_MANUAL_SYNC,
        consumerGroup = MqConstant.CONSUMER_GROUP_C5_ORDER_MANUAL_SYNC,
        consumeThreadNumber = 1
)
public class C5OrderManualSyncConsumer implements RocketMQListener<C5OrderManualSyncMessage> {

    private final C5OrderSyncService c5OrderSyncService;

    @Override
    public void onMessage(C5OrderManualSyncMessage message) {
        Assert.notNull(message.getUserId(), "用户ID不能为空");

        log.info("【C5手动同步消费者】收到消息, userId={}, daysBefore={}", message.getUserId(), message.getDaysBefore());

        try {
            int syncedCount = c5OrderSyncService.syncOrders(message.getUserId(), message.getDaysBefore());
            log.info("【C5手动同步消费者】处理完成, userId={}, daysBefore={}, syncedCount={}",
                    message.getUserId(), message.getDaysBefore(), syncedCount);
        } catch (Exception e) {
            log.error("【C5手动同步消费者】处理失败, userId={}, daysBefore={}",
                    message.getUserId(), message.getDaysBefore(), e);
            throw new RuntimeException("C5 手动同步失败: " + e.getMessage(), e);
        }
    }
}
