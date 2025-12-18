package com.buff.web.enums;

import lombok.Getter;

/**
 * @author liyl
 * @date 2025-12-18
 * @description 临时枚举
 */
@Getter
public enum TempEnum {
    /**
     * 正常
     */
    NORMAL(1, "正常");

    private final Integer code;
    private final String desc;

    TempEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
