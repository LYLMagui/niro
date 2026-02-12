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
    selectorExpression = "*"
)
public class TestMessageConsumer implements RocketMQListener<String> {

    @Override
    public void onMessage(String message) {
        log.info("收到测试消息: {}", message);
    }
}
