package com.niro.sdk.c5.purchase;

import cn.hutool.http.Method;
import com.alibaba.fastjson2.TypeReference;
import com.niro.sdk.c5.client.core.C5HttpExecutor;
import com.niro.sdk.c5.constant.C5GameAPI;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.CompletableFuture;

/**
 * C5 求购相关接口
 */
@RequiredArgsConstructor
public class C5PurchaseClient {


    private final C5HttpExecutor engine;

    /**
     * 发起求购
     */
    public Long createPurchase(C5PurchaseCreateRequest req) {
        return engine.execute(C5GameAPI.Purchase.CREATE, Method.POST, req,
                new TypeReference<>() {
                });
    }

    /**
     * 异步发起求购
     */
    public CompletableFuture<Long> createPurchaseAsync(C5PurchaseCreateRequest req) {
        return engine.executeAsync(C5GameAPI.Purchase.CREATE, Method.POST, req,
                new TypeReference<>() {
                });
    }

    /**
     * 取消求购
     */
    public Boolean cancelPurchase(C5PurchaseCancelRequest req) {
        return engine.execute(C5GameAPI.Purchase.CANCEL, Method.POST, req,
                new TypeReference<>() {
                });
    }

    /**
     * 异步取消求购
     */
    public CompletableFuture<Boolean> cancelPurchaseAsync(C5PurchaseCancelRequest req) {
        return engine.executeAsync(C5GameAPI.Purchase.CANCEL, Method.POST, req,
                new TypeReference<>() {
                });
    }

    /**
     * 求购列表
     */
    public C5PurchaseListResponse getPurchaseList(C5PurchaseListRequest req) {
        return engine.execute(C5GameAPI.Purchase.LIST, Method.GET, req,
                new TypeReference<>() {
                });
    }

    /**
     * 异步查询求购列表
     */
    public CompletableFuture<C5PurchaseListResponse> getPurchaseListAsync(C5PurchaseListRequest req) {
        return engine.executeAsync(C5GameAPI.Purchase.LIST, Method.GET, req,
                new TypeReference<>() {
                });
    }

    /**
     * 求购详情
     */
    public C5PurchaseDetailResponse getPurchaseDetail(C5PurchaseDetailRequest req) {
        return engine.execute(C5GameAPI.Purchase.DETAIL, Method.GET, req,
                new TypeReference<>() {
                });
    }

    /**
     * 异步查询求购详情
     */
    public CompletableFuture<C5PurchaseDetailResponse> getPurchaseDetailAsync(C5PurchaseDetailRequest req) {
        return engine.executeAsync(C5GameAPI.Purchase.DETAIL, Method.GET, req,
                new TypeReference<>() {
                });
    }

    /**
     * 求购最高价
     */
    public C5PurchaseMaxPriceResponse getPurchaseMaxPrice(C5PurchaseMaxPriceRequest req) {
        return engine.execute(C5GameAPI.Purchase.MAX_PRICE, Method.GET, req,
                new TypeReference<>() {
                });
    }

    /**
     * 异步查询求购最高价
     */
    public CompletableFuture<C5PurchaseMaxPriceResponse> getPurchaseMaxPriceAsync(C5PurchaseMaxPriceRequest req) {
        return engine.executeAsync(C5GameAPI.Purchase.MAX_PRICE, Method.GET, req,
                new TypeReference<>() {
                });
    }
}
