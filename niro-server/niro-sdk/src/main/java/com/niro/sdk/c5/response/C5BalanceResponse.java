package com.niro.sdk.c5.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class C5BalanceResponse {
    private Long userId;
    private BigDecimal balance;
}
