package com.niro.sdk.c5.client.module;

import com.fasterxml.jackson.core.type.TypeReference;
import com.niro.sdk.c5.client.core.C5HttpEngine;
import com.niro.sdk.c5.request.account.C5SteamInfoRequest;
import com.niro.sdk.c5.response.C5BalanceResponse;
import com.niro.sdk.c5.response.C5BaseResponse;
import com.niro.sdk.c5.response.account.C5SteamInfoResponse;
import lombok.RequiredArgsConstructor;

/**
 * 账户相关接口
 */
@RequiredArgsConstructor
public class C5AccountClient {

    private final C5HttpEngine engine;

    /**
     * 查询账户余额
     * GET /merchant/account/v2/balance
     */
    public C5BalanceResponse getBalance() {
        return engine.execute("/merchant/account/v2/balance", "GET", null, new TypeReference<C5BaseResponse<C5BalanceResponse>>() {});
    }

    /**
     * 查询用户 steam 信息
     * GET /merchant/account/v1/steamInfo
     */
    public C5SteamInfoResponse getSteamInfo() {
        return engine.execute("/merchant/account/v1/steamInfo", "GET", null, new TypeReference<C5BaseResponse<C5SteamInfoResponse>>() {});
    }

    /**
     * 分页查询用户 steam 信息
     * GET /merchant/account/v2/steamInfo
     */
    public C5SteamInfoResponse getSteamInfoV2(C5SteamInfoRequest req) {
        return engine.execute("/merchant/account/v2/steamInfo", "GET", req, new TypeReference<C5BaseResponse<C5SteamInfoResponse>>() {});
    }
}
