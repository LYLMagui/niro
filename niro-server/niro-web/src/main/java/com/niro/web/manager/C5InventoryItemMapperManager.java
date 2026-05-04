package com.niro.web.manager;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.dto.C5InventoryAggregateQueryDTO;
import com.niro.web.entity.C5InventoryItem;
import com.niro.web.mapper.C5InventoryItemMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
                .in(C5InventoryItem::getInventoryStatus, "IN_STOCK", "LISTING")
                .eq(accountId != null, C5InventoryItem::getAccountId, accountId)
                .and(StrUtil.isNotBlank(keyword), wrapper -> wrapper
                        .like(C5InventoryItem::getName, keyword)
                        .or()
                        .like(C5InventoryItem::getMarketHashName, keyword))
                .eq("tradable".equals(status), C5InventoryItem::getInventoryStatus, "IN_STOCK")
                .eq("tradable".equals(status), C5InventoryItem::getC5Status, 0)
                .eq("cooldown".equals(status), C5InventoryItem::getInventoryStatus, "IN_STOCK")
                .eq("cooldown".equals(status), C5InventoryItem::getC5Status, 4)
                .eq("selling".equals(status), C5InventoryItem::getInventoryStatus, "LISTING")
                .count();
    }

    /**
     * 查询当前用户库存聚合卡片数量。
     *
     * @param query 聚合查询参数
     * @return 聚合卡片数量
     */
    public long countAggregated(C5InventoryAggregateQueryDTO query) {
        return baseMapper.countAggregated(query);
    }

    /**
     * 分页查询当前用户库存聚合卡片。
     *
     * @param query 聚合查询参数
     * @return 聚合卡片列表
     */
    public List<C5InventoryItem> listAggregatedPage(C5InventoryAggregateQueryDTO query) {
        return baseMapper.listAggregatedPage(query);
    }

    /**
     * 统计当前用户活跃库存总价值。
     *
     * @param query 聚合查询参数
     * @return 活跃库存总价值
     */
    public BigDecimal sumActiveInventoryValue(C5InventoryAggregateQueryDTO query) {
        BigDecimal value = baseMapper.sumActiveInventoryValue(query);
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 分页查询聚合卡片背后的真实库存明细。
     *
     * @param userId 用户 ID
     * @param accountId 账号 ID
     * @param marketHashName Steam 市场 Hash 名称
     * @param name 商品名称
     * @param exteriorName 外观名称
     * @param ifTradable 是否可交易
     * @param page 当前页
     * @param pageSize 每页数量
     * @return 库存明细分页
     */
    public Page<C5InventoryItem> pageGroupItems(Long userId, Long accountId, String marketHashName, String name,
                                                String exteriorName, Boolean ifTradable, long page, long pageSize) {
        String groupName = StrUtil.blankToDefault(marketHashName, name);
        return this.lambdaQuery()
                .eq(C5InventoryItem::getUserId, userId)
                .eq(C5InventoryItem::getAccountId, accountId)
                .eq(C5InventoryItem::getInventoryStatus, "IN_STOCK")
                .eq(C5InventoryItem::getC5Status, 0)
                .eq(StrUtil.isNotBlank(groupName), StrUtil.isNotBlank(marketHashName) ? C5InventoryItem::getMarketHashName : C5InventoryItem::getName, groupName)
                .and(StrUtil.isBlank(exteriorName), wrapper -> wrapper
                        .isNull(C5InventoryItem::getExteriorName)
                        .or()
                        .eq(C5InventoryItem::getExteriorName, ""))
                .eq(StrUtil.isNotBlank(exteriorName), C5InventoryItem::getExteriorName, exteriorName)
                .eq(ifTradable != null, C5InventoryItem::getIfTradable, ifTradable)
                .orderByAsc(C5InventoryItem::getWear)
                .orderByDesc(C5InventoryItem::getLastSyncTime)
                .page(new Page<>(page, pageSize));
    }

    /**
     * 查询当前用户指定账号下的库存明细。
     *
     * @param userId 用户 ID
     * @param accountId 账号 ID
     * @param ids 本地库存 ID 集合
     * @return 库存明细列表
     */
    public List<C5InventoryItem> listByUserIdAccountIdAndIds(Long userId, Long accountId, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return this.lambdaQuery()
                .eq(C5InventoryItem::getUserId, userId)
                .eq(C5InventoryItem::getAccountId, accountId)
                .in(C5InventoryItem::getId, ids)
                .list();
    }

    /**
     * 将指定账号下的资产标记为上架中。
     *
     * @param accountId 账号 ID
     * @param assetIds 资产 ID 集合
     * @param updateTime 更新时间
     */
    public void markListingByAssetIds(Long accountId, Collection<String> assetIds, LocalDateTime updateTime) {
        if (assetIds == null || assetIds.isEmpty()) {
            return;
        }
        this.lambdaUpdate()
                .eq(C5InventoryItem::getAccountId, accountId)
                .in(C5InventoryItem::getAssetId, assetIds)
                .set(C5InventoryItem::getInventoryStatus, "LISTING")
                .set(C5InventoryItem::getUpdateTime, updateTime)
                .update();
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
                .in(C5InventoryItem::getInventoryStatus, "IN_STOCK", "LISTING")
                .eq(accountId != null, C5InventoryItem::getAccountId, accountId)
                .and(StrUtil.isNotBlank(keyword), wrapper -> wrapper
                        .like(C5InventoryItem::getName, keyword)
                        .or()
                        .like(C5InventoryItem::getMarketHashName, keyword))
                .eq("tradable".equals(status), C5InventoryItem::getInventoryStatus, "IN_STOCK")
                .eq("tradable".equals(status), C5InventoryItem::getC5Status, 0)
                .eq("cooldown".equals(status), C5InventoryItem::getInventoryStatus, "IN_STOCK")
                .eq("cooldown".equals(status), C5InventoryItem::getC5Status, 4)
                .eq("selling".equals(status), C5InventoryItem::getInventoryStatus, "LISTING")
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
     * 将账号下本次未返回的活跃库存快照标记为已移除。
     *
     * @param accountId 账号 ID
     * @param returnedAssetIds 本次返回的资产 ID 集合
     * @param syncTime 同步时间
     * @return 标记移除数量
     */
    public long markMissingInStockRemoved(Long accountId, Collection<String> returnedAssetIds, LocalDateTime syncTime) {
        long pendingRemoveCount = this.lambdaQuery()
                .eq(C5InventoryItem::getAccountId, accountId)
                .in(C5InventoryItem::getInventoryStatus, "IN_STOCK", "LISTING")
                .notIn(returnedAssetIds != null && !returnedAssetIds.isEmpty(), C5InventoryItem::getAssetId, returnedAssetIds)
                .count();
        if (pendingRemoveCount <= 0) {
            return 0L;
        }

        this.lambdaUpdate()
                .eq(C5InventoryItem::getAccountId, accountId)
                .in(C5InventoryItem::getInventoryStatus, "IN_STOCK", "LISTING")
                .notIn(returnedAssetIds != null && !returnedAssetIds.isEmpty(), C5InventoryItem::getAssetId, returnedAssetIds)
                .set(C5InventoryItem::getInventoryStatus, "REMOVED")
                .set(C5InventoryItem::getLastSyncTime, syncTime)
                .set(C5InventoryItem::getUpdateTime, syncTime)
                .update();
        return pendingRemoveCount;
    }
}
