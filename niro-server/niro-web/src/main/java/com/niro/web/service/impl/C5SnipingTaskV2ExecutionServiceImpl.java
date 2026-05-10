package com.niro.web.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.niro.core.util.Assert;
import com.niro.sdk.c5.account.C5BalanceResponse;
import com.niro.sdk.c5.client.C5ApiClient;
import com.niro.sdk.c5.constant.C5GameAPI;
import com.niro.sdk.c5.market.C5ProductListRequest;
import com.niro.sdk.c5.market.C5ProductListResponse;
import com.niro.sdk.c5.market.C5ProductSearchRequest;
import com.niro.sdk.c5.trade.C5BatchBuyRequest;
import com.niro.sdk.c5.trade.C5BatchBuyResponse;
import com.niro.web.constant.C5SnipingTaskV2Constants;
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

    private final C5ApiClientService c5ApiClientService;
    private final Cs2GoodsMapperManager cs2GoodsMapperManager;
    private final C5SnipingTaskV2MapperManager taskManager;
    private final C5SnipingAccountMapperManager accountManager;
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
     * @return 执行结果
     */
    @Override
    public C5SnipingTaskV2ExecutionResult executeOneCycle(C5SnipingTaskV2 task) {
        try {
            C5SnipingTaskV2 latestTask = taskManager.getById(task.getId());
            if (latestTask == null || !C5SnipingTaskV2StatusEnum.RUNNING.equals(latestTask.getTaskStatus())) {
                return C5SnipingTaskV2ExecutionResult.stopped(C5SnipingTaskV2ExecutionReasonEnum.TASK_STATUS_CHANGED.getCode());
            }
            C5SnipingTaskV2ExecutionResult balanceStop = checkBalanceStop(latestTask);
            if (balanceStop.isStopTask()) {
                return balanceStop;
            }

            Cs2Goods goods = cs2GoodsMapperManager.getEnabledById(latestTask.getCs2GoodsId());
            Assert.notNull(goods, C5SnipingTaskV2Constants.ERROR_GOODS_NOT_FOUND);
            Assert.notBlank(goods.getMarketHashName(), C5SnipingTaskV2Constants.ERROR_GOODS_MARKET_HASH_NAME_EMPTY);
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
                taskManager.clearLastError(latestTask.getId());
                return checkCompletion(latestTask);
            }

            int writtenHits = 0;
            for (C5ProductListResponse.ProductDTO listing : hits) {
                String listingId = StrUtil.trim(listing.getProductId());
                if (StrUtil.isBlank(listingId) || isDuplicateHit(latestTask.getAccountId(), listingId)) {
                    continue;
                }
                C5SnipingHitRecordV2 hitRecord = saveHitRecord(latestTask, listing);
                writtenHits++;
                taskManager.incrementHitCount(latestTask.getId(), 1);
                publishEvent(latestTask, C5SnipingTaskV2EventTypeEnum.HIT_RECORD_CREATED.getCode(), hitRecord.getId(), null, null);
                attemptBuy(client, account, latestTask, goods, listing, hitRecord);
                C5SnipingTaskV2ExecutionResult completion = checkCompletion(taskManager.getById(latestTask.getId()));
                if (completion.isStopTask()) {
                    return completion;
                }
            }

            if (writtenHits > 0) {
                publishEvent(latestTask, C5SnipingTaskV2EventTypeEnum.TASK_PROGRESS.getCode(), null, null, null);
            }
            taskManager.clearLastError(latestTask.getId());
            return checkCompletion(taskManager.getById(latestTask.getId()));
        } catch (Exception e) {
            String message = StrUtil.maxLength(StrUtil.blankToDefault(e.getMessage(), C5SnipingTaskV2Constants.ERROR_EXECUTE_ONE_CYCLE), C5SnipingTaskV2Constants.MESSAGE_MAX_LENGTH);
            taskManager.updateLastError(task.getId(), message);
            log.warn("C5扫货2.0单轮执行异常，将继续下一轮: taskId={}, msg={}", task.getId(), message, e);
            return C5SnipingTaskV2ExecutionResult.continueRunning();
        }
    }

    /**
     * 获取并校验可用于扫货的 C5 账号。
     */
    private C5SnipingAccount requireAvailableAccount(Long accountId) {
        C5SnipingAccount account = accountManager.getAvailableAccount(accountId);
        Assert.notNull(account, C5SnipingTaskV2Constants.ERROR_ACCOUNT_UNAVAILABLE);
        Assert.notBlank(account.getC5AppKeyEncrypted(), C5SnipingTaskV2Constants.ERROR_ACCOUNT_APP_KEY_EMPTY);
        Assert.notBlank(account.getSteamTradeUrl(), C5SnipingTaskV2Constants.ERROR_ACCOUNT_STEAM_TRADE_URL_EMPTY);
        return account;
    }

    /**
     * 按商品属性查询 C5 挂单列表。
     */
    private List<C5ProductListResponse.ProductDTO> searchListings(C5ApiClient client, Cs2Goods goods, C5SnipingTaskV2 task) {
        boolean nonWearable = !Boolean.TRUE.equals(goods.getHasExterior());
        C5ProductListResponse response;
        if (nonWearable) {
            C5ProductListRequest request = new C5ProductListRequest()
                    .setAppId(C5SnipingTaskV2Constants.APP_ID_CS2)
                    .setMarketHashName(goods.getMarketHashName())
                    .setPageNum(C5SnipingTaskV2Constants.LISTING_PAGE_NUM)
                    .setPageSize(C5SnipingTaskV2Constants.LISTING_PAGE_SIZE);
            response = client.getMarket().searchProductList(request);
            logProductListResult(task, request, response);
        } else {
            C5ProductSearchRequest request = new C5ProductSearchRequest()
                    .setAppId(C5SnipingTaskV2Constants.APP_ID_CS2)
                    .setMarketHashName(goods.getMarketHashName())
                    .setWearMin(task.getMinPaintwear() == null ? null : task.getMinPaintwear().doubleValue())
                    .setWearMax(task.getMaxPaintwear() == null ? null : task.getMaxPaintwear().doubleValue())
                    .setPriceMax(C5SnipingTaskV2Constants.GLOBAL_MAX_PRICE)
                    .setPageNum(C5SnipingTaskV2Constants.LISTING_PAGE_NUM)
                    .setPageSize(C5SnipingTaskV2Constants.LISTING_PAGE_SIZE);
            response = client.getMarket().productSearch(request);
        }
        return response == null || response.getList() == null ? List.of() : response.getList();
    }

    /**
     * 记录 C5 挂单查询结果摘要。
     */
    private void logProductListResult(C5SnipingTaskV2 task, C5ProductListRequest request, C5ProductListResponse response) {
        BigDecimal minPrice = response == null || response.getList() == null ? null : response.getList().stream()
                                                                                      .map(C5ProductListResponse.ProductDTO::getPrice)
                                                                                      .filter(price -> price != null)
                                                                                      .min(BigDecimal::compareTo)
                                                                                      .orElse(null);
        log.info("C5扫货products/list查询: 用户id={}, taskId={}, requestUrl='GET {}', appId={}, marketHashName={}, pageNum={}, pageSize={}, 当前最低价={}, 目标价格={}",
                task.getUserId(), task.getId(), C5GameAPI.Market.PRODUCT_LIST,
                request.getAppId(), request.getMarketHashName(), request.getPageNum(), request.getPageSize(), minPrice, task.getMaxPrice());
    }

    /**
     * 判断挂单磨损是否命中任务配置。
     */
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

    /**
     * 解析挂单磨损值。
     */
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

    /**
     * 判断账号维度挂单是否处于本地去重窗口。
     */
    private boolean isDuplicateHit(Long accountId, String listingId) {
        cleanupDedup();
        String key = accountId + C5SnipingTaskV2Constants.KEY_SEPARATOR + listingId;
        Long previous = recentDedup.putIfAbsent(key, System.currentTimeMillis());
        return previous != null;
    }

    /**
     * 清理本地命中去重窗口中的过期记录。
     */
    private void cleanupDedup() {
        long expireBefore = System.currentTimeMillis() - C5SnipingTaskV2Constants.DEDUP_TTL_MS;
        recentDedup.entrySet().removeIf(entry -> entry.getValue() < expireBefore);
    }

    /**
     * 保存本轮命中的挂单记录。
     */
    private C5SnipingHitRecordV2 saveHitRecord(C5SnipingTaskV2 task, C5ProductListResponse.ProductDTO listing) {
        C5SnipingHitRecordV2 record = new C5SnipingHitRecordV2();
        record.setTaskId(task.getId());
        record.setAccountId(task.getAccountId());
        record.setListingId(listing.getProductId());
        record.setListingPrice(listing.getPrice());
        record.setPaintwear(resolvePaintwear(listing));
        record.setDecisionResult(C5SnipingHitDecisionResultEnum.HIT.getCode());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("productId", listing.getProductId());
        snapshot.put("price", listing.getPrice());
        snapshot.put("paintwear", record.getPaintwear());
        snapshot.put("img", listing.getImg());
        record.setItemSnapshotJson(snapshot);
        return hitRecordManager.saveHitRecord(record);
    }

    /**
     * 尝试对命中挂单发起 C5 下单。
     */
    private void attemptBuy(C5ApiClient client, C5SnipingAccount account, C5SnipingTaskV2 task,
                            Cs2Goods goods, C5ProductListResponse.ProductDTO listing, C5SnipingHitRecordV2 hitRecord) {
        boolean reserved = false;
        if (C5SnipingTaskV2StopModeEnum.BUY_COUNT.equals(task.getStopMode())) {
            reserved = taskManager.reserveBuySlot(task.getId());
            if (!reserved) {
                finishHit(hitRecord, C5SnipingHitDecisionResultEnum.NO_BUY_SLOT.getCode(), C5SnipingTaskV2Constants.REASON_NO_BUY_SLOT);
                logBuyResult(task, listing, C5SnipingHitDecisionResultEnum.NO_BUY_SLOT.getCode(), C5SnipingTaskV2Constants.REASON_NO_BUY_SLOT);
                return;
            }
        }

        String outTradeNo = IdUtil.getSnowflakeNextIdStr();
        C5SnipingBuyAttemptV2 attempt = null;
        String lockKey = C5SnipingTaskV2Constants.ACCOUNT_IN_FLIGHT_LOCK_KEY_PREFIX + task.getAccountId();
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(C5SnipingTaskV2Constants.ACCOUNT_IN_FLIGHT_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                skipNoAccountInFlightSlot(task, listing, hitRecord, reserved);
                return;
            }

            int maxInFlightAttempts = accountRuntimeManager.resolveMaxInFlightAttempts(task.getAccountId());
            if (buyAttemptManager.countInFlightAttempts(task.getAccountId()) >= maxInFlightAttempts) {
                skipNoAccountInFlightSlot(task, listing, hitRecord, reserved);
                return;
            }

            attempt = new C5SnipingBuyAttemptV2();
            attempt.setTaskId(task.getId());
            attempt.setHitRecordId(hitRecord.getId());
            attempt.setAccountId(task.getAccountId());
            attempt.setListingId(listing.getProductId());
            attempt.setIdempotencyKey(task.getAccountId() + C5SnipingTaskV2Constants.KEY_SEPARATOR + listing.getProductId());
            attempt.setOutTradeNo(outTradeNo);
            attempt.setSlotReserved(reserved);
            attempt.setSlotReleased(!reserved);
            attempt.setInFlightAmount(listing.getPrice());
            attempt.setInitExpireAt(LocalDateTime.now().plusSeconds(C5SnipingTaskV2Constants.INIT_ATTEMPT_TTL_SECONDS));
            boolean created = buyAttemptManager.saveInitAttemptIfAbsent(attempt);
            if (!created) {
                if (reserved) {
                    taskManager.releaseBuySlot(task.getId());
                }
                finishHit(hitRecord, C5SnipingHitDecisionResultEnum.SKIPPED_DUPLICATE.getCode(), C5SnipingTaskV2Constants.REASON_DUPLICATE_ATTEMPT);
                logBuyResult(task, listing, C5SnipingHitDecisionResultEnum.SKIPPED_DUPLICATE.getCode(), C5SnipingTaskV2Constants.REASON_DUPLICATE_ATTEMPT);
                publishEvent(task, C5SnipingTaskV2EventTypeEnum.ATTEMPT_SKIPPED.getCode(), hitRecord.getId(), null, C5SnipingTaskV2Constants.REASON_DUPLICATE_ATTEMPT);
                return;
            }
            publishEvent(task, C5SnipingTaskV2EventTypeEnum.ATTEMPT_CREATED.getCode(), hitRecord.getId(), attempt.getId(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            skipNoAccountInFlightSlot(task, listing, hitRecord, reserved);
            return;
        } catch (Exception e) {
            String message = StrUtil.maxLength(StrUtil.blankToDefault(e.getMessage(), C5SnipingTaskV2Constants.REASON_INIT_ERROR), C5SnipingTaskV2Constants.MESSAGE_MAX_LENGTH);
            if (attempt != null && attempt.getId() != null) {
                finishFailedAttempt(attempt.getId(), task.getAccountId(), C5SnipingBuyFailureCodeEnum.INIT_ERROR.getCode(), message, reserved, task.getId(), false);
                finishHit(hitRecord, C5SnipingHitDecisionResultEnum.BUY_FAILED.getCode(), message);
                logBuyResult(task, listing, C5SnipingBuyFailureCodeEnum.INIT_ERROR.getCode(), message);
                throw new IllegalStateException(message, e);
            }
            if (reserved) {
                taskManager.releaseBuySlot(task.getId());
            }
            finishHit(hitRecord, C5SnipingHitDecisionResultEnum.BUY_FAILED.getCode(), message);
            logBuyResult(task, listing, C5SnipingBuyFailureCodeEnum.INIT_ERROR.getCode(), message);
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
            buyRequest = buildBuyRequest(account, listing, outTradeNo);
        } catch (Exception e) {
            String message = StrUtil.maxLength(e.getMessage(), C5SnipingTaskV2Constants.MESSAGE_MAX_LENGTH);
            finishFailedAttempt(attempt.getId(), task.getAccountId(), C5SnipingBuyFailureCodeEnum.EXCEPTION.getCode(), message, reserved, task.getId(), false);
            finishHit(hitRecord, C5SnipingHitDecisionResultEnum.BUY_FAILED.getCode(), message);
            logBuyResult(task, listing, C5SnipingBuyFailureCodeEnum.EXCEPTION.getCode(), message);
            return;
        }

        C5BatchBuyResponse response;
        try {
            response = client.getTrade().batchBuy(buyRequest);
        } catch (Exception e) {
            String message = StrUtil.maxLength(e.getMessage(), C5SnipingTaskV2Constants.MESSAGE_MAX_LENGTH);
            finishFailedAttempt(attempt.getId(), task.getAccountId(), C5SnipingBuyFailureCodeEnum.EXCEPTION.getCode(), message, reserved, task.getId(), true);
            finishHit(hitRecord, C5SnipingHitDecisionResultEnum.BUY_FAILED.getCode(), message);
            logBuyResult(task, listing, C5SnipingBuyFailureCodeEnum.EXCEPTION.getCode(), message);
            return;
        }

        if (response == null) {
            String message = C5SnipingTaskV2Constants.REASON_EMPTY_BUY_RESPONSE;
            finishFailedAttempt(attempt.getId(), task.getAccountId(), C5SnipingBuyFailureCodeEnum.EMPTY_RESPONSE.getCode(), message, reserved, task.getId(), true);
            finishHit(hitRecord, C5SnipingHitDecisionResultEnum.BUY_FAILED.getCode(), message);
            logBuyResult(task, listing, C5SnipingBuyFailureCodeEnum.EMPTY_RESPONSE.getCode(), message);
            return;
        }
        C5BatchBuyResponse.SuccessItem success = findSuccess(response, outTradeNo);
        if (success != null) {
            TradeOrderRecord orderRecord = createOrderRecord(task, goods, listing, outTradeNo, success.getOrderId());
            boolean finished = buyAttemptManager.finishAttempt(attempt.getId(), C5SnipingBuyAttemptV2StatusEnum.SUCCESS, orderRecord.getId(), null, null);
            if (!finished) {
                return;
            }
            if (reserved) {
                settleReservedBuySlot(attempt.getId(), task.getId(), true);
            }
            publishEvent(task, C5SnipingTaskV2EventTypeEnum.ATTEMPT_SUCCESS.getCode(), hitRecord.getId(), attempt.getId(), null);
            finishHit(hitRecord, C5SnipingHitDecisionResultEnum.BUY_SUCCESS.getCode(), null);
            logBuyResult(task, listing, C5SnipingHitDecisionResultEnum.BUY_SUCCESS.getCode(), "orderId=" + StrUtil.blankToDefault(success.getOrderId(), ""));
            return;
        }

        C5BatchBuyResponse.FailedItem failed = findFailure(response, outTradeNo);
        String failureCode = failed == null || failed.getErrorCode() == null ? C5SnipingHitDecisionResultEnum.BUY_FAILED.getCode() : String.valueOf(failed.getErrorCode());
        String failureMessage = failed == null ? C5SnipingTaskV2Constants.REASON_NO_SUCCESS_ITEM : StrUtil.blankToDefault(failed.getErrorMsg(), C5SnipingTaskV2Constants.REASON_BUY_FAILED);
        finishFailedAttempt(attempt.getId(), task.getAccountId(), failureCode, failureMessage, reserved, task.getId(), true);
        finishHit(hitRecord, C5SnipingHitDecisionResultEnum.BUY_FAILED.getCode(), failureMessage);
        logBuyResult(task, listing, failureCode, failureMessage);
    }

    /**
     * 标记账号在途下单名额不足并释放预占名额。
     */
    private void skipNoAccountInFlightSlot(C5SnipingTaskV2 task, C5ProductListResponse.ProductDTO listing,
                                           C5SnipingHitRecordV2 hitRecord, boolean reserved) {
        String reason = C5SnipingTaskV2Constants.REASON_ACCOUNT_IN_FLIGHT_LIMIT_REACHED;
        if (reserved) {
            taskManager.releaseBuySlot(task.getId());
        }
        finishHit(hitRecord, C5SnipingHitDecisionResultEnum.NO_ACCOUNT_IN_FLIGHT_SLOT.getCode(), reason);
        logBuyResult(task, listing, C5SnipingHitDecisionResultEnum.NO_ACCOUNT_IN_FLIGHT_SLOT.getCode(), reason);
    }

    /**
     * 创建 C5 成功下单对应的订单记录。
     */
    private TradeOrderRecord createOrderRecord(C5SnipingTaskV2 task, Cs2Goods goods,
                                               C5ProductListResponse.ProductDTO listing,
                                               String outTradeNo, String orderId) {
        TradeOrderRecord record = new TradeOrderRecord();
        record.setUserId(task.getUserId());
        record.setTaskId(task.getId());
        record.setAccountId(task.getAccountId());
        record.setPlatform(PlatformEnum.C5.getCode());
        record.setGoodsName(goods.getDisplayName());
        record.setMarketHashName(goods.getMarketHashName());
        record.setGoodsImg(goods.getImageUrl());
        record.setPrice(listing.getPrice());
        record.setStatus(OrderStatusEnum.SUCCESS.getCode());
        record.setOrderId(orderId);
        record.setOutTradeNo(outTradeNo);
        Map<String, Object> extra = new HashMap<>();
        extra.put("listingId", listing.getProductId());
        extra.put("paintwear", resolvePaintwear(listing));
        record.setExtraInfo(extra);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        tradeOrderRecordManager.save(record);
        return record;
    }

    /**
     * 构造 C5 批量购买请求。
     */
    private C5BatchBuyRequest buildBuyRequest(C5SnipingAccount account, C5ProductListResponse.ProductDTO listing, String outTradeNo) {
        Assert.notBlank(account.getSteamTradeUrl(), C5SnipingTaskV2Constants.ERROR_ACCOUNT_STEAM_TRADE_URL_EMPTY);
        C5BatchBuyRequest.BatchProduct product = new C5BatchBuyRequest.BatchProduct();
        product.setProductId(Long.valueOf(listing.getProductId()));
        product.setBuyPrice(listing.getPrice());
        product.setOutTradeNo(outTradeNo);
        return new C5BatchBuyRequest().setTradeUrl(account.getSteamTradeUrl()).setProductList(List.of(product));
    }

    /**
     * 查找指定外部交易号的成功下单项。
     */
    private C5BatchBuyResponse.SuccessItem findSuccess(C5BatchBuyResponse response, String outTradeNo) {
        if (CollUtil.isEmpty(response.getSuccessList())) {
            return null;
        }
        return response.getSuccessList().stream()
                .filter(item -> StrUtil.equals(item.getOutTradeNo(), outTradeNo))
                .findFirst()
                .orElse(null);
    }

    /**
     * 查找指定外部交易号的失败下单项。
     */
    private C5BatchBuyResponse.FailedItem findFailure(C5BatchBuyResponse response, String outTradeNo) {
        if (CollUtil.isEmpty(response.getFailedList())) {
            return null;
        }
        return response.getFailedList().stream()
                .filter(item -> StrUtil.equals(item.getOutTradeNo(), outTradeNo))
                .findFirst()
                .orElse(null);
    }

    /**
     * 完成失败下单尝试并按需冷却账号。
     */
    private void finishFailedAttempt(Long attemptId, Long accountId, String code, String message, boolean reserved, Long taskId, boolean cooldownAccount) {
        boolean finished = buyAttemptManager.finishAttempt(attemptId, C5SnipingBuyAttemptV2StatusEnum.FAILED, null, code, message);
        if (!finished) {
            return;
        }
        if (reserved) {
            settleReservedBuySlot(attemptId, taskId, false);
        }
        if (cooldownAccount && accountId != null) {
            accountRuntimeManager.coolDown(accountId, LocalDateTime.now().plusSeconds(C5SnipingTaskV2Constants.ACCOUNT_COOLDOWN_SECONDS), code + C5SnipingTaskV2Constants.KEY_SEPARATOR + StrUtil.blankToDefault(message, ""));
        }
        C5SnipingBuyAttemptV2 attempt = buyAttemptManager.getById(attemptId);
        if (attempt != null) {
            C5SnipingTaskV2 task = taskManager.getById(taskId);
            if (task != null) {
                publishEvent(task, C5SnipingTaskV2EventTypeEnum.ATTEMPT_FAILED.getCode(), attempt.getHitRecordId(), attempt.getId(), message);
            }
        }
    }

    /**
     * 结算已预占的购买名额。
     */
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
                throw new IllegalStateException(C5SnipingTaskV2Constants.ERROR_RESERVED_SLOT_SETTLE_FAILED_PREFIX + attemptId);
            }
        });
    }

    /**
     * 更新命中记录的最终下单决策。
     */
    private void finishHit(C5SnipingHitRecordV2 hitRecord, String decisionResult, String failureReason) {
        hitRecordManager.lambdaUpdate()
                .eq(C5SnipingHitRecordV2::getId, hitRecord.getId())
                .set(C5SnipingHitRecordV2::getDecisionResult, decisionResult)
                .set(C5SnipingHitRecordV2::getBuyFailureReason, StrUtil.blankToDefault(failureReason, ""))
                .update();
    }

    /**
     * 记录单次下单决策结果。
     */
    private void logBuyResult(C5SnipingTaskV2 task, C5ProductListResponse.ProductDTO listing, String result, String message) {
        log.info("C5扫货2.0下单结果: userId={}, taskId={}, accountId={}, listingId={}, hitPrice={}, maxPrice={}, result={}, message={}",
                task.getUserId(), task.getId(), task.getAccountId(), listing.getProductId(), listing.getPrice(), task.getMaxPrice(), result, StrUtil.blankToDefault(message, ""));
    }

    /**
     * 发布任务执行过程事件。
     */
    private void publishEvent(C5SnipingTaskV2 task, String eventType, Long hitRecordId, Long attemptId, String message) {
        C5SnipingTaskV2 latestTask = taskManager.getById(task.getId());
        if (latestTask == null) {
            return;
        }
        eventService.publish(latestTask.getUserId(), C5SnipingTaskV2EventDTO.builder()
                .taskId(latestTask.getId())
                .eventType(eventType)
                .occurredAt(LocalDateTime.now())
                .hitRecordId(hitRecordId)
                .attemptId(attemptId)
                .taskStatus(latestTask.getTaskStatus() == null ? null : latestTask.getTaskStatus().getCode())
                .finishedAt(latestTask.getFinishedAt())
                .stopRequested(latestTask.getStopRequested())
                .successBuyCount(latestTask.getSuccessBuyCount())
                .reservedBuyCount(latestTask.getReservedBuyCount())
                .hitCount(latestTask.getHitCount())
                .lastErrorMessage(latestTask.getLastErrorMessage())
                .message(message)
                .build());
    }

    /**
     * 检查任务购买数完成条件。
     */
    private C5SnipingTaskV2ExecutionResult checkCompletion(C5SnipingTaskV2 task) {
        if (task == null) {
            return C5SnipingTaskV2ExecutionResult.stopped(C5SnipingTaskV2ExecutionReasonEnum.TASK_REMOVED.getCode());
        }
        if (C5SnipingTaskV2StopModeEnum.BUY_COUNT.equals(task.getStopMode()) && task.getTargetBuyCount() != null
                && task.getSuccessBuyCount() != null && task.getSuccessBuyCount() >= task.getTargetBuyCount()) {
            return C5SnipingTaskV2ExecutionResult.completed(C5SnipingTaskV2ExecutionReasonEnum.BUY_COUNT_REACHED.getCode());
        }
        return C5SnipingTaskV2ExecutionResult.continueRunning();
    }

    /**
     * 检查任务余额保护完成条件。
     */
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
            return C5SnipingTaskV2ExecutionResult.completed(C5SnipingTaskV2ExecutionReasonEnum.BALANCE_GUARD_REACHED.getCode());
        }
        return C5SnipingTaskV2ExecutionResult.continueRunning();
    }
}
