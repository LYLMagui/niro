package com.niro.web.controller;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.niro.core.exception.BusinessException;
import com.niro.web.dto.param.UpdateRoleParam;
import com.niro.web.entity.RoleResourceDraft;
import com.niro.web.entity.RoleResourcePublished;
import com.niro.web.entity.SysRole;
import com.niro.web.entity.SysUserRole;
import com.niro.web.manager.RoleResourceDraftMapperManager;
import com.niro.web.manager.RoleResourcePublishedMapperManager;
import com.niro.web.service.SysRoleService;
import com.niro.web.service.SysUserRoleService;
import com.niro.web.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RbacManageControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private SysRoleService sysRoleService;

    @Mock
    private SysUserRoleService sysUserRoleService;

    @Mock
    private RoleResourceDraftMapperManager roleResourceDraftMapperManager;

    @Mock
    private RoleResourcePublishedMapperManager roleResourcePublishedMapperManager;

    @Mock
    private LambdaQueryChainWrapper<SysUserRole> sysUserRoleQuery;

    @Mock
    private LambdaQueryChainWrapper<RoleResourceDraft> roleResourceDraftQuery;

    @Mock
    private LambdaQueryChainWrapper<RoleResourcePublished> roleResourcePublishedQuery;

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
    void deleteRole_shouldRejectWhenRoleBoundUsers() {
        SysRole role = buildOperatorRole();
        when(sysRoleService.getById(2L)).thenReturn(role);

        when(sysUserRoleService.lambdaQuery()).thenReturn(sysUserRoleQuery);
        when(sysUserRoleQuery.eq(any(), any())).thenReturn(sysUserRoleQuery);
        when(sysUserRoleQuery.count()).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.deleteRole(2L));
        assertEquals("角色已绑定用户，请先解绑后再删除", ex.getMessage());
    }

    @Test
    void deleteRole_shouldRejectWhenRoleBoundNewPermissionResources() {
        SysRole role = buildOperatorRole();
        when(sysRoleService.getById(2L)).thenReturn(role);

        when(sysUserRoleService.lambdaQuery()).thenReturn(sysUserRoleQuery);
        when(sysUserRoleQuery.eq(any(), any())).thenReturn(sysUserRoleQuery);
        when(sysUserRoleQuery.count()).thenReturn(0L);
        when(roleResourceDraftMapperManager.lambdaQuery()).thenReturn(roleResourceDraftQuery);
        when(roleResourceDraftQuery.eq(any(), any())).thenReturn(roleResourceDraftQuery);
        when(roleResourceDraftQuery.count()).thenReturn(1L);
        when(roleResourcePublishedMapperManager.lambdaQuery()).thenReturn(roleResourcePublishedQuery);
        when(roleResourcePublishedQuery.eq(any(), any())).thenReturn(roleResourcePublishedQuery);
        when(roleResourcePublishedQuery.count()).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.deleteRole(2L));
        assertEquals("角色已绑定权限资源，请先解绑后再删除", ex.getMessage());
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

    private SysRole buildOperatorRole() {
        SysRole role = new SysRole();
        role.setRoleId(2L);
        role.setRoleName("运营");
        role.setRoleKey("operator");
        role.setRoleSort(2);
        role.setStatus(1);
        return role;
    }
}
