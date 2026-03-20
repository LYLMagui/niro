package com.niro.web.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.constant.UserConstants;
import com.niro.web.entity.SysRole;
import com.niro.web.entity.SysUserRole;
import com.niro.web.mapper.SysRoleMapper;
import com.niro.web.service.SysRoleService;
import com.niro.web.service.SysUserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色业务层实现
 */
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final SysUserRoleService sysUserRoleService;

    @Override
    public Set<String> selectRolePermissionByUserId(Long userId) {
        Set<String> roles = new HashSet<>();
        // 管理员拥有所有权限
        if (UserConstants.ADMIN_ID.equals(userId)) {
            roles.add("admin");
            return roles;
        }

        // 1. 查询用户角色关联
        List<SysUserRole> userRoles = sysUserRoleService.lambdaQuery()
                .eq(SysUserRole::getUserId, userId)
                .list();

        if (CollUtil.isEmpty(userRoles)) {
            return roles;
        }

        List<Long> roleIds = userRoles.stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());

        // 2. 查询角色信息
        List<SysRole> sysRoles = this.lambdaQuery()
                .in(SysRole::getRoleId, roleIds)
                .eq(SysRole::getStatus, UserConstants.ROLE_STATUS_NORMAL)
                .list();

        if (CollUtil.isEmpty(sysRoles)) {
            return roles;
        }

        sysRoles.stream()
                .map(SysRole::getRoleKey)
                .filter(StrUtil::isNotBlank)
                .forEach(roles::add);

        return roles;
    }
}
