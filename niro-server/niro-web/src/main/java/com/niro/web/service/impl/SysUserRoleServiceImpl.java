package com.niro.web.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.core.util.Assert;
import com.niro.web.constant.UserConstants;
import com.niro.web.dto.param.BatchAppendUserRolesParam;
import com.niro.web.entity.SysRole;
import com.niro.web.entity.SysUserRole;
import com.niro.web.manager.SysRoleMapperManager;
import com.niro.web.manager.SysUserRoleMapperManager;
import com.niro.web.manager.UserMapperManager;
import com.niro.web.mapper.SysUserRoleMapper;
import com.niro.web.service.SysUserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户和角色关联业务层实现
 */
@Service
@RequiredArgsConstructor
public class SysUserRoleServiceImpl extends ServiceImpl<SysUserRoleMapper, SysUserRole> implements SysUserRoleService {

    private final SysUserRoleMapperManager sysUserRoleMapperManager;
    private final SysRoleMapperManager sysRoleMapperManager;
    private final UserMapperManager userMapperManager;

    @Override
    public List<Long> listRoleIdsByUserId(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return sysUserRoleMapperManager.listRoleIdsByUserId(userId);
    }

    /**
     * 覆盖保存用户角色关系。
     *
     * @param operatorUserId 当前操作人ID
     * @param userId 用户ID
     * @param roleIds 角色ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceUserRoles(Long operatorUserId, Long userId, List<Long> roleIds) {
        List<Long> oldRoleIds = sysUserRoleMapperManager.listRoleIdsByUserId(userId);
        Set<Long> newRoleIdSet = roleIds == null ? Collections.emptySet() : new LinkedHashSet<>(roleIds);
        enforceAdminRoleGuard(operatorUserId, userId, oldRoleIds, newRoleIdSet);

        sysUserRoleMapperManager.removeByUserId(userId);
        if (CollUtil.isEmpty(roleIds)) {
            return;
        }

        List<SysUserRole> bindings = new LinkedHashSet<>(roleIds).stream()
                .map(roleId -> buildBinding(userId, roleId))
                .collect(Collectors.toList());
        Assert.isTrue(sysUserRoleMapperManager.saveUserRoles(bindings), "保存用户角色关系失败");
    }

    /**
     * 批量追加用户角色关系。
     *
     * @param operatorUserId 当前操作人ID
     * @param param 批量追加参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchAppendUserRoles(Long operatorUserId, BatchAppendUserRolesParam param) {
        Assert.notNull(param, "请求参数不能为空");

        List<Long> userIds = normalizeIds(param.getUserIds());
        List<Long> roleIds = normalizeIds(param.getRoleIds());
        Assert.notEmpty(userIds, "用户ID列表不能为空");
        Assert.notEmpty(roleIds, "角色ID列表不能为空");
        Assert.isTrue(userMapperManager.existsByIds(userIds, userIds.size()), "存在无效用户");
        Assert.isTrue(sysRoleMapperManager.existsNormalByRoleIds(roleIds, roleIds.size()), "存在无效或停用角色");

        Map<Long, List<Long>> userRoleMap = sysUserRoleMapperManager.mapRoleIdsByUserIds(userIds);
        List<SysUserRole> newBindings = new ArrayList<>();
        for (Long userId : userIds) {
            LinkedHashSet<Long> currentRoleIds = new LinkedHashSet<>(userRoleMap.getOrDefault(userId, Collections.emptyList()));
            LinkedHashSet<Long> mergedRoleIds = new LinkedHashSet<>(currentRoleIds);
            mergedRoleIds.addAll(roleIds);
            enforceAdminRoleGuard(operatorUserId, userId, currentRoleIds, mergedRoleIds);
            for (Long roleId : roleIds) {
                if (currentRoleIds.add(roleId)) {
                    newBindings.add(buildBinding(userId, roleId));
                }
            }
        }

        if (CollUtil.isEmpty(newBindings)) {
            return;
        }
        Assert.isTrue(sysUserRoleMapperManager.saveUserRoles(newBindings), "批量追加用户角色失败");
    }

    private void enforceAdminRoleGuard(Long operatorUserId,
                                       Long targetUserId,
                                       Collection<Long> oldRoleIds,
                                       Collection<Long> newRoleIds) {
        SysRole adminRole = sysRoleMapperManager.getAdminRole();
        if (adminRole == null) {
            return;
        }
        Long adminRoleId = adminRole.getRoleId();
        boolean hadAdmin = oldRoleIds != null && oldRoleIds.contains(adminRoleId);
        boolean willHaveAdmin = newRoleIds != null && newRoleIds.contains(adminRoleId);
        if (hadAdmin == willHaveAdmin) {
            return;
        }

        boolean currentIsAdmin = UserConstants.ADMIN_ID.equals(operatorUserId)
                || sysUserRoleMapperManager.existsByUserIdAndRoleId(operatorUserId, adminRoleId);
        Assert.isTrue(currentIsAdmin, "仅超级管理员可变动 admin 角色");

        boolean isSelfGrant = !hadAdmin && willHaveAdmin && Objects.equals(operatorUserId, targetUserId);
        Assert.isFalse(isSelfGrant, "禁止给自己分配 admin 角色");

        if (hadAdmin && !willHaveAdmin) {
            Assert.isTrue(sysUserRoleMapperManager.existsOtherUserByRoleId(adminRoleId, targetUserId), "系统至少保留一个 admin 用户，禁止撤销最后一个 admin");
        }
    }

    private SysUserRole buildBinding(Long userId, Long roleId) {
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        return userRole;
    }

    private static List<Long> normalizeIds(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }
}
