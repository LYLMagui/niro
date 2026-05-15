package com.niro.web.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
    topic = "niro-test-topic",
    consumerGroup = "niro-test-consumer-group",
    namespace = "${ROCKETMQ_NAMESPACE:}",
    selectorExpression = "*"
)
public class DemoMessageConsumer implements RocketMQListener<String> {

    @Override
    public void onMessage(String message) {
        log.info("【Demo消费者】收到消息: {}", message);

        // 模拟业务处理
        try {
            Thread.sleep(100);
            log.info("【Demo消费者】消息处理完成");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("【Demo消费者】处理被中断");
        }
    }
}
