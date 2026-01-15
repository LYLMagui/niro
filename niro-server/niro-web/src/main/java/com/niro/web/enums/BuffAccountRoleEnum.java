package com.niro.web.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * BUFF账号角色枚举
 *
 * @author niro
 * @since 2026-01-15
 */
@Getter
@AllArgsConstructor
public enum BuffAccountRoleEnum {

    /**
     * 扫描
     */
    SCAN("SCAN", "扫描"),

    /**
     * 下单
     */
    TRADE("TRADE", "下单"),

    /**
     * 全能
     */
    BOTH("BOTH", "全能");

    @EnumValue
    @JsonValue
    private final String code;

    private final String description;
}
