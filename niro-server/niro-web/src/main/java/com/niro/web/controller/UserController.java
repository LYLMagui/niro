package com.niro.web.controller;

import com.niro.web.dto.UserDTO;
import com.niro.web.dto.param.UserLoginParam;
import com.niro.web.dto.param.UserRegisterParam;
import com.niro.web.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
    @Operation(summary = "退出登录")
    public void logout(){
        userService.logout();
    }
    
    @GetMapping("/getUser/{id}")
    @Operation(summary = "根据id获取用户")
    public UserDTO getUser(@PathVariable("id") Long id){
        return userService.getUser(id);
    }
}
