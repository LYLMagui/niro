package com.niro.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.niro.core.result.Result;
import com.niro.web.dto.UserPlatformSettingsDTO;
import com.niro.web.dto.param.UserPlatformSettingsParam;
import com.niro.web.service.UserPlatformSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 用户平台配置接口
 *
 * @author liyl
 * @since 2025-12-24
 */
@Slf4j
@Tag(name = "个人配置")
@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class UserPlatformSettingsController {

    private final UserPlatformSettingsService userPlatformSettingsService;

    @Operation(summary = "获取配置")
    @GetMapping
    public Result<UserPlatformSettingsDTO> getSettings() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.success(userPlatformSettingsService.getByUserId(userId));
    }

    @Operation(summary = "保存配置")
    @PostMapping
    public Result<Void> saveSettings(@RequestBody @Valid UserPlatformSettingsParam param) {
        Long userId = StpUtil.getLoginIdAsLong();
        userPlatformSettingsService.saveOrUpdate(userId, param);
        return Result.success();
    }

    @Operation(summary = "发送测试通知")
    @PostMapping("/test-notify")
    public Result<Void> sendTestNotify() {
        Long userId = StpUtil.getLoginIdAsLong();
        userPlatformSettingsService.sendTestNotify(userId);
        return Result.success();
    }
}
