package com.niro.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.niro.core.result.Result;
import com.niro.web.dto.BuffAccountDTO;
import com.niro.web.enums.BuffAccountStatusEnum;
import com.niro.web.service.BuffAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * BUFF 账号管理接口
 *
 * @author niro
 * @since 2026-01-15
 */
@Slf4j
@Tag(name = "BUFF账号管理")
@RestController
@RequestMapping("/buff/account")
@RequiredArgsConstructor
public class BuffAccountController {

    private final BuffAccountService buffAccountService;

    @Operation(summary = "获取账号列表")
    @GetMapping("/list")
    public Result<List<BuffAccountDTO>> list() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.success(buffAccountService.listByUserId(userId));
    }

    @Operation(summary = "保存或更新账号")
    @PostMapping("/save")
    public Result<Void> save(@RequestBody BuffAccountDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        buffAccountService.saveOrUpdateAccount(userId, dto);
        return Result.success();
    }

    @Operation(summary = "删除账号")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        buffAccountService.deleteAccount(userId, id);
        return Result.success();
    }

    @Operation(summary = "单账号Cookie检测")
    @PostMapping("/check/{id}")
    public Result<Void> check(@PathVariable("id") Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        buffAccountService.checkCookie(userId, id);
        return Result.success();
    }

    @Operation(summary = "一键检测所有Cookie")
    @PostMapping("/check/all")
    public Result<Void> checkAll() {
        Long userId = StpUtil.getLoginIdAsLong();
        buffAccountService.checkAllCookies(userId);
        return Result.success();
    }

    @Operation(summary = "更新账号信息 (爬虫反馈)")
    @PostMapping("/report/status")
    public Result<Void> reportStatus(@RequestBody BuffAccountDTO dto) {
        log.info("📥 收到账号反馈: id={}, status={}, balance={}", dto.getId(), dto.getStatus(), dto.getBalance());
        // 此接口通常由爬虫或内部服务调用，此处简单放行，实际可增加签名校验
        buffAccountService.reportAccountInfo(dto);
        return Result.success();
    }
}
