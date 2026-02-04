package com.niro.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

/**
 * 用户信息详情（包含角色权限）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户信息详情（包含角色权限）")
public class UserInfoDTO extends UserDTO {
    
    @Schema(description = "角色列表")
    private Set<String> roles;

    @Schema(description = "权限列表")
    private Set<String> permissions;
}
