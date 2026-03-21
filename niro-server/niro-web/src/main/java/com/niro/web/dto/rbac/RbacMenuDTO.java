package com.niro.web.dto.rbac;

import lombok.Data;

/**
 * RBAC 菜单视图
 */
@Data
public class RbacMenuDTO {

    /**
     * 菜单ID
     */
    private Long id;

    /**
     * 父菜单ID
     */
    private Long parentId;

    /**
     * 菜单标题
     */
    private String title;

    /**
     * 路由名称
     */
    private String name;

    /**
     * 路由路径
     */
    private String path;

    /**
     * 组件路径
     */
    private String component;

    /**
     * 菜单图标
     */
    private String icon;

    /**
     * 菜单排序
     */
    private Integer sortOrder;

    /**
     * 菜单类型（0目录 1菜单 2按钮）
     */
    private Integer type;

    /**
     * 权限标识
     */
    private String permission;

    /**
     * 菜单状态（1正常 0停用）
     */
    private Integer status;

    /**
     * 是否隐藏
     */
    private Boolean hidden;

    /**
     * 是否缓存
     */
    private Boolean keepAlive;

    /**
     * 重定向地址
     */
    private String redirect;
}
