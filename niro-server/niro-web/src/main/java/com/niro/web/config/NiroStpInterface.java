package com.niro.web.config;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.convert.Convert;
import com.niro.web.service.SysMenuService;
import com.niro.web.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sa-Token 角色与权限加载器
 */
@Component
@RequiredArgsConstructor
public class NiroStpInterface implements StpInterface {

    private final SysRoleService sysRoleService;
    private final SysMenuService sysMenuService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Long userId = Convert.toLong(loginId);
        return new ArrayList<>(sysMenuService.selectPermsByUserId(userId));
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = Convert.toLong(loginId);
        return new ArrayList<>(sysRoleService.selectRolePermissionByUserId(userId));
    }
}
