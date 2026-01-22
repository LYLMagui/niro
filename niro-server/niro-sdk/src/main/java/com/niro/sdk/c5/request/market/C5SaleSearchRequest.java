package com.niro.sdk.c5.request.market;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class C5SaleSearchRequest {
    /**
     * 游戏id
     */
    private Integer appId;
    /**
     * 所属steamId，该参数必须要传，可以设置空
     */
    private String steamId;
    /**
     * 发货方式 发货模式 1人工 2自动
     */
    private Integer delivery;
    /**
     * 页码
     */
    private Integer page;
    /**
     * 每页数量
     */
    private Integer limit;
}
