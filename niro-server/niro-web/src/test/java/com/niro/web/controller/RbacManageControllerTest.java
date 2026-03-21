package com.niro.web.controller;

import com.niro.core.exception.BusinessException;
import com.niro.web.dto.param.AssignRoleMenusParam;
import com.niro.web.dto.param.CreateMenuParam;
import com.niro.web.dto.param.UpdateRoleParam;
import com.niro.web.entity.SysMenu;
import com.niro.web.entity.SysRole;
import com.niro.web.entity.SysRoleMenu;
import com.niro.web.entity.SysUserRole;
import com.niro.web.service.SysMenuService;
import com.niro.web.service.SysRoleMenuService;
import com.niro.web.service.SysRoleService;
import com.niro.web.service.SysUserRoleService;
import com.niro.web.service.UserService;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RbacManageControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private SysRoleService sysRoleService;

    @Mock
    private SysMenuService sysMenuService;

    @Mock
    private SysUserRoleService sysUserRoleService;

    @Mock
    private SysRoleMenuService sysRoleMenuService;

    @Mock
    private LambdaQueryChainWrapper<SysUserRole> sysUserRoleQuery;

    @Mock
    private LambdaQueryChainWrapper<SysMenu> sysMenuQuery;

    @InjectMocks
    private RbacManageController controller;

    @Test
    void updateRole_shouldRejectAdminRoleKeyChange() {
        SysRole adminRole = buildAdminRole();
        when(sysRoleService.getById(1L)).thenReturn(adminRole);

        UpdateRoleParam param = new UpdateRoleParam();
        param.setRoleName("管理员");
        param.setRoleKey("admin_new");
        param.setRoleSort(1);
        param.setStatus(1);

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.updateRole(1L, param));
        assertEquals("内置角色admin不可修改角色编码", ex.getMessage());
    }

    @Test
    void updateRole_shouldRejectAdminDisable() {
        SysRole adminRole = buildAdminRole();
        when(sysRoleService.getById(1L)).thenReturn(adminRole);

        UpdateRoleParam param = new UpdateRoleParam();
        param.setRoleName("管理员");
        param.setRoleKey("admin");
        param.setRoleSort(1);
        param.setStatus(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.updateRole(1L, param));
        assertEquals("内置角色admin不可停用", ex.getMessage());
    }

    @Test
    void deleteRole_shouldRejectAdminDelete() {
        when(sysRoleService.getById(1L)).thenReturn(buildAdminRole());

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.deleteRole(1L));
        assertEquals("内置角色admin不可删除", ex.getMessage());
    }

    @Test
    void assignRoleMenus_shouldRejectDisabledRole() {
        SysRole disabledRole = new SysRole();
        disabledRole.setRoleId(2L);
        disabledRole.setRoleKey("operator");
        disabledRole.setStatus(0);
        when(sysRoleService.getById(2L)).thenReturn(disabledRole);

        AssignRoleMenusParam param = new AssignRoleMenusParam();
        param.setMenuIds(Collections.singletonList(10L));

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.assignRoleMenus(2L, param));
        assertEquals("停用角色不允许分配菜单", ex.getMessage());
    }

    @Test
    void createMenu_shouldRejectInvalidPermissionFormat() {
        CreateMenuParam param = new CreateMenuParam();
        param.setParentId(0L);
        param.setTitle("权限管理");
        param.setType(1);
        param.setStatus(1);
        param.setPermission("System:Permission:Manage");

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.createMenu(param));
        assertEquals("非Buff权限码必须是三段式小写格式，例如 system:user:list", ex.getMessage());
    }

    @Test
    void createMenu_shouldRejectButtonWithoutParentMenu() {
        CreateMenuParam param = new CreateMenuParam();
        param.setParentId(0L);
        param.setTitle("新增角色按钮");
        param.setType(2);
        param.setStatus(1);
        param.setPermission("system:permission:add-role");

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.createMenu(param));
        assertEquals("按钮必须挂在菜单节点下", ex.getMessage());
    }

    @Test
    void createMenu_shouldRejectDuplicatePermission() {
        when(sysMenuService.lambdaQuery()).thenReturn(sysMenuQuery);
        when(sysMenuQuery.eq(any(), any())).thenReturn(sysMenuQuery);
        when(sysMenuQuery.ne(anyBoolean(), any(), any())).thenReturn(sysMenuQuery);
        when(sysMenuQuery.count()).thenReturn(1L);

        CreateMenuParam param = new CreateMenuParam();
        param.setParentId(0L);
        param.setTitle("权限管理");
        param.setType(1);
        param.setStatus(1);
        param.setPermission("system:permission:manage");

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.createMenu(param));
        assertEquals("权限码已存在", ex.getMessage());
    }

    @Test
    void deleteRole_shouldRejectWhenRoleBoundUsers() {
        SysRole role = new SysRole();
        role.setRoleId(2L);
        role.setRoleKey("operator");
        when(sysRoleService.getById(2L)).thenReturn(role);

        when(sysUserRoleService.lambdaQuery()).thenReturn(sysUserRoleQuery);
        when(sysUserRoleQuery.eq(any(), any())).thenReturn(sysUserRoleQuery);
        when(sysUserRoleQuery.count()).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.deleteRole(2L));
        assertEquals("角色已绑定用户，请先解绑后再删除", ex.getMessage());
    }

    private SysRole buildAdminRole() {
        SysRole role = new SysRole();
        role.setRoleId(1L);
        role.setRoleName("管理员");
        role.setRoleKey("admin");
        role.setRoleSort(1);
        role.setStatus(1);
        return role;
    }
}
