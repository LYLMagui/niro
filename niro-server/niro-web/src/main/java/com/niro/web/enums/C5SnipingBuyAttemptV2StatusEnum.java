package com.niro.web.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum C5SnipingBuyAttemptV2StatusEnum {

    INIT("INIT", "初始化"),
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败"),
    SKIPPED("SKIPPED", "跳过");

    @EnumValue
    @JsonValue
    private final String code;

    private final String description;
}
