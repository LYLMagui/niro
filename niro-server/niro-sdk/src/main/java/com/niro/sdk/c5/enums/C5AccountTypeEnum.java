package com.niro.sdk.c5.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum C5AccountTypeEnum {
    BALANCE(0, "账户余额"),
    PURCHASE_BALANCE(1, "求购余额");

    private final int code;
    private final String desc;
}
