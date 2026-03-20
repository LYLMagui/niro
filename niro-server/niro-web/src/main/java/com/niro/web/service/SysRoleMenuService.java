package com.niro.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.niro.web.entity.SysRoleMenu;

import java.util.List;

/**
 * 角色和菜单关联业务层
 */
public interface SysRoleMenuService extends IService<SysRoleMenu> {

    /**
     * 查询角色已授权的菜单ID列表
     *
     * @param roleId 角色ID
     * @return 菜单ID列表
     */
    List<Long> listMenuIdsByRoleId(Long roleId);

    /**
     * 覆盖保存角色菜单关系（先删后增）
     *
     * @param roleId  角色ID
     * @param menuIds 菜单ID列表
     */
    void replaceRoleMenus(Long roleId, List<Long> menuIds);
}
