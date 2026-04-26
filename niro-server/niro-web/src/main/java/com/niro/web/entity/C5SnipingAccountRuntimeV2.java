package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * C5 扫货 2.0 账号运行态配置实体。
 */
@Data
@TableName("c5_sniping_account_runtime_v2")
public class C5SnipingAccountRuntimeV2 {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long accountId;
    private Integer concurrencyLimit;
    private Integer maxInFlightAttempts;
    private LocalDateTime cooldownUntil;
    private String cooldownReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
