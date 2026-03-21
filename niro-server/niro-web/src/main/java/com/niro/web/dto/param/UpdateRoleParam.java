package com.niro.web.dto.param;

import lombok.Data;

/**
 * Role update request.
 */
@Data
public class UpdateRoleParam {

    private String roleName;

    private String roleKey;

    private Integer roleSort;

    private Integer status;

    private String remark;
}