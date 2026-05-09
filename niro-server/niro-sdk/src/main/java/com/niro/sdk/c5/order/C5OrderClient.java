package com.niro.sdk.c5.order;

import cn.hutool.http.Method;
import com.fasterxml.jackson.core.type.TypeReference;
import com.niro.sdk.c5.client.core.C5HttpExecutor;
import com.niro.sdk.c5.constant.C5GameAPI;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.CompletableFuture;

/**
 * C5 订单相关接口
 */
@RequiredArgsConstructor
public class C5OrderClient {


    private final C5HttpExecutor httpExecutor;

    /**
     * 批量查询买家订单状态
     */
    public C5BuyerStatusResponse batchBuyerStatus(C5BuyerStatusRequest req) {
        return httpExecutor.execute(C5GameAPI.Order.BUYER_STATUS, Method.POST, req,
                new TypeReference<>() {
                });
    }

    /**
     * 异步批量查询买家订单状态
     */
    public CompletableFuture<C5BuyerStatusResponse> batchBuyerStatusAsync(C5BuyerStatusRequest req) {
        return httpExecutor.executeAsync(C5GameAPI.Order.BUYER_STATUS, Method.POST, req,
                new TypeReference<>() {
                });
    }
}
