package com.niro.web.dto.rbac;

import lombok.Data;

/**
 * RBAC 角色视图
 */
@Data
public class RbacRoleDTO {

    /**
     * 角色ID
     */
    private Long roleId;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 角色编码
     */
    private String roleKey;

    /**
     * 角色状态（1正常 0停用）
     */
    private Integer status;
}
