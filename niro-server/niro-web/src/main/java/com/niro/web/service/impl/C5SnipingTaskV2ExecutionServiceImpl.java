package com.niro.web.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Method;
import com.niro.core.util.Assert;
import com.niro.sdk.c5.client.C5ApiClient;
import com.niro.sdk.c5.constant.C5GameAPI;
import com.niro.sdk.c5.market.C5ProductListRequest;
import com.niro.sdk.c5.market.C5ProductSearchRequest;
import com.niro.sdk.c5.trade.C5BatchBuyRequest;
import com.niro.sdk.c5.account.C5BalanceResponse;
import com.niro.sdk.c5.market.C5ProductListResponse;
import com.niro.sdk.c5.trade.C5BatchBuyResponse;
import com.niro.web.dto.C5SnipingTaskV2EventDTO;
import com.niro.web.entity.*;
import com.niro.web.enums.*;
import com.niro.web.manager.*;
import com.niro.web.service.C5ApiClientService;
import com.niro.web.service.C5SnipingAccountService;
import com.niro.web.service.C5SnipingTaskV2EventService;
import com.niro.web.service.C5SnipingTaskV2ExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * C5 扫货 2.0 单轮执行服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class C5SnipingTaskV2ExecutionServiceImpl implements C5SnipingTaskV2ExecutionService {

    private static final int APP_ID_CS2 = 730;
    private static final int LISTING_PAGE_SIZE = 20;
    private static final int MAX_ERROR_COUNT = 3;
    private static final long ACCOUNT_COOLDOWN_SECONDS = 30L;
    private static final String ACCOUNT_IN_FLIGHT_LOCK_KEY_PREFIX = "niro:c5:sniping:v2:account-in-flight:";
    private static final long ACCOUNT_IN_FLIGHT_LOCK_WAIT_SECONDS = 0L;
    private static final long INIT_ATTEMPT_TTL_SECONDS = 60L;
    private static final BigDecimal GLOBAL_MAX_PRICE = new BigDecimal("999999");
    private static final long DEDUP_TTL_MS = 60_000L;

    private final C5ApiClientService c5ApiClientService;
    private final Cs2GoodsMapperManager cs2GoodsMapperManager;
    private final C5SnipingTaskV2MapperManager taskManager;
    private final C5SnipingAccountMapperManager accountManager;
    private final C5SnipingTaskRunV2MapperManager runManager;
    private final C5SnipingHitRecordV2MapperManager hitRecordManager;
    private final C5SnipingBuyAttemptV2MapperManager buyAttemptManager;
    private final C5SnipingAccountRuntimeV2MapperManager accountRuntimeManager;
    private final TradeOrderRecordMapperManager tradeOrderRecordManager;
    private final C5SnipingTaskV2EventService eventService;
    private final C5SnipingAccountService c5SnipingAccountService;
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;
    private final Map<String, Long> recentDedup = new ConcurrentHashMap<>();

    /**
     * 执行一轮扫描、命中、下单和停止条件判断。
     *
     * @param task 任务定义
     * @param run 运行实例
     * @return 执行结果
     */
    @Override
    public C5SnipingTaskV2ExecutionResult executeOneCycle(C5SnipingTaskV2 task, C5SnipingTaskRunV2 run) {
        try {
            C5SnipingTaskV2 latestTask = taskManager.getById(task.getId());
            if (latestTask == null || !latestTask.getTaskStatus().equals(task.getTaskStatus())) {
                return C5SnipingTaskV2ExecutionResult.stopped("TASK_STATUS_CHANGED");
            }
            C5SnipingTaskV2ExecutionResult balanceStop = checkBalanceStop(latestTask);
            if (balanceStop.isStopTask()) {
                return balanceStop;
            }

            Cs2Goods goods = cs2GoodsMapperManager.getEnabledById(latestTask.getCs2GoodsId());
            Assert.notNull(goods, "CS2商品不存在或未启用");
            Assert.notBlank(goods.getMarketHashName(), "CS2商品 MarketHashName 为空");
            C5SnipingAccount account = requireAvailableAccount(latestTask.getAccountId());
            C5ApiClient client = c5ApiClientService.getClientByAppKey(c5SnipingAccountService.decryptAccountAppKey(account));
            List<C5ProductListResponse.ProductDTO> listings = searchListings(client, goods, latestTask);
            List<C5ProductListResponse.ProductDTO> hits = listings.stream()
                    .filter(item -> item.getProductId() != null)
                    .filter(item -> item.getPrice() != null && item.getPrice().compareTo(latestTask.getMaxPrice()) <= 0)
                    .filter(item -> matchPaintwear(item, latestTask, goods))
                    .sorted(Comparator.comparing(C5ProductListResponse.ProductDTO::getPrice))
                    .collect(Collectors.toList());
            if (CollUtil.isEmpty(hits)) {
                return checkCompletion(latestTask);
            }

            int writtenHits = 0;
            for (C5ProductListResponse.ProductDTO listing : hits) {
                String listingId = StrUtil.trim(listing.getProductId());
                if (StrUtil.isBlank(listingId) || isDuplicateHit(latestTask.getAccountId(), listingId)) {
                    continue;
                }
                C5SnipingHitRecordV2 hitRecord = saveHitRecord(latestTask, run, listing);
                writtenHits++;
                taskManager.incrementHitCount(latestTask.getId(), 1);
                runManager.incrementHitCount(run.getId(), 1);
                publishEvent(latestTask, "HIT_RECORD_CREATED", run.getId(), hitRecord.getId(), null, null);
                attemptBuy(client, account, latestTask, run, goods, listing, hitRecord);
                C5SnipingTaskV2ExecutionResult completion = checkCompletion(taskManager.getById(latestTask.getId()));
                if (completion.isStopTask()) {
                    return completion;
                }
            }

            if (writtenHits > 0) {
                publishEvent(latestTask, "TASK_PROGRESS", run.getId(), null, null, null);
            }
            return checkCompletion(taskManager.getById(latestTask.getId()));
        } catch (Exception e) {
            String message = StrUtil.maxLength(StrUtil.blankToDefault(e.getMessage(), "扫货执行异常"), 500);
            runManager.markCycleError(run.getId(), message);
            C5SnipingTaskRunV2 latestRun = runManager.getById(run.getId());
            if (latestRun != null && latestRun.getConsecutiveErrorCount() != null
                    && latestRun.getConsecutiveErrorCount() >= MAX_ERROR_COUNT) {
                return C5SnipingTaskV2ExecutionResult.error("MAX_RETRY_EXCEEDED", message);
            }
            log.warn("C5扫货2.0单轮执行异常: taskId={}, runId={}, msg={}", task.getId(), run.getId(), message);
            return C5SnipingTaskV2ExecutionResult.continueRunning();
        }
    }

    private C5SnipingAccount requireAvailableAccount(Long accountId) {
        C5SnipingAccount account = accountManager.getAvailableAccount(accountId);
        Assert.notNull(account, "C5扫货账号不存在或不可用");
        Assert.notBlank(account.getC5AppKeyEncrypted(), "账号未配置 C5 AppKey");
        Assert.notBlank(account.getSteamTradeUrl(), "账号未配置 Steam 交易链接");
        return account;
    }

    private List<C5ProductListResponse.ProductDTO> searchListings(C5ApiClient client, Cs2Goods goods, C5SnipingTaskV2 task) {
        boolean nonWearable = !Boolean.TRUE.equals(goods.getHasExterior());
        C5ProductListResponse response;
        if (nonWearable) {
            C5ProductListRequest request = new C5ProductListRequest()
                    .setAppId(APP_ID_CS2)
                    .setMarketHashName(goods.getMarketHashName())
                    .setPageNum(1)
                    .setPageSize(LISTING_PAGE_SIZE);
            response = client.getMarket().searchProductList(request);
            logProductListResult(task, request, response);
        } else {
            C5ProductSearchRequest request = new C5ProductSearchRequest()
                    .setAppId(APP_ID_CS2)
                    .setMarketHashName(goods.getMarketHashName())
                    .setWearMin(task.getMinPaintwear() == null ? null : task.getMinPaintwear().doubleValue())
                    .setWearMax(task.getMaxPaintwear() == null ? null : task.getMaxPaintwear().doubleValue())
                    .setPriceMax(GLOBAL_MAX_PRICE)
                    .setPageNum(1)
                    .setPageSize(LISTING_PAGE_SIZE);
            response = client.getMarket().productSearch(request);
        }
        return response == null || response.getList() == null ? List.of() : response.getList();
    }

    private void logProductListResult(C5SnipingTaskV2 task, C5ProductListRequest request, C5ProductListResponse response) {
        BigDecimal minPrice = response == null || response.getList() == null ? null : response.getList().stream()
                .map(C5ProductListResponse.ProductDTO::getPrice)
                .filter(price -> price != null)
                .min(BigDecimal::compareTo)
                .orElse(null);
        log.info("C5扫货products/list查询: userId={}, taskId={}, requestUrl='{} {}', appId={}, marketHashName={}, pageNum={}, pageSize={}, currentMinPrice={}, configuredMaxPrice={}",
                task.getUserId(), task.getId(), Method.POST, C5GameAPI.Market.PRODUCT_LIST,
                request.getAppId(), request.getMarketHashName(), request.getPageNum(), request.getPageSize(), minPrice, task.getMaxPrice());
    }

    private boolean matchPaintwear(C5ProductListResponse.ProductDTO item, C5SnipingTaskV2 task, Cs2Goods goods) {
        if (!Boolean.TRUE.equals(goods.getHasExterior())) {
            return true;
        }
        if (task.getMinPaintwear() == null && task.getMaxPaintwear() == null) {
            return true;
        }
        BigDecimal paintwear = resolvePaintwear(item);
        if (paintwear == null) {
            return false;
        }
        if (task.getMinPaintwear() != null && paintwear.compareTo(task.getMinPaintwear()) < 0) {
            return false;
        }
        return task.getMaxPaintwear() == null || paintwear.compareTo(task.getMaxPaintwear()) <= 0;
    }

    private BigDecimal resolvePaintwear(C5ProductListResponse.ProductDTO item) {
        if (item.getAssetInfo() == null) {
            return null;
        }
        Double wear = item.getAssetInfo().getFloatWear();
        if (wear == null) {
            wear = item.getAssetInfo().getWear();
        }
        return wear == null ? null : BigDecimal.valueOf(wear);
    }

    private boolean isDuplicateHit(Long accountId, String listingId) {
        cleanupDedup();
        String key = accountId + ":" + listingId;
        Long previous = recentDedup.putIfAbsent(key, System.currentTimeMillis());
        return previous != null;
    }

    private void cleanupDedup() {
        long expireBefore = System.currentTimeMillis() - DEDUP_TTL_MS;
        recentDedup.entrySet().removeIf(entry -> entry.getValue() < expireBefore);
    }

    private C5SnipingHitRecordV2 saveHitRecord(C5SnipingTaskV2 task, C5SnipingTaskRunV2 run, C5ProductListResponse.ProductDTO listing) {
        C5SnipingHitRecordV2 record = new C5SnipingHitRecordV2();
        record.setTaskId(task.getId());
        record.setRunId(run.getId());
        record.setAccountId(task.getAccountId());
        record.setListingId(listing.getProductId());
        record.setListingPrice(listing.getPrice());
        record.setPaintwear(resolvePaintwear(listing));
        record.setDecisionResult("HIT");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("productId", listing.getProductId());
        snapshot.put("price", listing.getPrice());
        snapshot.put("paintwear", record.getPaintwear());
        snapshot.put("img", listing.getImg());
        record.setItemSnapshotJson(snapshot);
        return hitRecordManager.saveHitRecord(record);
    }

    private void attemptBuy(C5ApiClient client, C5SnipingAccount account, C5SnipingTaskV2 task, C5SnipingTaskRunV2 run,
                            Cs2Goods goods, C5ProductListResponse.ProductDTO listing, C5SnipingHitRecordV2 hitRecord) {
        boolean reserved = false;
        if (C5SnipingTaskV2StopModeEnum.BUY_COUNT.equals(task.getStopMode())) {
            reserved = taskManager.reserveBuySlot(task.getId());
            if (!reserved) {
                finishHit(hitRecord, "NO_BUY_SLOT");
                return;
            }
        }

        TradeOrderRecord orderRecord = null;
        C5SnipingBuyAttemptV2 attempt = null;
        String lockKey = ACCOUNT_IN_FLIGHT_LOCK_KEY_PREFIX + task.getAccountId();
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(ACCOUNT_IN_FLIGHT_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                skipNoAccountInFlightSlot(task, run, hitRecord, reserved);
                return;
            }

            int maxInFlightAttempts = accountRuntimeManager.resolveMaxInFlightAttempts(task.getAccountId());
            if (buyAttemptManager.countInFlightAttempts(task.getAccountId()) >= maxInFlightAttempts) {
                skipNoAccountInFlightSlot(task, run, hitRecord, reserved);
                return;
            }

            orderRecord = createOrderRecord(task, goods, listing);
            attempt = new C5SnipingBuyAttemptV2();
            attempt.setTaskId(task.getId());
            attempt.setRunId(run.getId());
            attempt.setHitRecordId(hitRecord.getId());
            attempt.setAccountId(task.getAccountId());
            attempt.setListingId(listing.getProductId());
            attempt.setIdempotencyKey(task.getAccountId() + ":" + listing.getProductId());
            attempt.setOutTradeNo(orderRecord.getOutTradeNo());
            attempt.setOrderRecordId(orderRecord.getId());
            attempt.setSlotReserved(reserved);
            attempt.setSlotReleased(!reserved);
            attempt.setInFlightAmount(listing.getPrice());
            attempt.setInitExpireAt(LocalDateTime.now().plusSeconds(INIT_ATTEMPT_TTL_SECONDS));
            boolean created = buyAttemptManager.saveInitAttemptIfAbsent(attempt);
            if (!created) {
                markOrderFailed(orderRecord, "重复下单尝试");
                if (reserved) {
                    taskManager.releaseBuySlot(task.getId());
                }
                finishHit(hitRecord, "SKIPPED_DUPLICATE");
                publishEvent(task, "ATTEMPT_SKIPPED", run.getId(), hitRecord.getId(), null, "重复下单尝试");
                return;
            }
            runManager.incrementAttemptCount(run.getId());
            publishEvent(task, "ATTEMPT_CREATED", run.getId(), hitRecord.getId(), attempt.getId(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            skipNoAccountInFlightSlot(task, run, hitRecord, reserved);
            return;
        } catch (Exception e) {
            String message = StrUtil.maxLength(StrUtil.blankToDefault(e.getMessage(), "下单尝试初始化异常"), 500);
            if (attempt != null && attempt.getId() != null) {
                finishFailedAttempt(attempt.getId(), orderRecord, "INIT_ERROR", message, reserved, task.getId(), false);
                throw new IllegalStateException(message, e);
            }
            try {
                markOrderFailed(orderRecord, message);
            } finally {
                if (reserved) {
                    taskManager.releaseBuySlot(task.getId());
                }
            }
            throw new IllegalStateException(message, e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        if (attempt == null) {
            return;
        }

        C5BatchBuyRequest buyRequest;
        try {
            buyRequest = buildBuyRequest(account, listing, orderRecord.getOutTradeNo());
        } catch (Exception e) {
            finishFailedAttempt(attempt.getId(), orderRecord, "EXCEPTION", StrUtil.maxLength(e.getMessage(), 500), reserved, task.getId(), false);
            finishHit(hitRecord, "BUY_FAILED");
            return;
        }

        C5BatchBuyResponse response;
        try {
            response = client.getTrade().batchBuy(buyRequest);
        } catch (Exception e) {
            finishFailedAttempt(attempt.getId(), orderRecord, "EXCEPTION", StrUtil.maxLength(e.getMessage(), 500), reserved, task.getId(), true);
            finishHit(hitRecord, "BUY_FAILED");
            return;
        }

        if (response == null) {
            finishFailedAttempt(attempt.getId(), orderRecord, "EMPTY_RESPONSE", "C5批量下单响应为空", reserved, task.getId(), true);
            finishHit(hitRecord, "BUY_FAILED");
            return;
        }
        C5BatchBuyResponse.SuccessItem success = findSuccess(response, orderRecord.getOutTradeNo());
        if (success != null) {
            boolean finished = buyAttemptManager.finishAttempt(attempt.getId(), C5SnipingBuyAttemptV2StatusEnum.SUCCESS, orderRecord.getId(), null, null);
            if (!finished) {
                return;
            }
            tradeOrderRecordManager.lambdaUpdate()
                    .eq(TradeOrderRecord::getId, orderRecord.getId())
                    .set(TradeOrderRecord::getStatus, OrderStatusEnum.SUCCESS.getCode())
                    .set(StrUtil.isNotBlank(success.getOrderId()), TradeOrderRecord::getOrderId, success.getOrderId())
                    .set(TradeOrderRecord::getUpdateTime, LocalDateTime.now())
                    .update();
            if (reserved) {
                settleReservedBuySlot(attempt.getId(), task.getId(), true);
            }
            publishEvent(task, "ATTEMPT_SUCCESS", run.getId(), hitRecord.getId(), attempt.getId(), null);
            runManager.incrementSuccessCount(run.getId());
            finishHit(hitRecord, "BUY_SUCCESS");
            return;
        }

        C5BatchBuyResponse.FailedItem failed = findFailure(response, orderRecord.getOutTradeNo());
        String failureCode = failed == null || failed.getErrorCode() == null ? "BUY_FAILED" : String.valueOf(failed.getErrorCode());
        String failureMessage = failed == null ? "C5未返回成功项" : StrUtil.blankToDefault(failed.getErrorMsg(), "C5下单失败");
        finishFailedAttempt(attempt.getId(), orderRecord, failureCode, failureMessage, reserved, task.getId(), true);
        finishHit(hitRecord, "BUY_FAILED");
    }

    private void skipNoAccountInFlightSlot(C5SnipingTaskV2 task, C5SnipingTaskRunV2 run,
                                           C5SnipingHitRecordV2 hitRecord, boolean reserved) {
        if (reserved) {
            taskManager.releaseBuySlot(task.getId());
        }
        finishHit(hitRecord, "NO_ACCOUNT_IN_FLIGHT_SLOT");
        publishEvent(task, "ATTEMPT_SKIPPED", run.getId(), hitRecord.getId(), null, "账号在途下单数已达上限");
    }

    private TradeOrderRecord createOrderRecord(C5SnipingTaskV2 task, Cs2Goods goods, C5ProductListResponse.ProductDTO listing) {
        TradeOrderRecord record = new TradeOrderRecord();
        record.setUserId(task.getUserId());
        record.setTaskId(task.getId());
        record.setAccountId(task.getAccountId());
        record.setPlatform(PlatformEnum.C5.getCode());
        record.setGoodsName(goods.getDisplayName());
        record.setMarketHashName(goods.getMarketHashName());
        record.setGoodsImg(goods.getImageUrl());
        record.setPrice(listing.getPrice());
        record.setStatus(OrderStatusEnum.PENDING.getCode());
        record.setOutTradeNo(IdUtil.getSnowflakeNextIdStr());
        Map<String, Object> extra = new HashMap<>();
        extra.put("listingId", listing.getProductId());
        extra.put("paintwear", resolvePaintwear(listing));
        record.setExtraInfo(extra);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        tradeOrderRecordManager.save(record);
        return record;
    }

    private C5BatchBuyRequest buildBuyRequest(C5SnipingAccount account, C5ProductListResponse.ProductDTO listing, String outTradeNo) {
        Assert.notBlank(account.getSteamTradeUrl(), "账号未配置 Steam 交易链接");
        C5BatchBuyRequest.BatchProduct product = new C5BatchBuyRequest.BatchProduct();
        product.setProductId(Long.valueOf(listing.getProductId()));
        product.setBuyPrice(listing.getPrice());
        product.setOutTradeNo(outTradeNo);
        return new C5BatchBuyRequest().setTradeUrl(account.getSteamTradeUrl()).setProductList(List.of(product));
    }

    private C5BatchBuyResponse.SuccessItem findSuccess(C5BatchBuyResponse response, String outTradeNo) {
        if (CollUtil.isEmpty(response.getSuccessList())) {
            return null;
        }
        return response.getSuccessList().stream()
                .filter(item -> StrUtil.equals(item.getOutTradeNo(), outTradeNo))
                .findFirst()
                .orElse(null);
    }

    private C5BatchBuyResponse.FailedItem findFailure(C5BatchBuyResponse response, String outTradeNo) {
        if (CollUtil.isEmpty(response.getFailedList())) {
            return null;
        }
        return response.getFailedList().stream()
                .filter(item -> StrUtil.equals(item.getOutTradeNo(), outTradeNo))
                .findFirst()
                .orElse(null);
    }

    private void finishFailedAttempt(Long attemptId, TradeOrderRecord orderRecord, String code, String message,
                                     boolean reserved, Long taskId, boolean cooldownAccount) {
        Long orderRecordId = orderRecord == null ? null : orderRecord.getId();
        boolean finished = buyAttemptManager.finishAttempt(attemptId, C5SnipingBuyAttemptV2StatusEnum.FAILED, orderRecordId, code, message);
        if (!finished) {
            return;
        }
        if (reserved) {
            settleReservedBuySlot(attemptId, taskId, false);
        }
        markOrderFailed(orderRecord, message);
        Long accountId = orderRecord == null ? null : orderRecord.getAccountId();
        if (cooldownAccount && accountId != null) {
            accountRuntimeManager.coolDown(accountId, LocalDateTime.now().plusSeconds(ACCOUNT_COOLDOWN_SECONDS), code + ':' + StrUtil.blankToDefault(message, ""));
        }
        C5SnipingBuyAttemptV2 attempt = buyAttemptManager.getById(attemptId);
        if (attempt != null) {
            C5SnipingTaskV2 task = taskManager.getById(taskId);
            if (task != null) {
                publishEvent(task, "ATTEMPT_FAILED", attempt.getRunId(), attempt.getHitRecordId(), attempt.getId(), message);
            }
        }
    }

    private void settleReservedBuySlot(Long attemptId, Long taskId, boolean success) {
        if (attemptId == null || taskId == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            C5SnipingBuyAttemptV2 latestAttempt = buyAttemptManager.getById(attemptId);
            if (latestAttempt == null || !Boolean.TRUE.equals(latestAttempt.getSlotReserved())
                    || Boolean.TRUE.equals(latestAttempt.getSlotReleased())) {
                return;
            }
            if (!buyAttemptManager.markSlotReleasedIfNeeded(attemptId)) {
                return;
            }
            boolean taskUpdated = success ? taskManager.confirmBuySuccess(taskId) : taskManager.releaseBuySlot(taskId);
            if (!taskUpdated) {
                throw new IllegalStateException("C5扫货2.0预占名额结算失败: attemptId=" + attemptId);
            }
        });
    }

    /**
     * 将已创建的订单记录标记为失败；订单尚未创建时不处理。
     *
     * @param orderRecord 订单记录
     * @param message 失败信息
     */
    private void markOrderFailed(TradeOrderRecord orderRecord, String message) {
        if (orderRecord == null || orderRecord.getId() == null) {
            return;
        }
        tradeOrderRecordManager.lambdaUpdate()
                .eq(TradeOrderRecord::getId, orderRecord.getId())
                .set(TradeOrderRecord::getStatus, OrderStatusEnum.FAILED.getCode())
                .set(TradeOrderRecord::getErrorMsg, message)
                .set(TradeOrderRecord::getUpdateTime, LocalDateTime.now())
                .update();
    }

    private void finishHit(C5SnipingHitRecordV2 hitRecord, String decisionResult) {
        hitRecordManager.lambdaUpdate()
                .eq(C5SnipingHitRecordV2::getId, hitRecord.getId())
                .set(C5SnipingHitRecordV2::getDecisionResult, decisionResult)
                .update();
    }

    private void publishEvent(C5SnipingTaskV2 task, String eventType, Long runId, Long hitRecordId, Long attemptId, String message) {
        C5SnipingTaskV2 latestTask = taskManager.getById(task.getId());
        if (latestTask == null) {
            return;
        }
        eventService.publish(latestTask.getUserId(), C5SnipingTaskV2EventDTO.builder()
                .taskId(latestTask.getId())
                .eventType(eventType)
                .occurredAt(LocalDateTime.now())
                .runId(runId)
                .hitRecordId(hitRecordId)
                .attemptId(attemptId)
                .taskStatus(latestTask.getTaskStatus() == null ? null : latestTask.getTaskStatus().getCode())
                .stopRequested(latestTask.getStopRequested())
                .successBuyCount(latestTask.getSuccessBuyCount())
                .reservedBuyCount(latestTask.getReservedBuyCount())
                .hitCount(latestTask.getHitCount())
                .lastErrorMessage(latestTask.getLastErrorMessage())
                .message(message)
                .build());
    }

    private C5SnipingTaskV2ExecutionResult checkCompletion(C5SnipingTaskV2 task) {
        if (task == null) {
            return C5SnipingTaskV2ExecutionResult.stopped("TASK_REMOVED");
        }
        if (C5SnipingTaskV2StopModeEnum.BUY_COUNT.equals(task.getStopMode()) && task.getTargetBuyCount() != null
                && task.getSuccessBuyCount() != null && task.getSuccessBuyCount() >= task.getTargetBuyCount()) {
            return C5SnipingTaskV2ExecutionResult.completed("BUY_COUNT_REACHED");
        }
        return C5SnipingTaskV2ExecutionResult.continueRunning();
    }

    private C5SnipingTaskV2ExecutionResult checkBalanceStop(C5SnipingTaskV2 task) {
        if (!C5SnipingTaskV2StopModeEnum.BALANCE_GUARD.equals(task.getStopMode())) {
            return C5SnipingTaskV2ExecutionResult.continueRunning();
        }
        C5SnipingAccount account = requireAvailableAccount(task.getAccountId());
        C5BalanceResponse balance = c5ApiClientService.getClientByAppKey(c5SnipingAccountService.decryptAccountAppKey(account)).getAccount().getBalance();
        if (balance == null || balance.getMoneyAmount() == null) {
            return C5SnipingTaskV2ExecutionResult.continueRunning();
        }
        BigDecimal threshold = C5SnipingTaskV2BalanceGuardModeEnum.RESERVE_BALANCE.equals(task.getBalanceGuardMode())
                ? task.getReserveBalance()
                : task.getMaxPrice();
        BigDecimal availableBalance = balance.getMoneyAmount().subtract(buyAttemptManager.sumInFlightAmount(task.getAccountId()));
        if (threshold != null && availableBalance.compareTo(threshold) < 0) {
            return C5SnipingTaskV2ExecutionResult.completed("BALANCE_GUARD_REACHED");
        }
        return C5SnipingTaskV2ExecutionResult.continueRunning();
    }
}
