package com.niro.web.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 菜单类型
 */
@Getter
@AllArgsConstructor
public enum MenuTypeEnum {
    DIR("M", "目录"),
    MENU("C", "菜单"),
    BUTTON("F", "按钮");

    private final String code;
    private final String info;
}
