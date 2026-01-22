package com.niro.sdk.c5.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum C5DeliveryEnum {
    MANUAL(1, "人工发货"),
    AUTO(2, "自动发货");

    private final int code;
    private final String desc;
}
