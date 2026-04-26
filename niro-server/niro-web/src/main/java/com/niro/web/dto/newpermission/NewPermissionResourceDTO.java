package com.niro.web.dto.newpermission;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 新权限资源视图
 */
@Data
public class NewPermissionResourceDTO {
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
    private List<NewPermissionResourceDTO> children = new ArrayList<>();
}
