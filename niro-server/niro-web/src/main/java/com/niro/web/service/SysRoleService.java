package com.niro.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.niro.web.dto.param.CreateRoleParam;
import com.niro.web.dto.param.UpdateRoleParam;
import com.niro.web.entity.SysRole;

import java.util.Set;

/**
 * 角色业务层
 */
public interface SysRoleService extends IService<SysRole> {

    /**
     * 根据用户ID查询角色权限。
     *
     * @param userId 用户ID
     * @return 角色权限集合
     */
    Set<String> selectRolePermissionByUserId(Long userId);

    /**
     * 新增角色。
     *
     * @param param 角色参数
     */
    void createRole(CreateRoleParam param);

    /**
     * 更新角色。
     *
     * @param roleId 角色ID
     * @param param 角色参数
     */
    void updateRole(Long roleId, UpdateRoleParam param);

    /**
     * 删除角色。
     *
     * @param roleId 角色ID
     */
    void deleteRole(Long roleId);
}
