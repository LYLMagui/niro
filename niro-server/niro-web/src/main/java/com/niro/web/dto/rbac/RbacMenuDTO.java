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
}
