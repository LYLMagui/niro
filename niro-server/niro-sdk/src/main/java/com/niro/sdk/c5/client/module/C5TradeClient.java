package com.niro.sdk.c5.client.module;

import com.fasterxml.jackson.core.type.TypeReference;
import com.niro.sdk.c5.client.core.C5HttpEngine;
import com.niro.sdk.c5.request.trade.C5BatchBuyRequest;
import com.niro.sdk.c5.request.trade.C5NormalBuyRequest;
import com.niro.sdk.c5.request.trade.C5QuickBuyRequest;
import com.niro.sdk.c5.response.C5BaseResponse;
import com.niro.sdk.c5.response.C5BuyResponse;
import com.niro.sdk.c5.response.trade.C5BatchBuyResponse;
import lombok.RequiredArgsConstructor;

/**
 * 交易/购买相关接口
 */
@RequiredArgsConstructor
public class C5TradeClient {

    private final C5HttpEngine engine;

    /**
     * 普通购买
     * POST /merchant/trade/v2/normal-buy
     */
    public C5BuyResponse normalBuy(C5NormalBuyRequest req) {
        return engine.execute("/merchant/trade/v2/normal-buy", "POST", req, new TypeReference<C5BaseResponse<C5BuyResponse>>() {});
    }

    /**
     * 快速购买
     * POST /merchant/trade/v2/quick-buy
     */
    public C5BuyResponse quickBuy(C5QuickBuyRequest req) {
        return engine.execute("/merchant/trade/v2/quick-buy", "POST", req, new TypeReference<C5BaseResponse<C5BuyResponse>>() {});
    }

    /**
     * 批量购买
     * POST /merchant/trade/v1/batch/buy
     */
    public C5BatchBuyResponse batchBuy(C5BatchBuyRequest req) {
        return engine.execute("/merchant/trade/v1/batch/buy", "POST", req, new TypeReference<C5BaseResponse<C5BatchBuyResponse>>() {});
    }
}
