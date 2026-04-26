package com.niro.web.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * C5 扫货独立账号余额刷新结果 DTO。
 */
@Data
public class C5SnipingAccountBalanceRefreshResultDTO {

    /**
     * 账号 ID。
     */
    private Long accountId;

    /**
     * 账号展示名称。
     */
    private String accountName;

    /**
     * 是否刷新成功。
     */
    private Boolean success;

    /**
     * 刷新后的 C5 可用余额，兼容旧前端字段。
     */
    private BigDecimal balance;

    /**
     * C5 可用余额，对应余额 V2 moneyAmount。
     */
    private BigDecimal moneyAmount;

    /**
     * 保证金余额。
     */
    private BigDecimal depositAmount;

    /**
     * 交易待结算余额。
     */
    private BigDecimal pendingBalance;

    /**
     * 秒到账余额。
     */
    private BigDecimal creditMoney;

    /**
     * 秒到账保证金。
     */
    private BigDecimal creditDeposit;

    /**
     * 刷新结果说明。
     */
    private String message;
}
