package com.niro.sdk.c5.market;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class C5BatchPriceRequest {
    /**
     * 游戏id
     */
    private String appId;
    /**
     * 饰品英文名称数组
     */
    private List<String> marketHashNames;
}
