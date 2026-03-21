package com.niro.web.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.niro.core.util.Assert;
import com.niro.web.constant.UserConstants;
import com.niro.web.dto.param.AssignRoleMenusParam;
import com.niro.web.dto.param.AssignUserRolesParam;
import com.niro.web.dto.param.BatchAppendUserRolesParam;
import com.niro.web.dto.param.CreateMenuParam;
import com.niro.web.dto.param.CreateRoleParam;
import com.niro.web.dto.param.UpdateMenuParam;
import com.niro.web.dto.param.UpdateRoleParam;
import com.niro.web.dto.rbac.RbacMenuDTO;
import com.niro.web.dto.rbac.RbacRoleDTO;
import com.niro.web.dto.rbac.RbacUserDTO;
import com.niro.web.entity.SysMenu;
import com.niro.web.entity.SysRole;
import com.niro.web.entity.SysRoleMenu;
import com.niro.web.entity.SysUserRole;
import com.niro.web.entity.User;
import com.niro.web.enums.MenuTypeEnum;
import com.niro.web.service.SysMenuService;
import com.niro.web.service.SysRoleMenuService;
import com.niro.web.service.SysRoleService;
import com.niro.web.service.SysUserRoleService;
import com.niro.web.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RBAC 管理接口
 */
@RestController
@RequestMapping("/api/rbac")
@SaCheckRole("admin")
@RequiredArgsConstructor
@Tag(name = "RBAC 管理", description = "用户分配角色、角色分配菜单以及角色/菜单维护能力")
public class RbacManageController {

    private static final Pattern NON_BUFF_PERMISSION_PATTERN =
            Pattern.compile("^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$");

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
    @Transactional(rollbackFor = Exception.class)
    @Operation(summary = "删除角色")
    public void deleteRole(@PathVariable Long roleId) {
        SysRole role = requireRole(roleId);
        Assert.isFalse(isAdminRole(role), "内置角色admin不可删除");

        Long userBindingCount = sysUserRoleService.lambdaQuery()
                .eq(SysUserRole::getRoleId, roleId)
                .count();
        Assert.isTrue(userBindingCount == 0, "角色已绑定用户，请先解绑后再删除");

        Long menuBindingCount = sysRoleMenuService.lambdaQuery()
                .eq(SysRoleMenu::getRoleId, roleId)
                .count();
        Assert.isTrue(menuBindingCount == 0, "角色已绑定菜单，请先解绑后再删除");

        Assert.isTrue(sysRoleService.removeById(roleId), "删除角色失败");
    }

    @GetMapping("/menus")
    @Operation(summary = "查询菜单列表")
    public List<RbacMenuDTO> listMenus(@RequestParam(required = false) Integer type,
                                       @RequestParam(required = false) Integer status) {
        if (type != null) {
            Assert.isTrue(isValidMenuType(type), "菜单类型非法");
        }
        if (status != null) {
            Assert.isTrue(isValidStatus(status), "菜单状态非法");
        }

        List<SysMenu> menus = sysMenuService.lambdaQuery()
                .eq(type != null, SysMenu::getType, type)
                .eq(status != null, SysMenu::getStatus, status)
                .orderByAsc(SysMenu::getSortOrder, SysMenu::getId)
                .list();
        return menus.stream()
                .map(this::toMenuDTO)
                .collect(Collectors.toList());
    }

    @PostMapping("/menus")
    @Transactional(rollbackFor = Exception.class)
    @Operation(summary = "新增菜单")
    public void createMenu(@RequestBody CreateMenuParam param) {
        Assert.notNull(param, "请求参数不能为空");

        MenuInput menuInput = validateMenuInput(
                null,
                param.getParentId(),
                param.getTitle(),
                param.getName(),
                param.getPath(),
                param.getComponent(),
                param.getIcon(),
                param.getSortOrder(),
                param.getType(),
                param.getPermission(),
                param.getStatus(),
                param.getHidden(),
                param.getKeepAlive(),
                param.getRedirect()
        );

        SysMenu menu = new SysMenu();
        menu.setParentId(menuInput.parentId());
        menu.setTitle(menuInput.title());
        menu.setName(menuInput.name());
        menu.setPath(menuInput.path());
        menu.setComponent(menuInput.component());
        menu.setIcon(menuInput.icon());
        menu.setSortOrder(menuInput.sortOrder());
        menu.setType(menuInput.type());
        menu.setPermission(menuInput.permission());
        menu.setStatus(menuInput.status());
        menu.setHidden(menuInput.hidden());
        menu.setKeepAlive(menuInput.keepAlive());
        menu.setRedirect(menuInput.redirect());
        Assert.isTrue(sysMenuService.save(menu), "新增菜单失败");
    }

    @PutMapping("/menus/{id}")
    @Transactional(rollbackFor = Exception.class)
    @Operation(summary = "更新菜单")
    public void updateMenu(@PathVariable Long id, @RequestBody UpdateMenuParam param) {
        Assert.notNull(param, "请求参数不能为空");
        SysMenu menu = requireMenu(id);

        MenuInput menuInput = validateMenuInput(
                id,
                param.getParentId(),
                param.getTitle(),
                param.getName(),
                param.getPath(),
                param.getComponent(),
                param.getIcon(),
                param.getSortOrder(),
                param.getType(),
                param.getPermission(),
                param.getStatus(),
                param.getHidden(),
                param.getKeepAlive(),
                param.getRedirect()
        );

        menu.setParentId(menuInput.parentId());
        menu.setTitle(menuInput.title());
        menu.setName(menuInput.name());
        menu.setPath(menuInput.path());
        menu.setComponent(menuInput.component());
        menu.setIcon(menuInput.icon());
        menu.setSortOrder(menuInput.sortOrder());
        menu.setType(menuInput.type());
        menu.setPermission(menuInput.permission());
        menu.setStatus(menuInput.status());
        menu.setHidden(menuInput.hidden());
        menu.setKeepAlive(menuInput.keepAlive());
        menu.setRedirect(menuInput.redirect());
        Assert.isTrue(sysMenuService.updateById(menu), "更新菜单失败");
    }

    @DeleteMapping("/menus/{id}")
    @Transactional(rollbackFor = Exception.class)
    @Operation(summary = "删除菜单")
    public void deleteMenu(@PathVariable Long id) {
        requireMenu(id);

        Long childCount = sysMenuService.lambdaQuery()
                .eq(SysMenu::getParentId, id)
                .count();
        Assert.isTrue(childCount == 0, "菜单存在子节点，请先删除子节点");

        Long roleBindingCount = sysRoleMenuService.lambdaQuery()
                .eq(SysRoleMenu::getMenuId, id)
                .count();
        Assert.isTrue(roleBindingCount == 0, "菜单已被角色绑定，请先解绑后再删除");

        Assert.isTrue(sysMenuService.removeById(id), "删除菜单失败");
    }

    @GetMapping("/roles/{roleId}/menus")
    @Operation(summary = "查询角色已授权菜单ID")
    public List<Long> getRoleMenuIds(@PathVariable Long roleId) {
        requireRole(roleId);
        return sysRoleMenuService.listMenuIdsByRoleId(roleId);
    }

    @PutMapping("/users/{userId}/roles")
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

    @PutMapping("/roles/{roleId}/menus")
    @Operation(summary = "角色分配菜单（覆盖）")
    public void assignRoleMenus(@PathVariable Long roleId, @RequestBody AssignRoleMenusParam param) {
        Assert.notNull(param, "请求参数不能为空");
        SysRole role = requireRole(roleId);
        Assert.isTrue(UserConstants.ROLE_STATUS_NORMAL.equals(role.getStatus()), "停用角色不允许分配菜单");

        List<Long> menuIds = normalizeIds(param.getMenuIds());
        if (CollUtil.isNotEmpty(menuIds)) {
            Long validMenuCount = sysMenuService.lambdaQuery()
                    .in(SysMenu::getId, menuIds)
                    .eq(SysMenu::getStatus, UserConstants.MENU_STATUS_NORMAL)
                    .count();
            Assert.isTrue(Objects.equals(validMenuCount, (long) menuIds.size()), "存在无效或停用菜单");
            menuIds = appendAncestorMenuIds(menuIds);
        }

        sysRoleMenuService.replaceRoleMenus(roleId, menuIds);
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

    private MenuInput validateMenuInput(Long currentMenuId,
                                        Long parentId,
                                        String title,
                                        String name,
                                        String path,
                                        String component,
                                        String icon,
                                        Integer sortOrder,
                                        Integer type,
                                        String permission,
                                        Integer status,
                                        Boolean hidden,
                                        Boolean keepAlive,
                                        String redirect) {
        String normalizedTitle = normalizeRequired(title, "菜单标题不能为空");
        Assert.notNull(type, "菜单类型不能为空");
        Assert.notNull(status, "菜单状态不能为空");
        Assert.isTrue(isValidMenuType(type), "菜单类型非法");
        Assert.isTrue(isValidStatus(status), "菜单状态非法");

        Long normalizedParentId = parentId == null ? 0L : parentId;
        Assert.isTrue(normalizedParentId >= 0, "父菜单ID非法");
        if (currentMenuId != null) {
            Assert.isFalse(currentMenuId.equals(normalizedParentId), "上级菜单不能选择自己");
        }

        SysMenu parentMenu = null;
        if (normalizedParentId > 0) {
            parentMenu = sysMenuService.getById(normalizedParentId);
            Assert.notNull(parentMenu, "上级菜单不存在");
            Assert.isTrue(UserConstants.MENU_STATUS_NORMAL.equals(parentMenu.getStatus()), "上级菜单已停用");
        }

        if (MenuTypeEnum.BUTTON.getCode().equals(type)) {
            Assert.isTrue(normalizedParentId > 0, "按钮必须挂在菜单节点下");
            Assert.notNull(parentMenu, "按钮上级菜单不存在");
            Assert.isTrue(MenuTypeEnum.MENU.getCode().equals(parentMenu.getType()), "按钮上级必须是菜单");
        }

        if (currentMenuId != null && normalizedParentId > 0) {
            assertMenuParentNotDescendant(currentMenuId, normalizedParentId);
        }

        String normalizedPermission = normalizeNullable(permission);
        if (!MenuTypeEnum.DIR.getCode().equals(type)) {
            Assert.notBlank(normalizedPermission, "菜单/按钮权限码不能为空");
        }
        validatePermission(normalizedPermission, currentMenuId);

        Integer normalizedSortOrder = sortOrder == null ? 0 : sortOrder;
        Boolean normalizedHidden = hidden == null ? Boolean.FALSE : hidden;
        Boolean normalizedKeepAlive = keepAlive == null ? Boolean.FALSE : keepAlive;
        return new MenuInput(
                normalizedParentId,
                normalizedTitle,
                normalizeNullable(name),
                normalizeNullable(path),
                normalizeNullable(component),
                normalizeNullable(icon),
                normalizedSortOrder,
                type,
                normalizedPermission,
                status,
                normalizedHidden,
                normalizedKeepAlive,
                normalizeNullable(redirect)
        );
    }

    private void validatePermission(String permission, Long excludeMenuId) {
        if (StrUtil.isBlank(permission)) {
            return;
        }

        if (!isBuffPermission(permission)) {
            Assert.isTrue(
                    NON_BUFF_PERMISSION_PATTERN.matcher(permission).matches(),
                    "非Buff权限码必须是三段式小写格式，例如 system:user:list"
            );
        }

        Long duplicateCount = sysMenuService.lambdaQuery()
                .eq(SysMenu::getPermission, permission)
                .ne(excludeMenuId != null, SysMenu::getId, excludeMenuId)
                .count();
        Assert.isTrue(duplicateCount == 0, "权限码已存在");
    }

    private void assertMenuParentNotDescendant(Long currentMenuId, Long parentId) {
        Long currentParentId = parentId;
        Set<Long> visited = new HashSet<>();
        while (currentParentId != null && currentParentId > 0) {
            Assert.isTrue(visited.add(currentParentId), "菜单层级存在循环，请检查父子关系");
            Assert.isFalse(currentMenuId.equals(currentParentId), "上级菜单不能选择自己的子节点");
            SysMenu menu = sysMenuService.getById(currentParentId);
            if (menu == null) {
                break;
            }
            currentParentId = menu.getParentId();
        }
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

    private RbacMenuDTO toMenuDTO(SysMenu menu) {
        RbacMenuDTO dto = new RbacMenuDTO();
        dto.setId(menu.getId());
        dto.setParentId(menu.getParentId());
        dto.setTitle(menu.getTitle());
        dto.setName(menu.getName());
        dto.setPath(menu.getPath());
        dto.setComponent(menu.getComponent());
        dto.setIcon(menu.getIcon());
        dto.setSortOrder(menu.getSortOrder());
        dto.setType(menu.getType());
        dto.setPermission(menu.getPermission());
        dto.setStatus(menu.getStatus());
        dto.setHidden(menu.getHidden());
        dto.setKeepAlive(menu.getKeepAlive());
        dto.setRedirect(menu.getRedirect());
        return dto;
    }

    private SysRole requireRole(Long roleId) {
        SysRole role = sysRoleService.getById(roleId);
        Assert.notNull(role, "角色不存在");
        return role;
    }

    private SysMenu requireMenu(Long menuId) {
        SysMenu menu = sysMenuService.getById(menuId);
        Assert.notNull(menu, "菜单不存在");
        return menu;
    }

    private boolean isAdminRole(SysRole role) {
        return role != null && StrUtil.equalsIgnoreCase(UserConstants.ADMIN_ROLE_KEY, role.getRoleKey());
    }

    private boolean isBuffPermission(String permission) {
        return StrUtil.startWithIgnoreCase(permission, "buff:");
    }

    private boolean isValidStatus(Integer status) {
        return UserConstants.ROLE_STATUS_NORMAL.equals(status)
                || UserConstants.ROLE_STATUS_DISABLED.equals(status);
    }

    private boolean isValidMenuType(Integer type) {
        return MenuTypeEnum.DIR.getCode().equals(type)
                || MenuTypeEnum.MENU.getCode().equals(type)
                || MenuTypeEnum.BUTTON.getCode().equals(type);
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

    private List<Long> appendAncestorMenuIds(List<Long> menuIds) {
        if (CollUtil.isEmpty(menuIds)) {
            return Collections.emptyList();
        }

        List<SysMenu> activeMenus = sysMenuService.lambdaQuery()
                .eq(SysMenu::getStatus, UserConstants.MENU_STATUS_NORMAL)
                .list();
        if (CollUtil.isEmpty(activeMenus)) {
            return menuIds;
        }

        Map<Long, SysMenu> menuMap = activeMenus.stream()
                .collect(Collectors.toMap(SysMenu::getId, menu -> menu, (a, b) -> a));

        LinkedHashSet<Long> allMenuIds = new LinkedHashSet<>(menuIds);
        for (Long menuId : menuIds) {
            SysMenu current = menuMap.get(menuId);
            while (current != null && current.getParentId() != null && current.getParentId() > 0) {
                Long parentMenuId = current.getParentId();
                if (!allMenuIds.add(parentMenuId)) {
                    break;
                }
                current = menuMap.get(parentMenuId);
            }
        }
        return new ArrayList<>(allMenuIds);
    }

    private record RoleInput(String roleName,
                             String roleKey,
                             Integer roleSort,
                             Integer status,
                             String remark) {
    }

    private record MenuInput(Long parentId,
                             String title,
                             String name,
                             String path,
                             String component,
                             String icon,
                             Integer sortOrder,
                             Integer type,
                             String permission,
                             Integer status,
                             Boolean hidden,
                             Boolean keepAlive,
                             String redirect) {
    }
}
