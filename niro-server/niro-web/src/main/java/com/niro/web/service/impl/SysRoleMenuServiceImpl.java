package com.niro.web.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.SysRoleMenu;
import com.niro.web.mapper.SysRoleMenuMapper;
import com.niro.web.service.SysRoleMenuService;
import org.springframework.stereotype.Service;

/**
 * 角色和菜单关联业务层实现
 */
@Service
public class SysRoleMenuServiceImpl extends ServiceImpl<SysRoleMenuMapper, SysRoleMenu> implements SysRoleMenuService {
}
