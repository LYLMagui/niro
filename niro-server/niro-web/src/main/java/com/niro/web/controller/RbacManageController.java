package com.niro.web.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.niro.core.util.Assert;
import com.niro.web.constant.PermissionConstants;
import com.niro.web.constant.UserConstants;
import com.niro.web.dto.param.AssignUserRolesParam;
import com.niro.web.dto.param.BatchAppendUserRolesParam;
import com.niro.web.dto.param.CreateRoleParam;
import com.niro.web.dto.param.UpdateRoleParam;
import com.niro.web.dto.rbac.RbacRoleDTO;
import com.niro.web.dto.rbac.RbacUserDTO;
import com.niro.web.entity.SysRole;
import com.niro.web.entity.SysUserRole;
import com.niro.web.entity.User;
import com.niro.web.service.SysRoleService;
import com.niro.web.service.SysUserRoleService;
import com.niro.web.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 角色与用户角色管理接口
 */
@RestController
@RequestMapping("/api/rbac")
@RequiredArgsConstructor
@Tag(name = "RBAC 管理", description = "用户分配角色与角色维护能力")
public class RbacManageController {

    private final UserService userService;
    private final SysRoleService sysRoleService;
    private final SysUserRoleService sysUserRoleService;

    @GetMapping("/users")
    @SaCheckPermission(PermissionConstants.Permission.ROLE_AUTH_READ)
    @Operation(summary = "查询用户列表（含角色）")
    public List<RbacUserDTO> listUsers() {
        List<User> users = userService.lambdaQuery()
                .orderByAsc(User::getId)
                .list();

        if (CollUtil.isEmpty(users)) {
            return Collections.emptyList();
        }

        List<Long> userIds = users.stream()
                .map(User::getId)
                .collect(Collectors.toList());

        List<SysUserRole> bindings = sysUserRoleService.lambdaQuery()
                .in(SysUserRole::getUserId, userIds)
                .list();

        Map<Long, List<Long>> userRoleMap = bindings.stream()
                .collect(Collectors.groupingBy(
                        SysUserRole::getUserId,
                        Collectors.mapping(SysUserRole::getRoleId, Collectors.toList())
                ));

        return users.stream().map(user -> {
            RbacUserDTO dto = new RbacUserDTO();
            dto.setId(user.getId());
            dto.setUsername(user.getUsername());
            dto.setNickname(user.getNickname());
            dto.setStatus(user.getStatus() == null ? null : user.getStatus().getCode());
            dto.setRoleIds(userRoleMap.getOrDefault(user.getId(), Collections.emptyList()));
            return dto;
        }).collect(Collectors.toList());
    }

    @GetMapping("/roles")
    @SaCheckPermission(PermissionConstants.Permission.ROLE_AUTH_READ)
    @Operation(summary = "查询角色列表")
    public List<RbacRoleDTO> listRoles(@RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) Integer status) {
        List<SysRole> roles = sysRoleService.lambdaQuery()
                .and(StrUtil.isNotBlank(keyword), q -> q.like(SysRole::getRoleName, keyword)
                        .or()
                        .like(SysRole::getRoleKey, keyword))
                .eq(status != null, SysRole::getStatus, status)
                .orderByAsc(SysRole::getRoleSort, SysRole::getRoleId)
                .list();
        return roles.stream()
                .map(this::toRoleDTO)
                .collect(Collectors.toList());
    }

    @PostMapping("/roles")
    @SaCheckPermission(value = {PermissionConstants.Permission.ROLE_CREATE, PermissionConstants.Permission.ROLE_COPY}, mode = SaMode.OR)
    @Operation(summary = "新增角色")
    public void createRole(@RequestBody CreateRoleParam param) {
        sysRoleService.createRole(param);
    }

    @PutMapping("/roles/{roleId}")
    @SaCheckPermission(PermissionConstants.Permission.ROLE_UPDATE)
    @Operation(summary = "更新角色")
    public void updateRole(@PathVariable Long roleId, @RequestBody UpdateRoleParam param) {
        sysRoleService.updateRole(roleId, param);
    }

    @DeleteMapping("/roles/{roleId}")
    @SaCheckPermission(PermissionConstants.Permission.ROLE_DELETE)
    @Operation(summary = "删除角色")
    public void deleteRole(@PathVariable Long roleId) {
        sysRoleService.deleteRole(roleId);
    }

    @PutMapping("/users/{userId}/roles")
    @SaCheckPermission(PermissionConstants.Permission.USER_ASSIGN)
    @Operation(summary = "用户分配角色（覆盖）")
    public void assignUserRoles(@PathVariable Long userId, @RequestBody AssignUserRolesParam param) {
        Assert.notNull(param, "请求参数不能为空");
        Assert.notNull(userService.getById(userId), "用户不存在");

        List<Long> roleIds = normalizeIds(param.getRoleIds());
        if (CollUtil.isNotEmpty(roleIds)) {
            Long validRoleCount = sysRoleService.lambdaQuery()
                    .in(SysRole::getRoleId, roleIds)
                    .eq(SysRole::getStatus, UserConstants.ROLE_STATUS_NORMAL)
                    .count();
            Assert.isTrue(Objects.equals(validRoleCount, (long) roleIds.size()), "存在无效或停用角色");
        }

        sysUserRoleService.replaceUserRoles(StpUtil.getLoginIdAsLong(), userId, roleIds);
    }

    @PostMapping("/users/roles/batch-append")
    @SaCheckPermission(PermissionConstants.Permission.USER_ASSIGN)
    @Operation(summary = "批量追加用户角色")
    public void batchAppendUserRoles(@RequestBody BatchAppendUserRolesParam param) {
        sysUserRoleService.batchAppendUserRoles(StpUtil.getLoginIdAsLong(), param);
    }

    private RbacRoleDTO toRoleDTO(SysRole role) {
        RbacRoleDTO dto = new RbacRoleDTO();
        dto.setRoleId(role.getRoleId());
        dto.setRoleName(role.getRoleName());
        dto.setRoleKey(role.getRoleKey());
        dto.setRoleSort(role.getRoleSort());
        dto.setStatus(role.getStatus());
        dto.setRemark(role.getRemark());
        return dto;
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }
}
