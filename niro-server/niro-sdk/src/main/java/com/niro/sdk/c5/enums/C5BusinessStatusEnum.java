package com.niro.sdk.c5.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * C5 业务状态码。
 */
@Getter
@AllArgsConstructor
public enum C5BusinessStatusEnum {
    INSUFFICIENT_BALANCE(70001, "余额不足");

    private final int code;
    private final String desc;

    public static C5BusinessStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> item.code == code)
                .findFirst()
                .orElse(null);
    }

    public static String getDesc(Integer code, String defaultDesc) {
        C5BusinessStatusEnum status = of(code);
        if (status == null) {
            return defaultDesc;
        }
        return status.getDesc();
    }
}
