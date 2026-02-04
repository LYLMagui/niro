package com.niro.web.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 是否枚举 (1=是/正常, 0=否/停用)
 */
@Getter
@AllArgsConstructor
public enum YesNoEnum {
    YES(1, "是/正常/显示"),
    NO(0, "否/停用/隐藏");

    private final Integer code;
    private final String info;
}
