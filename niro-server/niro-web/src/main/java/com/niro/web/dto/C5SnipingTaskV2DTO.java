package com.niro.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "扫货2.0任务信息")
public class C5SnipingTaskV2DTO {

    private Long id;
    private Long accountId;
    private Long cs2GoodsId;
    private String name;
    private String goodsDisplayName;
    private String goodsIconUrl;
    private String marketHashName;
    private Boolean hasExterior;
    private BigDecimal maxPrice;
    private BigDecimal minPaintwear;
    private BigDecimal maxPaintwear;
    private String stopMode;
    private Integer targetBuyCount;
    private String balanceGuardMode;
    private BigDecimal reserveBalance;
    private Integer priority;
    private Long scanIntervalMs;
    private String taskStatus;
    private Boolean stopRequested;
    private LocalDateTime stopRequestedAt;
    private LocalDateTime nextScanAt;
    private Integer successBuyCount;
    private Integer reservedBuyCount;
    private Integer hitCount;
    private String lastErrorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private C5SnipingTaskV2RunSummaryDTO latestRun;
}
