package com.niro.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.core.bean.BeanUtil;
import com.niro.web.dto.UserDTO;
import com.niro.web.dto.UserInfoDTO;
import com.niro.web.dto.param.UserLoginParam;
import com.niro.web.dto.param.UserRegisterParam;
import com.niro.web.service.SysMenuService;
import com.niro.web.service.SysRoleService;
import com.niro.web.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.Set;

/**
 * <p>
 * 用户表 前端控制器
 * </p>
 *
 * @author liyl
 * @since 2025-12-19
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "用户管理",description = "用户服务相关接口")
public class UserController {

    private final UserService userService;
    private final SysRoleService sysRoleService;
    private final SysMenuService sysMenuService;
    
    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public void register(@RequestBody UserRegisterParam param){
        userService.register(param);
    }
    
    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public UserDTO login(@RequestBody UserLoginParam param){
        return userService.login(param);
    }

    @PostMapping("/logout")
    @SaCheckLogin
    @Operation(summary = "退出登录")
    public void logout(){
        userService.logout();
    }

    @GetMapping("/getInfo")
    @SaCheckLogin
    @Operation(summary = "获取用户信息详情")
    public UserInfoDTO getInfo() {
        Long userId = StpUtil.getLoginIdAsLong();
        UserDTO userDTO = userService.getUser(userId);
        
        UserInfoDTO userInfoDTO = new UserInfoDTO();
        BeanUtil.copyProperties(userDTO, userInfoDTO);
        
        // 获取角色
        Set<String> roles = sysRoleService.selectRolePermissionByUserId(userId);
        userInfoDTO.setRoles(roles);
        
        // 获取权限
        Set<String> permissions = new HashSet<>(sysMenuService.selectPermsByUserId(userId));
        userInfoDTO.setPermissions(permissions);
        
        return userInfoDTO;
    }
    
    @GetMapping("/getUser/{id}")
    @SaCheckLogin
    @Operation(summary = "根据id获取用户")
    public UserDTO getUser(@PathVariable("id") Long id){
        return userService.getUser(id);
    }
}
