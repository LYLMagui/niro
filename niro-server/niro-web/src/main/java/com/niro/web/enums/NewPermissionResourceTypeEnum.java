package com.niro.web.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 新权限资源类型
 */
@Getter
@AllArgsConstructor
public enum NewPermissionResourceTypeEnum {
    PAGE("PAGE", "页面"),
    MENU("MENU", "菜单"),
    BUTTON("BUTTON", "按钮");

    private final String code;
    private final String info;
}
