package com.niro.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "扫货2.0命中明细")
public class C5SnipingHitRecordV2DTO {

    private Long id;
    private String listingId;
    private BigDecimal listingPrice;
    private BigDecimal paintwear;
    private String decisionResult;
    private String buyFailureReason;
    private LocalDateTime hitAt;
    private LocalDateTime createTime;
}
