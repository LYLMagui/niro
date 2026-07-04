package com.niro.web.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.core.util.Assert;
import com.niro.web.constant.UserConstants;
import com.niro.web.dto.param.CreateRoleParam;
import com.niro.web.dto.param.UpdateRoleParam;
import com.niro.web.entity.SysRole;
import com.niro.web.manager.RoleResourceDraftMapperManager;
import com.niro.web.manager.RoleResourcePublishedMapperManager;
import com.niro.web.manager.SysRoleMapperManager;
import com.niro.web.manager.SysUserRoleMapperManager;
import com.niro.web.mapper.SysRoleMapper;
import com.niro.web.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 角色业务层实现
 */
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final SysRoleMapperManager sysRoleMapperManager;
    private final SysUserRoleMapperManager sysUserRoleMapperManager;
    private final RoleResourceDraftMapperManager roleResourceDraftMapperManager;
    private final RoleResourcePublishedMapperManager roleResourcePublishedMapperManager;

    @Override
    public Set<String> selectRolePermissionByUserId(Long userId) {
        Set<String> roles = new HashSet<>();
        if (UserConstants.ADMIN_ID.equals(userId)) {
            roles.add(UserConstants.ADMIN_ROLE_KEY);
            return roles;
        }

        List<Long> roleIds = sysUserRoleMapperManager.listRoleIdsByUserId(userId);
        if (CollUtil.isEmpty(roleIds)) {
            return roles;
        }

        List<SysRole> sysRoles = sysRoleMapperManager.listNormalByRoleIds(roleIds);
        if (CollUtil.isEmpty(sysRoles)) {
            return roles;
        }

        sysRoles.stream()
                .map(SysRole::getRoleKey)
                .filter(StrUtil::isNotBlank)
                .forEach(roles::add);
        return roles;
    }

    /**
     * 新增角色。
     *
     * @param param 角色参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createRole(CreateRoleParam param) {
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
        Assert.isTrue(sysRoleMapperManager.saveRole(role), "新增角色失败");
    }

    /**
     * 更新角色。
     *
     * @param roleId 角色ID
     * @param param 角色参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long roleId, UpdateRoleParam param) {
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
        Assert.isTrue(sysRoleMapperManager.updateRole(role), "更新角色失败");
    }

    /**
     * 删除角色。
     *
     * @param roleId 角色ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long roleId) {
        SysRole role = requireRole(roleId);
        Assert.isFalse(isAdminRole(role), "内置角色admin不可删除");
        Assert.isFalse(sysUserRoleMapperManager.existsByRoleId(roleId), "角色已绑定用户，请先解绑后再删除");
        Assert.isFalse(roleResourceDraftMapperManager.existsByRoleId(roleId)
                || roleResourcePublishedMapperManager.existsByRoleId(roleId), "角色已绑定权限资源，请先解绑后再删除");
        Assert.isTrue(sysRoleMapperManager.removeRole(roleId), "删除角色失败");
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
        Assert.isFalse(sysRoleMapperManager.existsByRoleKey(roleKey, excludeRoleId), "角色编码已存在");
    }

    private SysRole requireRole(Long roleId) {
        SysRole role = sysRoleMapperManager.getByRoleId(roleId);
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

    private record RoleInput(String roleName,
                             String roleKey,
                             Integer roleSort,
                             Integer status,
                             String remark) {
    }
}
