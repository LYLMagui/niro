package com.niro.web.dto;

import com.niro.web.enums.C5SnipingAccountStatusEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * C5 扫货独立账号 DTO。
 * <p>
 * 用于 C5 扫货 2.0 账号管理和任务可用账号列表返回，不暴露 BUFF 账号角色字段。
 * </p>
 */
@Data
public class C5SnipingAccountDTO {

    /**
     * 账号 ID。
     */
    private Long id;

    /**
     * 所属用户 ID。
     */
    private Long userId;

    /**
     * 账号展示名称。
     */
    private String accountName;

    /**
     * C5 AppKey 脱敏展示值。
     */
    private String c5AppKeyMasked;

    /**
     * 是否已配置 C5 AppKey。
     */
    private Boolean hasC5AppKey;

    /**
     * Steam 交易链接。
     */
    private String steamTradeUrl;

    /**
     * Steam ID，用于 C5 库存接口。
     */
    private String steamId;

    /**
     * 账号状态。
     */
    private C5SnipingAccountStatusEnum status;

    /**
     * C5 可用余额，兼容旧前端字段。
     */
    private BigDecimal balance;

    /**
     * C5 可用余额，对应余额 V2 moneyAmount。
     */
    private BigDecimal moneyAmount;

    /**
     * 交易待结算余额。
     */
    private BigDecimal pendingBalance;

    /**
     * 保证金余额。
     */
    private BigDecimal depositAmount;

    /**
     * 秒到账余额。
     */
    private BigDecimal creditMoney;

    /**
     * 秒到账保证金。
     */
    private BigDecimal creditDeposit;

    /**
     * 账号所有余额项合计。
     */
    private BigDecimal totalBalance;

    /**
     * 最近一次检测时间。
     */
    private LocalDateTime lastCheckTime;

    /**
     * 用户备注。
     */
    private String remark;

    /**
     * 账号级并发上限。
     */
    private Integer concurrencyLimit;

    /**
     * 账号级最大在途下单尝试数。
     */
    private Integer maxInFlightAttempts;

    /**
     * 当前账号绑定的未删除任务 ID。
     */
    private Long boundTaskId;

    /**
     * 当前账号绑定的未删除任务名称。
     */
    private String boundTaskName;

    /**
     * 当前账号警告信息。
     */
    private String warningMsg;

    /**
     * 今日扫描次数。
     */
    private Integer todayScanCount;

    /**
     * 成功下单次数。
     */
    private Integer tradeSuccessCount;

    /**
     * 总下单次数。
     */
    private Integer tradeTotalCount;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;
}
