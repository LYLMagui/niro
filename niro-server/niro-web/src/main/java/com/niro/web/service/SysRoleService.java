package com.niro.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.niro.web.entity.SysRole;

import java.util.Set;

/**
 * 角色业务层
 */
public interface SysRoleService extends IService<SysRole> {

    /**
     * 根据用户ID查询角色权限
     *
     * @param userId 用户ID
     * @return 角色权限集合
     */
    Set<String> selectRolePermissionByUserId(Long userId);
}
