package com.niro.sdk.c5.purchase;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class C5PurchaseListRequest {
    /**
     * 游戏id，如dota2为570;csgo为730
     */
    private Integer appId;
    /**
     * 状态:不限不传;求购状态;1:求购中;4:隐藏中;
     */
    private Integer status;
    /**
     * 最低价
     */
    private String minPrice;
    /**
     * 最高价
     */
    private String maxPrice;
    /**
     * 页码
     */
    private Integer page;
    /**
     * 每页显示数
     */
    private Integer limit;
}
