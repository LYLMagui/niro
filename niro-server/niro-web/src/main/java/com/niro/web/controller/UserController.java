package com.niro.web.controller;

import com.niro.web.dto.UserDTO;
import com.niro.web.dto.param.UserLoginParam;
import com.niro.web.dto.param.UserRegisterParam;
import com.niro.web.service.UserService;
import com.niro.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public Result<Void> register(@RequestBody UserRegisterParam param){
        userService.register(param);
        return Result.success();
    }
    
    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public Result<UserDTO> login(@RequestBody UserLoginParam param){
        return userService.login(param);
    }
}
