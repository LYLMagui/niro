package com.niro.web.mq;

import cn.hutool.core.collection.CollUtil;
import com.niro.core.constant.MqConstant;
import com.niro.core.util.Assert;
import com.niro.sdk.c5.client.C5ApiClient;
import com.niro.sdk.c5.config.C5Config;
import com.niro.sdk.c5.order.C5BuyerStatusRequest;
import com.niro.sdk.c5.order.C5BuyerStatusResponse;
import com.niro.web.constant.C5OrderSyncConstants;
import com.niro.web.dto.C5OrderStatusSyncMessage;
import com.niro.web.entity.TradeOrderRecord;
import com.niro.web.entity.UserPlatformSettings;
import com.niro.web.enums.OrderStatusEnum;
import com.niro.web.manager.TradeOrderRecordMapperManager;
import com.niro.web.manager.UserPlatformSettingsMapperManager;
import com.niro.web.service.UserPlatformSettingsService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * C5 订单状态同步消费者
 * 
 * <p>
 * 消费定时任务发送的状态同步消息，异步调用 C5 API 查询订单最新状态
 * </p>
 *
 * @author niro
 * @since 2026-02-17
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstant.TOPIC_C5_ORDER,
        selectorExpression = MqConstant.TAG_C5_ORDER_STATUS_SYNC,
        consumerGroup = MqConstant.CONSUMER_GROUP_C5_ORDER_STATUS_SYNC,
        namespace = "${ROCKETMQ_NAMESPACE:}",
        consumeThreadNumber = 5
)
public class C5OrderStatusSyncConsumer implements RocketMQListener<C5OrderStatusSyncMessage> {

    private final TradeOrderRecordMapperManager tradeOrderRecordMapperManager;
    private final UserPlatformSettingsMapperManager userPlatformSettingsMapperManager;
    private final UserPlatformSettingsService userPlatformSettingsService;
    private final RedissonClient redissonClient;

    @Value("${c5.base-url:https://openapi.c5game.com}")
    private String c5BaseUrl;

    private RRateLimiter c5ApiLimiter;

    @PostConstruct
    private void initLimiter() {
        c5ApiLimiter = redissonClient.getRateLimiter(C5OrderSyncConstants.ORDER_STATUS_SYNC_LIMITER_KEY);
        // 限流: 每 1 秒产生 5 个令牌，与 consumeThreadNumber 匹配
        c5ApiLimiter.trySetRate(RateType.OVERALL, 5, 1, RateIntervalUnit.SECONDS);
    }

    @Override
    public void onMessage(C5OrderStatusSyncMessage message) {
        String messageKey = message.getRecordId() + "_" + message.getOrderId();
        log.info("【C5订单状态同步消费者】收到消息, recordId={}, orderId={}, userId={}",
                message.getRecordId(), message.getOrderId(), message.getUserId());

        try {
            // 参数校验
            Assert.notNull(message.getRecordId(), "订单记录ID不能为空");
            Assert.notNull(message.getOrderId(), "C5订单号不能为空");
            Assert.notNull(message.getUserId(), "用户ID不能为空");

            // 获取限流令牌
            if (!acquireLimiter()) {
                log.warn("获取限流令牌失败，放弃处理, recordId={}", message.getRecordId());
                throw new RuntimeException("获取 C5 API 限流令牌超时");
            }

            UserPlatformSettings settings = userPlatformSettingsMapperManager.lambdaQuery()
                    .eq(UserPlatformSettings::getUserId, message.getUserId())
                    .one();
            Assert.notNull(settings, "用户未配置 C5 平台");
            Assert.notNull(settings.getC5AppKeyEncrypted(), "用户未配置 C5 App Key");
            String appKey = userPlatformSettingsService.decryptC5AppKey(settings);
            Assert.notBlank(appKey, "C5 App Key 解密失败");

            // 初始化 C5 客户端
            C5Config config = new C5Config()
                    .setAppKey(appKey)
                    .setBaseUrl(c5BaseUrl);
            C5ApiClient c5ApiClient = new C5ApiClient(config);

            // 查询订单状态
            C5BuyerStatusRequest request = new C5BuyerStatusRequest()
                    .setOrderIds(Collections.singletonList(message.getOrderId()));
            C5BuyerStatusResponse response = c5ApiClient.getOrder().batchBuyerStatus(request);

            if (response == null || CollUtil.isEmpty(response.getList())) {
                log.warn("C5 API 返回空数据, recordId={}, orderId={}", 
                        message.getRecordId(), message.getOrderId());
                return;
            }

            // 处理返回的状态
            C5BuyerStatusResponse.OrderBuyDTO statusDTO = response.getList().get(0);
            updateOrderStatus(message, statusDTO);

            log.info("【C5订单状态同步消费者】处理完成, recordId={}, status={}",
                    message.getRecordId(), statusDTO.getStatus());

        } catch (Exception e) {
            log.error("【C5订单状态同步消费者】处理失败, recordId={}, orderId={}",
                    message.getRecordId(), message.getOrderId(), e);
            // 抛出异常触发 MQ 重试
            throw new RuntimeException("订单状态同步失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新订单状态
     */
    private void updateOrderStatus(C5OrderStatusSyncMessage message,
                                   C5BuyerStatusResponse.OrderBuyDTO statusDTO) {
        TradeOrderRecord order = tradeOrderRecordMapperManager.getById(message.getRecordId());
        if (order == null) {
            log.warn("订单不存在, recordId={}", message.getRecordId());
            return;
        }

        Integer c5Status = statusDTO.getStatus();
        if (c5Status == null) {
            return;
        }

        Integer nextStatus = null;
        String nextErrorMsg = order.getErrorMsg();
        if (c5Status == 11) {
            nextStatus = OrderStatusEnum.CANCELLED.getCode();
            nextErrorMsg = "C5平台自动取消";
        } else if (c5Status == 10 || c5Status == 200) {
            nextStatus = OrderStatusEnum.SUCCESS.getCode();
            nextErrorMsg = null;
        }

        if (nextStatus == null || nextStatus.equals(order.getStatus())) {
            return;
        }

        order.setStatus(nextStatus);
        order.setErrorMsg(nextErrorMsg);
        order.setUpdateTime(LocalDateTime.now());
        tradeOrderRecordMapperManager.updateById(order);


        log.info("已将订单 {} ({}) 的状态更新为 {}",
                order.getOrderId(), order.getMarketHashName(), nextStatus);
    }

    /**
     * 获取限流令牌
     */
    private boolean acquireLimiter() {
        if (c5ApiLimiter == null) {
            log.warn("限流器未就绪，直接放行");
            return true;
        }
        try {
            return c5ApiLimiter.tryAcquire(1, C5OrderSyncConstants.LIMITER_ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("获取 C5 API 令牌异常，降级直接放行", e);
            return true;
        }
    }
}
