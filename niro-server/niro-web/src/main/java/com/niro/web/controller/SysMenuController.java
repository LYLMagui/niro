package com.niro.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.niro.core.result.Result;
import com.niro.web.dto.vo.RouterVo;
import com.niro.web.entity.SysMenu;
import com.niro.web.service.SysMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单控制器
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "菜单管理", description = "动态路由相关接口")
public class SysMenuController {

    private final SysMenuService sysMenuService;

    @GetMapping("/menus")
    @Operation(summary = "获取用户动态路由")
    public List<RouterVo> getRouters() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<SysMenu> menus = sysMenuService.selectMenuTreeByUserId(userId);
        return sysMenuService.buildMenus(menus);
    }
}
