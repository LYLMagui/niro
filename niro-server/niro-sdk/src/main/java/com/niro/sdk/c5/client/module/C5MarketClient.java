package com.niro.sdk.c5.client.module;

import cn.hutool.core.collection.CollUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.niro.sdk.c5.client.core.C5HttpEngine;
import com.niro.sdk.c5.request.market.*;
import com.niro.sdk.c5.response.C5BaseResponse;
import com.niro.sdk.c5.response.market.*;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 市场/商品相关接口
 */
@RequiredArgsConstructor
public class C5MarketClient {

    private final C5HttpEngine engine;

    /**
     * MarketHashNames批量查询在售最低价和数量
     * POST /merchant/product/price/batch
     */
    public Map<String, C5BatchPriceInfo> batchGetPrice(C5BatchPriceRequest req) {
        return engine.execute("/merchant/product/price/batch", "POST", req, new TypeReference<C5BaseResponse<Map<String, C5BatchPriceInfo>>>() {});
    }

    /**
     * 查看在售列表
     * GET /merchant/sale/v1/search
     */
    public C5SaleSearchResponse searchSale(C5SaleSearchRequest req) {
        return engine.execute("/merchant/sale/v1/search", "GET", req, new TypeReference<C5BaseResponse<C5SaleSearchResponse>>() {});
    }

    /**
     * 价格查询
     * GET /price/info
     */
    public Map<String, Object> getPriceInfo(List<String> itemIds) {
        Map<String, Object> params = new HashMap<>();
        if (CollUtil.isNotEmpty(itemIds)) {
            params.put("itemIds", itemIds);
        }
        return engine.execute("/price/info", "GET", params, new TypeReference<C5BaseResponse<Map<String, Object>>>() {});
    }

    /**
     * 根据marketHashName查询在售列表
     * POST /merchant/market/v2/products/condition/hash/name
     * @deprecated 请使用 {@link #searchProductList(C5ProductListRequest)}
     */
    @Deprecated
    public C5ProductSearchResponse searchProductsByHashName(C5ProductSearchRequest req) {
        return engine.execute("/merchant/market/v2/products/condition/hash/name", "POST", req, new TypeReference<C5BaseResponse<C5ProductSearchResponse>>() {});
    }

    /**
     * 查询在售列表 (V2)
     * POST /merchant/market/v2/products/list
     */
    public C5ProductListResponse searchProductList(C5ProductListRequest req) {
        return engine.execute("/merchant/market/v2/products/list", "POST", req, new TypeReference<C5BaseResponse<C5ProductListResponse>>() {});
    }

    /**
     * 高级搜索在售列表 (V2)
     * POST /merchant/market/v2/products/search
     */
    public C5ProductListResponse productSearch(C5ProductSearchRequest req) {
        return engine.execute("/merchant/market/v2/products/search", "POST", req, new TypeReference<C5BaseResponse<C5ProductListResponse>>() {});
    }

    /**
     * 根据marketHashName查询统计信息
     * POST /merchant/market/v2/item/stat/hash/name
     */
    public Map<String, C5ItemStatInfo> getItemStatByHashName(C5ItemStatRequest req) {
        return engine.execute("/merchant/market/v2/item/stat/hash/name", "POST", req, new TypeReference<C5BaseResponse<Map<String, C5ItemStatInfo>>>() {});
    }

    /**
     * 根据marketHashName查询存世量
     * POST /merchant/market/v2/item/survival/hash/name
     */
    public Map<String, String> getItemSurvivalByHashName(C5ItemStatRequest req) {
        return engine.execute("/merchant/market/v2/item/survival/hash/name", "POST", req, new TypeReference<C5BaseResponse<Map<String, String>>>() {});
    }
}
