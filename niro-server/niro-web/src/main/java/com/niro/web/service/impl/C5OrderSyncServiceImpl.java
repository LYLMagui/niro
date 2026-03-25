package com.niro.web.service.impl;

import cn.hutool.core.util.StrUtil;
import com.niro.core.exception.BusinessException;
import com.niro.core.util.Assert;
import com.niro.sdk.c5.client.C5ApiClient;
import com.niro.sdk.c5.exception.C5ApiException;
import com.niro.sdk.c5.config.C5Config;
import com.niro.sdk.c5.request.order.C5BuyerStatusRequest;
import com.niro.sdk.c5.request.trade.C5OrderDetailRequest;
import com.niro.sdk.c5.response.order.C5BuyerStatusResponse;
import com.niro.sdk.c5.response.order.C5BuyerStatusResponse.OrderBuyDTO;
import com.niro.sdk.c5.response.trade.C5OrderDetailResponse;
import com.niro.web.dto.UserPlatformSettingsDTO;
import com.niro.web.entity.TradeOrderRecord;
import com.niro.web.entity.UserPlatformSettings;
import com.niro.web.enums.PlatformEnum;
import com.niro.web.enums.platform.C5OrderStatusEnum;
import com.niro.web.manager.TradeOrderRecordMapperManager;
import com.niro.web.service.C5OrderSyncService;
import com.niro.web.service.TradeOrderRecordService;
import com.niro.web.service.UserPlatformSettingsService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
                int synced = syncUserOrders(userId, settings.getC5AppKey(), startTime);
                totalSynced += synced;
                log.info("用户 [{}] 同步完成，新增订单: {}", userId, synced);
            } catch (C5ApiException e) {
                // API 调用失败（如 IP 白名单、认证失败等），属于系统级错误，立即终止任务
                log.error("用户 [{}] C5 API 调用失败，终止同步任务: {}", userId, e.getMessage(), e);
                throw new BusinessException("C5 API 调用失败: " + e.getMessage());
            } catch (Exception e) {
                // 其他异常（如数据处理异常），记录后继续下一个用户
                log.error("同步用户 [{}] 订单失败，继续处理下一个用户", userId, e);
            }
        }

        log.info("C5 订单同步完成，总计新增: {}", totalSynced);
    }

    private int syncUserOrders(Long userId, String appKey, LocalDateTime startTime) {
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

            int synced = processOrders(userId, appKey, orders);
            totalSynced += synced;

            log.debug("用户 [{}] 第 {}/{} 页处理完成，新增: {}", userId, pageNum, totalPages, synced);
            pageNum++;

        } while (pageNum <= totalPages);

        return totalSynced;
    }

    private int processOrders(Long userId, String appKey, List<OrderBuyDTO> orders) {
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

        // 批量保存，MyBatis-Plus 会回填自增 ID
        boolean saved = tradeOrderRecordMapperManager.saveBatch(records);
        Assert.isTrue(saved, "批量保存订单记录失败");

        // 同步补齐订单详情，简化版不再走 MQ 异步链路
        for (TradeOrderRecord record : records) {
            Assert.notNull(record.getId(), "订单记录 ID 不能为空，数据库 ID 回填失败");
            enrichOrderDetail(record, appKey);
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

    /**
     * 同步补齐订单详情
     */
    private void enrichOrderDetail(TradeOrderRecord record, String appKey) {
        try {
            if (!StrUtil.isNotBlank(record.getOrderId())) {
                log.warn("【C5订单详情同步】订单号为空，跳过补齐, recordId={}", record.getId());
                return;
            }

            acquireLimiter();

            C5Config config = new C5Config()
                    .setAppKey(appKey)
                    .setBaseUrl(c5BaseUrl);
            C5ApiClient c5ApiClient = new C5ApiClient(config);

            C5OrderDetailRequest request = new C5OrderDetailRequest()
                    .setOrderId(record.getOrderId());
            C5OrderDetailResponse detail = c5ApiClient.getTrade().getOrderDetail(request);

            if (detail == null) {
                record.setErrorMsg("C5 订单详情返回为空");
                tradeOrderRecordMapperManager.updateById(record);
                log.warn("【C5订单详情同步】C5 订单详情返回为空, orderId={}", record.getOrderId());
                return;
            }

            updateOrderDetail(record, detail);
        } catch (Exception e) {
            log.error("【C5订单详情同步】补齐订单详情失败, orderId={}", record.getOrderId(), e);
            record.setErrorMsg("调用 C5 订单详情接口失败: " + e.getMessage());
            tradeOrderRecordMapperManager.updateById(record);
        }
    }

    private void updateOrderDetail(TradeOrderRecord record, C5OrderDetailResponse detail) {
        if (detail.getPrice() != null &&
                (record.getPrice() == null || record.getPrice().compareTo(detail.getPrice()) != 0)) {
            record.setPrice(detail.getPrice());
        }

        Integer localStatus = C5OrderStatusEnum.mapToInternalStatus(detail.getStatus()).getCode();
        record.setStatus(localStatus);

        if (detail.getFailedDesc() != null) {
            record.setErrorMsg(detail.getFailedDesc());
        }

        if (detail.getOpenItemInfo() != null) {
            String goodsName = detail.getOpenItemInfo().getName();
            if (!StrUtil.isNotBlank(goodsName)) {
                goodsName = detail.getOpenItemInfo().getMarketHashName();
            }
            record.setGoodsName(goodsName);
            if (detail.getOpenItemInfo().getImageUrl() != null) {
                record.setGoodsImg(detail.getOpenItemInfo().getImageUrl());
            }
            if (detail.getOpenItemInfo().getMarketHashName() != null) {
                record.setMarketHashName(detail.getOpenItemInfo().getMarketHashName());
            }
        }

        if (detail.getExtra() != null && !detail.getExtra().isEmpty()) {
            record.setExtraInfo(detail.getExtra());
        }

        boolean updated = tradeOrderRecordMapperManager.updateById(record);
        Assert.isTrue(updated, "更新订单记录失败");
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
