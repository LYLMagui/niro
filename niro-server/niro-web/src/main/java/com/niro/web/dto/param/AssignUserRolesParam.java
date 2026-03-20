package com.niro.web.dto.param;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户分配角色请求
 */
@Data
public class AssignUserRolesParam {

    /**
     * 角色ID列表
     */
    private List<Long> roleIds = new ArrayList<>();
}
