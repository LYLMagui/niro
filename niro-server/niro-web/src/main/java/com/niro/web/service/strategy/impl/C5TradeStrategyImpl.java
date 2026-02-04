package com.niro.web.service.strategy.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.niro.core.exception.BusinessException;
import com.niro.sdk.c5.client.C5ApiClient;
import com.niro.sdk.c5.config.C5Config;
import com.niro.sdk.c5.request.account.C5AccountBalanceRequest;
import com.niro.sdk.c5.request.market.C5ProductSearchRequest;
import com.niro.sdk.c5.request.trade.C5BatchBuyRequest;
import com.niro.sdk.c5.response.C5BalanceResponse;
import com.niro.sdk.c5.response.market.C5ProductSearchResponse;
import com.niro.sdk.c5.response.trade.C5BatchBuyResponse;
import com.niro.web.dto.UserPlatformSettingsDTO;
import com.niro.web.entity.*;
import com.niro.web.enums.PlatformEnum;
import com.niro.web.manager.TradeOrderRecordManagerMapper;
import com.niro.web.mapper.BuffScanTaskMapper;
import com.niro.web.scheduler.C5TaskScheduler;
import com.niro.web.service.BuffGoodsCategoryService;
import com.niro.web.service.BuffGoodsService;
import com.niro.web.service.UserPlatformSettingsService;
import com.niro.web.service.strategy.IPlatformStrategy;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * C5 平台策略实现
 * <p>
 * 核心策略：Market Depth (市场深度) 锚点定价
 * 不依赖 Buff 参考价，仅根据 C5 实时在售列表的分布情况，动态计算安全买入价。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class C5TradeStrategyImpl implements IPlatformStrategy {

    private final C5TaskScheduler c5TaskScheduler;
    private final UserPlatformSettingsService userPlatformSettingsService;
    private final BuffGoodsService buffGoodsService;
    private final BuffGoodsCategoryService buffGoodsCategoryService;
    private final TradeOrderRecordManagerMapper tradeOrderRecordManagerMapper;
    private final BuffScanTaskMapper buffScanTaskMapper;

    private static final BigDecimal GLOBAL_MAX_PRICE = new BigDecimal("999999");

    @Value("${c5.base-url:https://openapi.c5game.com}")
    private String c5BaseUrl;

    // 客户端缓存 (UserId -> Client)
    private final Map<Long, C5ApiClient> clientCache = new ConcurrentHashMap<>();

    @Override
    public void stopTask(Long taskId) {
        c5TaskScheduler.stop(taskId);
    }

    @Override
    public PlatformEnum getPlatform() {
        return PlatformEnum.C5;
    }

    private C5ApiClient getClient(Long userId) {
        return clientCache.computeIfAbsent(userId, uid -> {
            UserPlatformSettingsDTO settings = userPlatformSettingsService.getByUserId(uid);
            if (settings == null) {
                throw new BusinessException("用户配置不存在");
            }
            if (StrUtil.isBlank(settings.getC5AppKey())) {
                throw new BusinessException("C5 App Key 未配置");
            }
            C5Config config = new C5Config()
                    .setApiKey(settings.getC5AppKey())
                    .setSecretKey(settings.getC5SecretKey())
                    .setBaseUrl(c5BaseUrl);
            return new C5ApiClient(config);
        });
    }

    @Override
    public void handleTask(BuffScanTask task) {
        // 传递 executeTrade 作为业务逻辑回调
        c5TaskScheduler.start(task, this::executeTrade);
    }

    @Override
    public void syncAccountBalance(BuffAccount account) {
        try {
            C5ApiClient client = getClient(account.getUserId());
            C5BalanceResponse balance = client.getAccount().getBalance(
                    new C5AccountBalanceRequest().setAccountType(0)
            );
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
     * 1. 解析动态配置 (Anchor, SafeMargin)
     * 2. 并行全量搜索 (不设价格上限)
     * 3. 深度锚点算法计算 DynamicLimit
     * 4. 批量下单 (Batch Buy)
     * </p>
     */
    public void executeTrade(BuffScanTask task) {
        // 0. 校验任务状态
        if (task.getBuyCount() != null && task.getBuyCount() > 0) {
            long currentSuccess = tradeOrderRecordManagerMapper.countSuccess(task.getId());
            if (currentSuccess >= task.getBuyCount()) {
                log.info("任务 [{}] 已完成购买目标 ({}/{})，停止执行", task.getId(), currentSuccess, task.getBuyCount());
                c5TaskScheduler.complete(task.getId());
                return;
            }
        }

        // 1. 准备参数与配置
        BuffGoods goods = buffGoodsService.lambdaQuery().eq(BuffGoods::getGoodsId, task.getGoodsId()).one();
        if (goods == null || StrUtil.isBlank(goods.getMarketHashName())) {
            String errorMsg = goods == null ? "商品不存在" : "商品 MarketHashName 为空";
            log.error("任务 [{}] {}", task.getId(), errorMsg);
            updateLastError(task, errorMsg);
            c5TaskScheduler.stop(task.getId());
            return;
        }
        String marketHashName = goods.getMarketHashName();
        StrategyConfig config = buildStrategyConfig(task);

        final C5ApiClient client = getClient(task.getUserId());

        // 检查是否为非磨损类物品 (父分类为"其他"或"Other")
        boolean isNonWearable = false;
        if (goods.getCategoryId() != null) {
            BuffGoodsCategory category = buffGoodsCategoryService.getById(goods.getCategoryId());
            if (category != null && category.getParentId() != null) {
                BuffGoodsCategory parentCategory = buffGoodsCategoryService.getById(category.getParentId());
                if (parentCategory != null) {
                    String pName = parentCategory.getName();
                    if ("其他".equals(pName) || "Other".equalsIgnoreCase(pName)) {
                        isNonWearable = true;
                    }
                }
            }
        }

        // 2. 并行搜索 (不传 MaxPrice 以获取市场全貌)
        // 注意：为了获取锚点，我们需要看到比用户限价更高的商品，所以这里 maxPrice 传 GLOBAL_MAX_PRICE
        // 如果是非磨损类物品，强制 minWear/maxWear 为 null
        final BigDecimal minWear = isNonWearable ? null : task.getMinPaintwear();
        final BigDecimal maxWear = isNonWearable ? null : task.getMaxPaintwear();

        // 中断检查
        if (Thread.currentThread().isInterrupted()) {
            log.warn("任务 [{}] 在搜索前被中断", task.getId());
            return;
        }


        CompletableFuture<List<C5ProductSearchResponse.ProductItem>> manualFuture = CompletableFuture.supplyAsync(() ->
                searchProducts(client, marketHashName, GLOBAL_MAX_PRICE, minWear, maxWear, 1)
        );
        CompletableFuture<List<C5ProductSearchResponse.ProductItem>> autoFuture = CompletableFuture.supplyAsync(() ->
                searchProducts(client, marketHashName, GLOBAL_MAX_PRICE, minWear, maxWear, 2)
        );

        try {
            CompletableFuture.allOf(manualFuture, autoFuture).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.warn("任务 [{}] 搜索等待被中断", task.getId());
            Thread.currentThread().interrupt();
            return;
        } catch (Exception e) {
            log.error("任务 [{}] 搜索过程异常或超时: {}", task.getId(), e.getMessage());
        }

        List<C5ProductSearchResponse.ProductItem> allItems = new ArrayList<>();
        try {
            List<C5ProductSearchResponse.ProductItem> list = manualFuture.get();
            if (CollUtil.isNotEmpty(list)) allItems.addAll(list);
        } catch (Exception e) {
            log.warn("任务 [{}] 搜索人工发货失败: {}", task.getId(), e.getMessage());
        }
        try {
            List<C5ProductSearchResponse.ProductItem> list = autoFuture.get();
            if (CollUtil.isNotEmpty(list)) allItems.addAll(list);
        } catch (Exception e) {
            log.warn("任务 [{}] 搜索自动发货失败: {}", task.getId(), e.getMessage());
        }

        if (CollUtil.isEmpty(allItems)) {
            return;
        }

        // 3. 排序 (价格升序)
        List<C5ProductSearchResponse.ProductItem> sortedItems = allItems.stream()
                .filter(item -> item.getPrice() != null)
                .sorted(Comparator.comparing(C5ProductSearchResponse.ProductItem::getPrice))
                .collect(Collectors.toList());

        // 4. 深度锚点算法 (Depth Anchor Algorithm)
        BigDecimal dynamicMaxPrice = calculateDynamicLimit(task, sortedItems, config);

        // 中断检查 (锚点计算后)
        if (Thread.currentThread().isInterrupted()) {
            log.warn("任务 [{}] 在计算锚点后被中断", task.getId());
            return;
        }

        final boolean finalIsNonWearable = isNonWearable;

        // 5. 最终筛选
        List<C5ProductSearchResponse.ProductItem> qualifiedItems = sortedItems.stream()
                .filter(item -> item.getPrice().compareTo(task.getMaxPrice()) <= 0) // 硬上限
                .filter(item -> item.getPrice().compareTo(dynamicMaxPrice) <= 0)    // 动态上限
                .filter(item -> checkWear(item, task, finalIsNonWearable))          // 磨损范围
                .collect(Collectors.toList());

        if (CollUtil.isEmpty(qualifiedItems)) {
            return;
        }

        // 6. 批量下单
        // 计算本轮最大购买数
        long currentSuccess = tradeOrderRecordManagerMapper.countSuccess(task.getId());
        int remaining = (task.getBuyCount() != null && task.getBuyCount() > 0)
                ? (int) (task.getBuyCount() - currentSuccess)
                : 1; // 未限制数量时默认为1，避免并发风险
        // 如果 remaining <= 0，前面已经拦截，但在并发下可能需要再次检查
        if (remaining <= 0) return;

        List<C5ProductSearchResponse.ProductItem> toBuyList = qualifiedItems.subList(0, Math.min(qualifiedItems.size(), remaining));
        doBatchBuy(client, task, toBuyList, goods);
    }

    private boolean checkWear(C5ProductSearchResponse.ProductItem item, BuffScanTask task, boolean isNonWearable) {
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
            wearVal = item.getAssetInfo().getWear();
        } else if (item.getItemInfo() != null) {
            // 优先从 assetInfo 获取磨损信息
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

    /**
     * 计算动态上限 (Price Tier Anchoring)
     */
    private BigDecimal calculateDynamicLimit(BuffScanTask task, List<C5ProductSearchResponse.ProductItem> sortedProducts, StrategyConfig config) {
        BigDecimal userMax = task.getMaxPrice();

        // 深度不足，降级处理
        if (sortedProducts.size() < config.minConcurrency) {
            log.warn("任务 [{}] 市场深度不足 (当前: {}, 阈值: {}), 降级使用用户限价: {}",
                    task.getId(), sortedProducts.size(), config.minConcurrency, userMax);
            return userMax;
        }

        // 1. 提取价格阶梯 (去重 + 排序)
        // Note: BigDecimal distinct() uses equals() which checks scale. Use compareTo for value equality.
        List<BigDecimal> rawPrices = sortedProducts.stream()
                .map(C5ProductSearchResponse.ProductItem::getPrice)
                .sorted()
                .collect(Collectors.toList());

        List<BigDecimal> priceTiers = new ArrayList<>();
        for (BigDecimal price : rawPrices) {
            if (priceTiers.isEmpty() || priceTiers.get(priceTiers.size() - 1).compareTo(price) != 0) {
                priceTiers.add(price);
            }
        }

        // 2. 确定锚点
        BigDecimal anchorPrice;
        double currentMargin = config.safeMargin;

        if (priceTiers.size() > config.anchorTierIndex) {
            // 正常情况：取第 N 个阶梯 (例如 anchorTierIndex=1, 取第2个价格)
            anchorPrice = priceTiers.get(config.anchorTierIndex);
        } else {
            // 阶梯不足 (例如只有一种价格): 取第1个价格，并扩大安全边际
            anchorPrice = priceTiers.get(0);
            currentMargin = config.safeMargin * 1.5;
            log.info("任务 [{}] 价格阶梯不足 ({}个), 启用保守模式 (Margin x1.5)", task.getId(), priceTiers.size());
        }

        // 3. 计算动态上限
        BigDecimal dynamicLimit;
        if (config.anchorTierIndex == 0) {
            // 如果锚定第1个阶梯 (ladderStep=1)，则不应用安全边际，直接以锚点价为上限
            dynamicLimit = anchorPrice;
        } else {
            BigDecimal safeRatio = BigDecimal.ONE.subtract(BigDecimal.valueOf(currentMargin));
            dynamicLimit = anchorPrice.multiply(safeRatio);
        }

        // 4. 阻断告警 (Fail Loudly)
        if (CollUtil.isNotEmpty(priceTiers) && dynamicLimit.compareTo(priceTiers.get(0)) < 0) {
            BigDecimal diff = priceTiers.get(0).subtract(dynamicLimit);
            log.warn("任务 [{}] 动态上限过低警告! 动态上限: {}, 市场最低价: {}, 差值: {}. 因安全边际过高导致无法购买.",
                    task.getId(), dynamicLimit, priceTiers.get(0), diff);
        }

        // 5. 日志
        // 为了日志简洁，只打印前5个阶梯
        String tierLog = priceTiers.stream().limit(5).map(String::valueOf).collect(Collectors.joining(", "));
        log.info("任务 [{}] 价格阶梯: [{}], 锚定: {}, 动态上限: {} (Margin={}%), 用户上限={}",
                task.getId(), tierLog, anchorPrice, dynamicLimit, String.format("%.2f", currentMargin * 100), userMax);

        return dynamicLimit;
    }

    private void doBatchBuy(C5ApiClient client, BuffScanTask task, List<C5ProductSearchResponse.ProductItem> items, BuffGoods goods) {
        // 中断检查 (下单动作前)
        if (Thread.currentThread().isInterrupted()) {
            log.warn("任务 [{}] 在批量下单前被中断", task.getId());
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
            updateLastError(task, "未配置 Steam Trade URL");
            return;
        }

        for (C5ProductSearchResponse.ProductItem item : items) {
            // 中断检查 (循环内)
            if (Thread.currentThread().isInterrupted()) {
                log.warn("任务 [{}] 在构造订单记录时被中断", task.getId());
                return;
            }
            String outTradeNo = IdUtil.getSnowflakeNextIdStr(); // 每个商品独立的流水号

            // 构建请求项
            C5BatchBuyRequest.BatchProduct bp = new C5BatchBuyRequest.BatchProduct();
            bp.setProductId(Long.valueOf(item.getId()));
            bp.setBuyPrice(item.getPrice());
            bp.setOutTradeNo(outTradeNo);
            batchProducts.add(bp);

            // 构建数据库记录
            TradeOrderRecord record = new TradeOrderRecord();
            record.setUserId(task.getUserId());
            record.setTaskId(task.getId());
            record.setPlatform(PlatformEnum.C5.name());
            record.setGoodsName(goods.getMarketHashName()); // 或 item.getMarketHashName()
            record.setMarketHashName(goods.getMarketHashName());
            record.setGoodsId(task.getGoodsId());
            record.setPrice(item.getPrice());
            record.setGoodsImg(goods.getIconUrl()); // 简单取 goods 图
            if (item.getAssetInfo() != null && item.getAssetInfo().getWear() != null) {
                record.setPaintwear(BigDecimal.valueOf(item.getAssetInfo().getWear()));
            }
            record.setStatus(0); // 处理中
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            record.setOutTradeNo(outTradeNo);

            records.add(record);
        }

        // 保存记录
        for (TradeOrderRecord record : records) {
            tradeOrderRecordManagerMapper.save(record);
        }

        // 2. 调用 API
        try {
            // 中断检查 (API 调用前)
            if (Thread.currentThread().isInterrupted()) {
                log.warn("任务 [{}] 在调用 C5 购买 API 前被中断", task.getId());
                return;
            }
            C5BatchBuyRequest req = new C5BatchBuyRequest()
                    .setTradeUrl(tradeUrl)
                    .setProductList(batchProducts);

            C5BatchBuyResponse resp = client.getTrade().batchBuy(req);

            if (resp == null) {
                updateLastError(task, "C5 批量下单响应为空");
                markPendingAsFailed(records, "API返回为空");
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
                        msg = StrUtil.format("购买失败 [Code: {}, Msg: {}]", failedItem.getErrorCode(), failedItem.getErrorMsg());
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
                    tradeOrderRecordManagerMapper.lambdaUpdate()
                            .eq(TradeOrderRecord::getOutTradeNo, zombie.getOutTradeNo())
                            .set(TradeOrderRecord::getStatus, 2)
                            .set(TradeOrderRecord::getErrorMsg, "API无响应/未匹配到结果")
                            .set(TradeOrderRecord::getUpdateTime, LocalDateTime.now())
                            .update();
                }
            }

            // 7. 更新任务进度
            if (successCount > 0) {
                // 清空错误信息
                updateLastError(task, null);

                log.info("任务 [{}] 批次 {} 完成，成功: {}, 失败: {}", task.getId(), batchId, successCount, resp.getFailNum());

                // 检查自动完成
                if (task.getBuyCount() != null) {
                    long totalSuccess = tradeOrderRecordManagerMapper.countSuccess(task.getId());
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
            } catch (Exception dbEx) {
                log.error("任务 [{}] 状态回写失败 (严重): {}", task.getId(), dbEx.getMessage());
            }

            // 4. 现场还原 (恢复中断状态，让上层感知)
            if (isInterrupted || e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void updateRecordStatus(List<TradeOrderRecord> records, String outTradeNo, Integer status, String platformOrderId, String errorMsg) {
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
                tradeOrderRecordManagerMapper.lambdaUpdate()
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
                tradeOrderRecordManagerMapper.updateById(record);
            }
        }
    }

    private void updateLastError(BuffScanTask task, String error) {
        task.setLastError(StrUtil.maxLength(error, 500));
        buffScanTaskMapper.updateById(task);
    }

    /**
     * 执行单个搜索请求
     */
    private List<C5ProductSearchResponse.ProductItem> searchProducts(C5ApiClient client, String marketHashName, BigDecimal maxPrice, BigDecimal minWear, BigDecimal maxWear, Integer delivery) {
        try {
            C5ProductSearchRequest req = new C5ProductSearchRequest()
                    .setAppId(730) // CS2
                    .setMarketHashName(marketHashName)
                    .setPageNum(1)
                    .setPageSize(20) // 每次取前20个
                    .setMaxPrice(maxPrice) // 传 null 则不限制
                    .setDelivery(delivery);

            if (minWear != null) {
                req.setMinWear(minWear.doubleValue());
            }
            if (maxWear != null) {
                req.setMaxWear(maxWear.doubleValue());
            }
            log.info("requestBody:{}", JSONUtil.toJsonPrettyStr(req));
            C5ProductSearchResponse response = client.getMarket().searchProductsByHashName(req);
            return response != null ? response.getList() : Collections.emptyList();
        } catch (Exception e) {
            // log.warn("C5搜索异常 [HashName={}, Delivery={}]: {}", marketHashName, delivery, e.getMessage());
            return Collections.emptyList();
        }
    }

    private StrategyConfig buildStrategyConfig(BuffScanTask task) {
        StrategyConfig config = new StrategyConfig();

        // 1. 先尝试解析 ExtraConfig (兼容旧数据)
        if (StrUtil.isNotBlank(task.getExtraConfig())) {
            try {
                JSONObject json = JSONUtil.parseObj(task.getExtraConfig());
                config.anchorTierIndex = json.getInt("anchorTierIndex", 1);
                config.safeMargin = json.getDouble("safeMargin", 0.03);
                config.minConcurrency = json.getInt("minConcurrency", 5);
            } catch (Exception e) {
                log.warn("解析 ExtraConfig 失败，使用默认值", e);
            }
        }

        // 2. 优先使用独立字段 (如果存在)
        if (task.getSafetyMargin() != null) {
            config.safeMargin = task.getSafetyMargin().doubleValue();
        }
        if (task.getLadderStep() != null) {
            // 新字段 ladderStep 是 1-based (1=Top1)，转换为 0-based index
            // 如果 ladderStep=1 -> index=0
            // 增加 Math.min 限制，防止配置的阶梯数超过实际搜索到的阶梯上限 (PageSize=20)
            config.anchorTierIndex = Math.min(Math.max(0, task.getLadderStep().intValue() - 1), 19);
        }

        return config;
    }

    private StrategyConfig parseConfig(String extraConfigJson) {
        // Deprecated, replaced by buildStrategyConfig
        return new StrategyConfig();
    }

    @Data
    private static class StrategyConfig {
        // 默认锚定第2个阶梯 (Index 1: 次低价)
        private int anchorTierIndex = 1;

        /**
         * 安全边际 (Safe Margin)
         * <p>
         * 0.01-0.02 (1%-2%): 高流动性通货 (如钥匙、红线)
         * 0.03-0.05 (3%-5%): [默认] 热门饰品，平衡成交率与抗跌
         * 0.08+ (8%+): 低流动性/高价值饰品，深水防套
         * </p>
         */
        private double safeMargin = 0.03;
        private int minConcurrency = 5;
    }
}