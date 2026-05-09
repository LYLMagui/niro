package com.niro.sdk.c5.trade;

import cn.hutool.http.Method;
import com.fasterxml.jackson.core.type.TypeReference;
import com.niro.sdk.c5.client.core.C5HttpExecutor;
import com.niro.sdk.c5.constant.C5GameAPI;
import com.niro.sdk.c5.order.C5OrderDetailRequest;
import com.niro.sdk.c5.order.C5OrderDetailResponse;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.CompletableFuture;

/**
 * C5 交易 / 购买相关接口
 */
@RequiredArgsConstructor
public class C5TradeClient {


    private final C5HttpExecutor engine;

    /**
     * 普通购买
     */
    public C5BuyResponse normalBuy(C5NormalBuyRequest req) {
        return engine.execute(C5GameAPI.Trade.NORMAL_BUY, Method.POST, req,
                new TypeReference<>() {
                });
    }

    /**
     * 异步普通购买
     */
    public CompletableFuture<C5BuyResponse> normalBuyAsync(C5NormalBuyRequest req) {
        return engine.executeAsync(C5GameAPI.Trade.NORMAL_BUY, Method.POST, req,
                new TypeReference<>() {
                });
    }

    /**
     * 快速购买
     */
    public C5BuyResponse quickBuy(C5QuickBuyRequest req) {
        return engine.execute(C5GameAPI.Trade.QUICK_BUY, Method.POST, req,
                new TypeReference<>() {
                });
    }

    /**
     * 异步快速购买
     */
    public CompletableFuture<C5BuyResponse> quickBuyAsync(C5QuickBuyRequest req) {
        return engine.executeAsync(C5GameAPI.Trade.QUICK_BUY, Method.POST, req,
                new TypeReference<>() {
                });
    }

    /**
     * 批量购买，允许远端返回部分失败明细，由调用方自行根据 data 判定每条记录成败。
     */
    public C5BatchBuyResponse batchBuy(C5BatchBuyRequest req) {
        return engine.executeAllowFailureData(C5GameAPI.Trade.BATCH_BUY, Method.POST, req,
                new TypeReference<>() {
                });
    }

    /**
     * 异步批量购买，允许远端返回部分失败明细，由调用方自行根据 data 判定每条记录成败。
     */
    public CompletableFuture<C5BatchBuyResponse> batchBuyAsync(C5BatchBuyRequest req) {
        return engine.executeAllowFailureDataAsync(C5GameAPI.Trade.BATCH_BUY, Method.POST, req,
                new TypeReference<>() {
                });
    }

    /**
     * 订单详情查询
     */
    public C5OrderDetailResponse getOrderDetail(C5OrderDetailRequest req) {
        return engine.execute(C5GameAPI.Order.BUY_DETAIL, Method.GET, req,
                new TypeReference<>() {
                });
    }

    /**
     * 异步订单详情查询
     */
    public CompletableFuture<C5OrderDetailResponse> getOrderDetailAsync(C5OrderDetailRequest req) {
        return engine.executeAsync(C5GameAPI.Order.BUY_DETAIL, Method.GET, req,
                new TypeReference<>() {
                });
    }
}
