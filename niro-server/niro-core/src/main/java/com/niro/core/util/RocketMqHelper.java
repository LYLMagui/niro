package com.niro.core.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RocketMQ 增强助手 (Refactor版)
 * <p>
 * 1. 移除手动JSON序列化，遵循 Spring MessageConverter 机制。
 * 2. 优化 Keys 策略，默认绑定 TraceId 实现全链路追踪。
 * 3. 简化 API 层次，提供更纯粹的 Builder 体验。
 * </p>
 *
 * @author Niro Technical Expert
 * @date 2026-02-12
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RocketMqHelper {

    private static final String TRACE_ID_MDC_KEY = "traceId";
    private static final String TRACE_ID_HEADER = "TRACE_ID";

    private final RocketMQTemplate template;

    @Value("${rocketmq.producer.send-message-timeout:3000}")
    private long defaultTimeout;

    /**
     * 开始构建消息
     */
    public MessageBuilder topic(String topic) {
        Assert.notBlank(topic, "Topic must not be blank");
        return new MessageBuilder(topic);
    }

    /**
     * 批量构建消息（支持 Tag）
     */
    public MessageBuilder topic(String topic, String tag) {
        Assert.notBlank(topic, "Topic must not be blank");
        return new MessageBuilder(topic).tag(tag);
    }

    /**
     * 自定义业务异常
     */
    public static class MqException extends RuntimeException {
        public MqException(String message) {
            super(message);
        }

        public MqException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 消息构建器
     */
    public class MessageBuilder {
        private final String topic;
        private String tag;
        private String key;
        private String traceId;
        private Integer delayLevel;
        private String hashKey;
        private long timeout = defaultTimeout;
        // Note: 使用全包名避免与内部类 MessageBuilder 命名冲突
        private Consumer<org.springframework.messaging.support.MessageBuilder<?>> customizer;

        public MessageBuilder(String topic) {
            this.topic = topic;
            // 默认绑定 MDC 中的 TraceId
            this.traceId = MDC.get(TRACE_ID_MDC_KEY);
        }

        public MessageBuilder tag(String tag) {
            this.tag = tag;
            return this;
        }

        public MessageBuilder key(String key) {
            this.key = key;
            return this;
        }

        public MessageBuilder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public MessageBuilder delay(DelayLevel level) {
            if (level != null) {
                this.delayLevel = level.getLevel();
            }
            return this;
        }

        public MessageBuilder orderly(String hashKey) {
            this.hashKey = hashKey;
            return this;
        }

        public MessageBuilder timeout(long timeout) {
            Assert.isTrue(timeout > 0, "Timeout must be greater than 0");
            this.timeout = timeout;
            return this;
        }

        /**
         * 高级定制：允许直接操作 Spring Message Builder
         */
        // Note: 使用全包名避免与内部类 MessageBuilder 命名冲突
        public MessageBuilder customize(Consumer<org.springframework.messaging.support.MessageBuilder<?>> customizer) {
            this.customizer = customizer;
            return this;
        }

        // ==================== 发送动作 ====================

        /**
         * 同步发送
         */
        public SendResult send(Object payload) {
            Message<?> message = buildMessage(payload);
            String destination = buildDestination();
            String sendContext = buildSendContext(destination, payload);
            try {
                SendResult result;
                if (StringUtils.hasText(hashKey)) {
                    result = template.syncSendOrderly(destination, message, hashKey, timeout);
                } else {
                    result = template.syncSend(destination, message, timeout);
                }
                return validateResult(result);
            } catch (Exception e) {
                throw new MqException("Sync send failed, " + sendContext, e);
            }
        }

        /**
         * 批量发送
         */
        public SendResult sendBatch(Collection<?> payloads) {
            if (CollectionUtils.isEmpty(payloads)) {
                throw new MqException("Batch payloads cannot be empty");
            }
            if (delayLevel != null && delayLevel > 0) {
                throw new MqException("Batch send does not support delay messages");
            }
            if (StringUtils.hasText(hashKey)) {
                throw new MqException("Batch send does not support orderly messages");
            }

            String destination = buildDestination();
            List<Message<?>> messages = new ArrayList<>(payloads.size());
            int index = 0;
            for (Object payload : payloads) {
                Assert.notNull(payload, "Batch payload item must not be null, index=" + index);
                messages.add(buildMessage(payload));
                index++;
            }

            String sendContext = buildSendContext(destination, "batch(size=" + messages.size() + ")");
            try {
                SendResult result = template.syncSend(destination, messages, timeout);
                log.info("Batch sent to {}, count={}, msgId={}", destination, messages.size(), result.getMsgId());
                return validateResult(result);
            } catch (Exception e) {
                throw new MqException("Batch send failed, " + sendContext, e);
            }
        }

        /**
         * 异步发送
         */
        public CompletableFuture<SendResult> sendAsync(Object payload) {
            Message<?> message = buildMessage(payload);
            String destination = buildDestination();
            String sendContext = buildSendContext(destination, payload);
            String currentTraceId = this.traceId;
            CompletableFuture<SendResult> future = new CompletableFuture<>();
            Map<String, String> mdcContext = MDC.getCopyOfContextMap();

            try {
                SendCallback callback = new SendCallback() {
                    private void restoreMdc() {
                        if (mdcContext == null || mdcContext.isEmpty()) {
                            MDC.clear();
                            return;
                        }
                        MDC.setContextMap(mdcContext);
                    }

                    @Override
                    public void onSuccess(SendResult result) {
                        try {
                            restoreMdc();
                            if (StringUtils.hasText(currentTraceId)) {
                                MDC.put(TRACE_ID_MDC_KEY, currentTraceId);
                            }
                            validateResult(result);
                            future.complete(result);
                        } catch (Exception e) {
                            future.completeExceptionally(e);
                        } finally {
                            MDC.clear();
                        }
                    }

                    @Override
                    public void onException(Throwable e) {
                        try {
                            restoreMdc();
                            if (StringUtils.hasText(currentTraceId)) {
                                MDC.put(TRACE_ID_MDC_KEY, currentTraceId);
                            }
                            log.error("Async send failed, {}", sendContext, e);
                        } finally {
                            MDC.clear();
                        }
                        future.completeExceptionally(new MqException("Async send failed, " + sendContext, e));
                    }
                };

                if (StringUtils.hasText(hashKey)) {
                    template.asyncSendOrderly(destination, message, hashKey, callback);
                } else {
                    template.asyncSend(destination, message, callback, timeout);
                }
            } catch (Exception e) {
                future.completeExceptionally(new MqException("Async send trigger failed, " + sendContext, e));
            }
            return future;
        }

        /**
         * 单向发送
         */
        public void sendOneWay(Object payload) {
            Message<?> message = buildMessage(payload);
            String destination = buildDestination();
            String sendContext = buildSendContext(destination, payload);
            try {
                if (StringUtils.hasText(hashKey)) {
                    template.sendOneWayOrderly(destination, message, hashKey);
                } else {
                    template.sendOneWay(destination, message);
                }
            } catch (Exception e) {
                log.error("Oneway send failed, {}", sendContext, e);
                throw new MqException("Oneway send failed, " + sendContext, e);
            }
        }

        /**
         * 事务消息
         */
        public TransactionSendResult sendTransaction(Object payload, Object arg) {
            Message<?> message = buildMessage(payload);
            String destination = buildDestination();
            String sendContext = buildSendContext(destination, payload);
            try {
                TransactionSendResult result = template.sendMessageInTransaction(destination, message, arg);
                log.info("Tx sent to {}, txId={}", destination, result.getTransactionId());
                return result;
            } catch (Exception e) {
                throw new MqException("Transaction send failed, " + sendContext, e);
            }
        }

        // ==================== 内部逻辑 ====================

        private Message<?> buildMessage(Object payload) {
            Assert.notNull(payload, "Payload must not be null");
            // 关键改进：不再手动 JSON 序列化，直接使用 payload。
            // RocketMQTemplate 会根据配置的 MessageConverter 进行转换。
            // Note: 使用全包名避免与内部类 MessageBuilder 命名冲突
            org.springframework.messaging.support.MessageBuilder<?> builder = 
                    org.springframework.messaging.support.MessageBuilder.withPayload(payload);

            // 1. Keys 处理：用户未设置 Key 时，默认使用 TraceId
            String finalKey = StringUtils.hasText(this.key) ? this.key : this.traceId;
            if (StringUtils.hasText(finalKey)) {
                builder.setHeader(MessageConst.PROPERTY_KEYS, finalKey);
            }

            // 2. TraceId 透传
            if (StringUtils.hasText(traceId)) {
                builder.setHeader(TRACE_ID_HEADER, traceId);
            }

            // 3. 延迟级别
            if (delayLevel != null && delayLevel > 0) {
                builder.setHeader(MessageConst.PROPERTY_DELAY_TIME_LEVEL, delayLevel);
            }

            // 4. 扩展定制 (保护系统保留 Header)
            if (customizer != null) {
                customizer.accept(builder);
                // 强制还原核心 Header，防止被 customizer 误改
                if (StringUtils.hasText(finalKey)) {
                    builder.setHeader(MessageConst.PROPERTY_KEYS, finalKey);
                }
                if (StringUtils.hasText(traceId)) {
                    builder.setHeader(TRACE_ID_HEADER, traceId);
                }
                if (delayLevel != null && delayLevel > 0) {
                    builder.setHeader(MessageConst.PROPERTY_DELAY_TIME_LEVEL, delayLevel);
                }
            }

            return builder.build();
        }

        private String buildDestination() {
            return StringUtils.hasText(tag) ? topic + ":" + tag : topic;
        }

        private String buildSendContext(String destination, Object payload) {
            String payloadType = payload == null ? "null" : payload.getClass().getName();
            return "destination=" + destination
                + ", key=" + key
                + ", traceId=" + traceId
                + ", hashKey=" + hashKey
                + ", delayLevel=" + delayLevel
                + ", timeout=" + timeout
                + ", payloadType=" + payloadType;
        }

        private SendResult validateResult(SendResult result) {
            if (result == null) {
                throw new MqException("Send result is null");
            }
            if (result.getSendStatus() != SendStatus.SEND_OK) {
                log.warn("Send status not OK: {}", result.getSendStatus());
                throw new MqException("Send status not OK: " + result.getSendStatus());
            }
            return result;
        }
    }

    /**
     * 延迟级别枚举
     */
    @Getter
    @RequiredArgsConstructor
    public enum DelayLevel {
        LEVEL_1(1, "1s"),
        LEVEL_2(2, "5s"),
        LEVEL_3(3, "10s"),
        LEVEL_4(4, "30s"),
        LEVEL_5(5, "1m"),
        LEVEL_6(6, "2m"),
        LEVEL_7(7, "3m"),
        LEVEL_8(8, "4m"),
        LEVEL_9(9, "5m"),
        LEVEL_10(10, "6m"),
        LEVEL_11(11, "7m"),
        LEVEL_12(12, "8m"),
        LEVEL_13(13, "9m"),
        LEVEL_14(14, "10m"),
        LEVEL_15(15, "20m"),
        LEVEL_16(16, "30m"),
        LEVEL_17(17, "1h"),
        LEVEL_18(18, "2h");

        private final int level;
        private final String desc;
    }
}
