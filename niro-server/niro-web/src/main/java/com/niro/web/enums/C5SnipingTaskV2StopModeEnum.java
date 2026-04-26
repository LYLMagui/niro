package com.niro.web.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum C5SnipingTaskV2StopModeEnum {

    BUY_COUNT("BUY_COUNT", "按购买数量停止"),
    BALANCE_GUARD("BALANCE_GUARD", "按余额保护停止");

    @EnumValue
    @JsonValue
    private final String code;

    private final String description;
}
