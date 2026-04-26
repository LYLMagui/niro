package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.niro.web.enums.C5SnipingBuyAttemptV2StatusEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("c5_sniping_buy_attempt_v2")
public class C5SnipingBuyAttemptV2 {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long runId;
    private Long hitRecordId;
    private Long accountId;
    private String listingId;
    private String idempotencyKey;
    private String outTradeNo;
    private Boolean slotReserved;
    private Boolean slotReleased;
    private BigDecimal inFlightAmount;
    private LocalDateTime remoteCheckedAt;
    private LocalDateTime initExpireAt;
    private Integer recoveryAttemptCount;
    private C5SnipingBuyAttemptV2StatusEnum attemptStatus;
    private Long orderRecordId;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
    private LocalDateTime updateTime;
}
