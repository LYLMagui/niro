package com.niro.web.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 开箱记录DTO
 */
@Data
public class UnboxRecordDTO {

    private Long id;

    private Long boxGoodsId;

    private String boxName;

    private LocalDate unboxDate;

    private BigDecimal defaultDiscount;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<UnboxRecordItemDTO> items = new ArrayList<>();
}
