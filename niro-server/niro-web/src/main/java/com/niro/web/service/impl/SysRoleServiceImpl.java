package com.niro.web.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.SysRole;
import com.niro.web.entity.SysUserRole;
import com.niro.web.mapper.SysRoleMapper;
import com.niro.web.mapper.SysUserRoleMapper;
import com.niro.web.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
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

    private final SysUserRoleMapper sysUserRoleMapper;

    @Override
    public Set<String> selectRolePermissionByUserId(Long userId) {
        Set<String> roles = new HashSet<>();
        // 管理员拥有所有权限
        if (userId == 1L) {
            roles.add("admin");
            return roles;
        }

        // 1. 查询用户角色关联
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId)
        );

        if (CollUtil.isEmpty(userRoles)) {
            return roles;
        }

        List<Long> roleIds = userRoles.stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());

        // 2. 查询角色信息
        List<SysRole> sysRoles = baseMapper.selectList(
                new LambdaQueryWrapper<SysRole>()
                        .in(SysRole::getRoleId, roleIds)
                        .eq(SysRole::getStatus, 0) // 正常
                        .eq(SysRole::getDelFlag, 0) // 未删除
        );

        if (CollUtil.isNotEmpty(sysRoles)) {
            for (SysRole role : sysRoles) {
                if (role.getRoleKey() != null) {
                    roles.addAll(Arrays.asList(role.getRoleKey().trim().split(",")));
                }
            }
        }
        return roles;
    }
}
