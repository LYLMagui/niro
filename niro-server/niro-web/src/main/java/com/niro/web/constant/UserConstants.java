package com.niro.web.constant;

/**
 * 用户常量信息
 */
public class UserConstants {
    /**
     * 超级管理员ID
     */
    public static final Long ADMIN_ID = 1L;

    /**
     * 默认角色编码
     */
    public static final String DEFAULT_ROLE_KEY = "user";

    public static final String ADMIN_ROLE_KEY = "admin";

    public static final String DEFAULT_DATA_SCOPE = "1";

    /**
     * 角色状态: 正常
     */
    public static final Integer ROLE_STATUS_NORMAL = 1;

    public static final Integer ROLE_STATUS_DISABLED = 0;

    /**
     * 菜单状态: 正常
     */
    public static final Integer MENU_STATUS_NORMAL = 1;

    public static final Integer MENU_STATUS_DISABLED = 0;

    /**
     * 组件标识：Layout
     */
    public static final String LAYOUT = "Layout";

    /**
     * 组件标识：ParentView
     */
    public static final String PARENT_VIEW = "ParentView";

    /**
     * 组件标识：InnerLink
     */
    public static final String INNER_LINK = "InnerLink";

    /**
     * 路由跳转标识：noRedirect
     */
    public static final String NO_REDIRECT = "noRedirect";
}
