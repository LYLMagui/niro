package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.niro.web.enums.C5SnipingTaskV2BalanceGuardModeEnum;
import com.niro.web.enums.C5SnipingTaskV2StatusEnum;
import com.niro.web.enums.C5SnipingTaskV2StopModeEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("c5_sniping_task_v2")
public class C5SnipingTaskV2 {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long accountId;
    private Long cs2GoodsId;
    private String name;
    private BigDecimal maxPrice;
    private BigDecimal minPaintwear;
    private BigDecimal maxPaintwear;
    private C5SnipingTaskV2StopModeEnum stopMode;
    private Integer targetBuyCount;
    private C5SnipingTaskV2BalanceGuardModeEnum balanceGuardMode;
    private BigDecimal reserveBalance;
    private Integer priority;
    private Long scanIntervalMs;
    private C5SnipingTaskV2StatusEnum taskStatus;
    private Long latestRunId;
    private Boolean stopRequested;
    private LocalDateTime stopRequestedAt;
    private LocalDateTime nextScanAt;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private Integer successBuyCount;
    private Integer reservedBuyCount;
    private Integer hitCount;
    private String lastErrorMessage;
    private Integer version;
    private Integer delFlag;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
