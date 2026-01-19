package com.niro.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.niro.core.result.Result;
import com.niro.web.dto.UserBuffSettingsDTO;
import com.niro.web.dto.param.UserBuffSettingsParam;
import com.niro.web.service.UserBuffSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 用户Buff配置接口
 *
 * @author liyl
 * @since 2025-12-24
 */
@Slf4j
@Tag(name = "个人配置")
@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class UserBuffSettingsController {

    private final UserBuffSettingsService userBuffSettingsService;

    @Operation(summary = "获取配置")
    @GetMapping
    public Result<UserBuffSettingsDTO> getSettings() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.success(userBuffSettingsService.getByUserId(userId));
    }

    @Operation(summary = "保存配置")
    @PostMapping
    public Result<Void> saveSettings(@RequestBody @Valid UserBuffSettingsParam param) {
        Long userId = StpUtil.getLoginIdAsLong();
        userBuffSettingsService.saveOrUpdate(userId, param);
        return Result.success();
    }

    @Operation(summary = "发送测试通知")
    @PostMapping("/test-notify")
    public Result<Void> sendTestNotify() {
        Long userId = StpUtil.getLoginIdAsLong();
        userBuffSettingsService.sendTestNotify(userId);
        return Result.success();
    }
}
