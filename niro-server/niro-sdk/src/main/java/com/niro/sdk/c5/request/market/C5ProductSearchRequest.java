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
     * 最小价格
     */
    private BigDecimal priceMin;
    /**
     * 最大价格 (别名，保持兼容)
     */
    private BigDecimal priceMax;
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
    private Double wearMin;
    /**
     * 最大磨损度
     */
    private Double wearMax;
    /**
     * 最小渐变度
     */
    private Integer fadeMin;
    /**
     * 最大渐变度
     */
    private Integer fadeMax;
    /**
     * 特殊款式
     */
    private String specialStyle;
    /**
     * 是否接受议价：0：否；1：是
     */
    private Integer acceptBargain;
}
