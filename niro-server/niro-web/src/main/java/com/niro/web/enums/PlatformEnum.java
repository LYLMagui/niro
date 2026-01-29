package com.niro.web.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 平台类型枚举
 */
@Getter
@AllArgsConstructor
public enum PlatformEnum {
    BUFF("BUFF", "网易BUFF"),
    C5("C5", "C5GAME");

    @EnumValue
    @JsonValue
    private final String code;
    private final String desc;

    public static PlatformEnum getByCode(String code) {
        if (code == null) return null;
        for (PlatformEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
