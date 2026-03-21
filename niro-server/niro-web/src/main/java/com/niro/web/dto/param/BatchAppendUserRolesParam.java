package com.niro.web.dto.param;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量追加用户角色请求参数
 */
@Data
public class BatchAppendUserRolesParam {

    /**
     * 用户ID列表
     */
    private List<Long> userIds = new ArrayList<>();

    /**
     * 角色ID列表
     */
    private List<Long> roleIds = new ArrayList<>();
}

