package com.niro.web.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.niro.core.util.Assert;
import com.niro.sdk.c5.client.C5ApiClient;
import com.niro.sdk.c5.config.C5Config;
import com.niro.sdk.c5.request.order.C5BuyerStatusRequest;
import com.niro.sdk.c5.response.order.C5BuyerStatusResponse;
import com.niro.sdk.c5.response.order.C5BuyerStatusResponse.OrderBuyDTO;
import com.niro.web.dto.UserPlatformSettingsDTO;
import com.niro.web.entity.BuffGoods;
import com.niro.web.entity.TradeOrderRecord;
import com.niro.web.entity.UserPlatformSettings;
import com.niro.web.enums.PlatformEnum;
import com.niro.web.enums.platform.C5OrderStatusEnum;
import com.niro.web.manager.TradeOrderRecordMapperManager;
import com.niro.web.service.BuffGoodsService;
import com.niro.web.service.C5OrderSyncService;
import com.niro.web.service.TradeOrderRecordService;
import com.niro.web.service.UserPlatformSettingsService;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * C5 订单同步服务实现
 * <p>
 * 定时从 C5 平台拉取用户订单，同步到本地数据库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class C5OrderSyncServiceImpl implements C5OrderSyncService {

    private static final String PLATFORM_C5 = "C5";
    private static final int PAGE_SIZE = 100;
    private static final String LIMITER_KEY = "niro:limiter:c5:api";

    private final TradeOrderRecordService tradeOrderRecordService;
    private final TradeOrderRecordMapperManager tradeOrderRecordMapperManager;
    private final UserPlatformSettingsService userPlatformSettingsService;
    private final BuffGoodsService buffGoodsService;
    private final RedissonClient redissonClient;

    private RRateLimiter c5ApiLimiter;

    @Value("${c5.base-url:https://openapi.c5game.com}")
    private String c5BaseUrl;

    @PostConstruct
    private void initLimiter() {
        log.debug("开始初始化 C5 API 分布式限流器, key={}", LIMITER_KEY);
        try {
            c5ApiLimiter = redissonClient.getRateLimiter(LIMITER_KEY);
            // 每 1 秒产生 10 个令牌
            c5ApiLimiter.trySetRate(RateType.OVERALL, 10, 1, RateIntervalUnit.SECONDS);
            log.info("C5 API 分布式限流器初始化成功: 10 QPS");
        } catch (Exception e) {
            log.error("C5 API 限流器初始化失败", e);
        }
    }

    private void acquireLimiter() {
        if (c5ApiLimiter == null) {
            log.warn("限流器未就绪，直接放行");
            return;
        }
        try {
            log.debug("获取 C5 API 令牌...");
            c5ApiLimiter.acquire(1);
        } catch (Exception e) {
            // Redis 异常时降级直接放行，避免阻塞业务
            log.error("获取 C5 API 令牌异常，降级直接放行", e);
        }
    }

    private final Map<Long, C5ApiClient> clientCache = new ConcurrentHashMap<>();

    private C5ApiClient getC5Client(Long userId) {
        return clientCache.computeIfAbsent(userId, uid -> {
            UserPlatformSettingsDTO settings = userPlatformSettingsService.getByUserId(uid);
            Assert.notNull(settings, "用户配置不存在");
            Assert.notBlank(settings.getC5AppKey(), "C5 App Key 未配置");

            C5Config config = new C5Config()
                    .setAppKey(settings.getC5AppKey())
                    .setBaseUrl(c5BaseUrl);
            return new C5ApiClient(config);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncOrders(Integer daysBefore) {
        log.info("开始同步 C5 订单, daysBefore={}", daysBefore);

        // 计算时间阈值
        LocalDateTime startTime = null;
        if (daysBefore != null && daysBefore >= 0) {
            startTime = LocalDateTime.now().minusDays(daysBefore).withHour(0).withMinute(0).withSecond(0);
        }

        // 获取所有配置了 C5 AppKey 的用户
        List<UserPlatformSettings> settingsList = userPlatformSettingsService.lambdaQuery()
                .isNotNull(UserPlatformSettings::getC5AppKey)
                .ne(UserPlatformSettings::getC5AppKey, "")
                .list();

        if (settingsList.isEmpty()) {
            log.info("没有用户配置 C5 App Key，跳过同步");
            return;
        }

        int totalSynced = 0;
        for (UserPlatformSettings settings : settingsList) {
            Long userId = settings.getUserId();
            try {
                int synced = syncUserOrders(userId, startTime);
                totalSynced += synced;
                log.info("用户 [{}] 同步完成，新增订单: {}", userId, synced);
            } catch (Exception e) {
                log.error("同步用户 [{}] 订单失败", userId, e);
            }
        }

        log.info("C5 订单同步完成，总计新增: {}", totalSynced);
    }

    private int syncUserOrders(Long userId, LocalDateTime startTime) {
        C5ApiClient client = getC5Client(userId);
        int totalSynced = 0;
        int pageNum = 1;
        int totalPages = 1;

        do {
            C5BuyerStatusRequest request = new C5BuyerStatusRequest()
                    .setPageNum(pageNum)
                    .setPageSize(PAGE_SIZE);

            // 接口调用前获取限流令牌
            acquireLimiter();
            C5BuyerStatusResponse response = client.getOrder().batchBuyerStatus(request);

            if (response == null || response.getList() == null || response.getList().isEmpty()) {
                log.debug("用户 [{}] 第 {} 页无数据", userId, pageNum);
                break;
            }

            totalPages = response.getPages() != null ? response.getPages() : 1;

            List<OrderBuyDTO> orders = response.getList();

            // 按时间过滤：只保留 >= startTime 的订单
            if (startTime != null) {
                orders = orders.stream()
                        .filter(o -> {
                            LocalDateTime orderTime = timestampToLocalDateTime(o.getCreateTime());
                            return orderTime != null && !orderTime.isBefore(startTime);
                        })
                        .collect(Collectors.toList());

                // 如果本页所有订单都早于 startTime，说明后续页面也不用查了
                if (orders.isEmpty()) {
                    log.debug("用户 [{}] 第 {} 页订单均早于时间阈值，停止同步", userId, pageNum);
                    break;
                }
            }

            int synced = processOrders(userId, orders);
            totalSynced += synced;

            log.debug("用户 [{}] 第 {}/{} 页处理完成，新增: {}", userId, pageNum, totalPages, synced);
            pageNum++;

        } while (pageNum <= totalPages);

        return totalSynced;
    }

    private int processOrders(Long userId, List<OrderBuyDTO> orders) {
        if (orders.isEmpty()) {
            return 0;
        }

        // 提取订单ID列表
        List<String> orderIds = orders.stream()
                .map(OrderBuyDTO::getOrderId)
                .distinct()
                .collect(Collectors.toList());

        // 查询数据库中已存在的订单ID
        List<String> existingIds = tradeOrderRecordService.selectExistingOrderIds(PLATFORM_C5, orderIds);
        Set<String> existingIdSet = existingIds != null ? new HashSet<>(existingIds) : Set.of();

        // 过滤出需要新增的订单
        List<OrderBuyDTO> newOrders = orders.stream()
                .filter(o -> !existingIdSet.contains(o.getOrderId()))
                .toList();

        if (newOrders.isEmpty()) {
            return 0;
        }

        // 转换为实体并保存
        List<TradeOrderRecord> records = new ArrayList<>();
        for (OrderBuyDTO order : newOrders) {
            TradeOrderRecord record = convertToRecord(userId, order);
            records.add(record);
        }

        // 批量保存
        for (TradeOrderRecord record : records) {
            tradeOrderRecordMapperManager.save(record);
        }

        return records.size();
    }

    private TradeOrderRecord convertToRecord(Long userId, OrderBuyDTO order) {
        TradeOrderRecord record = new TradeOrderRecord();
        record.setUserId(userId);
        record.setPlatform(PlatformEnum.C5.getCode());
        record.setOrderId(order.getOrderId());
        record.setOutTradeNo(order.getOutTradeNo());
        record.setPrice(order.getPrice() != null ? order.getPrice() : BigDecimal.ZERO);
        record.setCreateTime(timestampToLocalDateTime(order.getCreateTime()));
        record.setUpdateTime(timestampToLocalDateTime(order.getUpdateTime()));
        // 使用枚举映射状态
        record.setStatus(C5OrderStatusEnum.mapToInternalStatus(order.getStatus()).getCode());
        
        return record;
    }

    private LocalDateTime timestampToLocalDateTime(Long timestamp) {
        if (timestamp == null) {
            return null;
        }
        // C5 返回的是秒级时间戳
        if (timestamp < 10000000000L) {
            timestamp = timestamp * 1000;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
    }
}
