package com.niro.web.service.strategy.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.niro.core.constant.BuffConstant;
import com.niro.core.util.RedisUtil;
import com.niro.sdk.c5.client.C5ApiClient;
import com.niro.sdk.c5.request.account.C5AccountBalanceRequest;
import com.niro.sdk.c5.request.market.C5ProductListRequest;
import com.niro.sdk.c5.request.market.C5ProductSearchRequest;
import com.niro.sdk.c5.request.trade.C5BatchBuyRequest;
import com.niro.sdk.c5.response.C5BalanceResponse;
import com.niro.sdk.c5.response.market.C5ProductListResponse;
import com.niro.sdk.c5.response.trade.C5BatchBuyResponse;
import com.niro.web.dto.UserPlatformSettingsDTO;
import com.niro.web.entity.*;
import com.niro.web.enums.PlatformEnum;
import com.niro.web.enums.TaskStatusEnum;
import com.niro.web.manager.TradeOrderRecordMapperManager;
import com.niro.web.mapper.BuffScanTaskMapper;
import com.niro.web.scheduler.C5TaskScheduler;
import com.niro.web.manager.Cs2GoodsMapperManager;
import com.niro.web.service.C5ApiClientService;
import com.niro.web.service.UserPlatformSettingsService;
import com.niro.web.service.strategy.IPlatformStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * C5 平台策略实现
 * <p>
 * 扫货过滤上限直接使用任务配置的 {@code maxPrice}，不再引入深度锚点算法。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class C5TradeStrategyImpl implements IPlatformStrategy {

    private final C5TaskScheduler c5TaskScheduler;
    private final C5ApiClientService c5ApiClientService;
    private final UserPlatformSettingsService userPlatformSettingsService;
    private final Cs2GoodsMapperManager cs2GoodsMapperManager;
    private final TradeOrderRecordMapperManager tradeOrderRecordMapperManager;
    private final BuffScanTaskMapper buffScanTaskMapper;
    private final RedisUtil redisUtil;

    private static final BigDecimal GLOBAL_MAX_PRICE = new BigDecimal("999999");
    private static final long PRODUCT_PENDING_TTL_SECONDS = 15L;
    private static final String C5_PRODUCT_PENDING_KEY_PREFIX = "c5:buy:pending:";
    private static final DefaultRedisScript<Long> RESERVE_QUOTA_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> RESTORE_QUOTA_SCRIPT = new DefaultRedisScript<>();

    static {
        RESERVE_QUOTA_SCRIPT.setResultType(Long.class);
        RESERVE_QUOTA_SCRIPT.setScriptText("""
                local current = redis.call('GET', KEYS[1])
                if not current then
                  return 0
                end
                current = tonumber(current)
                if not current or current <= 0 then
                  return 0
                end
                local requested = tonumber(ARGV[1])
                local approved = math.min(current, requested)
                redis.call('DECRBY', KEYS[1], approved)
                return approved
                """);

        RESTORE_QUOTA_SCRIPT.setResultType(Long.class);
        RESTORE_QUOTA_SCRIPT.setScriptText("""
                local quota = redis.call('INCRBY', KEYS[1], ARGV[1])
                local total = redis.call('GET', KEYS[2])
                if not total then
                  return quota
                end
                total = tonumber(total)
                if not total then
                  return quota
                end
                if quota > total then
                  redis.call('SET', KEYS[1], total)
                  return total
                end
                return quota
                """);
    }

    @Override
    public void stopTask(Long taskId) {
        c5TaskScheduler.stop(taskId);
    }

    @Override
    public void completeTask(Long taskId) {
        c5TaskScheduler.complete(taskId);
    }

    @Override
    public PlatformEnum getPlatform() {
        return PlatformEnum.C5;
    }

    private C5ApiClient resolveClient(Long userId) {
        return c5ApiClientService.getClient(userId);
    }

    @Override
    public TaskStatusEnum handleTask(BuffScanTask task) {
        // 使用 lambda 替代方法引用，避免可能的 Spring 代理方法解析问题
        return c5TaskScheduler.start(task, this::executeTrade);
    }

    @Override
    public void syncAccountBalance(BuffAccount account) {
        try {
            C5ApiClient client = resolveClient(account.getUserId());
            C5BalanceResponse balance = client.getAccount().getBalance(
                    new C5AccountBalanceRequest().setAccountType(0));
            if (balance != null && balance.getBalance() != null) {
                account.setBalance(balance.getBalance());
                log.info("账号 [{}] C5 余额同步成功: {}", account.getAccountName(), balance.getBalance());
            }
        } catch (Exception e) {
            log.error("同步 C5 余额失败: {}", e.getMessage());
        }
    }

    /**
     * 执行 C5 交易核心逻辑
     * <p>
     * 1. 校验任务状态与商品信息
     * 2. 全量搜索 C5 在售商品
     * 3. 按 {@code task.maxPrice} 与磨损区间筛选
     * 4. 批量下单 (Batch Buy)
     * </p>
     */
    public void executeTrade(BuffScanTask task) {
        // 0. 校验任务状态
        if (task.getBuyCount() != null && task.getBuyCount() > 0) {
            long currentSuccess = tradeOrderRecordMapperManager.countSuccess(task.getId());
            if (currentSuccess >= task.getBuyCount()) {
                log.info("任务 [{}] 已完成购买目标 ({}/{})，停止执行", task.getId(), currentSuccess, task.getBuyCount());
                c5TaskScheduler.complete(task.getId());
                return;
            }
        }

        // 1. 准备参数
        Cs2Goods goods = cs2GoodsMapperManager.getEnabledById(task.getCs2GoodsId());
        if (goods == null || StrUtil.isBlank(goods.getMarketHashName())) {
            String errorMsg = goods == null ? "CS2商品不存在" : "商品 MarketHashName 为空";
            log.error("任务 [{}] {}", task.getId(), errorMsg);
            markTaskError(task, errorMsg);
            c5TaskScheduler.stop(task.getId());
            return;
        }
        String marketHashName = goods.getMarketHashName();

        final C5ApiClient client = resolveClient(task.getUserId());

        boolean isNonWearable = !Boolean.TRUE.equals(goods.getHasExterior());

        // 2. 搜索在售商品 (Search Products)
        final BigDecimal minWear = isNonWearable ? null : task.getMinPaintwear();
        final BigDecimal maxWear = isNonWearable ? null : task.getMaxPaintwear();

        // 中断检查
        if (Thread.currentThread().isInterrupted()) {
            log.warn("任务 [{}] 在搜索前被中断", task.getId());
            return;
        }

        List<C5ProductListResponse.ProductDTO> allItems = searchProducts(client, marketHashName,
                minWear, maxWear, isNonWearable, task);

        if (CollUtil.isEmpty(allItems)) {
            return;
        }

        // 3. 排序 (价格升序)
        List<C5ProductListResponse.ProductDTO> sortedItems = allItems.stream()
                .filter(item -> item.getPrice() != null)
                .sorted(Comparator.comparing(C5ProductListResponse.ProductDTO::getPrice))
                .collect(Collectors.toList());

        final boolean finalIsNonWearable = isNonWearable;

        // 4. 最终筛选：价格不超过用户上限 + 磨损范围
        List<C5ProductListResponse.ProductDTO> qualifiedItems = sortedItems.stream()
                .filter(item -> item.getPrice().compareTo(task.getMaxPrice()) <= 0)
                .filter(item -> checkWear(item, task, finalIsNonWearable))
                .collect(Collectors.toList());

        if (CollUtil.isEmpty(qualifiedItems)) {
            return;
        }

        // 5. 批量下单
        int requested = task.getBuyCount() != null && task.getBuyCount() > 0
                ? Math.min(qualifiedItems.size(), task.getBuyCount())
                : 1;
        int approved = reserveQuota(task, requested);
        if (approved <= 0) {
            log.info("任务 [{}] 当前无可用配额，跳过本轮下单", task.getId());
            return;
        }

        List<C5ProductListResponse.ProductDTO> reservedItems = reservePendingProducts(task, qualifiedItems, approved);
        if (CollUtil.isEmpty(reservedItems)) {
            log.info("任务 [{}] 命中商品占位防重，跳过本轮下单", task.getId());
            restoreQuota(task, approved);
            return;
        }

        int lockedCount = reservedItems.size();
        if (lockedCount < approved) {
            restoreQuota(task, approved - lockedCount);
        }

        doBatchBuy(client, task, reservedItems, goods, lockedCount);
    }

    private boolean checkWear(C5ProductListResponse.ProductDTO item, BuffScanTask task, boolean isNonWearable) {
        if (isNonWearable) {
            return true;
        }
        // 如果没有磨损要求，直接通过
        if (task.getMinPaintwear() == null && task.getMaxPaintwear() == null) {
            return true;
        }

        // 获取商品磨损
        Double wearVal = null;
        if (item.getAssetInfo() != null) {
            wearVal = item.getAssetInfo().getFloatWear();
            if (wearVal == null) {
                wearVal = item.getAssetInfo().getWear();
            }
        }

        // 无磨损信息商品默认视为符合条件
        // 这里假设如果用户设置了磨损区间，但商品无磨损，则不买
        if (wearVal == null) {
            return task.getMinPaintwear() == null && task.getMaxPaintwear() == null;
        }

        BigDecimal wear = BigDecimal.valueOf(wearVal);

        if (task.getMinPaintwear() != null && wear.compareTo(task.getMinPaintwear()) < 0) {
            return false;
        }
        if (task.getMaxPaintwear() != null && wear.compareTo(task.getMaxPaintwear()) > 0) {
            return false;
        }
        return true;
    }

    private void doBatchBuy(C5ApiClient client, BuffScanTask task, List<C5ProductListResponse.ProductDTO> items,
                            Cs2Goods goods, int reservedQuota) {
        // 中断检查 (下单动作前)
        if (Thread.currentThread().isInterrupted()) {
            log.warn("任务 [{}] 在批量下单前被中断", task.getId());
            releasePendingProducts(task, items);
            restoreQuota(task, reservedQuota);
            return;
        }

        String batchId = IdUtil.fastSimpleUUID();
        log.info("任务 [{}] 触发批量购买, 批次: {}, 数量: {}", task.getId(), batchId, items.size());

        // 1. 预生成订单记录 (状态: 处理中)
        List<TradeOrderRecord> records = new ArrayList<>();
        List<C5BatchBuyRequest.BatchProduct> batchProducts = new ArrayList<>();

        // 获取 TradeUrl
        UserPlatformSettingsDTO settings = userPlatformSettingsService.getByUserId(task.getUserId());
        String tradeUrl = (settings != null) ? settings.getSteamTradeUrl() : null;
        if (StrUtil.isBlank(tradeUrl)) {
            markTaskError(task, "未配置 Steam Trade URL");
            releasePendingProducts(task, items);
            restoreQuota(task, reservedQuota);
            return;
        }

        for (C5ProductListResponse.ProductDTO item : items) {
            // 中断检查 (循环内)
            if (Thread.currentThread().isInterrupted()) {
                log.warn("任务 [{}] 在构造订单记录时被中断", task.getId());
                releasePendingProducts(task, items);
                restoreQuota(task, reservedQuota);
                return;
            }
            String outTradeNo = IdUtil.getSnowflakeNextIdStr(); // 每个商品独立的流水号

            // 构建请求项
            C5BatchBuyRequest.BatchProduct bp = new C5BatchBuyRequest.BatchProduct();
            bp.setProductId(Long.valueOf(item.getProductId()));
            bp.setBuyPrice(item.getPrice());
            bp.setOutTradeNo(outTradeNo);
            batchProducts.add(bp);

            // 构建数据库记录
            TradeOrderRecord record = new TradeOrderRecord();
            record.setUserId(task.getUserId());
            record.setTaskId(task.getId());
            record.setPlatform(PlatformEnum.C5.name());
            record.setGoodsName(goods.getDisplayName());
            record.setMarketHashName(goods.getMarketHashName());
            record.setPrice(item.getPrice());
            record.setGoodsImg(goods.getImageUrl()); // 简单取 goods 图

            // 磨损值存储到 extra_info
            Double wearVal = null;
            if (item.getAssetInfo() != null) {
                wearVal = item.getAssetInfo().getFloatWear();
                if (wearVal == null) {
                    wearVal = item.getAssetInfo().getWear();
                }
            }
            if (wearVal != null) {
                Map<String, Object> extraInfo = new HashMap<>();
                extraInfo.put("paintwear", wearVal);
                record.setExtraInfo(extraInfo);
            }
            record.setStatus(0); // 处理中
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            record.setOutTradeNo(outTradeNo);

            records.add(record);
        }

        // 保存记录
        for (TradeOrderRecord record : records) {
            tradeOrderRecordMapperManager.save(record);
        }

        // 2. 调用 API
        try {
            // 中断检查 (API 调用前)
            if (Thread.currentThread().isInterrupted()) {
                log.warn("任务 [{}] 在调用 C5 购买 API 前被中断", task.getId());
                markPendingAsFailed(records, "任务已中断");
                releasePendingProducts(task, items);
                restoreQuota(task, reservedQuota);
                return;
            }
            C5BatchBuyRequest req = new C5BatchBuyRequest()
                    .setTradeUrl(tradeUrl)
                    .setProductList(batchProducts);

            C5BatchBuyResponse resp = client.getTrade().batchBuy(req);

            if (resp == null) {
                updateLastError(task, "C5 批量下单响应为空");
                markPendingAsFailed(records, "API返回为空");
                releasePendingProducts(task, items);
                restoreQuota(task, reservedQuota);
                return;
            }

            int successCount = 0;

            // 3. 处理成功项
            if (CollUtil.isNotEmpty(resp.getSuccessList())) {
                for (C5BatchBuyResponse.SuccessItem successItem : resp.getSuccessList()) {
                    updateRecordStatus(records, successItem.getOutTradeNo(), 1, successItem.getOrderId(), null);
                    successCount++;
                }
            }

            // 4. 处理失败项
            if (CollUtil.isNotEmpty(resp.getFailedList())) {
                C5BatchBuyResponse.FailedItem firstError = resp.getFailedList().get(0);
                String errorMsg = firstError.getErrorMsg();
                if (StrUtil.isBlank(errorMsg)) {
                    errorMsg = "购买失败 (ErrorCode: " + firstError.getErrorCode() + ")";
                }
                updateLastError(task, errorMsg);

                for (C5BatchBuyResponse.FailedItem failedItem : resp.getFailedList()) {
                    String msg = "购买失败";
                    if (failedItem.getErrorCode() != null || StrUtil.isNotBlank(failedItem.getErrorMsg())) {
                        msg = StrUtil.format("购买失败 [Code: {}, Msg: {}]", failedItem.getErrorCode(),
                                failedItem.getErrorMsg());
                    }
                    updateRecordStatus(records, failedItem.getOutTradeNo(), 2, null, msg);
                }
            }

            // 5. 特殊处理：如果响应中既没有成功也没有失败，但请求发出了，可能是 API 异常，保留处理中状态供人工检查
            if (CollUtil.isEmpty(resp.getSuccessList()) && CollUtil.isEmpty(resp.getFailedList())) {
                log.warn("任务 [{}] 批量购买响应内容为空 (Success/Failed 列表均无数据), 批次: {}", task.getId(), batchId);
                // 不调用 markAllFailed，保持记录为处理中 (status=0)
            }

            // 6. 兜底扫描 (Final Sweep)：将所有仍处于"处理中"状态的记录标记为失败
            // 防止因 API 未返回 out_trade_no 或匹配失败导致订单状态永久卡死
            List<TradeOrderRecord> zombieRecords = records.stream()
                    .filter(r -> r.getStatus() == 0)
                    .collect(Collectors.toList());

            if (CollUtil.isNotEmpty(zombieRecords)) {
                log.warn("任务 [{}] 发现 {} 条僵尸订单 (API未返回状态), 统一切换为失败", task.getId(), zombieRecords.size());
                for (TradeOrderRecord zombie : zombieRecords) {
                    // 使用 out_trade_no 更新，确保原子性
                    tradeOrderRecordMapperManager.lambdaUpdate()
                            .eq(TradeOrderRecord::getOutTradeNo, zombie.getOutTradeNo())
                            .set(TradeOrderRecord::getStatus, 2)
                            .set(TradeOrderRecord::getErrorMsg, "API无响应/未匹配到结果")
                            .set(TradeOrderRecord::getUpdateTime, LocalDateTime.now())
                            .update();
                }
            }

            int failedQuota = (int) records.stream()
                    .filter(record -> !Integer.valueOf(1).equals(record.getStatus()))
                    .count();
            restoreQuota(task, failedQuota);

            // 7. 更新任务进度
            releasePendingProducts(task, items);

            if (successCount > 0) {
                // 清空错误信息
                updateLastError(task, null);

                log.info("任务 [{}] 批次 {} 完成，成功: {}, 失败: {}", task.getId(), batchId, successCount, resp.getFailNum());

                // 检查自动完成
                if (task.getBuyCount() != null) {
                    long totalSuccess = tradeOrderRecordMapperManager.countSuccess(task.getId());
                    if (totalSuccess >= task.getBuyCount()) {
                        c5TaskScheduler.complete(task.getId());
                    }
                }
            } else if (CollUtil.isNotEmpty(resp.getFailedList())) {
                log.warn("任务 [{}] 批次 {} 全部失败", task.getId(), batchId);
            }

        } catch (Exception e) {
            // 1. 探测并清除中断状态
            boolean isInterrupted = Thread.interrupted();

            // 2. 记录异常日志 (包含是否因中断导致的信息)
            if (isInterrupted || e instanceof InterruptedException) {
                log.warn("任务 [{}] 被中断 (Interrupted), 正在执行状态回写兜底逻辑. 原异常: {}", task.getId(), e.getMessage());
            } else {
                log.error("任务 [{}] 批量下单异常 (网络或解析错误)", task.getId(), e);
            }

            // 3. 安全落库 (此时线程状态已干净，可以获取 DB 连接)
            try {
                updateLastError(task, "批量下单异常: " + e.getMessage());
                // 只有在真正的系统异常（如网络不通）时才标记为失败，且只标记那些还是“处理中”的记录
                markPendingAsFailed(records, "异常: " + e.getMessage());
                restoreQuota(task, reservedQuota);
            } catch (Exception dbEx) {
                log.error("任务 [{}] 状态回写失败 (严重): {}", task.getId(), dbEx.getMessage());
            } finally {
                releasePendingProducts(task, items);
            }

            // 4. 现场还原 (恢复中断状态，让上层感知)
            if (isInterrupted || e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void updateRecordStatus(List<TradeOrderRecord> records, String outTradeNo, Integer status,
                                    String platformOrderId, String errorMsg) {
        if (StrUtil.isBlank(outTradeNo)) {
            log.warn("C5响应中存在空的 out_trade_no, 无法更新状态. ErrorMsg: {}", errorMsg);
            return;
        }

        boolean matched = false;
        for (TradeOrderRecord record : records) {
            if (StrUtil.equals(record.getOutTradeNo(), outTradeNo)) {
                record.setStatus(status);
                if (platformOrderId != null) {
                    record.setOrderId(platformOrderId);
                }
                if (errorMsg != null) {
                    record.setErrorMsg(errorMsg);
                }
                record.setUpdateTime(LocalDateTime.now());

                // 使用 out_trade_no 进行数据库更新，不依赖 id
                tradeOrderRecordMapperManager.lambdaUpdate()
                        .eq(TradeOrderRecord::getOutTradeNo, outTradeNo)
                        .set(TradeOrderRecord::getStatus, status)
                        .set(platformOrderId != null, TradeOrderRecord::getOrderId, platformOrderId)
                        .set(errorMsg != null, TradeOrderRecord::getErrorMsg, errorMsg)
                        .set(TradeOrderRecord::getUpdateTime, LocalDateTime.now())
                        .update();

                log.info("订单状态更新成功 [{}]: Status={}, OrderId={}", outTradeNo, status, platformOrderId);
                matched = true;
                break;
            }
        }

        if (!matched) {
            log.warn("C5响应包含未知的 out_trade_no [{}], 可能是并发或超时重试导致", outTradeNo);
        }
    }

    private void markPendingAsFailed(List<TradeOrderRecord> records, String errorMsg) {
        for (TradeOrderRecord record : records) {
            if (record.getStatus() == 0) { // 仅处理仍处于“处理中”状态的记录
                record.setStatus(2);
                record.setErrorMsg(errorMsg);
                record.setUpdateTime(LocalDateTime.now());
                tradeOrderRecordMapperManager.updateById(record);
            }
        }
    }

    private int reserveQuota(BuffScanTask task, int requested) {
        if (requested <= 0) {
            return 0;
        }
        if (task.getBuyCount() == null || task.getBuyCount() <= 0) {
            return Math.min(requested, 1);
        }

        String quotaKey = BuffConstant.REDIS_TASK_QUOTA_PREFIX + task.getId();
        Long approved = redisUtil.getStringRedisTemplate().execute(RESERVE_QUOTA_SCRIPT, List.of(quotaKey), String.valueOf(requested));
        return approved == null ? 0 : approved.intValue();
    }

    private List<C5ProductListResponse.ProductDTO> reservePendingProducts(BuffScanTask task,
                                                                           List<C5ProductListResponse.ProductDTO> items,
                                                                           int limit) {
        if (limit <= 0 || CollUtil.isEmpty(items)) {
            return Collections.emptyList();
        }

        List<C5ProductListResponse.ProductDTO> reservedItems = new ArrayList<>();
        Set<String> seenProductIds = new HashSet<>();
        for (C5ProductListResponse.ProductDTO item : items) {
            if (reservedItems.size() >= limit) {
                break;
            }
            String productId = StrUtil.trim(item.getProductId());
            if (StrUtil.isBlank(productId) || !seenProductIds.add(productId)) {
                continue;
            }
            if (tryReservePendingProduct(task, productId)) {
                reservedItems.add(item);
            }
        }
        return reservedItems;
    }

    private boolean tryReservePendingProduct(BuffScanTask task, String productId) {
        Boolean success = redisUtil.getStringRedisTemplate().opsForValue().setIfAbsent(
                buildPendingProductKey(task.getUserId(), productId),
                String.valueOf(task.getId()),
                PRODUCT_PENDING_TTL_SECONDS,
                TimeUnit.SECONDS
        );
        return Boolean.TRUE.equals(success);
    }

    private void releasePendingProducts(BuffScanTask task, List<C5ProductListResponse.ProductDTO> items) {
        if (CollUtil.isEmpty(items)) {
            return;
        }
        Set<String> keys = items.stream()
                .map(C5ProductListResponse.ProductDTO::getProductId)
                .map(StrUtil::trim)
                .filter(StrUtil::isNotBlank)
                .map(productId -> buildPendingProductKey(task.getUserId(), productId))
                .collect(Collectors.toSet());
        if (CollUtil.isNotEmpty(keys)) {
            redisUtil.getStringRedisTemplate().delete(keys);
        }
    }

    private String buildPendingProductKey(Long userId, String productId) {
        return C5_PRODUCT_PENDING_KEY_PREFIX + userId + ":" + productId;
    }

    private void restoreQuota(BuffScanTask task, int amount) {
        if (amount <= 0 || task.getBuyCount() == null || task.getBuyCount() <= 0) {
            return;
        }
        String quotaKey = BuffConstant.REDIS_TASK_QUOTA_PREFIX + task.getId();
        String totalKey = BuffConstant.REDIS_TASK_QUOTA_TOTAL_PREFIX + task.getId();
        redisUtil.getStringRedisTemplate().execute(RESTORE_QUOTA_SCRIPT, List.of(quotaKey, totalKey), String.valueOf(amount));
    }

    private void updateLastError(BuffScanTask task, String error) {
        task.setLastError(StrUtil.maxLength(error, 500));
        buffScanTaskMapper.updateById(task);
    }

    private void markTaskError(BuffScanTask task, String error) {
        task.setStatus(TaskStatusEnum.ERROR.getCode());
        task.setLastError(StrUtil.maxLength(error, 500));
        buffScanTaskMapper.updateById(task);
    }

    /**
     * 执行单个搜索请求
     */
    private List<C5ProductListResponse.ProductDTO> searchProducts(C5ApiClient client, String marketHashName,BigDecimal minWear, BigDecimal maxWear, boolean isNonWearable, BuffScanTask task) {
        try {
            C5ProductListResponse response;
            if (isNonWearable) {
                // 快接口路由: 用于非磨损类商品
                C5ProductListRequest req = new C5ProductListRequest()
                        .setAppId(730)
                        .setMarketHashName(marketHashName)
                        .setPageNum(1)
                        .setPageSize(20);
                log.info("C5快接口搜索 [/products/list]: {}", JSONUtil.toJsonStr(req));
                response = client.getMarket().searchProductList(req);
            } else {
                // 慢接口路由: 用于磨损类商品，支持磨损/价格区间筛选
                C5ProductSearchRequest req = new C5ProductSearchRequest()
                        .setAppId(730)
                        .setMarketHashName(marketHashName)
                        .setWearMin(minWear != null ? minWear.doubleValue() : null)
                        .setWearMax(maxWear != null ? maxWear.doubleValue() : null)
                        .setPriceMax(C5TradeStrategyImpl.GLOBAL_MAX_PRICE)
                        .setPageNum(1)
                        .setPageSize(20);
                log.info("C5慢接口搜索 [/products/search]: {}", JSONUtil.toJsonStr(req));
                response = client.getMarket().productSearch(req);
            }
            return response != null ? response.getList() : Collections.emptyList();
        } catch (Exception e) {
            log.error("C5搜索异常 [HashName={}]: {}", marketHashName, e.getMessage());
            updateLastError(task, "搜索异常: " + e.getMessage());
            return Collections.emptyList();
        }
    }

}