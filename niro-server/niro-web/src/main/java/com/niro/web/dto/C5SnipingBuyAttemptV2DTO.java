package com.niro.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "扫货2.0下单尝试明细")
public class C5SnipingBuyAttemptV2DTO {

    private Long id;
    private String listingId;
    private String idempotencyKey;
    private String outTradeNo;
    private Boolean slotReserved;
    private Boolean slotReleased;
    private BigDecimal inFlightAmount;
    private LocalDateTime remoteCheckedAt;
    private LocalDateTime initExpireAt;
    private Integer recoveryAttemptCount;
    private String attemptStatus;
    private Long orderRecordId;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
