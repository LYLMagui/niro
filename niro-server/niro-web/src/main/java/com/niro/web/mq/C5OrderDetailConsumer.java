package com.niro.web.mq;

import cn.hutool.core.util.StrUtil;
import com.niro.core.constant.MqConstant;
import com.niro.core.util.Assert;
import com.niro.sdk.c5.client.C5ApiClient;
import com.niro.sdk.c5.config.C5Config;
import com.niro.sdk.c5.order.C5OrderDetailRequest;
import com.niro.sdk.c5.order.C5OrderDetailResponse;
import com.niro.web.dto.C5OrderDetailMessage;
import com.niro.web.entity.TradeOrderRecord;
import com.niro.web.enums.OrderStatusEnum;
import com.niro.web.enums.platform.C5OrderStatusEnum;
import com.niro.web.manager.TradeOrderRecordMapperManager;
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

import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * C5 订单详情同步消费者
 * <p>
 * 消费订单详情同步消息，异步调用 C5 订单详情接口更新订单数据。
 * 考虑到 MQ 最大并发消费数为 10，结合 C5 API 限流（10 QPS），
 * 使用分布式限流器控制并发访问，避免触发平台限流。
 * </p>
 *
 * @author niro
 * @since 2026-02-12
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = MqConstant.TOPIC_C5_ORDER,
    selectorExpression = MqConstant.TAG_C5_ORDER_DETAIL_SYNC,
    consumerGroup = MqConstant.CONSUMER_GROUP_C5_ORDER_DETAIL,
    consumeThreadNumber = 10
)
public class C5OrderDetailConsumer implements RocketMQListener<C5OrderDetailMessage> {

    private static final String LIMITER_KEY = "niro:limiter:c5:order:detail";
    private static final int ACQUIRE_TIMEOUT_SECONDS = 5;

    private final TradeOrderRecordMapperManager tradeOrderRecordMapperManager;
    private final RedissonClient redissonClient;

    private RRateLimiter c5ApiLimiter;

    @Value("${c5.base-url:https://openapi.c5game.com}")
    private String c5BaseUrl;

    @PostConstruct
    private void initLimiter() {
        log.debug("开始初始化 C5 订单详情消费者限流器, key={}", LIMITER_KEY);
        try {
            c5ApiLimiter = redissonClient.getRateLimiter(LIMITER_KEY);
            // 限流: 每 1 秒产生 10 个令牌，与 MQ 并发数匹配，避免触发 C5 平台限流
            c5ApiLimiter.trySetRate(RateType.OVERALL, 10, 1, RateIntervalUnit.SECONDS);
            log.info("C5 订单详情消费者限流器初始化成功: 10 QPS");
        } catch (Exception e) {
            log.error("C5 订单详情消费者限流器初始化失败", e);
        }
    }

    @Override
    public void onMessage(C5OrderDetailMessage message) {
        log.info("【C5订单详情消费者】收到消息, c5OrderId={}",message.getOrderId());

        // 验证消息
        Assert.notNull(message.getOrderId(), "C5订单号不能为空");
        Assert.notNull(message.getUserId(), "用户ID不能为空");
        Assert.notBlank(message.getAppKey(), "C5 App Key 不能为空");

        // 1. 查询本地订单记录
        TradeOrderRecord record = tradeOrderRecordMapperManager.getByOrderId(message.getOrderId());
        if (record == null) {
            log.warn("【C5订单详情消费者】订单记录不存在, tradeOrderRecordId={}", message.getOrderId());
            return;
        }

        // 获取限流令牌，控制并发
        if (!acquireLimiter()) {
            log.warn("【C5订单详情消费者】获取限流令牌失败，放弃处理, c5OrderNo={}", message.getOrderId());
            // 抛出异常触发 MQ 重试
            throw new RuntimeException("获取 C5 API 限流令牌超时");
        }

        try {
            // 2. 创建 C5 API 客户端，使用消息中的 appKey
            C5Config config = new C5Config();
            config.setBaseUrl(c5BaseUrl);
            config.setAppKey(message.getAppKey());
            C5ApiClient c5ApiClient = new C5ApiClient(config);

            // 3. 构造请求参数（只使用 orderId）
            C5OrderDetailRequest request = new C5OrderDetailRequest()
                .setOrderId(message.getOrderId());

            // 4. 调用 C5 订单详情接口
            C5OrderDetailResponse detail = c5ApiClient.getTrade().getOrderDetail(request);
            
            if (detail == null) {
                // 接口返回空，更新失败原因
                updateOrderError(record, "C5 订单详情返回为空");
                log.warn("【C5订单详情消费者】C5 订单详情返回为空, c5OrderNo={}", message.getOrderId());
                return;
            }

            // 5. 更新本地订单记录
            updateOrderRecord(record, detail);

            log.info("【C5订单详情消费者】订单详情同步成功, c5OrderNo={}, status={}", 
                message.getOrderId(), detail.getStatus());

        } catch (Exception e) {
            log.error("【C5订单详情消费者】订单详情同步失败, c5OrderNo={}", message.getOrderId(), e);
            // 更新失败原因到订单记录
            updateOrderError(record, "调用 C5 订单详情接口失败: " + e.getMessage());
            // 抛出异常触发 RocketMQ 重试机制
            throw e;
        }
    }

    /**
     * 获取限流令牌
     *
     * @return 是否成功获取令牌
     */
    private boolean acquireLimiter() {
        if (c5ApiLimiter == null) {
            log.warn("限流器未就绪，直接放行");
            return true;
        }
        try {
            return c5ApiLimiter.tryAcquire(1, ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("获取 C5 API 令牌异常，降级直接放行", e);
            return true;
        }
    }

    /**
     * 更新订单记录
     *
     * @param record 本地订单记录
     * @param detail C5 订单详情
     */
    private void updateOrderRecord(TradeOrderRecord record, C5OrderDetailResponse detail) {
        // 更新价格（如果 C5 返回的价格与本地不同）
        if (detail.getPrice() != null &&
            (record.getPrice() == null || record.getPrice().compareTo(detail.getPrice()) != 0)) {
            record.setPrice(detail.getPrice());
        }

        // 更新状态映射
        Integer localStatus = mapC5StatusToLocal(detail.getStatus());
        if (localStatus != null) {
            record.setStatus(localStatus);
            if (OrderStatusEnum.SUCCESS.getCode().equals(localStatus)) {
                record.setErrorMsg(null);
            }
        }

        // 更新失败原因
        if (detail.getFailedDesc() != null) {
            record.setErrorMsg(detail.getFailedDesc());
        }

        // 更新商品信息（如果有）
        if (detail.getOpenItemInfo() != null) {
            // 仅当本地 goodsName 为空时，才用 C5 返回值兜底
            if (StrUtil.isBlank(record.getGoodsName())) {
                String goodsName = detail.getOpenItemInfo().getName();
                if (goodsName == null || goodsName.isEmpty()) {
                    goodsName = detail.getOpenItemInfo().getMarketHashName();
                }
                record.setGoodsName(goodsName);
            }
            if (detail.getOpenItemInfo().getImageUrl() != null) {
                record.setGoodsImg(detail.getOpenItemInfo().getImageUrl());
            }
            if (detail.getOpenItemInfo().getMarketHashName() != null) {
                record.setMarketHashName(detail.getOpenItemInfo().getMarketHashName());
            }
        }

        // 更新扩展信息
        if (detail.getExtra() != null && !detail.getExtra().isEmpty()) {
            record.setExtraInfo(detail.getExtra());
        }

        // 保存更新
        boolean updated = tradeOrderRecordMapperManager.updateById(record);
        Assert.isTrue(updated, "更新订单记录失败");


        log.debug("订单记录更新成功, id={}, orderId={}", record.getId(), record.getOrderId());
    }

    /**
     * 更新订单错误信息
     *
     * @param record 本地订单记录
     * @param errorMsg 错误信息
     */
    private void updateOrderError(TradeOrderRecord record, String errorMsg) {
        record.setErrorMsg(errorMsg);
        record.setUpdateTime(LocalDateTime.now());
        
        boolean updated = tradeOrderRecordMapperManager.updateById(record);
        if (!updated) {
            log.warn("更新订单错误信息失败, id={}, orderId={}", record.getId(), record.getOrderId());
        } else {
            log.debug("订单错误信息更新成功, id={}, orderId={}, error={}", 
                record.getId(), record.getOrderId(), errorMsg);
        }
    }

    /**
     * 将 C5 订单状态映射到本地状态
     *
     * @param c5Status C5 状态码
     * @return 本地状态码
     * @see C5OrderStatusEnum#mapToInternalStatus(Integer)
     * @see OrderStatusEnum
     */
    private Integer mapC5StatusToLocal(Integer c5Status) {
        OrderStatusEnum internalStatus = C5OrderStatusEnum.mapToInternalStatus(c5Status);
        return internalStatus.getCode();
    }

}
