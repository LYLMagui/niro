package com.niro.sdk.c5.client.module;

import com.fasterxml.jackson.core.type.TypeReference;
import com.niro.sdk.c5.client.core.C5HttpEngine;
import com.niro.sdk.c5.request.order.C5BuyerStatusRequest;
import com.niro.sdk.c5.response.C5BaseResponse;
import com.niro.sdk.c5.response.order.C5BuyerStatusResponse;
import lombok.RequiredArgsConstructor;

/**
 * 订单相关接口
 */
@RequiredArgsConstructor
public class C5OrderClient {

    private final C5HttpEngine engine;

    /**
     * 批量查询买家订单状态
     * GET /merchant/order/v2/buyer/status
     */
    public C5BuyerStatusResponse batchBuyerStatus(C5BuyerStatusRequest req) {
        return engine.execute("/merchant/order/v2/buyer/status", "GET", req,
                new TypeReference<C5BaseResponse<C5BuyerStatusResponse>>() {
                });
    }
}
