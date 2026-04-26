package com.niro.web.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum C5SnipingTaskV2BalanceGuardModeEnum {

    MAX_PRICE("MAX_PRICE", "低于最高价停止"),
    RESERVE_BALANCE("RESERVE_BALANCE", "低于保底余额停止");

    @EnumValue
    @JsonValue
    private final String code;

    private final String description;
}
