package com.niro.sdk.c5.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * C5 HTTP 状态码。
 */
@Getter
@AllArgsConstructor
public enum C5HttpStatusEnum {
    TOO_MANY_REQUESTS(429, "请求过于频繁");

    private final int code;
    private final String desc;

    public static C5HttpStatusEnum of(int code) {
        return Arrays.stream(values())
                .filter(item -> item.code == code)
                .findFirst()
                .orElse(null);
    }

    public static String getDesc(int code, String defaultDesc) {
        C5HttpStatusEnum status = of(code);
        if (status == null) {
            return defaultDesc;
        }
        return status.getDesc();
    }
}
