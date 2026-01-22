package com.niro.sdk.c5.client.module;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.niro.sdk.c5.client.core.C5HttpEngine;
import com.niro.sdk.c5.response.C5BaseResponse;
import com.niro.sdk.c5.response.C5InventoryResponse;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 库存相关接口
 */
@RequiredArgsConstructor
public class C5InventoryClient {

    private final C5HttpEngine engine;

    /**
     * 库存列表
     * GET /merchant/inventory/v2/{steamId}/{appId}
     */
    public C5InventoryResponse getInventory(String steamId, String appId, String language, String startAssetId, Integer count) {
        String url = String.format("/merchant/inventory/v2/%s/%s", steamId, appId);
        Map<String, Object> params = new HashMap<>();
        if (StrUtil.isNotBlank(language)) params.put("language", language);
        if (StrUtil.isNotBlank(startAssetId)) params.put("startAssetId", startAssetId);
        if (count != null) params.put("count", count);

        return engine.execute(url, "GET", params, new TypeReference<C5BaseResponse<C5InventoryResponse>>() {});
    }
}
