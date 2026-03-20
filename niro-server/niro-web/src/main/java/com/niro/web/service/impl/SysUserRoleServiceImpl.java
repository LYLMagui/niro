package com.niro.web.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.SysUserRole;
import com.niro.web.mapper.SysUserRoleMapper;
import com.niro.web.service.SysUserRoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户和角色关联业务层实现
 */
@Service
public class SysUserRoleServiceImpl extends ServiceImpl<SysUserRoleMapper, SysUserRole> implements SysUserRoleService {

    @Override
    public List<Long> listRoleIdsByUserId(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return this.lambdaQuery()
                .eq(SysUserRole::getUserId, userId)
                .list()
                .stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceUserRoles(Long userId, List<Long> roleIds) {
        this.remove(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));

        if (CollUtil.isEmpty(roleIds)) {
            return;
        }

        List<SysUserRole> bindings = new LinkedHashSet<>(roleIds).stream()
                .map(roleId -> {
                    SysUserRole userRole = new SysUserRole();
                    userRole.setUserId(userId);
                    userRole.setRoleId(roleId);
                    return userRole;
                })
                .collect(Collectors.toList());

        this.saveBatch(bindings);
    }
}
