package com.niro.web.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 购买统计汇总 DTO
 */
@Data
public class PurchaseStatsSummaryDTO {

    /**
     * 购买总金额
     */
    private BigDecimal totalAmount;

    /**
     * 购买总件数
     */
    private Integer totalQuantity;

    /**
     * 平均单价
     */
    private BigDecimal avgPrice;

    /**
     * 商品种类数
     */
    private Integer goodsTypeCount;
}
