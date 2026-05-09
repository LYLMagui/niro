package com.niro.sdk.c5.account;

import cn.hutool.http.Method;
import com.fasterxml.jackson.core.type.TypeReference;
import com.niro.sdk.c5.client.core.C5HttpExecutor;
import com.niro.sdk.c5.constant.C5GameAPI;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.CompletableFuture;

/**
 * C5 账户相关接口
 */
@RequiredArgsConstructor
public class C5AccountClient {


    private final C5HttpExecutor engine;

    /**
     * 查询账户余额
     */
    public C5BalanceResponse getBalance() {
        return engine.execute(C5GameAPI.Account.BALANCE, Method.GET, null,
                new TypeReference<>() {
                });
    }

    /**
     * 异步查询账户余额
     */
    public CompletableFuture<C5BalanceResponse> getBalanceAsync() {
        return engine.executeAsync(C5GameAPI.Account.BALANCE, Method.GET, null,
                new TypeReference<>() {
                });
    }

    /**
     * 查询用户 steam 信息
     */
    public C5SteamInfoResponse getSteamInfo() {
        return engine.execute(C5GameAPI.Account.STEAM_INFO_V1, Method.GET, null,
                new TypeReference<>() {
                });
    }

    /**
     * 异步查询用户 steam 信息
     */
    public CompletableFuture<C5SteamInfoResponse> getSteamInfoAsync() {
        return engine.executeAsync(C5GameAPI.Account.STEAM_INFO_V1, Method.GET, null,
                new TypeReference<>() {
                });
    }

    /**
     * 分页查询用户 steam 信息
     */
    public C5SteamInfoResponse getSteamInfoV2(C5SteamInfoRequest req) {
        return engine.execute(C5GameAPI.Account.STEAM_INFO_V2, Method.GET, req,
                new TypeReference<>() {
                });
    }

    /**
     * 异步分页查询用户 steam 信息
     */
    public CompletableFuture<C5SteamInfoResponse> getSteamInfoV2Async(C5SteamInfoRequest req) {
        return engine.executeAsync(C5GameAPI.Account.STEAM_INFO_V2, Method.GET, req,
                new TypeReference<>() {
                });
    }
}
