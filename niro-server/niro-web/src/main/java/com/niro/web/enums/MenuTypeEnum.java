package com.niro.web.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 菜单类型
 */
@Getter
@AllArgsConstructor
public enum MenuTypeEnum {
    DIR(0, "目录"),
    MENU(1, "菜单"),
    BUTTON(2, "按钮");

    private final Integer code;
    private final String info;
}
