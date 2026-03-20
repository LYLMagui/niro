package com.niro.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.niro.web.entity.SysUserRole;

import java.util.List;

/**
 * 用户和角色关联业务层
 */
public interface SysUserRoleService extends IService<SysUserRole> {

    /**
     * 查询用户已绑定的角色ID列表
     *
     * @param userId 用户ID
     * @return 角色ID列表
     */
    List<Long> listRoleIdsByUserId(Long userId);

    /**
     * 覆盖保存用户角色关系（先删后增）
     *
     * @param userId  用户ID
     * @param roleIds 角色ID列表
     */
    void replaceUserRoles(Long userId, List<Long> roleIds);
}
