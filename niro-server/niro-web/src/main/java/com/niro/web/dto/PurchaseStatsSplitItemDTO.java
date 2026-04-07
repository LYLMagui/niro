package com.niro.web.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 购买统计按商品和日期拆分明细 DTO
 */
@Data
public class PurchaseStatsSplitItemDTO {

    /**
     * 商品名称
     */
    private String goodsName;

    /**
     * 商品图片
     */
    private String goodsImg;

    /**
     * 统计日期
     */
    private String date;

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
}
