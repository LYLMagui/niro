package com.niro.web.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.hutool.core.collection.CollUtil;
import com.niro.core.util.Assert;
import com.niro.web.constant.UserConstants;
import com.niro.web.dto.param.AssignRoleMenusParam;
import com.niro.web.dto.param.AssignUserRolesParam;
import com.niro.web.dto.rbac.RbacMenuDTO;
import com.niro.web.dto.rbac.RbacRoleDTO;
import com.niro.web.dto.rbac.RbacUserDTO;
import com.niro.web.entity.SysMenu;
import com.niro.web.entity.SysRole;
import com.niro.web.entity.SysUserRole;
import com.niro.web.entity.User;
import com.niro.web.service.SysMenuService;
import com.niro.web.service.SysRoleMenuService;
import com.niro.web.service.SysRoleService;
import com.niro.web.service.SysUserRoleService;
import com.niro.web.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RBAC 最小管理接口
 */
@RestController
@RequestMapping("/api/rbac")
@SaCheckRole("admin")
@RequiredArgsConstructor
@Tag(name = "RBAC 管理", description = "最小权限管理能力（用户分配角色 + 角色分配菜单）")
public class RbacManageController {

    private final UserService userService;
    private final SysRoleService sysRoleService;
    private final SysMenuService sysMenuService;
    private final SysUserRoleService sysUserRoleService;
    private final SysRoleMenuService sysRoleMenuService;

    @GetMapping("/users")
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
    @Operation(summary = "查询角色列表")
    public List<RbacRoleDTO> listRoles() {
        List<SysRole> roles = sysRoleService.lambdaQuery()
                .orderByAsc(SysRole::getRoleSort, SysRole::getRoleId)
                .list();
        return roles.stream().map(role -> {
            RbacRoleDTO dto = new RbacRoleDTO();
            dto.setRoleId(role.getRoleId());
            dto.setRoleName(role.getRoleName());
            dto.setRoleKey(role.getRoleKey());
            dto.setStatus(role.getStatus());
            return dto;
        }).collect(Collectors.toList());
    }

    @GetMapping("/menus")
    @Operation(summary = "查询菜单列表")
    public List<RbacMenuDTO> listMenus() {
        List<SysMenu> menus = sysMenuService.lambdaQuery()
                .orderByAsc(SysMenu::getSortOrder, SysMenu::getId)
                .list();
        return menus.stream().map(menu -> {
            RbacMenuDTO dto = new RbacMenuDTO();
            dto.setId(menu.getId());
            dto.setParentId(menu.getParentId());
            dto.setTitle(menu.getTitle());
            dto.setType(menu.getType());
            dto.setPermission(menu.getPermission());
            return dto;
        }).collect(Collectors.toList());
    }

    @GetMapping("/roles/{roleId}/menus")
    @Operation(summary = "查询角色已授权菜单ID")
    public List<Long> getRoleMenuIds(@PathVariable Long roleId) {
        Assert.notNull(sysRoleService.getById(roleId), "角色不存在");
        return sysRoleMenuService.listMenuIdsByRoleId(roleId);
    }

    @PutMapping("/users/{userId}/roles")
    @Operation(summary = "用户分配角色")
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

    @PutMapping("/roles/{roleId}/menus")
    @Operation(summary = "角色分配菜单")
    public void assignRoleMenus(@PathVariable Long roleId, @RequestBody AssignRoleMenusParam param) {
        Assert.notNull(param, "请求参数不能为空");
        Assert.notNull(sysRoleService.getById(roleId), "角色不存在");

        List<Long> menuIds = normalizeIds(param.getMenuIds());
        if (CollUtil.isNotEmpty(menuIds)) {
            Long validMenuCount = sysMenuService.lambdaQuery()
                    .in(SysMenu::getId, menuIds)
                    .count();
            Assert.isTrue(Objects.equals(validMenuCount, (long) menuIds.size()), "存在无效菜单");
        }

        sysRoleMenuService.replaceRoleMenus(roleId, menuIds);
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
