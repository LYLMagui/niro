package com.niro.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.niro.core.result.Result;
import com.niro.web.dto.AppKeyPublicKeyDTO;
import com.niro.web.dto.UserPlatformSettingsDTO;
import com.niro.web.dto.param.UserPlatformSettingsParam;
import com.niro.web.service.AppKeyCryptoService;
import com.niro.web.service.UserPlatformSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户平台配置接口
 *
 * @author liyl
 * @since 2025-12-24
 */
@Tag(name = "个人配置")
@RestController
@RequestMapping("/settings")
@SaCheckLogin
@RequiredArgsConstructor
public class UserPlatformSettingsController {

    private final UserPlatformSettingsService userPlatformSettingsService;
    private final AppKeyCryptoService appKeyCryptoService;

    @Operation(summary = "获取配置")
    @GetMapping
    public Result<UserPlatformSettingsDTO> getSettings() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.success(userPlatformSettingsService.getByUserId(userId));
    }

    @Operation(summary = "获取AppKey字段加密公钥")
    @GetMapping("/app-key/public-key")
    public Result<AppKeyPublicKeyDTO> getAppKeyPublicKey() {
        return Result.success(appKeyCryptoService.getPublicKey());
    }

    @Operation(summary = "保存配置")
    @PostMapping
    public Result<Void> saveSettings(@RequestBody @Valid UserPlatformSettingsParam param) {
        Long userId = StpUtil.getLoginIdAsLong();
        userPlatformSettingsService.saveOrUpdate(userId, param);
        return Result.success();
    }
}
