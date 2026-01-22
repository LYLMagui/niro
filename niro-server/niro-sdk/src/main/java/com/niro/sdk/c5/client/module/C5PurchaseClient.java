package com.niro.sdk.c5.client.module;

import com.fasterxml.jackson.core.type.TypeReference;
import com.niro.sdk.c5.client.core.C5HttpEngine;
import com.niro.sdk.c5.request.purchase.*;
import com.niro.sdk.c5.response.C5BaseResponse;
import com.niro.sdk.c5.response.purchase.C5PurchaseDetailResponse;
import com.niro.sdk.c5.response.purchase.C5PurchaseListResponse;
import com.niro.sdk.c5.response.purchase.C5PurchaseMaxPriceResponse;
import lombok.RequiredArgsConstructor;

/**
 * 求购相关接口
 */
@RequiredArgsConstructor
public class C5PurchaseClient {

    private final C5HttpEngine engine;

    /**
     * 发起求购
     * POST /merchant/purchase/v1/create
     */
    public Long createPurchase(C5PurchaseCreateRequest req) {
        return engine.execute("/merchant/purchase/v1/create", "POST", req, new TypeReference<C5BaseResponse<Long>>() {});
    }

    /**
     * 取消求购
     * POST /merchant/purchase/v1/cancel
     */
    public Boolean cancelPurchase(C5PurchaseCancelRequest req) {
        return engine.execute("/merchant/purchase/v1/cancel", "POST", req, new TypeReference<C5BaseResponse<Boolean>>() {});
    }

    /**
     * 求购列表
     * GET /merchant/purchase/v1/owned/list
     */
    public C5PurchaseListResponse getPurchaseList(C5PurchaseListRequest req) {
        return engine.execute("/merchant/purchase/v1/owned/list", "GET", req, new TypeReference<C5BaseResponse<C5PurchaseListResponse>>() {});
    }

    /**
     * 求购详情
     * GET /merchant/purchase/v1/order-detail
     */
    public C5PurchaseDetailResponse getPurchaseDetail(C5PurchaseDetailRequest req) {
        return engine.execute("/merchant/purchase/v1/order-detail", "GET", req, new TypeReference<C5BaseResponse<C5PurchaseDetailResponse>>() {});
    }

    /**
     * 求购最高价
     * GET /merchant/purchase/v1/max-price
     */
    public C5PurchaseMaxPriceResponse getPurchaseMaxPrice(C5PurchaseMaxPriceRequest req) {
        return engine.execute("/merchant/purchase/v1/max-price", "GET", req, new TypeReference<C5BaseResponse<C5PurchaseMaxPriceResponse>>() {});
    }
}
