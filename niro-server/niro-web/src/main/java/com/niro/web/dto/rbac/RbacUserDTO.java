package com.niro.web.dto.rbac;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * RBAC 用户视图
 */
@Data
public class RbacUserDTO {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 用户状态（1正常 0禁用）
     */
    private Integer status;

    /**
     * 已分配角色ID
     */
    private List<Long> roleIds = new ArrayList<>();
}
