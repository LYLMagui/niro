package com.niro.web.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.niro.web.constant.PermissionConstants;
import com.niro.web.dto.C5SnipingAccountDTO;
import com.niro.web.dto.param.C5SnipingAccountSaveParam;
import com.niro.web.service.C5SnipingAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C5 扫货 2.0 独立账号管理接口。
 */
@Tag(name = "C5扫货2.0账号管理")
@RestController
@RequestMapping("/api/c5/sniping/v2/accounts")
@RequiredArgsConstructor
@SaCheckLogin
public class C5SnipingAccountController {

    private final C5SnipingAccountService c5SnipingAccountService;

    /**
     * 查询当前用户 C5 扫货账号列表。
     *
     * @return C5 扫货账号列表
     */
    @GetMapping
    @SaCheckPermission(PermissionConstants.TASK_SCAN_LIST)
    @Operation(summary = "查询C5扫货2.0账号列表")
    public List<C5SnipingAccountDTO> listAccounts() {
        return c5SnipingAccountService.listAccounts();
    }

    /**
     * 保存或更新 C5 扫货账号。
     *
     * @param param 保存参数
     */
    @PostMapping
    @Operation(summary = "保存C5扫货2.0账号")
    public void saveAccount(@RequestBody @Valid C5SnipingAccountSaveParam param) {
        StpUtil.checkPermission(param.getId() == null
                ? PermissionConstants.C5_SNIPING_ACCOUNT_CREATE
                : PermissionConstants.C5_SNIPING_ACCOUNT_UPDATE);
        c5SnipingAccountService.saveAccount(param);
    }

    /**
     * 删除 C5 扫货账号。
     *
     * @param id 账号 ID
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission(PermissionConstants.C5_SNIPING_ACCOUNT_DELETE)
    @Operation(summary = "删除C5扫货2.0账号")
    public void deleteAccount(@Parameter(description = "账号ID") @PathVariable Long id) {
        c5SnipingAccountService.deleteAccount(id);
    }

    /**
     * 检测单个 C5 扫货账号配置。
     *
     * @param id 账号 ID
     */
    @PostMapping("/{id}/check")
    @SaCheckPermission(PermissionConstants.C5_SNIPING_ACCOUNT_DETAIL)
    @Operation(summary = "检测C5扫货2.0账号")
    public void checkAccount(@Parameter(description = "账号ID") @PathVariable Long id) {
        c5SnipingAccountService.checkAccount(id);
    }
}
