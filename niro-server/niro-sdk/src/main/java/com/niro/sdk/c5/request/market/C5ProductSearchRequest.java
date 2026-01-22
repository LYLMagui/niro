package com.niro.sdk.c5.request.market;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class C5ProductSearchRequest {
    /**
     * 当前页码
     */
    private Integer pageNum;
    /**
     * 每页数量
     */
    private Integer pageSize;
    /**
     * 游戏id
     */
    private Integer appId;
    /**
     * marketHashName
     */
    private String marketHashName;
    /**
     * 最大价格,币种人民币
     */
    private BigDecimal maxPrice;
    /**
     * 发货方式：1：人工；2：自动
     */
    private Integer delivery;
    /**
     * 款式id
     */
    private Integer styleId;
    /**
     * 最小磨损度
     */
    private Double minWear;
    /**
     * 最大磨损度
     */
    private Double maxWear;
}
