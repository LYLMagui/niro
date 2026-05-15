package com.niro.web.mq;

import com.niro.core.constant.MqConstant;
import com.niro.web.service.C5MarketPriceSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstant.TOPIC_C5_MARKET_PRICE_SNAPSHOT,
        selectorExpression = MqConstant.TAG_C5_MARKET_PRICE_SNAPSHOT_REFRESH,
        consumerGroup = MqConstant.CONSUMER_GROUP_C5_MARKET_PRICE_SNAPSHOT_REFRESH,
        namespace = "${ROCKETMQ_NAMESPACE:}",
        consumeThreadNumber = 1
)
public class C5MarketPriceSnapshotRefreshConsumer implements RocketMQListener<Long> {

    private final C5MarketPriceSnapshotService marketPriceSnapshotService;

    @Override
    public void onMessage(Long snapshotId) {
        try {
            marketPriceSnapshotService.consumeRefreshSnapshot(snapshotId);
        } catch (Exception e) {
            log.error("C5市场价格快照刷新消息处理失败, snapshotId={}", snapshotId, e);
        }
    }
}
