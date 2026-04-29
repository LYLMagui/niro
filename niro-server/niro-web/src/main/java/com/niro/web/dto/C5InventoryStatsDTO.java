package com.niro.web.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * C5 库存状态统计 DTO。
 */
@Data
public class C5InventoryStatsDTO {

    /**
     * 全部在库数量。
     */
    private Long all;

    /**
     * 可交易数量。
     */
    private Long tradable;

    /**
     * 冷却中数量。
     */
    private Long cooldown;

    /**
     * 寄售中数量。
     */
    private Long selling;

    /**
     * 当前筛选条件下的库存总价值。
     */
    private BigDecimal totalValue;
}
