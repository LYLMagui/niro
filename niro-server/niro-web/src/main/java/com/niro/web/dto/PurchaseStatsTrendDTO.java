package com.niro.web.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 购买统计趋势 DTO
 */
@Data
public class PurchaseStatsTrendDTO {

    /**
     * 日期
     */
    private String date;

    /**
     * 购买金额
     */
    private BigDecimal amount;

    /**
     * 购买件数
     */
    private Integer quantity;
}
