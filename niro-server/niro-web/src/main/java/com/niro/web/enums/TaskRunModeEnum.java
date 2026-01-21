package com.niro.web.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务运行模式枚举
 *
 * @author niro
 * @since 2026-01-21
 */
@Getter
@AllArgsConstructor
public enum TaskRunModeEnum {

    /**
     * 仅扫描
     */
    SCAN("SCAN", "仅扫描"),

    /**
     * 仅下单
     */
    TRADE("TRADE", "仅下单"),

    /**
     * 扫描并下单
     */
    BOTH("BOTH", "全能模式");

    @EnumValue
    @JsonValue
    private final String code;

    private final String description;
}
