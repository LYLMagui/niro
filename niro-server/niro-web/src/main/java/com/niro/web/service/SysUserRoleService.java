package com.niro.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.niro.web.dto.param.BatchAppendUserRolesParam;
import com.niro.web.entity.SysUserRole;

import java.util.List;

/**
 * 用户和角色关联业务层
 */
public interface SysUserRoleService extends IService<SysUserRole> {

    /**
     * 查询用户已绑定的角色ID列表。
     *
     * @param userId 用户ID
     * @return 角色ID列表
     */
    List<Long> listRoleIdsByUserId(Long userId);

    /**
     * 覆盖保存用户角色关系。
     *
     * @param operatorUserId 当前操作人ID
     * @param userId 用户ID
     * @param roleIds 角色ID列表
     */
    void replaceUserRoles(Long operatorUserId, Long userId, List<Long> roleIds);

    /**
     * 批量追加用户角色关系。
     *
     * @param operatorUserId 当前操作人ID
     * @param param 批量追加参数
     */
    void batchAppendUserRoles(Long operatorUserId, BatchAppendUserRolesParam param);
}
