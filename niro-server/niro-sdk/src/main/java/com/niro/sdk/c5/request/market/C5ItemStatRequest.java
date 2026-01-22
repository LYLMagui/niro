package com.niro.sdk.c5.request.market;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class C5ItemStatRequest {
    /**
     * 游戏id
     */
    private Integer appId;
    /**
     * marketHashName列表
     */
    private List<String> marketHashNames;
}
