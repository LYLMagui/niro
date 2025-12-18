package com.buff.web.controller;

import com.buff.web.dto.UserDTO;
import com.buff.web.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 *
 *
 * @author liyl
 * @date 2025/12/18
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "用户模块",description = "用户相关功能接口")
public class UserController {
    private final UserService userService;
    
    @GetMapping("/list")
    @Operation(summary = "获取所有用户")
    public List<UserDTO> getAllUser(){
        return userService.getAllUser();
    }
}
