package com.niro.web.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
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
import com.niro.web.entity.RoleResourceDraft;
import com.niro.web.entity.RoleResourcePublished;
import com.niro.web.entity.SysRole;
import com.niro.web.entity.SysUserRole;
import com.niro.web.entity.User;
import com.niro.web.manager.RoleResourceDraftMapperManager;
import com.niro.web.manager.RoleResourcePublishedMapperManager;
import com.niro.web.service.SysRoleService;
import com.niro.web.service.SysUserRoleService;
import com.niro.web.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private final RoleResourceDraftMapperManager roleResourceDraftMapperManager;
    private final RoleResourcePublishedMapperManager roleResourcePublishedMapperManager;

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
    @Transactional(rollbackFor = Exception.class)
    @Operation(summary = "新增角色")
    public void createRole(@RequestBody CreateRoleParam param) {
        Assert.notNull(param, "请求参数不能为空");

        RoleInput roleInput = validateRoleInput(
                param.getRoleName(),
                param.getRoleKey(),
                param.getRoleSort(),
                param.getStatus(),
                param.getRemark()
        );
        assertRoleKeyUnique(roleInput.roleKey(), null);

        SysRole role = new SysRole();
        role.setRoleName(roleInput.roleName());
        role.setRoleKey(roleInput.roleKey());
        role.setRoleSort(roleInput.roleSort());
        role.setStatus(roleInput.status());
        role.setRemark(roleInput.remark());
        role.setDataScope(UserConstants.DEFAULT_DATA_SCOPE);
        Assert.isTrue(sysRoleService.save(role), "新增角色失败");
    }

    @PutMapping("/roles/{roleId}")
    @SaCheckPermission(PermissionConstants.Permission.ROLE_UPDATE)
    @Transactional(rollbackFor = Exception.class)
    @Operation(summary = "更新角色")
    public void updateRole(@PathVariable Long roleId, @RequestBody UpdateRoleParam param) {
        Assert.notNull(param, "请求参数不能为空");

        SysRole role = requireRole(roleId);
        RoleInput roleInput = validateRoleInput(
                param.getRoleName(),
                param.getRoleKey(),
                param.getRoleSort(),
                param.getStatus(),
                param.getRemark()
        );

        if (isAdminRole(role)) {
            Assert.isTrue(StrUtil.equals(role.getRoleKey(), roleInput.roleKey()), "内置角色admin不可修改角色编码");
            Assert.isTrue(UserConstants.ROLE_STATUS_NORMAL.equals(roleInput.status()), "内置角色admin不可停用");
        }

        assertRoleKeyUnique(roleInput.roleKey(), roleId);

        role.setRoleName(roleInput.roleName());
        role.setRoleKey(roleInput.roleKey());
        role.setRoleSort(roleInput.roleSort());
        role.setStatus(roleInput.status());
        role.setRemark(roleInput.remark());
        Assert.isTrue(sysRoleService.updateById(role), "更新角色失败");
    }

    @DeleteMapping("/roles/{roleId}")
    @SaCheckPermission(PermissionConstants.Permission.ROLE_DELETE)
    @Transactional(rollbackFor = Exception.class)
    @Operation(summary = "删除角色")
    public void deleteRole(@PathVariable Long roleId) {
        SysRole role = requireRole(roleId);
        Assert.isFalse(isAdminRole(role), "内置角色admin不可删除");

        Long userBindingCount = sysUserRoleService.lambdaQuery()
                .eq(SysUserRole::getRoleId, roleId)
                .count();
        Assert.isTrue(userBindingCount == 0, "角色已绑定用户，请先解绑后再删除");

        Long draftBindingCount = roleResourceDraftMapperManager.lambdaQuery()
                .eq(RoleResourceDraft::getRoleId, roleId)
                .eq(RoleResourceDraft::getDelFlag, 0)
                .count();
        Long publishedBindingCount = roleResourcePublishedMapperManager.lambdaQuery()
                .eq(RoleResourcePublished::getRoleId, roleId)
                .eq(RoleResourcePublished::getDelFlag, 0)
                .count();
        Assert.isTrue(draftBindingCount == 0 && publishedBindingCount == 0, "角色已绑定权限资源，请先解绑后再删除");

        Assert.isTrue(sysRoleService.removeById(roleId), "删除角色失败");
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

        sysUserRoleService.replaceUserRoles(userId, roleIds);
    }

    @PostMapping("/users/roles/batch-append")
    @SaCheckPermission(PermissionConstants.Permission.USER_ASSIGN)
    @Transactional(rollbackFor = Exception.class)
    @Operation(summary = "批量追加用户角色")
    public void batchAppendUserRoles(@RequestBody BatchAppendUserRolesParam param) {
        Assert.notNull(param, "请求参数不能为空");

        List<Long> userIds = normalizeIds(param.getUserIds());
        List<Long> roleIds = normalizeIds(param.getRoleIds());
        Assert.notEmpty(userIds, "用户ID列表不能为空");
        Assert.notEmpty(roleIds, "角色ID列表不能为空");

        Long validUserCount = userService.lambdaQuery()
                .in(User::getId, userIds)
                .count();
        Assert.isTrue(Objects.equals(validUserCount, (long) userIds.size()), "存在无效用户");

        Long validRoleCount = sysRoleService.lambdaQuery()
                .in(SysRole::getRoleId, roleIds)
                .eq(SysRole::getStatus, UserConstants.ROLE_STATUS_NORMAL)
                .count();
        Assert.isTrue(Objects.equals(validRoleCount, (long) roleIds.size()), "存在无效或停用角色");

        List<SysUserRole> bindings = sysUserRoleService.lambdaQuery()
                .in(SysUserRole::getUserId, userIds)
                .list();

        Map<Long, List<Long>> userRoleMap = bindings.stream()
                .collect(Collectors.groupingBy(
                        SysUserRole::getUserId,
                        Collectors.mapping(SysUserRole::getRoleId, Collectors.toList())
                ));

        for (Long userId : userIds) {
            LinkedHashSet<Long> mergedRoleIds = new LinkedHashSet<>(
                    userRoleMap.getOrDefault(userId, Collections.emptyList())
            );
            mergedRoleIds.addAll(roleIds);
            sysUserRoleService.replaceUserRoles(userId, new ArrayList<>(mergedRoleIds));
        }
    }

    private RoleInput validateRoleInput(String roleName,
                                        String roleKey,
                                        Integer roleSort,
                                        Integer status,
                                        String remark) {
        String normalizedRoleName = normalizeRequired(roleName, "角色名称不能为空");
        String normalizedRoleKey = normalizeRequired(roleKey, "角色编码不能为空").toLowerCase(Locale.ROOT);
        Assert.notNull(roleSort, "角色排序不能为空");
        Assert.notNull(status, "角色状态不能为空");
        Assert.isTrue(isValidStatus(status), "角色状态非法");
        return new RoleInput(normalizedRoleName, normalizedRoleKey, roleSort, status, normalizeNullable(remark));
    }

    private void assertRoleKeyUnique(String roleKey, Long excludeRoleId) {
        Long count = sysRoleService.lambdaQuery()
                .eq(SysRole::getRoleKey, roleKey)
                .ne(excludeRoleId != null, SysRole::getRoleId, excludeRoleId)
                .count();
        Assert.isTrue(count == 0, "角色编码已存在");
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

    private SysRole requireRole(Long roleId) {
        SysRole role = sysRoleService.getById(roleId);
        Assert.notNull(role, "角色不存在");
        return role;
    }

    private boolean isAdminRole(SysRole role) {
        return role != null && StrUtil.equalsIgnoreCase(UserConstants.ADMIN_ROLE_KEY, role.getRoleKey());
    }

    private boolean isValidStatus(Integer status) {
        return UserConstants.ROLE_STATUS_NORMAL.equals(status)
                || UserConstants.ROLE_STATUS_DISABLED.equals(status);
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        Assert.notBlank(normalized, message);
        return normalized;
    }

    private String normalizeNullable(String value) {
        return StrUtil.emptyToNull(StrUtil.trim(value));
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

    private record RoleInput(String roleName,
                             String roleKey,
                             Integer roleSort,
                             Integer status,
                             String remark) {
    }
}
