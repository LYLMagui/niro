package com.niro.web.manager;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.SysUserRole;
import com.niro.web.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户角色关联管理器
 */
@Service
public class SysUserRoleMapperManager extends ServiceImpl<SysUserRoleMapper, SysUserRole> {

    public List<SysUserRole> listByUserId(Long userId) {
        return this.lambdaQuery()
                .eq(SysUserRole::getUserId, userId)
                .list();
    }

    public List<SysUserRole> listByUserIds(Collection<Long> userIds) {
        return this.lambdaQuery()
                .in(SysUserRole::getUserId, userIds)
                .list();
    }

    public List<Long> listRoleIdsByUserId(Long userId) {
        return listByUserId(userId).stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());
    }

    public Map<Long, List<Long>> mapRoleIdsByUserIds(Collection<Long> userIds) {
        return listByUserIds(userIds).stream()
                .collect(Collectors.groupingBy(
                        SysUserRole::getUserId,
                        Collectors.mapping(SysUserRole::getRoleId, Collectors.toList())
                ));
    }

    public boolean removeByUserId(Long userId) {
        return this.lambdaUpdate()
                .eq(SysUserRole::getUserId, userId)
                .remove();
    }

    public boolean saveUserRoles(List<SysUserRole> bindings) {
        return this.saveBatch(bindings);
    }

    public boolean existsByUserIdAndRoleId(Long userId, Long roleId) {
        return this.lambdaQuery()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getRoleId, roleId)
                .exists();
    }

    public boolean existsOtherUserByRoleId(Long roleId, Long excludeUserId) {
        return this.lambdaQuery()
                .eq(SysUserRole::getRoleId, roleId)
                .ne(SysUserRole::getUserId, excludeUserId)
                .exists();
    }

    public boolean existsByRoleId(Long roleId) {
        return this.lambdaQuery()
                .eq(SysUserRole::getRoleId, roleId)
                .exists();
    }
}
