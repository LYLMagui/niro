package com.niro.web.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.support.MessageBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@SpringBootTest
public class RocketMQProducerTest {
    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    private static final String TOPIC_TEST = "niro-test-topic";
    private static final String TAG_NORMAL = "normal";

    @Test
    void testSyncSend() {
        String messageId = UUID.randomUUID().toString();
        String payload = "同步消息测试 - " + LocalDateTime.now();

        String destination = TOPIC_TEST + ":" + TAG_NORMAL;

        SendResult result = rocketMQTemplate.syncSend(
            destination,
            MessageBuilder.withPayload(payload)
                .setHeader("KEYS", messageId)
                .setHeader("MSG_TYPE", "sync")
                .build()
        );

        if (result.getSendStatus() == SendStatus.SEND_OK) {
            log.info("同步消息发送成功, msgId={}, messageId={}", result.getMsgId(), messageId);
        } else {
            log.error("同步消息发送失败, status={}, messageId={}", result.getSendStatus(), messageId);
        }
    }

    @Test
    void testAsyncSend() throws InterruptedException {
        String messageId = UUID.randomUUID().toString();
        String payload = "异步消息测试 - " + LocalDateTime.now();

        String destination = TOPIC_TEST + ":" + TAG_NORMAL;

        rocketMQTemplate.asyncSend(
            destination,
            MessageBuilder.withPayload(payload)
                .setHeader("KEYS", messageId)
                .setHeader("MSG_TYPE", "async")
                .build(),
            new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("异步消息发送成功, msgId={}, messageId={}", sendResult.getMsgId(), messageId);
                }

                @Override
                public void onException(Throwable e) {
                    log.error("异步消息发送失败, messageId={}, error={}", messageId, e.getMessage());
                }
            }
        );

        Thread.sleep(3000);
    }

    @Test
    void testDelaySend() {
        String messageId = UUID.randomUUID().toString();
        String payload = "延迟消息测试 - " + LocalDateTime.now();

        String destination = TOPIC_TEST + ":" + TAG_NORMAL;

        // delayLevel: 1-18 对应延迟时间
        // 1: 1s, 2: 5s, 3: 10s, 4: 30s, 5: 1m, 6: 2m, 7: 3m, 8: 4m, 9: 5m
        // 10: 6m, 11: 7m, 12: 8m, 13: 9m, 14: 10m, 15: 20m, 16: 30m, 17: 1h, 18: 2h
        int delayLevel = 3; // 10秒后消费

        SendResult result = rocketMQTemplate.syncSendDelayTimeSeconds(
            destination,
            MessageBuilder.withPayload(payload)
                .setHeader("KEYS", messageId)
                .setHeader("MSG_TYPE", "delay")
                .build(),
            delayLevel
        );

        if (result.getSendStatus() == SendStatus.SEND_OK) {
            log.info("延迟消息发送成功, delayLevel={}, msgId={}, messageId={}", delayLevel, result.getMsgId(), messageId);
        } else {
            log.error("延迟消息发送失败, status={}, messageId={}", result.getSendStatus(), messageId);
        }
    }

    @Test
    void testOrderlySend() {
        String orderId = "ORDER_" + System.currentTimeMillis();

        for (int i = 1; i <= 5; i++) {
            String messageId = UUID.randomUUID().toString();
            String payload = String.format("顺序消息测试 [%d/5] - orderId=%s - %s", i, orderId, LocalDateTime.now());

            String destination = TOPIC_TEST + ":" + TAG_NORMAL;

            // 使用 orderId 作为 hashKey，保证同一 orderId 的消息进入同一队列
            SendResult result = rocketMQTemplate.syncSendOrderly(
                destination,
                MessageBuilder.withPayload(payload)
                    .setHeader("KEYS", messageId)
                    .setHeader("ORDER_ID", orderId)
                    .setHeader("SEQ", i)
                    .build(),
                orderId
            );

            if (result.getSendStatus() == SendStatus.SEND_OK) {
                log.info("顺序消息发送成功 [seq={}], msgId={}, orderId={}", i, result.getMsgId(), orderId);
            } else {
                log.error("顺序消息发送失败 [seq={}], status={}, orderId={}", i, result.getSendStatus(), orderId);
            }
        }
    }

    @Test
    void testSendWithObject() {
        String messageId = UUID.randomUUID().toString();

        TestMessage message = new TestMessage();
        message.setId(messageId);
        message.setContent("对象消息测试");
        message.setTimestamp(LocalDateTime.now());
        message.setOrderNo("ORDER_" + System.currentTimeMillis());

        String destination = TOPIC_TEST + ":" + TAG_NORMAL;

        SendResult result = rocketMQTemplate.syncSend(
            destination,
            MessageBuilder.withPayload(message)
                .setHeader("KEYS", messageId)
                .setHeader("MSG_TYPE", "object")
                .build()
        );

        if (result.getSendStatus() == SendStatus.SEND_OK) {
            log.info("对象消息发送成功, msgId={}, message={}", result.getMsgId(), message);
        } else {
            log.error("对象消息发送失败, status={}, message={}", result.getSendStatus(), message);
        }
    }

    /**
     * 发送消息到 Demo 消费者（随服务启动持续运行）
     * 需要先启动应用让 DemoMessageConsumer 注册，再运行此测试
     */
    @Test
    void testSendToDemoConsumer() {
        String messageId = UUID.randomUUID().toString();
        String payload = "Demo消费者测试消息 - " + LocalDateTime.now() + " - ID:" + messageId.substring(0, 8);

        String destination = "niro-demo-topic:*";

        SendResult result = rocketMQTemplate.syncSend(
            destination,
            MessageBuilder.withPayload(payload)
                .setHeader("KEYS", messageId)
                .build()
        );

        if (result.getSendStatus() == SendStatus.SEND_OK) {
            log.info("Demo消息发送成功, msgId={}, message={}", result.getMsgId(), payload);
        } else {
            log.error("Demo消息发送失败, status={}", result.getSendStatus());
        }
    }
}
