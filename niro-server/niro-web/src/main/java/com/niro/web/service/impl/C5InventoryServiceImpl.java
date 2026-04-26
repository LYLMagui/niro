package com.niro.web.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.niro.core.util.Assert;
import com.niro.sdk.c5.model.C5AssetInfo;
import com.niro.sdk.c5.model.C5ItemInfo;
import com.niro.sdk.c5.response.C5InventoryResponse;
import com.niro.web.dto.C5InventoryItemDTO;
import com.niro.web.dto.C5InventoryPageDTO;
import com.niro.web.dto.C5InventoryRefreshResultDTO;
import com.niro.web.dto.param.C5InventoryQueryParam;
import com.niro.web.dto.param.C5InventoryRefreshParam;
import com.niro.web.entity.C5InventoryItem;
import com.niro.web.entity.C5SnipingAccount;
import com.niro.web.manager.C5InventoryItemMapperManager;
import com.niro.web.manager.C5SnipingAccountMapperManager;
import com.niro.web.service.C5ApiClientService;
import com.niro.web.service.C5InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * C5 库存管理服务实现。
 */
@Service
@RequiredArgsConstructor
public class C5InventoryServiceImpl implements C5InventoryService {

    private static final String APP_ID_CS2 = "730";
    private static final String LANGUAGE_ZH = "zh";
    private static final String INITIAL_START_ASSET_ID = "0";
    private static final int PAGE_COUNT = 1000;
    private static final String STATUS_IN_STOCK = "IN_STOCK";
    private static final String STATUS_REMOVED = "REMOVED";

    private final C5SnipingAccountMapperManager accountManager;
    private final C5InventoryItemMapperManager inventoryItemManager;
    private final C5ApiClientService c5ApiClientService;

    /**
     * 刷新当前用户指定账号的 C5 库存快照。
     *
     * @param param 刷新参数
     * @return 刷新结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public C5InventoryRefreshResultDTO refreshInventory(C5InventoryRefreshParam param) {
        Assert.notNull(param, "库存刷新参数不能为空");
        Assert.notNull(param.getAccountId(), "账号ID不能为空");

        Long userId = StpUtil.getLoginIdAsLong();
        C5SnipingAccount account = accountManager.getByUserIdAndId(userId, param.getAccountId());
        Assert.notNull(account, "账号不存在");
        Assert.notBlank(account.getC5AppKey(), "账号 C5 AppKey 不能为空");
        Assert.notBlank(account.getSteamId(), "账号 Steam ID 不能为空，请先在账号配置页补充 Steam ID");

        LocalDateTime syncTime = LocalDateTime.now();
        List<C5InventoryResponse.InventoryItem> c5Items = fetchAllInventory(account);
        UpsertStat upsertStat = upsertInventoryItems(userId, account, c5Items, syncTime);
        long removedCount = inventoryItemManager.markMissingInStockRemoved(account.getId(), upsertStat.returnedAssetIds(), syncTime);

        C5InventoryRefreshResultDTO result = new C5InventoryRefreshResultDTO();
        result.setAccountId(account.getId());
        result.setAccountName(account.getAccountName());
        result.setTotal(c5Items.size());
        result.setAddedCount(upsertStat.addedCount());
        result.setUpdatedCount(upsertStat.updatedCount());
        result.setRemovedCount(Math.toIntExact(removedCount));
        result.setSyncTime(syncTime);
        return result;
    }

    /**
     * 分页查询当前用户 C5 库存快照。
     *
     * @param param 查询参数
     * @return 库存分页
     */
    @Override
    public C5InventoryPageDTO pageInventory(C5InventoryQueryParam param) {
        Long userId = StpUtil.getLoginIdAsLong();
        long page = normalizePage(param == null ? null : param.getPage());
        long pageSize = normalizePageSize(param == null ? null : param.getPageSize());
        Long accountId = param == null ? null : param.getAccountId();
        String keyword = param == null ? null : param.getKeyword();
        String status = normalizeStatus(param == null ? null : param.getStatus());

        List<C5InventoryItem> items = inventoryItemManager.listInventoryForAggregation(userId, accountId, keyword, status);
        Map<String, AggregatedInventoryCard> aggregatedMap = new LinkedHashMap<>();
        for (C5InventoryItem item : items) {
            String key = buildAggregationKey(accountId, item);
            AggregatedInventoryCard card = aggregatedMap.computeIfAbsent(key, ignored -> new AggregatedInventoryCard(item));
            card.increase();
        }

        List<AggregatedInventoryCard> aggregatedCards = new ArrayList<>(aggregatedMap.values());
        int fromIndex = Math.toIntExact(Math.min((page - 1) * pageSize, aggregatedCards.size()));
        int toIndex = Math.toIntExact(Math.min(fromIndex + pageSize, aggregatedCards.size()));
        List<C5InventoryItem> pageItems = aggregatedCards.subList(fromIndex, toIndex).stream()
                .map(AggregatedInventoryCard::representative)
                .collect(Collectors.toList());
        Map<Long, String> accountNameMap = resolveAccountNameMap(userId, pageItems);
        List<C5InventoryItemDTO> records = aggregatedCards.subList(fromIndex, toIndex).stream()
                .map(card -> toDTO(card.representative(), accountNameMap.get(card.representative().getAccountId()), card.quantityValue()))
                .collect(Collectors.toList());

        C5InventoryPageDTO result = new C5InventoryPageDTO();
        result.setRecords(records);
        result.setTotal((long) aggregatedCards.size());
        result.setItemTotal(inventoryItemManager.countInventoryItems(userId, accountId, keyword, status));
        result.setCurrent(page);
        result.setSize(pageSize);
        return result;
    }

    /**
     * 分页拉取 C5 库存。
     *
     * @param account C5 扫货账号
     * @return C5 库存明细列表
     */
    private List<C5InventoryResponse.InventoryItem> fetchAllInventory(C5SnipingAccount account) {
        List<C5InventoryResponse.InventoryItem> items = new ArrayList<>();
        String startAssetId = INITIAL_START_ASSET_ID;
        while (true) {
            C5InventoryResponse response = c5ApiClientService.getClientByAppKey(account.getC5AppKey())
                    .getInventory()
                    .getInventory(account.getSteamId(), APP_ID_CS2, LANGUAGE_ZH, startAssetId, PAGE_COUNT);
            List<C5InventoryResponse.InventoryItem> pageItems = response == null ? List.of() : response.getList();
            if (pageItems == null || pageItems.isEmpty()) {
                break;
            }

            items.addAll(pageItems);
            String lastAssetId = response.getLastAssetId();
            if (StrUtil.isBlank(lastAssetId) || lastAssetId.equals(startAssetId)) {
                break;
            }
            startAssetId = lastAssetId;
        }
        return items;
    }

    /**
     * Upsert 本次返回库存快照。
     *
     * @param userId 用户 ID
     * @param account C5 扫货账号
     * @param c5Items C5 返回库存列表
     * @param syncTime 同步时间
     * @return upsert 统计
     */
    private UpsertStat upsertInventoryItems(Long userId, C5SnipingAccount account,
                                            List<C5InventoryResponse.InventoryItem> c5Items,
                                            LocalDateTime syncTime) {
        Map<String, C5InventoryResponse.InventoryItem> itemMap = c5Items.stream()
                .filter(item -> StrUtil.isNotBlank(item.getAssetId()))
                .collect(Collectors.toMap(C5InventoryResponse.InventoryItem::getAssetId, item -> item, (left, right) -> right, LinkedHashMap::new));
        if (itemMap.isEmpty()) {
            return new UpsertStat(0, 0, Set.of());
        }

        Map<String, C5InventoryItem> existsMap = inventoryItemManager.mapByAccountIdAndAssetIds(account.getId(), itemMap.keySet());
        int addedCount = 0;
        int updatedCount = 0;
        for (Map.Entry<String, C5InventoryResponse.InventoryItem> entry : itemMap.entrySet()) {
            C5InventoryItem inventoryItem = existsMap.get(entry.getKey());
            if (inventoryItem == null) {
                inventoryItem = new C5InventoryItem();
                inventoryItem.setUserId(userId);
                inventoryItem.setAccountId(account.getId());
                inventoryItem.setAssetId(entry.getKey());
                inventoryItem.setCreateTime(syncTime);
                addedCount++;
            } else {
                updatedCount++;
            }

            fillInventoryItem(inventoryItem, account, entry.getValue(), syncTime);
            inventoryItemManager.saveOrUpdate(inventoryItem);
        }
        return new UpsertStat(addedCount, updatedCount, itemMap.keySet());
    }

    /**
     * 将 C5 返回字段填充到库存实体。
     *
     * @param inventoryItem 库存实体
     * @param account C5 扫货账号
     * @param c5Item C5 返回库存项目
     * @param syncTime 同步时间
     */
    private void fillInventoryItem(C5InventoryItem inventoryItem, C5SnipingAccount account,
                                   C5InventoryResponse.InventoryItem c5Item, LocalDateTime syncTime) {
        inventoryItem.setSteamId(StrUtil.blankToDefault(c5Item.getSteamId(), account.getSteamId()));
        inventoryItem.setAppId(c5Item.getAppId() == null ? Integer.valueOf(APP_ID_CS2) : c5Item.getAppId());
        inventoryItem.setInventoryStatus(STATUS_IN_STOCK);
        inventoryItem.setLastSyncTime(syncTime);
        inventoryItem.setToken(c5Item.getToken());
        inventoryItem.setStyleToken(c5Item.getStyleToken());
        inventoryItem.setC5Status(c5Item.getStatus());
        inventoryItem.setTradableTime(c5Item.getTradableTime());
        inventoryItem.setClassId(c5Item.getClassId());
        inventoryItem.setInstanceId(c5Item.getInstanceId());
        inventoryItem.setInspect(c5Item.getInspect());
        inventoryItem.setItemId(c5Item.getItemId());
        inventoryItem.setName(c5Item.getName());
        inventoryItem.setShortName(c5Item.getShortName());
        inventoryItem.setMarketHashName(c5Item.getMarketHashName());
        inventoryItem.setImageUrl(c5Item.getImageUrl());
        inventoryItem.setPrice(c5Item.getPrice());
        inventoryItem.setIfTradable(c5Item.getIfTradable());
        fillAssetInfo(inventoryItem, c5Item.getAssetInfo());
        fillItemInfo(inventoryItem, c5Item.getItemInfo());
        inventoryItem.setAssetInfoJson(c5Item.getAssetInfo());
        inventoryItem.setItemInfoJson(c5Item.getItemInfo());
        inventoryItem.setUpdateTime(syncTime);
    }

    /**
     * 填充 C5 assetInfo 字段。
     *
     * @param inventoryItem 库存实体
     * @param assetInfo C5 assetInfo
     */
    private void fillAssetInfo(C5InventoryItem inventoryItem, C5AssetInfo assetInfo) {
        if (assetInfo == null) {
            return;
        }
        Double wear = assetInfo.getWear() == null ? assetInfo.getFloatWear() : assetInfo.getWear();
        inventoryItem.setWear(wear == null ? null : BigDecimal.valueOf(wear));
        inventoryItem.setPaintIndex(assetInfo.getPaintIndex());
        inventoryItem.setPaintSeed(assetInfo.getPaintSeed());
        inventoryItem.setInspectImageUrl(assetInfo.getInspectImageUrl());
    }

    /**
     * 填充 C5 itemInfo 字段。
     *
     * @param inventoryItem 库存实体
     * @param itemInfo C5 itemInfo
     */
    private void fillItemInfo(C5InventoryItem inventoryItem, C5ItemInfo itemInfo) {
        if (itemInfo == null) {
            return;
        }
        inventoryItem.setRarity(itemInfo.getRarity());
        inventoryItem.setRarityName(itemInfo.getRarityName());
        inventoryItem.setRarityColor(itemInfo.getRarityColor());
        inventoryItem.setExterior(itemInfo.getExterior());
        inventoryItem.setExteriorName(itemInfo.getExteriorName());
        inventoryItem.setExteriorColor(itemInfo.getExteriorColor());
    }

    /**
     * 查询账号名称映射。
     *
     * @param userId 用户 ID
     * @param items 库存实体列表
     * @return 账号 ID 到账号名称的映射
     */
    private Map<Long, String> resolveAccountNameMap(Long userId, List<C5InventoryItem> items) {
        List<Long> accountIds = items.stream()
                .map(C5InventoryItem::getAccountId)
                .distinct()
                .collect(Collectors.toList());
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return accountManager.mapByUserIdAndIds(userId, accountIds).values().stream()
                .collect(Collectors.toMap(C5SnipingAccount::getId, C5SnipingAccount::getAccountName, (left, right) -> left));
    }

    /**
     * 转换库存实体为 DTO。
     *
     * @param item 库存实体
     * @param accountName 账号名称
     * @return C5 库存 DTO
     */
    private C5InventoryItemDTO toDTO(C5InventoryItem item, String accountName, int quantity) {
        C5InventoryItemDTO dto = BeanUtil.copyProperties(item, C5InventoryItemDTO.class);
        dto.setAccountName(accountName);
        dto.setQuantity(quantity);
        return dto;
    }

    /**
     * 构建库存卡片聚合键。
     *
     * @param selectedAccountId 页面选中的账号 ID
     * @param item 库存实体
     * @return 聚合键
     */
    private String buildAggregationKey(Long selectedAccountId, C5InventoryItem item) {
        Long accountId = selectedAccountId == null ? item.getAccountId() : selectedAccountId;
        return accountId + "|"
                + StrUtil.blankToDefault(item.getMarketHashName(), item.getName()) + "|"
                + StrUtil.blankToDefault(item.getExteriorName(), resolveWearLevel(item.getWear())) + "|"
                + item.getIfTradable();
    }

    /**
     * 按磨损值解析外观分组。
     *
     * @param wear 磨损值
     * @return 外观分组
     */
    private String resolveWearLevel(BigDecimal wear) {
        if (wear == null) {
            return "";
        }
        double value = wear.doubleValue();
        if (value <= 0.07) {
            return "崭新出厂";
        }
        if (value <= 0.15) {
            return "略有磨损";
        }
        if (value <= 0.38) {
            return "久经沙场";
        }
        if (value <= 0.45) {
            return "破损不堪";
        }
        return "战痕累累";
    }

    /**
     * 规范化页码。
     *
     * @param page 页码
     * @return 合法页码
     */
    private long normalizePage(Long page) {
        return page == null || page < 1 ? 1L : page;
    }

    /**
     * 规范化每页数量。
     *
     * @param pageSize 每页数量
     * @return 合法每页数量
     */
    private long normalizePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20L;
        }
        return Math.min(pageSize, 100L);
    }

    /**
     * 规范化状态筛选。
     *
     * @param status 状态筛选
     * @return 合法状态
     */
    private String normalizeStatus(String status) {
        return StrUtil.blankToDefault(status, "all");
    }

    /**
     * 聚合库存卡片。
     *
     * @param representative 代表库存记录
     * @param quantity 聚合数量
     */
    private static class AggregatedInventoryCard {

        private final C5InventoryItem representative;
        private int quantity;

        private AggregatedInventoryCard(C5InventoryItem representative) {
            this.representative = representative;
        }

        private C5InventoryItem representative() {
            return representative;
        }

        private void increase() {
            quantity++;
        }

        private int quantityValue() {
            return quantity;
        }
    }

    /**
     * Upsert 统计。
     *
     * @param addedCount 新增数量
     * @param updatedCount 更新数量
     * @param returnedAssetIds 本次返回资产 ID
     */
    private record UpsertStat(int addedCount, int updatedCount, Set<String> returnedAssetIds) {
    }
}
