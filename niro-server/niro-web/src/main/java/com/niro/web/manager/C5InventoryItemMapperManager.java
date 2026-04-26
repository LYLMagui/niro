package com.niro.web.manager;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.C5InventoryItem;
import com.niro.web.mapper.C5InventoryItemMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * C5 库存快照数据库访问管理器。
 */
@Service
public class C5InventoryItemMapperManager extends ServiceImpl<C5InventoryItemMapper, C5InventoryItem> {

    /**
     * 查询当前用户库存快照总件数。
     *
     * @param userId 用户 ID
     * @param accountId 账号 ID
     * @param keyword 商品关键字
     * @param status 状态筛选
     * @return 库存总件数
     */
    public long countInventoryItems(Long userId, Long accountId, String keyword, String status) {
        return this.lambdaQuery()
                .eq(C5InventoryItem::getUserId, userId)
                .eq(C5InventoryItem::getInventoryStatus, "IN_STOCK")
                .eq(accountId != null, C5InventoryItem::getAccountId, accountId)
                .and(StrUtil.isNotBlank(keyword), wrapper -> wrapper
                        .like(C5InventoryItem::getName, keyword)
                        .or()
                        .like(C5InventoryItem::getMarketHashName, keyword))
                .eq("tradable".equals(status), C5InventoryItem::getIfTradable, true)
                .and("cooldown".equals(status), wrapper -> wrapper
                        .eq(C5InventoryItem::getIfTradable, false)
                        .isNotNull(C5InventoryItem::getTradableTime)
                        .ne(C5InventoryItem::getTradableTime, ""))
                .apply("selling".equals(status), "1 = 0")
                .count();
    }

    /**
     * 查询当前用户库存快照用于后端聚合。
     *
     * @param userId 用户 ID
     * @param accountId 账号 ID
     * @param keyword 商品关键字
     * @param status 状态筛选
     * @return 库存快照列表
     */
    public List<C5InventoryItem> listInventoryForAggregation(Long userId, Long accountId, String keyword, String status) {
        return this.lambdaQuery()
                .eq(C5InventoryItem::getUserId, userId)
                .eq(C5InventoryItem::getInventoryStatus, "IN_STOCK")
                .eq(accountId != null, C5InventoryItem::getAccountId, accountId)
                .and(StrUtil.isNotBlank(keyword), wrapper -> wrapper
                        .like(C5InventoryItem::getName, keyword)
                        .or()
                        .like(C5InventoryItem::getMarketHashName, keyword))
                .eq("tradable".equals(status), C5InventoryItem::getIfTradable, true)
                .and("cooldown".equals(status), wrapper -> wrapper
                        .eq(C5InventoryItem::getIfTradable, false)
                        .isNotNull(C5InventoryItem::getTradableTime)
                        .ne(C5InventoryItem::getTradableTime, ""))
                .apply("selling".equals(status), "1 = 0")
                .orderByDesc(C5InventoryItem::getLastSyncTime)
                .orderByDesc(C5InventoryItem::getUpdateTime)
                .list();
    }

    /**
     * 分页查询当前用户库存快照。
     *
     * @param userId 用户 ID
     * @param accountId 账号 ID
     * @param keyword 商品关键字
     * @param status 状态筛选
     * @param page 当前页
     * @param pageSize 每页数量
     * @return 库存分页
     */
    public Page<C5InventoryItem> pageInventory(Long userId, Long accountId, String keyword, String status, long page, long pageSize) {
        return this.lambdaQuery()
                .eq(C5InventoryItem::getUserId, userId)
                .eq(C5InventoryItem::getInventoryStatus, "IN_STOCK")
                .eq(accountId != null, C5InventoryItem::getAccountId, accountId)
                .and(StrUtil.isNotBlank(keyword), wrapper -> wrapper
                        .like(C5InventoryItem::getName, keyword)
                        .or()
                        .like(C5InventoryItem::getMarketHashName, keyword))
                .eq("tradable".equals(status), C5InventoryItem::getIfTradable, true)
                .and("cooldown".equals(status), wrapper -> wrapper
                        .eq(C5InventoryItem::getIfTradable, false)
                        .isNotNull(C5InventoryItem::getTradableTime)
                        .ne(C5InventoryItem::getTradableTime, ""))
                .apply("selling".equals(status), "1 = 0")
                .orderByDesc(C5InventoryItem::getLastSyncTime)
                .orderByDesc(C5InventoryItem::getUpdateTime)
                .page(new Page<>(page, pageSize));
    }

    /**
     * 查询账号下指定资产 ID 的库存快照并按资产 ID 映射。
     *
     * @param accountId 账号 ID
     * @param assetIds 资产 ID 集合
     * @return 资产 ID 到库存实体的映射
     */
    public Map<String, C5InventoryItem> mapByAccountIdAndAssetIds(Long accountId, Collection<String> assetIds) {
        if (accountId == null || assetIds == null || assetIds.isEmpty()) {
            return Map.of();
        }
        return this.lambdaQuery()
                .eq(C5InventoryItem::getAccountId, accountId)
                .in(C5InventoryItem::getAssetId, assetIds)
                .list()
                .stream()
                .collect(Collectors.toMap(C5InventoryItem::getAssetId, Function.identity(), (left, right) -> left));
    }

    /**
     * 将账号下本次未返回的在库快照标记为已移除。
     *
     * @param accountId 账号 ID
     * @param returnedAssetIds 本次返回的资产 ID 集合
     * @param syncTime 同步时间
     * @return 标记移除数量
     */
    public long markMissingInStockRemoved(Long accountId, Collection<String> returnedAssetIds, LocalDateTime syncTime) {
        long pendingRemoveCount = this.lambdaQuery()
                .eq(C5InventoryItem::getAccountId, accountId)
                .eq(C5InventoryItem::getInventoryStatus, "IN_STOCK")
                .notIn(returnedAssetIds != null && !returnedAssetIds.isEmpty(), C5InventoryItem::getAssetId, returnedAssetIds)
                .count();
        if (pendingRemoveCount <= 0) {
            return 0L;
        }

        this.lambdaUpdate()
                .eq(C5InventoryItem::getAccountId, accountId)
                .eq(C5InventoryItem::getInventoryStatus, "IN_STOCK")
                .notIn(returnedAssetIds != null && !returnedAssetIds.isEmpty(), C5InventoryItem::getAssetId, returnedAssetIds)
                .set(C5InventoryItem::getInventoryStatus, "REMOVED")
                .set(C5InventoryItem::getLastSyncTime, syncTime)
                .set(C5InventoryItem::getUpdateTime, syncTime)
                .update();
        return pendingRemoveCount;
    }
}
