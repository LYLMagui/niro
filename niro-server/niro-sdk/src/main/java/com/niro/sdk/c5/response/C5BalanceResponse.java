package com.niro.sdk.c5.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class C5BalanceResponse {
    private Long userId;
    private BigDecimal moneyAmount;
    private BigDecimal depositAmount;
    private BigDecimal tradeSettleAmount;
    private BigDecimal creditMoney;
    private BigDecimal creditDeposit;
}
