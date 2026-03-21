package com.niro.web.dto.param;

import lombok.Data;

/**
 * Role create request.
 */
@Data
public class CreateRoleParam {

    private String roleName;

    private String roleKey;

    private Integer roleSort;

    private Integer status;

    private String remark;
}