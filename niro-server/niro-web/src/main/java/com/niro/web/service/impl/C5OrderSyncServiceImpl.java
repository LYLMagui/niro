package com.niro.web.service.impl;

import cn.hutool.core.util.StrUtil;
import com.niro.core.constant.MqConstant;
import com.niro.core.exception.BusinessException;
import com.niro.core.util.Assert;
import com.niro.core.util.RocketMqHelper;
import com.niro.sdk.c5.client.C5ApiClient;
import com.niro.sdk.c5.exception.C5ApiException;
import com.niro.sdk.c5.config.C5Config;
import com.niro.sdk.c5.order.C5BuyerStatusRequest;
import com.niro.sdk.c5.order.C5OrderDetailRequest;
import com.niro.sdk.c5.order.C5BuyerStatusResponse;
import com.niro.sdk.c5.order.C5BuyerStatusResponse.OrderBuyDTO;
import com.niro.sdk.c5.order.C5OrderDetailResponse;
import com.niro.web.dto.C5OrderDetailMessage;
import com.niro.web.dto.C5OrderManualSyncMessage;
import com.niro.web.entity.BuffGoods;
import com.niro.web.entity.C5SnipingAccount;
import com.niro.web.entity.TradeOrderRecord;
import com.niro.web.entity.UserPlatformSettings;
import com.niro.web.enums.PlatformEnum;
import com.niro.web.enums.platform.C5OrderStatusEnum;
import com.niro.web.manager.C5SnipingAccountMapperManager;
import com.niro.web.manager.TradeOrderRecordMapperManager;
import com.niro.web.service.BuffGoodsService;
import com.niro.web.service.C5OrderSyncService;
import com.niro.web.service.C5SnipingAccountService;
import com.niro.web.service.TradeOrderRecordService;
import com.niro.web.service.UserPlatformSettingsService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
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
    private static final String USER_SYNC_LOCK_KEY_PREFIX = "niro:lock:c5:order-sync:user:";
    private static final String ALL_SYNC_LOCK_KEY_PREFIX = "niro:lock:c5:order-sync:all:";
    private static final String USER_SYNC_SUBMIT_KEY_PREFIX = "niro:cooldown:c5:order-sync:user:";
    private static final long SYNC_LOCK_WAIT_SECONDS = 0L;
    private static final long SYNC_LOCK_LEASE_SECONDS = 300L;
    private static final long SYNC_SUBMIT_COOLDOWN_SECONDS = 60L;

    private final TradeOrderRecordService tradeOrderRecordService;
    private final TradeOrderRecordMapperManager tradeOrderRecordMapperManager;
    private final UserPlatformSettingsService userPlatformSettingsService;
    private final C5SnipingAccountService c5SnipingAccountService;
    private final BuffGoodsService buffGoodsService;
    private final C5SnipingAccountMapperManager c5SnipingAccountMapperManager;
    private final RedissonClient redissonClient;
    private final RocketMqHelper rocketMqHelper;

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

    private String buildUserSyncLockKey(Long userId, Long accountId, Integer daysBefore) {
        return USER_SYNC_LOCK_KEY_PREFIX + userId + ":" + accountId + ":" + normalizeDaysBefore(daysBefore);
    }

    private String buildAllSyncLockKey(Integer daysBefore) {
        return ALL_SYNC_LOCK_KEY_PREFIX + normalizeDaysBefore(daysBefore);
    }

    private String buildUserSyncSubmitKey(Long userId, Long accountId, Integer daysBefore) {
        return USER_SYNC_SUBMIT_KEY_PREFIX + userId + ":" + accountId + ":" + normalizeDaysBefore(daysBefore);
    }

    private int normalizeDaysBefore(Integer daysBefore) {
        return daysBefore == null ? 1 : daysBefore;
    }

    private RLock acquireSyncLock(String lockKey) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            boolean locked = lock.tryLock(SYNC_LOCK_WAIT_SECONDS, SYNC_LOCK_LEASE_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
            Assert.isTrue(locked, "同步任务正在执行，请勿重复触发");
            return lock;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("获取同步锁被中断");
        }
    }

    private void unlockSyncLock(RLock lock, String lockKey) {
        if (lock == null || !lock.isHeldByCurrentThread()) {
            return;
        }
        try {
            lock.unlock();
        } catch (Exception e) {
            log.warn("释放 C5 订单同步锁失败, lockKey={}", lockKey, e);
        }
    }

    private void checkAndMarkSubmitCooldown(Long userId, Long accountId, Integer daysBefore) {
        String submitKey = buildUserSyncSubmitKey(userId, accountId, daysBefore);
        try {
            boolean created = redissonClient.getBucket(submitKey)
                    .trySet("1", SYNC_SUBMIT_COOLDOWN_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
            Assert.isTrue(created, "60 秒内请勿重复提交同步任务");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("写入 C5 同步提交冷却失败，降级继续提交, submitKey={}", submitKey, e);
        }
    }

    private final Map<Long, C5ApiClient> clientCache = new ConcurrentHashMap<>();

    private C5ApiClient getC5Client(Long userId) {
        return clientCache.computeIfAbsent(userId, uid -> {
            UserPlatformSettings settings = userPlatformSettingsService.lambdaQuery()
                    .eq(UserPlatformSettings::getUserId, uid)
                    .one();
            Assert.notNull(settings, "用户配置不存在");
            String appKey = userPlatformSettingsService.decryptC5AppKey(settings);

            C5Config config = new C5Config()
                    .setAppKey(appKey)
                    .setBaseUrl(c5BaseUrl);
            return new C5ApiClient(config);
        });
    }

    private C5ApiClient buildC5Client(String appKey) {
        C5Config config = new C5Config()
                .setAppKey(appKey)
                .setBaseUrl(c5BaseUrl);
        return new C5ApiClient(config);
    }

    private C5SnipingAccount getSyncAccount(Long userId, Long accountId) {
        Assert.notNull(userId, "用户ID不能为空");
        Assert.notNull(accountId, "C5账号ID不能为空");
        C5SnipingAccount account = c5SnipingAccountMapperManager.getByUserIdAndId(userId, accountId);
        Assert.notNull(account, "C5账号不存在或无权访问");
        // 订单同步只依赖 AppKey，不要求账号必须处于任务可用状态。
        Assert.notBlank(account.getC5AppKeyEncrypted(), "C5 App Key 未配置");
        return account;
    }

    @Override
    public void submitSyncTask(Long userId, Long accountId, Integer daysBefore) {
        C5SnipingAccount account = getSyncAccount(userId, accountId);

        Integer normalizedDaysBefore = normalizeDaysBefore(daysBefore);
        String lockKey = buildUserSyncLockKey(userId, account.getId(), normalizedDaysBefore);
        boolean running = redissonClient.getLock(lockKey).isLocked();
        Assert.isTrue(!running, "同步任务正在执行，请勿重复触发");

        checkAndMarkSubmitCooldown(userId, account.getId(), normalizedDaysBefore);

        C5OrderManualSyncMessage message = C5OrderManualSyncMessage.builder()
                .userId(userId)
                .accountId(account.getId())
                .daysBefore(normalizedDaysBefore)
                .timestamp(System.currentTimeMillis())
                .build();

        rocketMqHelper.topic(MqConstant.TOPIC_C5_ORDER, MqConstant.TAG_C5_ORDER_MANUAL_SYNC)
                .key(userId + ":" + account.getId() + ":" + normalizedDaysBefore)
                .timeout(5000L)
                .send(message);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncOrders(Long userId, Long accountId, Integer daysBefore) {
        log.info("开始同步 C5 订单, userId={}, accountId={}, daysBefore={}", userId, accountId, daysBefore);
        C5SnipingAccount account = getSyncAccount(userId, accountId);

        String lockKey = buildUserSyncLockKey(userId, account.getId(), daysBefore);
        RLock lock = acquireSyncLock(lockKey);
        try {
            LocalDateTime startTime = buildStartTime(daysBefore);
            int synced = syncUserOrders(userId, account.getId(), c5SnipingAccountService.decryptAccountAppKey(account), startTime);
            log.info("用户 [{}] C5 账号 [{}] 订单同步完成，新增订单: {}", userId, account.getId(), synced);
            return synced;
        } finally {
            unlockSyncLock(lock, lockKey);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncOrders(Integer daysBefore) {
        log.info("开始同步全部 C5 订单, daysBefore={}", daysBefore);

        String lockKey = buildAllSyncLockKey(daysBefore);
        RLock lock = acquireSyncLock(lockKey);
        try {
            LocalDateTime startTime = buildStartTime(daysBefore);

            // 获取所有配置了 C5 AppKey 的用户
            List<UserPlatformSettings> settingsList = userPlatformSettingsService.lambdaQuery()
                    .isNotNull(UserPlatformSettings::getC5AppKeyEncrypted)
                    .ne(UserPlatformSettings::getC5AppKeyEncrypted, "")
                    .list();

            if (settingsList.isEmpty()) {
                log.info("没有用户配置 C5 App Key，跳过同步");
                return 0;
            }

            int totalSynced = 0;
            for (UserPlatformSettings settings : settingsList) {
                Long userId = settings.getUserId();
                try {
                    int synced = syncUserOrders(userId, null, userPlatformSettingsService.decryptC5AppKey(settings), startTime);
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
            return totalSynced;
        } finally {
            unlockSyncLock(lock, lockKey);
        }
    }

    private LocalDateTime buildStartTime(Integer daysBefore) {
        if (daysBefore == null || daysBefore < 0) {
            return null;
        }
        return LocalDateTime.now().minusDays(daysBefore).withHour(0).withMinute(0).withSecond(0);
    }

    private int syncUserOrders(Long userId, Long accountId, String appKey, LocalDateTime startTime) {
        C5ApiClient client = StrUtil.isNotBlank(appKey) ? buildC5Client(appKey) : getC5Client(userId);
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

            int synced = processOrders(userId, accountId, appKey, orders);
            totalSynced += synced;

            log.debug("用户 [{}] 第 {}/{} 页处理完成，新增: {}", userId, pageNum, totalPages, synced);
            pageNum++;

        } while (pageNum <= totalPages);

        return totalSynced;
    }

    private int processOrders(Long userId, Long accountId, String appKey, List<OrderBuyDTO> orders) {
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
            TradeOrderRecord record = convertToRecord(userId, accountId, appKey, order);
            records.add(record);
        }

        // 批量保存，MyBatis-Plus 会回填自增 ID
        boolean saved = tradeOrderRecordMapperManager.saveBatch(records);
        Assert.isTrue(saved, "批量保存订单记录失败");

        // 发送异步消息获取订单详情，携带 appKey
        for (TradeOrderRecord record : records) {
            Assert.notNull(record.getId(), "订单记录 ID 不能为空，数据库 ID 回填失败");
            sendOrderDetailMessage(record, appKey);
        }

        return records.size();
    }

    private TradeOrderRecord convertToRecord(Long userId, Long accountId, String appKey, OrderBuyDTO order) {
        TradeOrderRecord record = new TradeOrderRecord();
        record.setUserId(userId);
        record.setAccountId(accountId);
        record.setPlatform(PlatformEnum.C5.getCode());
        record.setOrderId(order.getOrderId());
        record.setOutTradeNo(order.getOutTradeNo());
        record.setPrice(order.getPrice() != null ? order.getPrice() : BigDecimal.ZERO);
        record.setCreateTime(timestampToLocalDateTime(order.getCreateTime()));
        record.setUpdateTime(timestampToLocalDateTime(order.getUpdateTime()));
        // 使用枚举映射状态
        record.setStatus(C5OrderStatusEnum.mapToInternalStatus(order.getStatus()).getCode());

        fillGoodsInfo(record, appKey, order);
        return record;
    }

    private void fillGoodsInfo(TradeOrderRecord record, String appKey, OrderBuyDTO order) {
        if (StrUtil.isBlank(order.getOrderId())) {
            return;
        }

        C5OrderDetailRequest request = new C5OrderDetailRequest()
                .setOrderId(order.getOrderId());

        try {
            C5OrderDetailResponse detail = buildC5Client(appKey).getTrade().getOrderDetail(request);
            if (detail == null || detail.getOpenItemInfo() == null) {
                return;
            }

            String marketHashName = StrUtil.trimToEmpty(detail.getOpenItemInfo().getMarketHashName());
            String goodsName = StrUtil.trimToEmpty(detail.getOpenItemInfo().getName());
            String goodsImg = StrUtil.trimToEmpty(detail.getOpenItemInfo().getImageUrl());

            BuffGoods goods = findBuffGoods(marketHashName, goodsName);
            if (goods != null) {
                record.setMarketHashName(StrUtil.blankToDefault(goods.getMarketHashName(), marketHashName));
                record.setGoodsName(StrUtil.blankToDefault(goods.getName(), goodsName));
                record.setGoodsImg(StrUtil.blankToDefault(goods.getIconUrl(), goodsImg));
                return;
            }

            record.setMarketHashName(marketHashName);
            record.setGoodsName(StrUtil.blankToDefault(goodsName, marketHashName));
            record.setGoodsImg(goodsImg);
        } catch (Exception e) {
            log.warn("同步 C5 订单时预取详情失败, orderId={}", order.getOrderId(), e);
        }
    }

    private BuffGoods findBuffGoods(String marketHashName, String goodsName) {
        if (StrUtil.isNotBlank(marketHashName)) {
            BuffGoods goods = buffGoodsService.lambdaQuery()
                    .eq(BuffGoods::getMarketHashName, marketHashName)
                    .one();
            if (goods != null) {
                return goods;
            }
        }

        if (StrUtil.isNotBlank(goodsName)) {
            return buffGoodsService.lambdaQuery()
                    .eq(BuffGoods::getName, goodsName)
                    .one();
        }
        return null;
    }

    /**
     * 发送订单详情同步延迟消息
     * <p>
     * 订单刚创建时立即查询可能获取不到完整的详情数据，
     * 使用延迟消息等待 C5 平台处理完成后再查询。
     * </p>
     * <p>
     * 使用 MqTxSender.afterCommitSendDelay 确保消息在事务提交后发送，
     * 解决"消息先于事务提交"导致消费者查不到订单的问题。
     * </p>
     *
     * @param record 交易订单记录
     * @param appKey C5 App Key
     */
    private void sendOrderDetailMessage(TradeOrderRecord record, String appKey) {
        try {
            // 检查订单号是否为空（平台下单失败或尚未下单时可能为空）
            if (!StrUtil.isNotBlank(record.getOrderId())) {
                log.warn("【C5订单详情消息】订单号为空，跳过发送, recordId={}", record.getId());
                return;
            }

            C5OrderDetailMessage message = C5OrderDetailMessage.builder()
                    .orderId(record.getOrderId())
                    .userId(record.getUserId())
                    .appKey(appKey)
                    .timestamp(System.currentTimeMillis())
                    .build();

            // 使用事务后发送，确保数据库事务提交后才发送消息
            // 延迟 10 秒，给 C5 平台处理时间
            rocketMqHelper.afterCommitSendDelay(
                    MqConstant.TOPIC_C5_ORDER,
                    MqConstant.TAG_C5_ORDER_DETAIL_SYNC,
                    message,
                    RocketMqHelper.DelayLevel.LEVEL_3
            );

            log.debug("【C5订单详情消息】已注册事务后延迟发送, orderId={}", record.getOrderId());
        } catch (Exception e) {
            log.error("【C5订单详情消息】发送异常, orderId={}", record.getOrderId(), e);
            // 消息发送失败不影响主流程
        }
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
