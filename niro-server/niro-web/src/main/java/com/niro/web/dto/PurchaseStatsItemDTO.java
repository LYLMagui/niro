package com.niro.web.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 购买统计商品明细 DTO
 */
@Data
public class PurchaseStatsItemDTO {

    /**
     * 商品名称
     */
    private String goodsName;

    /**
     * 商品图片
     */
    private String goodsImg;

    /**
     * 购买件数
     */
    private Integer totalQuantity;

    /**
     * 购买总金额
     */
    private BigDecimal totalAmount;

    /**
     * 平均单价
     */
    private BigDecimal avgPrice;

    /**
     * 购买金额占比
     */
    private BigDecimal amountRatio;

    /**
     * 最近购买日期
     */
    private String latestPurchaseDate;
}
