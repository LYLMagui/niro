package com.niro.web.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.SysRoleMenu;
import com.niro.web.mapper.SysRoleMenuMapper;
import com.niro.web.service.SysRoleMenuService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色和菜单关联业务层实现
 */
@Service
public class SysRoleMenuServiceImpl extends ServiceImpl<SysRoleMenuMapper, SysRoleMenu> implements SysRoleMenuService {

    @Override
    public List<Long> listMenuIdsByRoleId(Long roleId) {
        if (roleId == null) {
            return Collections.emptyList();
        }
        return this.lambdaQuery()
                .eq(SysRoleMenu::getRoleId, roleId)
                .list()
                .stream()
                .map(SysRoleMenu::getMenuId)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceRoleMenus(Long roleId, List<Long> menuIds) {
        this.remove(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));

        if (CollUtil.isEmpty(menuIds)) {
            return;
        }

        List<SysRoleMenu> bindings = new LinkedHashSet<>(menuIds).stream()
                .map(menuId -> {
                    SysRoleMenu roleMenu = new SysRoleMenu();
                    roleMenu.setRoleId(roleId);
                    roleMenu.setMenuId(menuId);
                    return roleMenu;
                })
                .collect(Collectors.toList());

        this.saveBatch(bindings);
    }
}
