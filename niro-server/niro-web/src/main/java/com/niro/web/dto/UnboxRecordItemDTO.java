package com.niro.web.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 开箱记录明细DTO
 */
@Data
public class UnboxRecordItemDTO {

    private Long id;

    private Integer sortNo;

    private String handlingStatus;

    private BigDecimal boxPurchasePrice;

    private String weaponName;

    private BigDecimal inGamePrice;

    private BigDecimal discount;

    private BigDecimal actualSellPrice;

    private BigDecimal wear;

    private Integer exterior;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
