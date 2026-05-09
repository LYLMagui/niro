package com.niro.sdk.c5.market;

import cn.hutool.http.Method;
import com.fasterxml.jackson.core.type.TypeReference;
import com.niro.sdk.c5.client.core.C5HttpExecutor;
import com.niro.sdk.c5.constant.C5GameAPI;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * C5 市场 / 商品相关接口
 */
@RequiredArgsConstructor
public class C5MarketClient {


    private final C5HttpExecutor httpExecutor;

    /**
     * marketHashName 批量查询在售最低价和数量
     */
    public Map<String, C5BatchPriceInfo> batchGetPrice(C5BatchPriceRequest req) {
        return httpExecutor.execute(C5GameAPI.Market.BATCH_PRICE, Method.POST, req,
                new TypeReference<>() {
                });
    }

    /**
     * 异步按 marketHashName 批量查询在售最低价和数量
     */
    public CompletableFuture<Map<String, C5BatchPriceInfo>> batchGetPriceAsync(C5BatchPriceRequest req) {
        return httpExecutor.executeAsync(C5GameAPI.Market.BATCH_PRICE, Method.POST, req,
                new TypeReference<>() {
                });
    }

    /**
     * 查看在售列表
     */
    public C5SaleSearchResponse searchSale(C5SaleSearchRequest req) {
        return httpExecutor.execute(C5GameAPI.Market.SALE_SEARCH, Method.GET, req,
                new TypeReference<>() {
                });
    }

    /**
     * 异步查看在售列表
     */
    public CompletableFuture<C5SaleSearchResponse> searchSaleAsync(C5SaleSearchRequest req) {
        return httpExecutor.executeAsync(C5GameAPI.Market.SALE_SEARCH, Method.GET, req,
                new TypeReference<>() {
                });
    }

    /**
     * 查询在售列表 (V2)
     */
    public C5ProductListResponse searchProductList(C5ProductListRequest req) {
        return httpExecutor.execute(C5GameAPI.Market.PRODUCT_LIST, Method.POST, req,
                new TypeReference<>() {
                });
    }

    /**
     * 异步查询在售列表 (V2)
     */
    public CompletableFuture<C5ProductListResponse> searchProductListAsync(C5ProductListRequest req) {
        return httpExecutor.executeAsync(C5GameAPI.Market.PRODUCT_LIST, Method.POST, req,
                new TypeReference<>() {
                });
    }

    /**
     * 高级搜索在售列表 (V2)
     */
    public C5ProductListResponse productSearch(C5ProductSearchRequest req) {
        return httpExecutor.execute(C5GameAPI.Market.PRODUCT_SEARCH, Method.POST, req,
                new TypeReference<>() {
                });
    }

    /**
     * 异步高级搜索在售列表 (V2)
     */
    public CompletableFuture<C5ProductListResponse> productSearchAsync(C5ProductSearchRequest req) {
        return httpExecutor.executeAsync(C5GameAPI.Market.PRODUCT_SEARCH, Method.POST, req,
                new TypeReference<>() {
                });
    }

    /**
     * 根据 marketHashName 查询统计信息
     */
    public Map<String, C5ItemStatInfo> getItemStatByHashName(C5ItemStatRequest req) {
        return httpExecutor.execute(C5GameAPI.Market.ITEM_STAT, Method.POST, req,
                new TypeReference<>() {
                });
    }

    /**
     * 异步根据 marketHashName 查询统计信息
     */
    public CompletableFuture<Map<String, C5ItemStatInfo>> getItemStatByHashNameAsync(C5ItemStatRequest req) {
        return httpExecutor.executeAsync(C5GameAPI.Market.ITEM_STAT, Method.POST, req,
                new TypeReference<>() {
                });
    }

    /**
     * 根据 marketHashName 查询存世量
     */
    public Map<String, String> getItemSurvivalByHashName(C5ItemStatRequest req) {
        return httpExecutor.execute(C5GameAPI.Market.ITEM_SURVIVAL, Method.POST, req,
                new TypeReference<>() {
                });
    }

    /**
     * 异步根据 marketHashName 查询存世量
     */
    public CompletableFuture<Map<String, String>> getItemSurvivalByHashNameAsync(C5ItemStatRequest req) {
        return httpExecutor.executeAsync(C5GameAPI.Market.ITEM_SURVIVAL, Method.POST, req,
                new TypeReference<>() {
                });
    }
}
