package com.niro.web.dto.param.newpermission;

import lombok.Data;

/**
 * 新权限资源保存请求
 */
@Data
public class NewPermissionResourceSaveParam {
    private Long id;
    private String resourceKey;
    private String resourceType;
    private Long parentResourceId;
    private String pageKey;
    private String title;
    private String icon;
    private Integer sortOrder;
    private Boolean hidden;
    private String permissionCode;
    private String buttonGroup;
    private String remark;
    private Integer status;
}
