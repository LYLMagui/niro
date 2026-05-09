package com.niro.sdk.c5.inventory;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Method;
import com.fasterxml.jackson.core.type.TypeReference;
import com.niro.sdk.c5.client.core.C5HttpExecutor;
import com.niro.sdk.c5.constant.C5GameAPI;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * C5 库存相关接口
 */
@RequiredArgsConstructor
public class C5InventoryClient {


    private final C5HttpExecutor httpExecutor;

    /**
     * 库存列表
     */
    public C5InventoryResponse getInventory(String steamId, String appId, String language,
                                            String startAssetId, Integer count) {
        return httpExecutor.execute(buildInventoryUrl(steamId, appId), Method.GET,
                buildInventoryParams(language, startAssetId, count),
                new TypeReference<>() {
                });
    }

    /**
     * 异步查询库存列表
     */
    public CompletableFuture<C5InventoryResponse> getInventoryAsync(String steamId, String appId, String language,
                                                                    String startAssetId, Integer count) {
        return httpExecutor.executeAsync(buildInventoryUrl(steamId, appId), Method.GET,
                buildInventoryParams(language, startAssetId, count),
                new TypeReference<>() {
                });
    }

    /**
     * 库存饰品上架
     */
    public C5InventoryListingCreateResponse createListing(C5InventoryListingCreateRequest req) {
        return httpExecutor.execute(C5GameAPI.Inventory.LISTING_CREATE, Method.POST, req,
                new TypeReference<>() {
                });
    }

    /**
     * 异步库存饰品上架
     */
    public CompletableFuture<C5InventoryListingCreateResponse> createListingAsync(C5InventoryListingCreateRequest req) {
        return httpExecutor.executeAsync(C5GameAPI.Inventory.LISTING_CREATE, Method.POST, req,
                new TypeReference<>() {
                });
    }

    /**
     * 计算上架手续费
     */
    public List<Map<String, Object>> calculateListingFee(C5ListingFeeCalculateRequest req) {
        return httpExecutor.execute(C5GameAPI.Inventory.LISTING_FEE, Method.POST, req,
                new TypeReference<>() {
                });
    }

    /**
     * 异步计算上架手续费
     */
    public CompletableFuture<List<Map<String, Object>>> calculateListingFeeAsync(C5ListingFeeCalculateRequest req) {
        return httpExecutor.executeAsync(C5GameAPI.Inventory.LISTING_FEE, Method.POST, req,
                new TypeReference<>() {
                });
    }

    private String buildInventoryUrl(String steamId, String appId) {
        return String.format(C5GameAPI.Inventory.INVENTORY_LIST, steamId, appId);
    }

    private Map<String, Object> buildInventoryParams(String language, String startAssetId, Integer count) {
        Map<String, Object> params = new HashMap<>();
        if (StrUtil.isNotBlank(language)) {
            params.put("language", language);
        }
        if (StrUtil.isNotBlank(startAssetId)) {
            params.put("startAssetId", startAssetId);
        }
        if (count != null) {
            params.put("count", count);
        }
        return params;
    }
}
