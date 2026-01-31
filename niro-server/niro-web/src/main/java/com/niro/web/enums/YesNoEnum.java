package com.niro.web.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 是否枚举 (0=是/正常, 1=否/停用)
 * 严格遵循项目约定：0代表正面/正常状态
 */
@Getter
@AllArgsConstructor
public enum YesNoEnum {
    YES(0, "是/正常/显示"),
    NO(1, "否/停用/隐藏");

    private final Integer code;
    private final String info;
}
