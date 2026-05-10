package com.niro.web.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 开箱记录汇总DTO
 */
@Data
public class UnboxRecordSummaryDTO {

    private Integer totalBatches;

    private BigDecimal totalPurchaseCost;

    private BigDecimal totalFee;

    private BigDecimal totalActualNetProfit;

    private BigDecimal totalActualProfitRate;
}
