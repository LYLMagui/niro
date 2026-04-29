package com.niro.web.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.niro.core.util.Assert;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.niro.sdk.c5.model.C5AssetInfo;
import com.niro.sdk.c5.model.C5ItemInfo;
import com.niro.sdk.c5.request.inventory.C5InventoryListingCreateRequest;
import com.niro.sdk.c5.request.inventory.C5ListingFeeCalculateRequest;
import com.niro.sdk.c5.request.market.C5ProductListRequest;
import com.niro.sdk.c5.request.market.C5ProductSearchRequest;
import com.niro.sdk.c5.response.C5InventoryResponse;
import com.niro.sdk.c5.response.inventory.C5InventoryListingCreateResponse;
import com.niro.sdk.c5.response.market.C5ProductListResponse;
import com.niro.web.dto.C5InventoryAggregateQueryDTO;
import com.niro.web.dto.C5InventoryAssetDTO;
import com.niro.web.dto.C5InventoryAssetPageDTO;
import com.niro.web.dto.C5InventoryItemDTO;
import com.niro.web.dto.C5InventoryListingFeeDTO;
import com.niro.web.dto.C5InventoryListingResultDTO;
import com.niro.web.dto.C5InventoryListingSuccessDTO;
import com.niro.web.dto.C5InventoryMarketReferenceDTO;
import com.niro.web.dto.C5InventoryMarketReferencePageDTO;
import com.niro.web.dto.C5InventoryPageDTO;
import com.niro.web.dto.C5InventoryRefreshResultDTO;
import com.niro.web.dto.C5InventoryStatsDTO;
import com.niro.web.dto.param.C5InventoryItemListParam;
import com.niro.web.dto.param.C5InventoryListingCreateItemParam;
import com.niro.web.dto.param.C5InventoryListingCreateParam;
import com.niro.web.dto.param.C5InventoryListingFeeBatchCalculateParam;
import com.niro.web.dto.param.C5InventoryListingFeeCalculateParam;
import com.niro.web.dto.param.C5InventoryMarketReferenceParam;
import com.niro.web.dto.param.C5InventoryQueryParam;
import com.niro.web.dto.param.C5InventoryRefreshParam;
import com.niro.web.entity.C5InventoryItem;
import com.niro.web.entity.C5SnipingAccount;
import com.niro.web.manager.C5InventoryItemMapperManager;
import com.niro.web.manager.C5SnipingAccountMapperManager;
import com.niro.web.service.C5ApiClientService;
import com.niro.web.service.C5InventoryService;
import com.niro.web.service.C5SnipingAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
    private static final int MAX_LISTING_COUNT = 100;
    private static final BigDecimal WEAR_REFERENCE_RANGE = new BigDecimal("0.01");
    private static final BigDecimal MIN_WEAR = BigDecimal.ZERO;
    private static final BigDecimal MAX_WEAR = BigDecimal.ONE;
    private static final String STATUS_IN_STOCK = "IN_STOCK";
    private static final String STATUS_REMOVED = "REMOVED";
    private static final String STATUS_LISTING = "LISTING";

    private final C5SnipingAccountMapperManager accountManager;
    private final C5InventoryItemMapperManager inventoryItemManager;
    private final C5ApiClientService c5ApiClientService;
    private final C5SnipingAccountService c5SnipingAccountService;

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
        Assert.notBlank(account.getC5AppKeyEncrypted(), "账号 C5 AppKey 不能为空");
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

        C5InventoryAggregateQueryDTO query = buildAggregateQuery(userId, accountId, keyword, status, page, pageSize);
        long total = inventoryItemManager.countAggregated(query);
        List<C5InventoryItem> pageItems = inventoryItemManager.listAggregatedPage(query);
        Map<Long, String> accountNameMap = resolveAccountNameMap(userId, pageItems);
        List<C5InventoryItemDTO> records = pageItems.stream()
                .map(item -> toDTO(item, accountNameMap.get(item.getAccountId()), resolveQuantity(item)))
                .collect(Collectors.toList());

        C5InventoryPageDTO result = new C5InventoryPageDTO();
        result.setRecords(records);
        result.setTotal(total);
        result.setItemTotal(total);
        result.setCurrent(page);
        result.setSize(pageSize);
        return result;
    }

    /**
     * 统计当前用户 C5 库存状态数量。
     *
     * @param param 查询参数
     * @return 状态数量统计
     */
    @Override
    public C5InventoryStatsDTO statsInventory(C5InventoryQueryParam param) {
        Long userId = StpUtil.getLoginIdAsLong();
        Long accountId = param == null ? null : param.getAccountId();
        String keyword = param == null ? null : param.getKeyword();

        C5InventoryStatsDTO result = new C5InventoryStatsDTO();
        result.setAll(inventoryItemManager.countInventoryItems(userId, accountId, keyword, "all"));
        result.setTradable(inventoryItemManager.countInventoryItems(userId, accountId, keyword, "tradable"));
        result.setCooldown(inventoryItemManager.countInventoryItems(userId, accountId, keyword, "cooldown"));
        result.setSelling(inventoryItemManager.countInventoryItems(userId, accountId, keyword, "selling"));
        result.setTotalValue(inventoryItemManager.sumActiveInventoryValue(buildAggregateQuery(userId, accountId, keyword, "all", 1L, 1L)));
        return result;
    }

    /**
     * 分页查询聚合卡片背后的真实库存明细。
     *
     * @param param 查询参数
     * @return 库存明细分页
     */
    @Override
    public C5InventoryAssetPageDTO pageInventoryItems(C5InventoryItemListParam param) {
        Assert.notNull(param, "库存明细查询参数不能为空");
        Assert.notNull(param.getAccountId(), "账号ID不能为空");
        Assert.isTrue(StrUtil.isNotBlank(param.getMarketHashName()) || StrUtil.isNotBlank(param.getName()), "商品名称不能为空");

        Long userId = StpUtil.getLoginIdAsLong();
        C5SnipingAccount account = accountManager.getByUserIdAndId(userId, param.getAccountId());
        Assert.notNull(account, "账号不存在");

        long page = normalizePage(param.getPage());
        long pageSize = normalizeDetailPageSize(param.getPageSize());
        Page<C5InventoryItem> itemPage = inventoryItemManager.pageGroupItems(
                userId,
                param.getAccountId(),
                param.getMarketHashName(),
                param.getName(),
                param.getExteriorName(),
                param.getIfTradable(),
                page,
                pageSize
        );

        List<C5InventoryAssetDTO> records = itemPage.getRecords().stream()
                .map(item -> toAssetDTO(item, account.getAccountName()))
                .collect(Collectors.toList());

        C5InventoryAssetPageDTO result = new C5InventoryAssetPageDTO();
        result.setRecords(records);
        result.setTotal(itemPage.getTotal());
        result.setCurrent(itemPage.getCurrent());
        result.setSize(itemPage.getSize());
        return result;
    }

    /**
     * 提交库存饰品上架。
     *
     * @param param 上架参数
     * @return 上架结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public C5InventoryListingResultDTO createInventoryListings(C5InventoryListingCreateParam param) {
        Assert.notNull(param, "库存上架参数不能为空");
        Assert.notNull(param.getAccountId(), "账号ID不能为空");
        Assert.notEmpty(param.getItems(), "上架库存明细不能为空");
        Assert.isTrue(param.getItems().size() <= MAX_LISTING_COUNT, "单次最多上架100件饰品");
        Assert.isTrue(param.getAcceptBargain() == 0 || param.getAcceptBargain() == 1, "是否允许还价参数不合法");

        Long userId = StpUtil.getLoginIdAsLong();
        C5SnipingAccount account = accountManager.getByUserIdAndId(userId, param.getAccountId());
        Assert.notNull(account, "账号不存在");
        Assert.notBlank(account.getC5AppKeyEncrypted(), "账号 C5 AppKey 不能为空");

        List<Long> inventoryItemIds = param.getItems().stream()
                .map(C5InventoryListingCreateItemParam::getInventoryItemId)
                .distinct()
                .collect(Collectors.toList());
        Assert.isTrue(inventoryItemIds.size() == param.getItems().size(), "上架库存明细不能重复");

        Map<Long, C5InventoryListingCreateItemParam> priceMap = param.getItems().stream()
                .collect(Collectors.toMap(C5InventoryListingCreateItemParam::getInventoryItemId, Function.identity()));
        List<C5InventoryItem> inventoryItems = inventoryItemManager.listByUserIdAccountIdAndIds(userId, account.getId(), inventoryItemIds);
        Assert.isTrue(inventoryItems.size() == inventoryItemIds.size(), "存在不可上架的库存明细");

        List<C5InventoryListingCreateRequest.ListingItem> c5Items = inventoryItems.stream()
                .map(item -> buildListingItem(item, priceMap.get(item.getId()), param))
                .collect(Collectors.toList());
        C5InventoryListingCreateRequest request = new C5InventoryListingCreateRequest().setDataList(c5Items);
        C5InventoryListingCreateResponse response = c5ApiClientService.getClientByAppKey(c5SnipingAccountService.decryptAccountAppKey(account))
                .getInventory()
                .createListing(request);

        C5InventoryListingResultDTO result = toListingResult(account.getId(), response);
        List<String> successAssetIds = result.getSuccessList().stream()
                .map(C5InventoryListingSuccessDTO::getAssetId)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
        inventoryItemManager.markListingByAssetIds(account.getId(), successAssetIds, LocalDateTime.now());
        return result;
    }

    /**
     * 查询 C5 同平台在售参考。
     *
     * @param param 查询参数
     * @return 在售参考分页
     */
    @Override
    public C5InventoryMarketReferencePageDTO listMarketReferences(C5InventoryMarketReferenceParam param) {
        Assert.notNull(param, "参考价查询参数不能为空");
        Assert.notNull(param.getAccountId(), "账号ID不能为空");
        Assert.notBlank(param.getMarketHashName(), "marketHashName不能为空");

        Long userId = StpUtil.getLoginIdAsLong();
        C5SnipingAccount account = accountManager.getByUserIdAndId(userId, param.getAccountId());
        Assert.notNull(account, "账号不存在");
        Assert.notBlank(account.getC5AppKeyEncrypted(), "账号 C5 AppKey 不能为空");

        BigDecimal wearMin = resolveWearMin(param);
        BigDecimal wearMax = resolveWearMax(param);
        validateWearRange(wearMin, wearMax);

        Integer pageNum = normalizeReferencePage(param.getPageNum());
        Integer pageSize = normalizeReferencePageSize(param.getPageSize());
        C5ProductListResponse response;
        if (wearMin == null && wearMax == null) {
            C5ProductListRequest request = new C5ProductListRequest()
                    .setAppId(Integer.valueOf(APP_ID_CS2))
                    .setMarketHashName(StrUtil.trim(param.getMarketHashName()))
                    .setPageNum(pageNum)
                    .setPageSize(pageSize);
            response = c5ApiClientService.getClientByAppKey(c5SnipingAccountService.decryptAccountAppKey(account))
                    .getMarket()
                    .searchProductList(request);
        } else {
            C5ProductSearchRequest request = new C5ProductSearchRequest()
                    .setAppId(Integer.valueOf(APP_ID_CS2))
                    .setMarketHashName(StrUtil.trim(param.getMarketHashName()))
                    .setWearMin(wearMin == null ? null : wearMin.doubleValue())
                    .setWearMax(wearMax == null ? null : wearMax.doubleValue())
                    .setPageNum(pageNum)
                    .setPageSize(pageSize);
            response = c5ApiClientService.getClientByAppKey(c5SnipingAccountService.decryptAccountAppKey(account))
                    .getMarket()
                    .productSearch(request);
        }

        List<C5ProductListResponse.ProductDTO> products = response == null || response.getList() == null
                ? List.of()
                : response.getList();
        C5InventoryMarketReferencePageDTO dto = new C5InventoryMarketReferencePageDTO();
        dto.setRecords(products.stream()
                .filter(product -> isWithinWearRange(product, wearMin, wearMax))
                .map(product -> toMarketReferenceDTO(product, param.getMarketHashName()))
                .sorted(Comparator.comparing(C5InventoryMarketReferenceDTO::getPrice, Comparator.nullsLast(BigDecimal::compareTo))
                        .thenComparing(C5InventoryMarketReferenceDTO::getWear, Comparator.nullsLast(BigDecimal::compareTo)))
                .collect(Collectors.toList()));
        dto.setPageNum(response != null && response.getPageNum() != null ? response.getPageNum() : pageNum);
        dto.setPageSize(response != null && response.getPageSize() != null ? response.getPageSize() : pageSize);
        dto.setHasMore(response != null && Boolean.TRUE.equals(response.getHasMore()));
        dto.setWearMin(wearMin);
        dto.setWearMax(wearMax);
        return dto;
    }

    /**
     * 计算 C5 上架手续费。
     *
     * @param param 计算参数
     * @return 手续费结果
     */
    @Override
    public C5InventoryListingFeeDTO calculateListingFee(C5InventoryListingFeeCalculateParam param) {
        Assert.notNull(param, "手续费计算参数不能为空");
        Assert.notNull(param.getAccountId(), "账号ID不能为空");
        Assert.notNull(param.getInventoryItemId(), "库存明细ID不能为空");
        Assert.notNull(param.getPrice(), "上架价格不能为空");
        Assert.isTrue(param.getPrice().compareTo(BigDecimal.ZERO) > 0, "上架价格必须大于0");

        C5InventoryListingCreateItemParam itemParam = new C5InventoryListingCreateItemParam();
        itemParam.setInventoryItemId(param.getInventoryItemId());
        itemParam.setPrice(param.getPrice());

        C5InventoryListingFeeBatchCalculateParam batchParam = new C5InventoryListingFeeBatchCalculateParam();
        batchParam.setAccountId(param.getAccountId());
        batchParam.setItems(List.of(itemParam));
        return calculateListingFees(batchParam).getFirst();
    }

    /**
     * 批量计算 C5 上架手续费。
     *
     * @param param 计算参数
     * @return 手续费结果
     */
    @Override
    public List<C5InventoryListingFeeDTO> calculateListingFees(C5InventoryListingFeeBatchCalculateParam param) {
        Assert.notNull(param, "手续费计算参数不能为空");
        Assert.notNull(param.getAccountId(), "账号ID不能为空");
        Assert.notEmpty(param.getItems(), "手续费计算明细不能为空");
        Assert.isTrue(param.getItems().size() <= MAX_LISTING_COUNT, "单次最多计算100件饰品手续费");

        Long userId = StpUtil.getLoginIdAsLong();
        C5SnipingAccount account = accountManager.getByUserIdAndId(userId, param.getAccountId());
        Assert.notNull(account, "账号不存在");
        Assert.notBlank(account.getC5AppKeyEncrypted(), "账号 C5 AppKey 不能为空");

        List<C5InventoryListingCreateItemParam> feeItems = param.getItems();
        feeItems.forEach(item -> {
            Assert.notNull(item, "手续费计算明细不能为空");
            Assert.notNull(item.getInventoryItemId(), "库存明细ID不能为空");
            Assert.notNull(item.getPrice(), "上架价格不能为空");
            Assert.isTrue(item.getPrice().compareTo(BigDecimal.ZERO) > 0, "上架价格必须大于0");
        });
        List<Long> inventoryItemIds = feeItems.stream()
                .map(C5InventoryListingCreateItemParam::getInventoryItemId)
                .distinct()
                .collect(Collectors.toList());
        Assert.isTrue(inventoryItemIds.size() == feeItems.size(), "手续费计算明细不能重复");

        Map<Long, C5InventoryListingCreateItemParam> priceMap = feeItems.stream()
                .collect(Collectors.toMap(C5InventoryListingCreateItemParam::getInventoryItemId, Function.identity()));
        List<C5InventoryItem> inventoryItems = inventoryItemManager.listByUserIdAccountIdAndIds(userId, account.getId(), inventoryItemIds);
        Assert.isTrue(inventoryItems.size() == inventoryItemIds.size(), "存在不可计算手续费的库存明细");

        Map<Long, C5InventoryItem> inventoryItemMap = inventoryItems.stream()
                .collect(Collectors.toMap(C5InventoryItem::getId, Function.identity()));
        List<C5InventoryItem> orderedInventoryItems = inventoryItemIds.stream()
                .map(inventoryItemMap::get)
                .collect(Collectors.toList());
        C5ListingFeeCalculateRequest request = new C5ListingFeeCalculateRequest()
                .setDataList(orderedInventoryItems.stream()
                        .map(item -> buildListingFeeCalculateItem(item, priceMap.get(item.getId())))
                        .collect(Collectors.toList()));
        List<Map<String, Object>> rawDataList = c5ApiClientService.getClientByAppKey(c5SnipingAccountService.decryptAccountAppKey(account))
                .getInventory()
                .calculateListingFee(request);

        List<C5InventoryListingFeeDTO> result = new ArrayList<>();
        for (int i = 0; i < orderedInventoryItems.size(); i++) {
            C5InventoryItem inventoryItem = orderedInventoryItems.get(i);
            Map<String, Object> rawData = rawDataList == null || i >= rawDataList.size() || rawDataList.get(i) == null ? Map.of() : rawDataList.get(i);
            result.add(toListingFeeDTO(account.getId(), inventoryItem, priceMap.get(inventoryItem.getId()).getPrice(), rawData));
        }
        return result;
    }

    private C5ListingFeeCalculateRequest.CalculateItem buildListingFeeCalculateItem(C5InventoryItem inventoryItem, C5InventoryListingCreateItemParam param) {
        Assert.isTrue(STATUS_IN_STOCK.equals(inventoryItem.getInventoryStatus()), "库存明细不是在库状态");
        Assert.notBlank(inventoryItem.getToken(), "库存明细缺少 C5 token");
        Assert.notBlank(inventoryItem.getStyleToken(), "库存明细缺少 C5 styleToken");
        return new C5ListingFeeCalculateRequest.CalculateItem()
                .setPrice(param.getPrice().setScale(2, RoundingMode.HALF_UP))
                .setToken(inventoryItem.getToken())
                .setStyleToken(inventoryItem.getStyleToken());
    }

    private C5InventoryListingFeeDTO toListingFeeDTO(Long accountId, C5InventoryItem inventoryItem, BigDecimal price, Map<String, Object> rawData) {
        BigDecimal scaledPrice = price.setScale(2, RoundingMode.HALF_UP);
        C5InventoryListingFeeDTO dto = new C5InventoryListingFeeDTO();
        dto.setAccountId(accountId);
        dto.setInventoryItemId(inventoryItem.getId());
        dto.setAssetId(readJsonString(inventoryItem.getAssetInfoJson(), "assetId"));
        dto.setPrice(scaledPrice);
        dto.setRawData(rawData);
        dto.setItemId(readString(rawData, "itemId"));
        dto.setFee(readDecimal(rawData, "fee", "sellerFee", "serviceFee", "commission"));
        dto.setFreeFeePrice(readDecimal(rawData, "freeFeePrice"));
        dto.setSellerPrice(readDecimal(rawData, "sellerPrice", "sellerAmount", "netPrice", "settlePrice"));
        if (dto.getSellerPrice() == null && dto.getFee() != null) {
            dto.setSellerPrice(scaledPrice.subtract(dto.getFee()));
        }
        dto.setIncome(readDecimal(rawData, "income", "actualIncome"));
        dto.setActualAmount(readDecimal(rawData, "actualAmount", "amount"));
        return dto;
    }

    /**
     * 构建库存聚合查询参数。
     *
     * @param userId 用户 ID
     * @param accountId 账号 ID
     * @param keyword 商品关键字
     * @param status 状态筛选
     * @param page 当前页
     * @param pageSize 每页数量
     * @return 聚合查询参数
     */
    private C5InventoryAggregateQueryDTO buildAggregateQuery(Long userId, Long accountId, String keyword, String status, Long page, Long pageSize) {
        C5InventoryAggregateQueryDTO query = new C5InventoryAggregateQueryDTO();
        query.setUserId(userId);
        query.setAccountId(accountId);
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setOffset((page - 1) * pageSize);
        query.setPageSize(pageSize);
        return query;
    }

    /**
     * 解析聚合数量。
     *
     * @param item 库存实体
     * @return 聚合数量
     */
    private int resolveQuantity(C5InventoryItem item) {
        return item.getQuantity() == null || item.getQuantity() < 1 ? 1 : item.getQuantity();
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
            C5InventoryResponse response = c5ApiClientService.getClientByAppKey(c5SnipingAccountService.decryptAccountAppKey(account))
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
        dto.setItemType(readJsonString(item.getItemInfoJson(), "type"));
        dto.setItemTypeName(readJsonString(item.getItemInfoJson(), "typeName"));
        return dto;
    }

    /**
     * 转换真实库存资产明细 DTO。
     *
     * @param item 库存实体
     * @param accountName 账号名称
     * @return 真实库存资产明细 DTO
     */
    private C5InventoryAssetDTO toAssetDTO(C5InventoryItem item, String accountName) {
        C5InventoryAssetDTO dto = BeanUtil.copyProperties(item, C5InventoryAssetDTO.class);
        dto.setAccountName(accountName);
        return dto;
    }

    /**
     * 转换 C5 同平台参考 DTO。
     *
     * @param product C5 在售商品
     * @param marketHashName Steam 市场 Hash 名称
     * @return 同平台参考 DTO
     */
    private C5InventoryMarketReferenceDTO toMarketReferenceDTO(C5ProductListResponse.ProductDTO product, String marketHashName) {
        C5InventoryMarketReferenceDTO dto = new C5InventoryMarketReferenceDTO();
        dto.setProductId(product.getProductId());
        dto.setPrice(product.getPrice());
        dto.setDelivery(product.getDelivery());
        dto.setAcceptBargain(product.getAcceptBargain());
        dto.setImageUrl(product.getImg());
        dto.setSellerUid(product.getSellerUid());
        dto.setMarketHashName(marketHashName);
        if (product.getAssetInfo() != null) {
            dto.setAssetId(product.getAssetInfo().getAssetId());
            dto.setWear(getProductWear(product));
        }
        return dto;
    }

    /**
     * 判断挂单磨损是否落在查询区间。
     *
     * @param product C5 在售商品
     * @param wearMin 最小磨损
     * @param wearMax 最大磨损
     * @return 是否匹配
     */
    private boolean isWithinWearRange(C5ProductListResponse.ProductDTO product, BigDecimal wearMin, BigDecimal wearMax) {
        BigDecimal wear = getProductWear(product);
        if (wear == null) {
            return wearMin == null && wearMax == null;
        }
        if (wearMin != null && wear.compareTo(wearMin) < 0) {
            return false;
        }
        return wearMax == null || wear.compareTo(wearMax) <= 0;
    }

    /**
     * 读取挂单磨损。
     *
     * @param product C5 在售商品
     * @return 挂单磨损
     */
    private BigDecimal getProductWear(C5ProductListResponse.ProductDTO product) {
        if (product == null || product.getAssetInfo() == null) {
            return null;
        }
        Double wear = product.getAssetInfo().getWear() != null
                ? product.getAssetInfo().getWear()
                : product.getAssetInfo().getFloatWear();
        return wear == null ? null : BigDecimal.valueOf(wear);
    }

    /**
     * 构建 C5 上架明细。
     *
     * @param item 库存实体
     * @param priceParam 价格参数
     * @param param 上架参数
     * @return C5 上架明细
     */
    private C5InventoryListingCreateRequest.ListingItem buildListingItem(C5InventoryItem item,
                                                                         C5InventoryListingCreateItemParam priceParam,
                                                                         C5InventoryListingCreateParam param) {
        Assert.isTrue(STATUS_IN_STOCK.equals(item.getInventoryStatus()), "库存明细不是在库状态");
        Assert.notBlank(item.getToken(), "库存明细缺少 C5 token");
        Assert.notBlank(item.getStyleToken(), "库存明细缺少 C5 styleToken");
        Assert.notNull(priceParam, "上架价格参数不能为空");
        Assert.notNull(priceParam.getPrice(), "上架价格不能为空");
        Assert.isTrue(priceParam.getPrice().compareTo(BigDecimal.ZERO) > 0, "上架价格必须大于0");

        return new C5InventoryListingCreateRequest.ListingItem()
                .setPrice(priceParam.getPrice().setScale(2, RoundingMode.HALF_UP))
                .setDescription(StrUtil.blankToDefault(param.getDescription(), ""))
                .setAcceptBargain(param.getAcceptBargain())
                .setToken(item.getToken())
                .setStyleToken(item.getStyleToken());
    }

    /**
     * 转换 C5 上架响应。
     *
     * @param accountId 账号 ID
     * @param response C5 上架响应
     * @return 本地上架结果
     */
    private C5InventoryListingResultDTO toListingResult(Long accountId, C5InventoryListingCreateResponse response) {
        C5InventoryListingCreateResponse safeResponse = response == null ? new C5InventoryListingCreateResponse() : response;
        C5InventoryListingResultDTO result = new C5InventoryListingResultDTO();
        result.setAccountId(accountId);
        result.setShopOn(safeResponse.getShopOn());
        result.setSucceed(safeResponse.getSucceed() == null ? 0 : safeResponse.getSucceed());
        result.setFailed(safeResponse.getFailed() == null ? 0 : safeResponse.getFailed());
        result.setFailedList(safeResponse.getFailedList() == null ? List.of() : safeResponse.getFailedList());
        result.setHighPriceItemIdList(safeResponse.getHighPriceItemIdList() == null ? List.of() : safeResponse.getHighPriceItemIdList());
        result.setPriceCheckResult(safeResponse.getPriceCheckResult());
        result.setSuccessList((safeResponse.getSuccessList() == null ? List.<C5InventoryListingCreateResponse.SuccessItem>of() : safeResponse.getSuccessList()).stream()
                .map(item -> {
                    C5InventoryListingSuccessDTO success = new C5InventoryListingSuccessDTO();
                    success.setAssetId(item.getAssetId());
                    success.setProductId(item.getProductId());
                    return success;
                })
                .collect(Collectors.toList()));
        return result;
    }

    /**
     * 从 JSON 对象中读取字符串字段。
     *
     * @param json JSON 对象
     * @param key 字段名
     * @return 字段值
     */
    private String readJsonString(Object json, String key) {
        if (!(json instanceof Map<?, ?> map)) {
            return null;
        }
        return MapUtil.getStr(map, key);
    }

    /**
     * 解析参考价查询最小磨损。
     *
     * @param param 查询参数
     * @return 最小磨损
     */
    private BigDecimal resolveWearMin(C5InventoryMarketReferenceParam param) {
        if (param.getWearMin() != null) {
            return clampWear(param.getWearMin());
        }
        if (param.getWear() == null) {
            return null;
        }
        return clampWear(param.getWear().subtract(WEAR_REFERENCE_RANGE));
    }

    /**
     * 解析参考价查询最大磨损。
     *
     * @param param 查询参数
     * @return 最大磨损
     */
    private BigDecimal resolveWearMax(C5InventoryMarketReferenceParam param) {
        if (param.getWearMax() != null) {
            return clampWear(param.getWearMax());
        }
        if (param.getWear() == null) {
            return null;
        }
        return clampWear(param.getWear().add(WEAR_REFERENCE_RANGE));
    }

    /**
     * 限制磨损合法范围。
     *
     * @param wear 磨损
     * @return 合法磨损
     */
    private BigDecimal clampWear(BigDecimal wear) {
        if (wear.compareTo(MIN_WEAR) < 0) {
            return MIN_WEAR;
        }
        if (wear.compareTo(MAX_WEAR) > 0) {
            return MAX_WEAR;
        }
        return wear;
    }

    /**
     * 校验磨损区间。
     *
     * @param wearMin 最小磨损
     * @param wearMax 最大磨损
     */
    private void validateWearRange(BigDecimal wearMin, BigDecimal wearMax) {
        if (wearMin != null && wearMax != null) {
            Assert.isTrue(wearMin.compareTo(wearMax) <= 0, "最小磨损不能大于最大磨损");
        }
    }

    /**
     * 读取 C5 原始返回金额字段。
     *
     * @param data 原始返回
     * @param keys 字段名
     * @return 金额
     */
    private BigDecimal readDecimal(Map<String, Object> data, String... keys) {
        if (data == null) {
            return null;
        }
        for (String key : keys) {
            BigDecimal value = toDecimal(data.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 读取 C5 原始返回文本字段。
     *
     * @param data 原始返回
     * @param key 字段名
     * @return 文本值
     */
    private String readString(Map<String, Object> data, String key) {
        if (data == null) {
            return null;
        }
        Object value = data.get(key);
        return value == null ? null : value.toString();
    }

    /**
     * 转换金额字段。
     *
     * @param value 原始值
     * @return 金额
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
        String text = value.toString();
        if (StrUtil.isBlank(text)) {
            return null;
        }
        return new BigDecimal(text);
    }

    /**
     * 规范化参考页码。
     *
     * @param pageNum 页码
     * @return 合法页码
     */
    private Integer normalizeReferencePage(Integer pageNum) {
        if (pageNum == null || pageNum < 1) {
            return 1;
        }
        return pageNum;
    }

    /**
     * 规范化参考每页数量。
     *
     * @param pageSize 每页数量
     * @return 合法每页数量
     */
    private Integer normalizeReferencePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 20);
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
     * 规范化明细每页数量。
     *
     * @param pageSize 每页数量
     * @return 合法每页数量
     */
    private long normalizeDetailPageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 200L;
        }
        return Math.min(pageSize, 500L);
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
     * Upsert 统计。
     *
     * @param addedCount 新增数量
     * @param updatedCount 更新数量
     * @param returnedAssetIds 本次返回资产 ID
     */
    private record UpsertStat(int addedCount, int updatedCount, Set<String> returnedAssetIds) {
    }
}
