package com.niro.web.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.SysUserRole;
import com.niro.web.mapper.SysUserRoleMapper;
import com.niro.web.service.SysUserRoleService;
import org.springframework.stereotype.Service;

/**
 * 用户和角色关联业务层实现
 */
@Service
public class SysUserRoleServiceImpl extends ServiceImpl<SysUserRoleMapper, SysUserRole> implements SysUserRoleService {
}
