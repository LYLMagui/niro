package com.niro.web.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 开箱记录分页DTO
 */
@Data
public class UnboxRecordPageDTO {

    private Long id;

    private Long goodsId;

    private String boxName;

    private LocalDate unboxDate;

    private BigDecimal defaultDiscount;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer totalCount;

    private BigDecimal totalPurchaseCost;

    private BigDecimal totalActualFee;

    private BigDecimal totalActualNetProfit;

    private BigDecimal totalActualProfitRate;

    private String status;
}
