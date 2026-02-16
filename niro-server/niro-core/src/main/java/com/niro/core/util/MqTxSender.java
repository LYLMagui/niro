package com.niro.core.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * MQ 事务发送器（基于 Spring 事务同步）
 * 
 * <p>特点：</p>
 * <ul>
 *   <li>事务提交后才发送消息，解决"消息先于事务提交"问题</li>
 *   <li>如果当前不在事务中，立即发送</li>
 *   <li>发送失败仅记录日志，不影响主流程</li>
 * </ul>
 * 
 * <p>注意：此方案为最终一致性，有极小概率丢消息（事务提交后 JVM 崩溃）。
 * 如需强一致性，请使用 RocketMQ 事务消息。</p>
 * 
 * <p>此类供 RocketMqHelper 内部使用，业务代码请通过 RocketMqHelper 调用。</p>
 * 
 * @author niro
 * @date 2026-02-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqTxSender {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 事务提交后发送消息
     * 
     * <p>如果当前不在事务中，立即发送；如果在事务中，等待事务提交后发送</p>
     * 
     * @param topic 主题，必填
     * @param tag 标签，可为 null
     * @param payload 消息体，必填
     */
    public void afterCommitSend(String topic, String tag, Object payload) {
        Assert.notBlank(topic, "Topic must not be blank");
        Assert.notNull(payload, "Payload must not be null");

        // 不在事务中，直接发送
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            log.debug("当前无事务，直接发送消息, topic={}", topic);
            doSend(topic, tag, payload, null);
            return;
        }

        // 在事务中，注册 afterCommit 回调
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSend(topic, tag, payload, null);
                }
            }
        );

        log.debug("已注册事务提交后发送回调, topic={}", topic);
    }

    /**
     * 事务提交后发送延迟消息
     * 
     * @param topic 主题，必填
     * @param tag 标签，可为 null
     * @param payload 消息体，必填
     * @param delayLevel 延迟级别，必填
     */
    public void afterCommitSendDelay(String topic, String tag, Object payload,
                                     RocketMqHelper.DelayLevel delayLevel) {
        Assert.notBlank(topic, "Topic must not be blank");
        Assert.notNull(payload, "Payload must not be null");
        Assert.notNull(delayLevel, "DelayLevel must not be null");

        // 不在事务中，直接发送
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            log.debug("当前无事务，直接发送延迟消息, topic={}, delayLevel={}", topic, delayLevel);
            doSend(topic, tag, payload, delayLevel);
            return;
        }

        // 在事务中，注册 afterCommit 回调
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSend(topic, tag, payload, delayLevel);
                }
            }
        );

        log.debug("已注册事务提交后延迟发送回调, topic={}, delayLevel={}", topic, delayLevel);
    }

    /**
     * 执行实际发送
     */
    private void doSend(String topic, String tag, Object payload, RocketMqHelper.DelayLevel delayLevel) {
        try {
            // 构建 destination（topic:tag 或只有 topic）
            String destination = (tag != null && !tag.isEmpty()) ? topic + ":" + tag : topic;
            
            // 构建 Spring Message
            org.springframework.messaging.Message<?> message = MessageBuilder.withPayload(payload).build();
            
            // 发送消息
            if (delayLevel != null) {
                rocketMQTemplate.syncSendDelayTimeSeconds(destination, message, delayLevel.getLevel());
            } else {
                rocketMQTemplate.syncSend(destination, message);
            }
            
            log.debug("事务提交后消息发送成功, topic={}", topic);
        } catch (Exception e) {
            log.error("事务提交后消息发送失败, topic={}", topic, e);
            // 注：这里可以考虑写入 Outbox 表由定时任务补偿
            // 但为保持简单，当前仅记录日志
        }
    }
}
