package com.niro.web.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.niro.core.constant.MqConstant;
import com.niro.core.util.Assert;
import com.niro.core.util.RocketMqHelper;
import com.niro.sdk.c5.exception.C5HttpException;
import com.niro.sdk.c5.market.C5ProductListRequest;
import com.niro.sdk.c5.market.C5ProductSearchRequest;
import com.niro.sdk.c5.market.C5ProductListResponse;
import com.niro.web.constant.C5MarketPriceSnapshotConstants;
import com.niro.web.dto.C5MarketPriceSnapshotListingDTO;
import com.niro.web.dto.C5MarketPriceSnapshotReferenceDTO;
import com.niro.web.dto.param.C5MarketPriceSnapshotReferenceParam;
import com.niro.web.dto.param.C5MarketPriceSnapshotRefreshRequestParam;
import com.niro.web.entity.C5MarketPriceSnapshot;
import com.niro.web.enums.C5MarketPriceSnapshotDisplayModeEnum;
import com.niro.web.enums.C5MarketPriceSnapshotRangeTypeEnum;
import com.niro.web.enums.C5MarketPriceSnapshotStatusEnum;
import com.niro.web.manager.C5MarketPriceSnapshotMapperManager;
import com.niro.web.service.C5ApiClientService;
import com.niro.web.service.C5MarketPriceSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * C5 市场价格快照服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class C5MarketPriceSnapshotServiceImpl implements C5MarketPriceSnapshotService {

    private final C5MarketPriceSnapshotMapperManager snapshotManager;
    private final C5ApiClientService c5ApiClientService;
    private final RedissonClient redissonClient;
    private final RocketMqHelper rocketMqHelper;

    @Value("${c5.market-price.system-app-key:}")
    private String systemAppKey;

    private RRateLimiter rateLimiter;

    /**
     * 初始化 Redis 限流器资源。
     */
    @PostConstruct
    public void initRedisResources() {
        rateLimiter = redissonClient.getRateLimiter(C5MarketPriceSnapshotConstants.LIMITER_KEY);
        rateLimiter.setRate(RateType.OVERALL, 1, 2, RateIntervalUnit.SECONDS);
    }

    /**
     * 获取指定商品与磨损范围的价格快照参考。
     *
     * @param param 快照查询参数
     * @return 价格快照参考结果
     */
    @Override
    @Transactional
    public C5MarketPriceSnapshotReferenceDTO getReference(C5MarketPriceSnapshotReferenceParam param) {
        Assert.notNull(param, "快照查询参数不能为空");
        SnapshotKey key = resolveSnapshotKey(param.getMarketHashName(), param.getRangeType(), param.getWear(), param.getWearMin(), param.getWearMax());
        LocalDateTime now = LocalDateTime.now();
        C5MarketPriceSnapshot snapshot = getOrCreateSnapshot(key, now);
        touchSnapshot(snapshot, now, false);
        String displayMode = resolveDisplayMode(param.getDisplayMode(), key.rangeType(), param.getCurrentWear());
        int pageNum = normalizePageNum(param.getPageNum());
        int limit = normalizeLimit(param.getLimit(), displayMode);
        return toReferenceDTO(snapshotManager.getById(snapshot.getId()), displayMode, param.getCurrentWear(), param.getWearMin(), param.getWearMax(), pageNum, limit, now);
    }

    /**
     * 申请手动刷新价格快照并返回当前参考数据。
     *
     * @param param 刷新申请参数
     * @return 当前价格快照参考结果
     */
    @Override
    @Transactional
    public C5MarketPriceSnapshotReferenceDTO requestRefresh(C5MarketPriceSnapshotRefreshRequestParam param) {
        Assert.notNull(param, "刷新申请参数不能为空");
        SnapshotKey key = resolveSnapshotKey(param.getMarketHashName(), param.getRangeType(), param.getWear(), param.getWearMin(), param.getWearMax());
        LocalDateTime now = LocalDateTime.now();
        C5MarketPriceSnapshot snapshot = getOrCreateSnapshot(key, now);
        int pageNum = normalizePageNum(param.getPageNum());
        int limit = normalizeLimit(param.getLimit(), C5MarketPriceSnapshotDisplayModeEnum.PRICE_LOWEST.name());
        boolean canRaisePriority = tryAcquireManualRefreshThrottle(snapshot.getId());
        touchSnapshot(snapshot, now, canRaisePriority);
        if (canRaisePriority && snapshotManager.acquireRefreshing(snapshot.getId(), now)) {
            try {
                refreshSnapshot(snapshot.getId(), false);
            } catch (RuntimeException ignored) {
            }
        }
        return toReferenceDTO(snapshotManager.getById(snapshot.getId()), C5MarketPriceSnapshotDisplayModeEnum.PRICE_LOWEST.name(), null, param.getWearMin(), param.getWearMax(), pageNum, limit, now);
    }

    /**
     * 扫描到期快照并投递刷新消息。
     *
     * @return 成功入队的快照数量
     */
    @Override
    @Transactional
    public int scanAndEnqueueDueSnapshots() {
        LocalDateTime now = LocalDateTime.now();
        recoverTimeoutSnapshots(now);
        List<C5MarketPriceSnapshot> dueSnapshots = snapshotManager.listDueSnapshots(now, C5MarketPriceSnapshotConstants.SCAN_BATCH_SIZE);
        int enqueued = 0;
        for (C5MarketPriceSnapshot snapshot : dueSnapshots) {
            boolean refreshing = snapshotManager.acquireRefreshing(snapshot.getId(), now);
            if (refreshing) {
                rocketMqHelper.afterCommitSend(
                        MqConstant.TOPIC_C5_MARKET_PRICE_SNAPSHOT,
                        MqConstant.TAG_C5_MARKET_PRICE_SNAPSHOT_REFRESH,
                        snapshot.getId()
                );
                enqueued++;
            }
        }
        return enqueued;
    }

    /**
     * 消费快照刷新消息并执行刷新。
     *
     * @param snapshotId 快照ID
     */
    @Override
    public void consumeRefreshSnapshot(Long snapshotId) {
        Assert.notNull(snapshotId, "快照ID不能为空");
        refreshSnapshot(snapshotId, true);
    }


    /**
     * 查询或创建价格快照记录。
     *
     * @param key 快照归一化键
     * @param now 当前时间
     * @return 快照记录
     */
    private C5MarketPriceSnapshot getOrCreateSnapshot(SnapshotKey key, LocalDateTime now) {
        C5MarketPriceSnapshot existing = snapshotManager.getByQueryKey(C5MarketPriceSnapshotConstants.APP_ID_CS2, key.marketHashName(), key.rangeType(), key.wearMin(), key.wearMax());
        if (existing != null) {
            return existing;
        }
        C5MarketPriceSnapshot snapshot = new C5MarketPriceSnapshot();
        snapshot.setAppId(C5MarketPriceSnapshotConstants.APP_ID_CS2);
        snapshot.setMarketHashName(key.marketHashName());
        snapshot.setRangeType(key.rangeType());
        snapshot.setWearMin(key.wearMin());
        snapshot.setWearMax(key.wearMax());
        snapshot.setPageNum(C5MarketPriceSnapshotConstants.DEFAULT_PAGE_NUM);
        snapshot.setPageSize(C5MarketPriceSnapshotConstants.DEFAULT_PAGE_SIZE);
        snapshot.setLowestPrice(BigDecimal.ZERO);
        snapshot.setAvgPrice(BigDecimal.ZERO);
        snapshot.setSampleCount(0);
        snapshot.setHasMore(false);
        snapshot.setListingsJson(List.of());
        snapshot.setRefreshEnabled(true);
        snapshot.setRefreshPriority(0);
        snapshot.setRefreshIntervalSeconds(C5MarketPriceSnapshotConstants.REFRESH_INTERVAL_SECONDS);
        snapshot.setNextRefreshTime(now);
        snapshot.setLastFetchTime(C5MarketPriceSnapshotConstants.EPOCH_TIME);
        snapshot.setLastSuccessTime(C5MarketPriceSnapshotConstants.EPOCH_TIME);
        snapshot.setLastRequestTime(now);
        snapshot.setStatus(C5MarketPriceSnapshotStatusEnum.PENDING.name());
        snapshot.setFetchCount(0L);
        snapshot.setFailCount(0);
        snapshot.setLastErrorMessage("");
        snapshot.setLastFetchAccountId(0L);
        snapshot.setRefreshStartTime(C5MarketPriceSnapshotConstants.EPOCH_TIME);
        snapshot.setCreateTime(now);
        snapshot.setUpdateTime(now);
        snapshotManager.save(snapshot);
        return snapshot;
    }

    /**
     * 更新快照请求时间，并在需要时提升刷新优先级。
     *
     * @param snapshot 快照记录
     * @param now 当前时间
     * @param manualRefresh 是否手动刷新
     */
    private void touchSnapshot(C5MarketPriceSnapshot snapshot, LocalDateTime now, boolean manualRefresh) {
        boolean stale = isStale(snapshot, now);
        snapshotManager.lambdaUpdate()
                .eq(C5MarketPriceSnapshot::getId, snapshot.getId())
                .set(C5MarketPriceSnapshot::getLastRequestTime, now)
                .set(stale || manualRefresh, C5MarketPriceSnapshot::getNextRefreshTime, now)
                .set(manualRefresh, C5MarketPriceSnapshot::getRefreshPriority, C5MarketPriceSnapshotConstants.MANUAL_REFRESH_PRIORITY)
                .set(C5MarketPriceSnapshot::getUpdateTime, now)
                .update();
    }

    /**
     * 尝试获取手动刷新冷却许可。
     *
     * @param snapshotId 快照ID
     * @return 是否允许本次手动刷新
     */
    private boolean tryAcquireManualRefreshThrottle(Long snapshotId) {
        String throttleKey = C5MarketPriceSnapshotConstants.MANUAL_REFRESH_THROTTLE_KEY_PREFIX + snapshotId;
        return redissonClient.getBucket(throttleKey)
                .trySet("1", C5MarketPriceSnapshotConstants.MANUAL_REFRESH_COOLDOWN_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 恢复长时间停留在刷新中的快照。
     *
     * @param now 当前时间
     */
    private void recoverTimeoutSnapshots(LocalDateTime now) {
        LocalDateTime timeoutBefore = now.minusMinutes(5);
        List<C5MarketPriceSnapshot> snapshots = snapshotManager.listRefreshingTimeout(timeoutBefore, C5MarketPriceSnapshotConstants.TIMEOUT_RECOVER_BATCH_SIZE);
        for (C5MarketPriceSnapshot snapshot : snapshots) {
            int failCount = safeFailCount(snapshot) + 1;
            snapshotManager.lambdaUpdate()
                    .eq(C5MarketPriceSnapshot::getId, snapshot.getId())
                    .eq(C5MarketPriceSnapshot::getStatus, C5MarketPriceSnapshotStatusEnum.REFRESHING.name())
                    .set(C5MarketPriceSnapshot::getStatus, C5MarketPriceSnapshotStatusEnum.FAILED.name())
                    .set(C5MarketPriceSnapshot::getFailCount, failCount)
                    .set(C5MarketPriceSnapshot::getLastErrorMessage, "刷新任务超时")
                    .set(C5MarketPriceSnapshot::getLastFetchTime, now)
                    .set(C5MarketPriceSnapshot::getNextRefreshTime, now.plusMinutes(resolveBackoffMinutes(failCount)))
                    .set(C5MarketPriceSnapshot::getRefreshPriority, 0)
                    .set(C5MarketPriceSnapshot::getUpdateTime, now)
                    .update();
        }
    }

    /**
     * 执行单个价格快照刷新。
     *
     * @param snapshotId 快照ID
     * @param waitForPermit 是否等待限流许可
     */
    private void refreshSnapshot(Long snapshotId, boolean waitForPermit) {
        C5MarketPriceSnapshot snapshot = snapshotManager.getById(snapshotId);
        if (snapshot == null || !C5MarketPriceSnapshotStatusEnum.REFRESHING.name().equals(snapshot.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (StrUtil.isBlank(systemAppKey)) {
            markRefreshFailed(snapshot, now, "系统 C5 市场查询 AppKey 未配置");
            return;
        }
        if (waitForPermit) {
            rateLimiter.acquire();
            now = LocalDateTime.now();
        } else if (!rateLimiter.tryAcquire()) {
            snapshotManager.lambdaUpdate()
                    .eq(C5MarketPriceSnapshot::getId, snapshot.getId())
                    .set(C5MarketPriceSnapshot::getStatus, C5MarketPriceSnapshotStatusEnum.PENDING.name())
                    .set(C5MarketPriceSnapshot::getNextRefreshTime, now.plusSeconds(C5MarketPriceSnapshotConstants.RATE_LIMIT_RETRY_SECONDS))
                    .set(C5MarketPriceSnapshot::getUpdateTime, now)
                    .update();
            return;
        }
        try {
            C5ProductListResponse response = fetchSnapshotFromC5(snapshot);
            List<C5MarketPriceSnapshotListingDTO> listings = toListings(response, snapshot.getMarketHashName());
            writeRefreshSuccess(snapshot, listings, response, now);
        } catch (Exception e) {
            log.warn("刷新C5市场价格快照失败, snapshotId={}, marketHashName={}", snapshot.getId(), snapshot.getMarketHashName(), e);
            markRefreshFailed(snapshot, now, e);
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("刷新C5市场价格快照失败", e);
        }
    }

    /**
     * 从 C5 查询快照对应的市场挂单数据。
     *
     * @param snapshot 快照记录
     * @return C5 商品列表响应
     */
    private C5ProductListResponse fetchSnapshotFromC5(C5MarketPriceSnapshot snapshot) {
        if (C5MarketPriceSnapshotRangeTypeEnum.ALL.name().equals(snapshot.getRangeType())) {
            C5ProductListRequest request = new C5ProductListRequest()
                    .setAppId(snapshot.getAppId())
                    .setMarketHashName(snapshot.getMarketHashName())
                    .setPageNum(C5MarketPriceSnapshotConstants.DEFAULT_PAGE_NUM)
                    .setPageSize(C5MarketPriceSnapshotConstants.DEFAULT_PAGE_SIZE);
            return c5ApiClientService.getClientByAppKey(systemAppKey).getMarket().searchProductList(request);
        }
        C5ProductSearchRequest request = new C5ProductSearchRequest()
                .setAppId(snapshot.getAppId())
                .setMarketHashName(snapshot.getMarketHashName())
                .setWearMin(snapshot.getWearMin().doubleValue())
                .setWearMax(snapshot.getWearMax().doubleValue())
                .setPageNum(C5MarketPriceSnapshotConstants.DEFAULT_PAGE_NUM)
                .setPageSize(C5MarketPriceSnapshotConstants.DEFAULT_PAGE_SIZE);
        return c5ApiClientService.getClientByAppKey(systemAppKey).getMarket().productSearch(request);
    }

    /**
     * 写入快照刷新成功后的统计与挂单数据。
     *
     * @param snapshot 快照记录
     * @param listings 挂单列表
     * @param response C5 原始响应
     * @param now 当前时间
     */
    private void writeRefreshSuccess(C5MarketPriceSnapshot snapshot, List<C5MarketPriceSnapshotListingDTO> listings,
                                     C5ProductListResponse response, LocalDateTime now) {
        BigDecimal lowestPrice = listings.stream()
                .map(C5MarketPriceSnapshotListingDTO::getPrice)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        BigDecimal avgPrice = BigDecimal.ZERO;
        List<BigDecimal> prices = listings.stream()
                .map(C5MarketPriceSnapshotListingDTO::getPrice)
                .filter(Objects::nonNull)
                .toList();
        if (!prices.isEmpty()) {
            avgPrice = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(prices.size()), 2, RoundingMode.HALF_UP);
        }
        snapshotManager.lambdaUpdate()
                .eq(C5MarketPriceSnapshot::getId, snapshot.getId())
                .set(C5MarketPriceSnapshot::getStatus, C5MarketPriceSnapshotStatusEnum.SUCCESS.name())
                .set(C5MarketPriceSnapshot::getFailCount, 0)
                .set(C5MarketPriceSnapshot::getLastErrorMessage, "")
                .set(C5MarketPriceSnapshot::getLastFetchTime, now)
                .set(C5MarketPriceSnapshot::getLastSuccessTime, now)
                .set(C5MarketPriceSnapshot::getNextRefreshTime, now.plusSeconds(snapshot.getRefreshIntervalSeconds()))
                .set(C5MarketPriceSnapshot::getFetchCount, safeFetchCount(snapshot) + 1)
                .set(C5MarketPriceSnapshot::getRefreshPriority, 0)
                .set(C5MarketPriceSnapshot::getLowestPrice, lowestPrice)
                .set(C5MarketPriceSnapshot::getAvgPrice, avgPrice)
                .set(C5MarketPriceSnapshot::getSampleCount, listings.size())
                .set(C5MarketPriceSnapshot::getHasMore, response != null && Boolean.TRUE.equals(response.getHasMore()))
                .set(C5MarketPriceSnapshot::getListingsJson, toJsonObject(listings))
                .set(C5MarketPriceSnapshot::getLastFetchAccountId, 0L)
                .set(C5MarketPriceSnapshot::getUpdateTime, now)
                .update();
    }

    /**
     * 按错误消息标记快照刷新失败。
     *
     * @param snapshot 快照记录
     * @param now 当前时间
     * @param errorMessage 错误消息
     */
    private void markRefreshFailed(C5MarketPriceSnapshot snapshot, LocalDateTime now, String errorMessage) {
        markRefreshFailed(snapshot, now, errorMessage, false);
    }

    /**
     * 按异常信息标记快照刷新失败。
     *
     * @param snapshot 快照记录
     * @param now 当前时间
     * @param exception 刷新异常
     */
    private void markRefreshFailed(C5MarketPriceSnapshot snapshot, LocalDateTime now, Exception exception) {
        String errorMessage = exception == null ? "刷新失败" : exception.getMessage();
        markRefreshFailed(snapshot, now, errorMessage, isC5RateLimited(exception));
    }

    /**
     * 标记快照刷新失败并计算下次刷新时间。
     *
     * @param snapshot 快照记录
     * @param now 当前时间
     * @param errorMessage 错误消息
     * @param rateLimited 是否触发 C5 限流
     */
    private void markRefreshFailed(C5MarketPriceSnapshot snapshot, LocalDateTime now, String errorMessage, boolean rateLimited) {
        int failCount = safeFailCount(snapshot) + 1;
        snapshotManager.lambdaUpdate()
                .eq(C5MarketPriceSnapshot::getId, snapshot.getId())
                .set(C5MarketPriceSnapshot::getStatus, rateLimited ? C5MarketPriceSnapshotStatusEnum.PENDING.name() : C5MarketPriceSnapshotStatusEnum.FAILED.name())
                .set(C5MarketPriceSnapshot::getFailCount, failCount)
                .set(C5MarketPriceSnapshot::getLastErrorMessage, StrUtil.blankToDefault(StrUtil.maxLength(errorMessage, 500), "刷新失败"))
                .set(C5MarketPriceSnapshot::getLastFetchTime, now)
                .set(C5MarketPriceSnapshot::getFetchCount, safeFetchCount(snapshot) + 1)
                .set(C5MarketPriceSnapshot::getRefreshPriority, rateLimited ? snapshot.getRefreshPriority() : 0)
                .set(C5MarketPriceSnapshot::getNextRefreshTime, rateLimited ? now.plusSeconds(C5MarketPriceSnapshotConstants.RATE_LIMIT_RETRY_SECONDS) : now.plusMinutes(resolveBackoffMinutes(failCount)))
                .set(C5MarketPriceSnapshot::getLastFetchAccountId, 0L)
                .set(C5MarketPriceSnapshot::getUpdateTime, now)
                .update();
    }

    /**
     * 将对象转换为 PostgreSQL JSON 参数。
     *
     * @param value 待序列化对象
     * @return JSON 参数对象
     */
    private PGobject toJsonObject(Object value) {
        PGobject jsonObject = new PGobject();
        jsonObject.setType("json");
        try {
            jsonObject.setValue(JSONUtil.toJsonStr(value));
        } catch (SQLException e) {
            throw new IllegalStateException("构建JSON参数失败", e);
        }
        return jsonObject;
    }

    /**
     * 将 C5 响应转换为快照挂单列表。
     *
     * @param response C5 商品列表响应
     * @param marketHashName 市场 Hash 名称
     * @return 快照挂单列表
     */
    private List<C5MarketPriceSnapshotListingDTO> toListings(C5ProductListResponse response, String marketHashName) {
        if (response == null || CollUtil.isEmpty(response.getList())) {
            return List.of();
        }
        return response.getList().stream()
                .map(product -> toListing(product, marketHashName))
                .sorted(Comparator.comparing(C5MarketPriceSnapshotListingDTO::getPrice, Comparator.nullsLast(BigDecimal::compareTo))
                        .thenComparing(C5MarketPriceSnapshotListingDTO::getWear, Comparator.nullsLast(BigDecimal::compareTo)))
                .limit(C5MarketPriceSnapshotConstants.DEFAULT_PAGE_SIZE)
                .toList();
    }

    /**
     * 将 C5 商品项转换为快照挂单 DTO。
     *
     * @param product C5 商品项
     * @param marketHashName 市场 Hash 名称
     * @return 快照挂单 DTO
     */
    private C5MarketPriceSnapshotListingDTO toListing(C5ProductListResponse.ProductDTO product, String marketHashName) {
        C5MarketPriceSnapshotListingDTO dto = new C5MarketPriceSnapshotListingDTO();
        dto.setProductId(product.getProductId());
        dto.setPrice(product.getPrice());
        dto.setDelivery(product.getDelivery());
        dto.setAcceptBargain(product.getAcceptBargain());
        dto.setSellerUid(product.getSellerUid());
        dto.setImageUrl(product.getImg());
        dto.setMarketHashName(marketHashName);
        if (product.getAssetInfo() != null) {
            dto.setAssetId(product.getAssetInfo().getAssetId());
            Double wear = product.getAssetInfo().getWear() != null ? product.getAssetInfo().getWear() : product.getAssetInfo().getFloatWear();
            dto.setWear(wear == null ? null : BigDecimal.valueOf(wear));
        }
        return dto;
    }

    /**
     * 从快照 JSON 字段读取挂单列表。
     *
     * @param snapshot 快照记录
     * @return 快照挂单列表
     */
    @SuppressWarnings("unchecked")
    private List<C5MarketPriceSnapshotListingDTO> readListings(C5MarketPriceSnapshot snapshot) {
        if (snapshot == null || !(snapshot.getListingsJson() instanceof List<?> rawList)) {
            return List.of();
        }
        List<C5MarketPriceSnapshotListingDTO> listings = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof C5MarketPriceSnapshotListingDTO dto) {
                listings.add(dto);
            } else if (item instanceof java.util.Map<?, ?> map) {
                listings.add(mapToListing(map));
            }
        }
        return listings;
    }

    /**
     * 将 Map 形式的挂单数据转换为 DTO。
     *
     * @param map 挂单字段 Map
     * @return 快照挂单 DTO
     */
    private C5MarketPriceSnapshotListingDTO mapToListing(java.util.Map<?, ?> map) {
        C5MarketPriceSnapshotListingDTO dto = new C5MarketPriceSnapshotListingDTO();
        dto.setProductId(toString(map.get("productId")));
        dto.setPrice(toDecimal(map.get("price")));
        dto.setWear(toDecimal(map.get("wear")));
        dto.setDelivery(toInteger(map.get("delivery")));
        dto.setAcceptBargain(toBoolean(map.get("acceptBargain")));
        dto.setSellerUid(toString(map.get("sellerUid")));
        dto.setImageUrl(toString(map.get("imageUrl")));
        dto.setAssetId(toString(map.get("assetId")));
        dto.setMarketHashName(toString(map.get("marketHashName")));
        return dto;
    }

    /**
     * 构建价格快照参考返回对象。
     *
     * @param snapshot 快照记录
     * @param displayMode 展示模式
     * @param currentWear 当前磨损
     * @param filterWearMin 过滤最小磨损
     * @param filterWearMax 过滤最大磨损
     * @param pageNum 页码
     * @param limit 每页数量
     * @param now 当前时间
     * @return 价格快照参考结果
     */
    private C5MarketPriceSnapshotReferenceDTO toReferenceDTO(C5MarketPriceSnapshot snapshot, String displayMode,
                                                             BigDecimal currentWear, BigDecimal filterWearMin,
                                                             BigDecimal filterWearMax, int pageNum, int limit,
                                                             LocalDateTime now) {
        List<C5MarketPriceSnapshotListingDTO> listings = filterListingsByWear(readListings(snapshot), filterWearMin, filterWearMax);
        if (C5MarketPriceSnapshotDisplayModeEnum.WEAR_NEAREST.name().equals(displayMode) && currentWear != null) {
            listings = listings.stream()
                    .sorted(Comparator.comparing((C5MarketPriceSnapshotListingDTO listing) -> wearDistance(listing.getWear(), currentWear), Comparator.nullsLast(BigDecimal::compareTo))
                            .thenComparing(C5MarketPriceSnapshotListingDTO::getPrice, Comparator.nullsLast(BigDecimal::compareTo)))
                    .toList();
        }
        int fromIndex = Math.min((pageNum - 1) * limit, listings.size());
        int toIndex = Math.min(fromIndex + limit, listings.size());
        List<C5MarketPriceSnapshotListingDTO> pageListings = listings.subList(fromIndex, toIndex);
        C5MarketPriceSnapshotReferenceDTO dto = new C5MarketPriceSnapshotReferenceDTO();
        dto.setRecords(pageListings);
        dto.setLowestPrice(snapshot.getLowestPrice());
        dto.setAvgPrice(snapshot.getAvgPrice());
        dto.setSampleCount(snapshot.getSampleCount());
        dto.setHasMore(toIndex < listings.size());
        dto.setPageNum(pageNum);
        dto.setPageSize(limit);
        dto.setSnapshotStatus(snapshot.getStatus());
        dto.setLastSuccessTime(snapshot.getLastSuccessTime());
        dto.setRefreshIntervalSeconds(snapshot.getRefreshIntervalSeconds());
        dto.setStale(isStale(snapshot, now));
        dto.setNormalizedWearMin(snapshot.getWearMin());
        dto.setNormalizedWearMax(snapshot.getWearMax());
        dto.setDisplayMode(displayMode);
        dto.setMessage(resolveMessage(snapshot, now));
        return dto;
    }

    /**
     * 按磨损范围过滤挂单列表。
     *
     * @param listings 挂单列表
     * @param wearMin 最小磨损
     * @param wearMax 最大磨损
     * @return 过滤后的挂单列表
     */
    private List<C5MarketPriceSnapshotListingDTO> filterListingsByWear(List<C5MarketPriceSnapshotListingDTO> listings,
                                                                       BigDecimal wearMin,
                                                                       BigDecimal wearMax) {
        if (wearMin == null && wearMax == null) {
            return listings;
        }
        BigDecimal min = wearMin == null ? C5MarketPriceSnapshotConstants.MIN_WEAR : clampWear(wearMin);
        BigDecimal max = wearMax == null ? C5MarketPriceSnapshotConstants.MAX_WEAR : clampWear(wearMax);
        return listings.stream()
                .filter(listing -> listing.getWear() != null)
                .filter(listing -> listing.getWear().compareTo(min) >= 0 && listing.getWear().compareTo(max) <= 0)
                .toList();
    }

    /**
     * 解析并归一化快照查询键。
     *
     * @param marketHashName 市场 Hash 名称
     * @param rangeTypeText 范围类型文本
     * @param wear 单点磨损
     * @param wearMin 最小磨损
     * @param wearMax 最大磨损
     * @return 快照归一化键
     */
    private SnapshotKey resolveSnapshotKey(String marketHashName, String rangeTypeText, BigDecimal wear,
                                           BigDecimal wearMin, BigDecimal wearMax) {
        Assert.notBlank(marketHashName, "marketHashName不能为空");
        String normalizedName = StrUtil.trim(marketHashName);
        String rangeType = StrUtil.blankToDefault(rangeTypeText, wear == null && wearMin == null && wearMax == null
                ? C5MarketPriceSnapshotRangeTypeEnum.ALL.name()
                : C5MarketPriceSnapshotRangeTypeEnum.WEAR.name()).toUpperCase();
        Assert.isTrue(C5MarketPriceSnapshotRangeTypeEnum.ALL.name().equals(rangeType)
                || C5MarketPriceSnapshotRangeTypeEnum.WEAR.name().equals(rangeType), "rangeType不合法");
        if (C5MarketPriceSnapshotRangeTypeEnum.ALL.name().equals(rangeType)) {
            return new SnapshotKey(normalizedName, rangeType, C5MarketPriceSnapshotConstants.MIN_WEAR, C5MarketPriceSnapshotConstants.MAX_WEAR);
        }
        if (wearMin != null && wearMax != null) {
            Assert.isTrue(wearMin.compareTo(wearMax) <= 0, "最小磨损不能大于最大磨损");
            return new SnapshotKey(normalizedName, rangeType, clampWear(wearMin), clampWear(wearMax));
        }
        Assert.notNull(wear, "WEAR类型必须传入wear或wearMin/wearMax");
        return normalizeWearKey(normalizedName, rangeType, wear);
    }

    /**
     * 将单点磨损归入标准磨损区间。
     *
     * @param marketHashName 市场 Hash 名称
     * @param rangeType 范围类型
     * @param wear 单点磨损
     * @return 快照归一化键
     */
    private SnapshotKey normalizeWearKey(String marketHashName, String rangeType, BigDecimal wear) {
        BigDecimal clamped = clampWear(wear);
        if (clamped.compareTo(new BigDecimal("0.07000000")) < 0) {
            return new SnapshotKey(marketHashName, rangeType, new BigDecimal("0.00000000"), new BigDecimal("0.07000000"));
        }
        if (clamped.compareTo(new BigDecimal("0.15000000")) < 0) {
            return new SnapshotKey(marketHashName, rangeType, new BigDecimal("0.07000000"), new BigDecimal("0.15000000"));
        }
        if (clamped.compareTo(new BigDecimal("0.38000000")) < 0) {
            return new SnapshotKey(marketHashName, rangeType, new BigDecimal("0.15000000"), new BigDecimal("0.38000000"));
        }
        if (clamped.compareTo(new BigDecimal("0.45000000")) < 0) {
            return new SnapshotKey(marketHashName, rangeType, new BigDecimal("0.38000000"), new BigDecimal("0.45000000"));
        }
        return new SnapshotKey(marketHashName, rangeType, new BigDecimal("0.45000000"), new BigDecimal("1.00000000"));
    }

    /**
     * 解析快照列表展示模式。
     *
     * @param displayModeText 展示模式文本
     * @param rangeType 范围类型
     * @param currentWear 当前磨损
     * @return 展示模式
     */
    private String resolveDisplayMode(String displayModeText, String rangeType, BigDecimal currentWear) {
        String displayMode = StrUtil.blankToDefault(displayModeText, currentWear != null && C5MarketPriceSnapshotRangeTypeEnum.WEAR.name().equals(rangeType)
                ? C5MarketPriceSnapshotDisplayModeEnum.WEAR_NEAREST.name()
                : C5MarketPriceSnapshotDisplayModeEnum.PRICE_LOWEST.name()).toUpperCase();
        Assert.isTrue(C5MarketPriceSnapshotDisplayModeEnum.PRICE_LOWEST.name().equals(displayMode)
                || C5MarketPriceSnapshotDisplayModeEnum.WEAR_NEAREST.name().equals(displayMode), "displayMode不合法");
        return displayMode;
    }

    /**
     * 归一化分页数量。
     *
     * @param limit 请求数量
     * @param displayMode 展示模式
     * @return 分页数量
     */
    private int normalizeLimit(Integer limit, String displayMode) {
        if (limit == null || limit < 1) {
            return C5MarketPriceSnapshotDisplayModeEnum.WEAR_NEAREST.name().equals(displayMode) ? C5MarketPriceSnapshotConstants.NEAREST_LIMIT : C5MarketPriceSnapshotConstants.DEFAULT_LIMIT;
        }
        return Math.min(limit, C5MarketPriceSnapshotConstants.MAX_LIMIT);
    }

    /**
     * 归一化页码。
     *
     * @param pageNum 请求页码
     * @return 有效页码
     */
    private int normalizePageNum(Integer pageNum) {
        if (pageNum == null || pageNum < 1) {
            return 1;
        }
        return pageNum;
    }

    /**
     * 判断快照数据是否已过期。
     *
     * @param snapshot 快照记录
     * @param now 当前时间
     * @return 是否过期
     */
    private boolean isStale(C5MarketPriceSnapshot snapshot, LocalDateTime now) {
        if (snapshot.getLastSuccessTime() == null || !snapshot.getLastSuccessTime().isAfter(C5MarketPriceSnapshotConstants.EPOCH_TIME)) {
            return true;
        }
        return snapshot.getLastSuccessTime().plusSeconds(snapshot.getRefreshIntervalSeconds()).isBefore(now);
    }

    /**
     * 生成快照状态提示文案。
     *
     * @param snapshot 快照记录
     * @param now 当前时间
     * @return 状态提示文案
     */
    private String resolveMessage(C5MarketPriceSnapshot snapshot, LocalDateTime now) {
        if (snapshot.getSampleCount() == null || snapshot.getSampleCount() <= 0) {
            if (C5MarketPriceSnapshotStatusEnum.FAILED.name().equals(snapshot.getStatus())) {
                return "暂无价格参考，最近刷新失败";
            }
            return "暂无价格参考，已加入刷新队列";
        }
        String timeText = snapshot.getLastSuccessTime() == null || !snapshot.getLastSuccessTime().isAfter(C5MarketPriceSnapshotConstants.EPOCH_TIME)
                ? "--:--"
                : snapshot.getLastSuccessTime().format(C5MarketPriceSnapshotConstants.TIME_FORMATTER);
        if (C5MarketPriceSnapshotStatusEnum.REFRESHING.name().equals(snapshot.getStatus())) {
            return "价格约每5分钟刷新，上次更新 " + timeText + "，正在刷新价格参考";
        }
        if (C5MarketPriceSnapshotStatusEnum.FAILED.name().equals(snapshot.getStatus())) {
            return "价格约每5分钟刷新，上次更新 " + timeText + "，最近刷新失败";
        }
        if (isStale(snapshot, now)) {
            return "价格约每5分钟刷新，上次更新 " + timeText + "，正在排队刷新";
        }
        return "价格约每5分钟刷新，上次更新 " + timeText;
    }

    /**
     * 将磨损值限制在有效范围内。
     *
     * @param wear 磨损值
     * @return 有效磨损值
     */
    private BigDecimal clampWear(BigDecimal wear) {
        if (wear.compareTo(BigDecimal.ZERO) < 0) {
            return C5MarketPriceSnapshotConstants.MIN_WEAR;
        }
        if (wear.compareTo(BigDecimal.ONE) > 0) {
            return C5MarketPriceSnapshotConstants.MAX_WEAR;
        }
        return wear.setScale(8, RoundingMode.HALF_UP);
    }

    /**
     * 计算挂单磨损与当前磨损的距离。
     *
     * @param wear 挂单磨损
     * @param currentWear 当前磨损
     * @return 磨损距离
     */
    private BigDecimal wearDistance(BigDecimal wear, BigDecimal currentWear) {
        return wear == null || currentWear == null ? null : wear.subtract(currentWear).abs();
    }

    /**
     * 读取安全的抓取次数。
     *
     * @param snapshot 快照记录
     * @return 抓取次数
     */
    private long safeFetchCount(C5MarketPriceSnapshot snapshot) {
        return snapshot.getFetchCount() == null ? 0L : snapshot.getFetchCount();
    }

    /**
     * 读取安全的失败次数。
     *
     * @param snapshot 快照记录
     * @return 失败次数
     */
    private int safeFailCount(C5MarketPriceSnapshot snapshot) {
        return snapshot.getFailCount() == null ? 0 : snapshot.getFailCount();
    }

    /**
     * 根据失败次数计算退避分钟数。
     *
     * @param failCount 失败次数
     * @return 退避分钟数
     */
    private long resolveBackoffMinutes(int failCount) {
        return Math.min(5L * failCount, 30L);
    }

    /**
     * 判断异常是否为 C5 限流响应。
     *
     * @param exception 异常对象
     * @return 是否限流
     */
    private boolean isC5RateLimited(Exception exception) {
        return exception instanceof C5HttpException c5HttpException
                && c5HttpException.getStatusCode() == C5MarketPriceSnapshotConstants.TOO_MANY_REQUESTS_STATUS_CODE;
    }

    /**
     * 将对象转换为字符串。
     *
     * @param value 原始值
     * @return 字符串值
     */
    private String toString(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * 将对象转换为金额或磨损数值。
     *
     * @param value 原始值
     * @return 数值对象
     */
    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    /**
     * 将对象转换为整数。
     *
     * @param value 原始值
     * @return 整数值
     */
    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(value.toString());
    }

    /**
     * 将对象转换为布尔值。
     *
     * @param value 原始值
     * @return 布尔值
     */
    private Boolean toBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.valueOf(value.toString());
    }

    private record SnapshotKey(String marketHashName, String rangeType, BigDecimal wearMin, BigDecimal wearMax) {
    }
}
