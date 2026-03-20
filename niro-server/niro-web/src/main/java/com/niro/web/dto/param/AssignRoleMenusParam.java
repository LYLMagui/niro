package com.niro.web.dto.param;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色分配菜单请求
 */
@Data
public class AssignRoleMenusParam {

    /**
     * 菜单ID列表
     */
    private List<Long> menuIds = new ArrayList<>();
}
