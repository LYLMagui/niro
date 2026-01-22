package com.niro.sdk.c5.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum C5PayStatusEnum {
    PAYING(0, "支付中"),
    SUCCESS(1, "支付成功"),
    FAILED(2, "支付失败");

    private final int code;
    private final String desc;
}
