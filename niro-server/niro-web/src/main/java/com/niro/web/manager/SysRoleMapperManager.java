package com.niro.web.manager;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.constant.UserConstants;
import com.niro.web.entity.SysRole;
import com.niro.web.mapper.SysRoleMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 角色管理器
 */
@Service
public class SysRoleMapperManager extends ServiceImpl<SysRoleMapper, SysRole> {

    public List<SysRole> listNormalByRoleIds(List<Long> roleIds) {
        return this.lambdaQuery()
                .in(SysRole::getRoleId, roleIds)
                .eq(SysRole::getStatus, UserConstants.ROLE_STATUS_NORMAL)
                .list();
    }

    public boolean existsByRoleKey(String roleKey, Long excludeRoleId) {
        return this.lambdaQuery()
                .eq(SysRole::getRoleKey, roleKey)
                .ne(excludeRoleId != null, SysRole::getRoleId, excludeRoleId)
                .exists();
    }

    public SysRole getByRoleId(Long roleId) {
        return this.lambdaQuery()
                .eq(SysRole::getRoleId, roleId)
                .one();
    }

    public SysRole getAdminRole() {
        return this.lambdaQuery()
                .eq(SysRole::getRoleKey, UserConstants.ADMIN_ROLE_KEY)
                .one();
    }

    public boolean existsNormalByRoleIds(List<Long> roleIds, int expectedCount) {
        return listNormalByRoleIds(roleIds).size() == expectedCount;
    }

    public boolean saveRole(SysRole role) {
        return this.save(role);
    }

    public boolean updateRole(SysRole role) {
        return this.updateById(role);
    }

    public boolean removeRole(Long roleId) {
        return this.removeById(roleId);
    }
}
