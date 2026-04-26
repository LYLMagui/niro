package com.niro.web.dto.param.newpermission;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 新权限角色授权保存请求
 */
@Data
public class NewPermissionRoleResourceSaveParam {
    private List<Long> resourceIds = new ArrayList<>();
}
