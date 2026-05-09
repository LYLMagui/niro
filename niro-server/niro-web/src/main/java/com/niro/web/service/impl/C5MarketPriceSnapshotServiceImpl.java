package com.niro.web.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.niro.core.constant.MqConstant;
import com.niro.core.util.Assert;
import com.niro.core.util.RocketMqHelper;
import com.niro.sdk.c5.market.C5ProductListRequest;
import com.niro.sdk.c5.market.C5ProductSearchRequest;
import com.niro.sdk.c5.market.C5ProductListResponse;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * C5 市场价格快照服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class C5MarketPriceSnapshotServiceImpl implements C5MarketPriceSnapshotService {

    private static final int APP_ID_CS2 = 730;
    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int DEFAULT_LIMIT = 10;
    private static final int NEAREST_LIMIT = 5;
    private static final int MAX_LIMIT = 50;
    private static final int REFRESH_INTERVAL_SECONDS = 300;
    private static final int MANUAL_REFRESH_PRIORITY = 100;
    private static final int MANUAL_REFRESH_COOLDOWN_SECONDS = 60;
    private static final int SCAN_BATCH_SIZE = 60;
    private static final int TIMEOUT_RECOVER_BATCH_SIZE = 100;
    private static final String LIMITER_KEY = "c5:market-price:products-search:rate-limit";
    private static final BigDecimal MIN_WEAR = BigDecimal.ZERO.setScale(8, RoundingMode.UNNECESSARY);
    private static final BigDecimal MAX_WEAR = BigDecimal.ONE.setScale(8, RoundingMode.UNNECESSARY);
    private static final LocalDateTime EPOCH_TIME = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final C5MarketPriceSnapshotMapperManager snapshotManager;
    private final C5ApiClientService c5ApiClientService;
    private final RedissonClient redissonClient;
    private final RocketMqHelper rocketMqHelper;

    @Value("${c5.market-price.system-app-key:}")
    private String systemAppKey;

    private RRateLimiter rateLimiter;

    @PostConstruct
    public void initRedisResources() {
        rateLimiter = redissonClient.getRateLimiter(LIMITER_KEY);
        rateLimiter.trySetRate(RateType.OVERALL, 1, 1, RateIntervalUnit.SECONDS);
    }

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

    @Override
    @Transactional
    public C5MarketPriceSnapshotReferenceDTO requestRefresh(C5MarketPriceSnapshotRefreshRequestParam param) {
        Assert.notNull(param, "刷新申请参数不能为空");
        SnapshotKey key = resolveSnapshotKey(param.getMarketHashName(), param.getRangeType(), param.getWear(), param.getWearMin(), param.getWearMax());
        LocalDateTime now = LocalDateTime.now();
        C5MarketPriceSnapshot snapshot = getOrCreateSnapshot(key, now);
        int pageNum = normalizePageNum(param.getPageNum());
        int limit = normalizeLimit(param.getLimit(), C5MarketPriceSnapshotDisplayModeEnum.PRICE_LOWEST.name());
        boolean hasUsableData = snapshot.getSampleCount() != null && snapshot.getSampleCount() > 0
                && snapshot.getLastSuccessTime() != null && snapshot.getLastSuccessTime().isAfter(EPOCH_TIME);
        if (!hasUsableData) {
            refreshSnapshotNow(snapshot, now);
            return toReferenceDTO(snapshotManager.getById(snapshot.getId()), C5MarketPriceSnapshotDisplayModeEnum.PRICE_LOWEST.name(), null, param.getWearMin(), param.getWearMax(), pageNum, limit, LocalDateTime.now());
        }
        boolean canRaisePriority = snapshot.getLastRequestTime() == null
                || snapshot.getLastRequestTime().isBefore(now.minusSeconds(MANUAL_REFRESH_COOLDOWN_SECONDS));
        touchSnapshot(snapshot, now, canRaisePriority);
        return toReferenceDTO(snapshotManager.getById(snapshot.getId()), C5MarketPriceSnapshotDisplayModeEnum.PRICE_LOWEST.name(), null, param.getWearMin(), param.getWearMax(), pageNum, limit, now);
    }

    @Override
    @Transactional
    public int scanAndEnqueueDueSnapshots() {
        LocalDateTime now = LocalDateTime.now();
        recoverTimeoutSnapshots(now);
        List<C5MarketPriceSnapshot> dueSnapshots = snapshotManager.listDueSnapshots(now, SCAN_BATCH_SIZE);
        int enqueued = 0;
        for (C5MarketPriceSnapshot snapshot : dueSnapshots) {
            if (snapshotManager.acquireRefreshing(snapshot.getId(), now)) {
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

    @Override
    public void consumeRefreshSnapshot(Long snapshotId) {
        Assert.notNull(snapshotId, "快照ID不能为空");
        refreshSnapshot(snapshotId);
    }


    private C5MarketPriceSnapshot getOrCreateSnapshot(SnapshotKey key, LocalDateTime now) {
        C5MarketPriceSnapshot existing = snapshotManager.getByQueryKey(APP_ID_CS2, key.marketHashName(), key.rangeType(), key.wearMin(), key.wearMax());
        if (existing != null) {
            return existing;
        }
        C5MarketPriceSnapshot snapshot = new C5MarketPriceSnapshot();
        snapshot.setAppId(APP_ID_CS2);
        snapshot.setMarketHashName(key.marketHashName());
        snapshot.setRangeType(key.rangeType());
        snapshot.setWearMin(key.wearMin());
        snapshot.setWearMax(key.wearMax());
        snapshot.setPageNum(DEFAULT_PAGE_NUM);
        snapshot.setPageSize(DEFAULT_PAGE_SIZE);
        snapshot.setLowestPrice(BigDecimal.ZERO);
        snapshot.setAvgPrice(BigDecimal.ZERO);
        snapshot.setSampleCount(0);
        snapshot.setHasMore(false);
        snapshot.setListingsJson(List.of());
        snapshot.setRefreshEnabled(true);
        snapshot.setRefreshPriority(0);
        snapshot.setRefreshIntervalSeconds(REFRESH_INTERVAL_SECONDS);
        snapshot.setNextRefreshTime(now);
        snapshot.setLastFetchTime(EPOCH_TIME);
        snapshot.setLastSuccessTime(EPOCH_TIME);
        snapshot.setLastRequestTime(now);
        snapshot.setStatus(C5MarketPriceSnapshotStatusEnum.PENDING.name());
        snapshot.setFetchCount(0L);
        snapshot.setFailCount(0);
        snapshot.setLastErrorMessage("");
        snapshot.setLastFetchAccountId(0L);
        snapshot.setRefreshStartTime(EPOCH_TIME);
        snapshot.setCreateTime(now);
        snapshot.setUpdateTime(now);
        snapshotManager.save(snapshot);
        return snapshot;
    }

    private void touchSnapshot(C5MarketPriceSnapshot snapshot, LocalDateTime now, boolean manualRefresh) {
        boolean stale = isStale(snapshot, now);
        snapshotManager.lambdaUpdate()
                .eq(C5MarketPriceSnapshot::getId, snapshot.getId())
                .set(C5MarketPriceSnapshot::getLastRequestTime, now)
                .set(stale || manualRefresh, C5MarketPriceSnapshot::getNextRefreshTime, now)
                .set(manualRefresh, C5MarketPriceSnapshot::getRefreshPriority, MANUAL_REFRESH_PRIORITY)
                .set(C5MarketPriceSnapshot::getUpdateTime, now)
                .update();
    }

    private void recoverTimeoutSnapshots(LocalDateTime now) {
        LocalDateTime timeoutBefore = now.minusMinutes(5);
        List<C5MarketPriceSnapshot> snapshots = snapshotManager.listRefreshingTimeout(timeoutBefore, TIMEOUT_RECOVER_BATCH_SIZE);
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

    private void refreshSnapshot(Long snapshotId) {
        C5MarketPriceSnapshot snapshot = snapshotManager.getById(snapshotId);
        if (snapshot == null || !C5MarketPriceSnapshotStatusEnum.REFRESHING.name().equals(snapshot.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (StrUtil.isBlank(systemAppKey)) {
            markRefreshFailed(snapshot, now, "系统 C5 市场查询 AppKey 未配置");
            return;
        }
        if (!rateLimiter.tryAcquire()) {
            snapshotManager.lambdaUpdate()
                    .eq(C5MarketPriceSnapshot::getId, snapshot.getId())
                    .set(C5MarketPriceSnapshot::getStatus, C5MarketPriceSnapshotStatusEnum.PENDING.name())
                    .set(C5MarketPriceSnapshot::getNextRefreshTime, now.plusSeconds(10))
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
            markRefreshFailed(snapshot, now, StrUtil.maxLength(e.getMessage(), 500));
        }
    }

    private void refreshSnapshotNow(C5MarketPriceSnapshot snapshot, LocalDateTime now) {
        if (StrUtil.isBlank(systemAppKey)) {
            markRefreshFailed(snapshot, now, "系统 C5 市场查询 AppKey 未配置");
            return;
        }
        if (!rateLimiter.tryAcquire()) {
            snapshotManager.lambdaUpdate()
                    .eq(C5MarketPriceSnapshot::getId, snapshot.getId())
                    .set(C5MarketPriceSnapshot::getStatus, C5MarketPriceSnapshotStatusEnum.PENDING.name())
                    .set(C5MarketPriceSnapshot::getNextRefreshTime, now.plusSeconds(10))
                    .set(C5MarketPriceSnapshot::getLastRequestTime, now)
                    .set(C5MarketPriceSnapshot::getUpdateTime, now)
                    .update();
            return;
        }
        snapshotManager.lambdaUpdate()
                .eq(C5MarketPriceSnapshot::getId, snapshot.getId())
                .set(C5MarketPriceSnapshot::getStatus, C5MarketPriceSnapshotStatusEnum.REFRESHING.name())
                .set(C5MarketPriceSnapshot::getRefreshStartTime, now)
                .set(C5MarketPriceSnapshot::getLastRequestTime, now)
                .set(C5MarketPriceSnapshot::getUpdateTime, now)
                .update();
        try {
            C5ProductListResponse response = fetchSnapshotFromC5(snapshot);
            List<C5MarketPriceSnapshotListingDTO> listings = toListings(response, snapshot.getMarketHashName());
            writeRefreshSuccess(snapshot, listings, response, now);
        } catch (Exception e) {
            log.warn("手动刷新C5市场价格快照失败, snapshotId={}, marketHashName={}", snapshot.getId(), snapshot.getMarketHashName(), e);
            markRefreshFailed(snapshot, now, StrUtil.maxLength(e.getMessage(), 500));
        }
    }

    private C5ProductListResponse fetchSnapshotFromC5(C5MarketPriceSnapshot snapshot) {
        if (C5MarketPriceSnapshotRangeTypeEnum.ALL.name().equals(snapshot.getRangeType())) {
            C5ProductListRequest request = new C5ProductListRequest()
                    .setAppId(snapshot.getAppId())
                    .setMarketHashName(snapshot.getMarketHashName())
                    .setPageNum(DEFAULT_PAGE_NUM)
                    .setPageSize(DEFAULT_PAGE_SIZE);
            return c5ApiClientService.getClientByAppKey(systemAppKey).getMarket().searchProductList(request);
        }
        C5ProductSearchRequest request = new C5ProductSearchRequest()
                .setAppId(snapshot.getAppId())
                .setMarketHashName(snapshot.getMarketHashName())
                .setWearMin(snapshot.getWearMin().doubleValue())
                .setWearMax(snapshot.getWearMax().doubleValue())
                .setPageNum(DEFAULT_PAGE_NUM)
                .setPageSize(DEFAULT_PAGE_SIZE);
        return c5ApiClientService.getClientByAppKey(systemAppKey).getMarket().productSearch(request);
    }

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

    private void markRefreshFailed(C5MarketPriceSnapshot snapshot, LocalDateTime now, String errorMessage) {
        int failCount = safeFailCount(snapshot) + 1;
        snapshotManager.lambdaUpdate()
                .eq(C5MarketPriceSnapshot::getId, snapshot.getId())
                .set(C5MarketPriceSnapshot::getStatus, C5MarketPriceSnapshotStatusEnum.FAILED.name())
                .set(C5MarketPriceSnapshot::getFailCount, failCount)
                .set(C5MarketPriceSnapshot::getLastErrorMessage, StrUtil.blankToDefault(errorMessage, "刷新失败"))
                .set(C5MarketPriceSnapshot::getLastFetchTime, now)
                .set(C5MarketPriceSnapshot::getFetchCount, safeFetchCount(snapshot) + 1)
                .set(C5MarketPriceSnapshot::getRefreshPriority, 0)
                .set(C5MarketPriceSnapshot::getNextRefreshTime, now.plusMinutes(resolveBackoffMinutes(failCount)))
                .set(C5MarketPriceSnapshot::getLastFetchAccountId, 0L)
                .set(C5MarketPriceSnapshot::getUpdateTime, now)
                .update();
    }

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

    private List<C5MarketPriceSnapshotListingDTO> toListings(C5ProductListResponse response, String marketHashName) {
        if (response == null || CollUtil.isEmpty(response.getList())) {
            return List.of();
        }
        return response.getList().stream()
                .map(product -> toListing(product, marketHashName))
                .sorted(Comparator.comparing(C5MarketPriceSnapshotListingDTO::getPrice, Comparator.nullsLast(BigDecimal::compareTo))
                        .thenComparing(C5MarketPriceSnapshotListingDTO::getWear, Comparator.nullsLast(BigDecimal::compareTo)))
                .limit(DEFAULT_PAGE_SIZE)
                .toList();
    }

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

    private List<C5MarketPriceSnapshotListingDTO> filterListingsByWear(List<C5MarketPriceSnapshotListingDTO> listings,
                                                                       BigDecimal wearMin,
                                                                       BigDecimal wearMax) {
        if (wearMin == null && wearMax == null) {
            return listings;
        }
        BigDecimal min = wearMin == null ? MIN_WEAR : clampWear(wearMin);
        BigDecimal max = wearMax == null ? MAX_WEAR : clampWear(wearMax);
        return listings.stream()
                .filter(listing -> listing.getWear() != null)
                .filter(listing -> listing.getWear().compareTo(min) >= 0 && listing.getWear().compareTo(max) <= 0)
                .toList();
    }

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
            return new SnapshotKey(normalizedName, rangeType, MIN_WEAR, MAX_WEAR);
        }
        if (wearMin != null && wearMax != null) {
            Assert.isTrue(wearMin.compareTo(wearMax) <= 0, "最小磨损不能大于最大磨损");
            return new SnapshotKey(normalizedName, rangeType, clampWear(wearMin), clampWear(wearMax));
        }
        Assert.notNull(wear, "WEAR类型必须传入wear或wearMin/wearMax");
        return normalizeWearKey(normalizedName, rangeType, wear);
    }

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

    private String resolveDisplayMode(String displayModeText, String rangeType, BigDecimal currentWear) {
        String displayMode = StrUtil.blankToDefault(displayModeText, currentWear != null && C5MarketPriceSnapshotRangeTypeEnum.WEAR.name().equals(rangeType)
                ? C5MarketPriceSnapshotDisplayModeEnum.WEAR_NEAREST.name()
                : C5MarketPriceSnapshotDisplayModeEnum.PRICE_LOWEST.name()).toUpperCase();
        Assert.isTrue(C5MarketPriceSnapshotDisplayModeEnum.PRICE_LOWEST.name().equals(displayMode)
                || C5MarketPriceSnapshotDisplayModeEnum.WEAR_NEAREST.name().equals(displayMode), "displayMode不合法");
        return displayMode;
    }

    private int normalizeLimit(Integer limit, String displayMode) {
        if (limit == null || limit < 1) {
            return C5MarketPriceSnapshotDisplayModeEnum.WEAR_NEAREST.name().equals(displayMode) ? NEAREST_LIMIT : DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private int normalizePageNum(Integer pageNum) {
        if (pageNum == null || pageNum < 1) {
            return 1;
        }
        return pageNum;
    }

    private boolean isStale(C5MarketPriceSnapshot snapshot, LocalDateTime now) {
        if (snapshot.getLastSuccessTime() == null || !snapshot.getLastSuccessTime().isAfter(EPOCH_TIME)) {
            return true;
        }
        return snapshot.getLastSuccessTime().plusSeconds(snapshot.getRefreshIntervalSeconds()).isBefore(now);
    }

    private String resolveMessage(C5MarketPriceSnapshot snapshot, LocalDateTime now) {
        if (snapshot.getSampleCount() == null || snapshot.getSampleCount() <= 0) {
            if (C5MarketPriceSnapshotStatusEnum.FAILED.name().equals(snapshot.getStatus())) {
                return "暂无价格参考，最近刷新失败";
            }
            return "暂无价格参考，已加入刷新队列";
        }
        String timeText = snapshot.getLastSuccessTime() == null || !snapshot.getLastSuccessTime().isAfter(EPOCH_TIME)
                ? "--:--"
                : snapshot.getLastSuccessTime().format(TIME_FORMATTER);
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

    private BigDecimal clampWear(BigDecimal wear) {
        if (wear.compareTo(BigDecimal.ZERO) < 0) {
            return MIN_WEAR;
        }
        if (wear.compareTo(BigDecimal.ONE) > 0) {
            return MAX_WEAR;
        }
        return wear.setScale(8, RoundingMode.HALF_UP);
    }

    private BigDecimal wearDistance(BigDecimal wear, BigDecimal currentWear) {
        return wear == null || currentWear == null ? null : wear.subtract(currentWear).abs();
    }

    private long safeFetchCount(C5MarketPriceSnapshot snapshot) {
        return snapshot.getFetchCount() == null ? 0L : snapshot.getFetchCount();
    }

    private int safeFailCount(C5MarketPriceSnapshot snapshot) {
        return snapshot.getFailCount() == null ? 0 : snapshot.getFailCount();
    }

    private long resolveBackoffMinutes(int failCount) {
        return Math.min(5L * failCount, 30L);
    }

    private String toString(Object value) {
        return value == null ? null : value.toString();
    }

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

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(value.toString());
    }

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
