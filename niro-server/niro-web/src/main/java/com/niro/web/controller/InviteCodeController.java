package com.niro.web.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.niro.web.constant.PermissionConstants;
import com.niro.web.dto.InviteCodeBatchCreateResultDTO;
import com.niro.web.dto.InviteCodeDetailDTO;
import com.niro.web.dto.InviteCodePageDTO;
import com.niro.web.dto.param.InviteCodeBatchCreateParam;
import com.niro.web.dto.param.InviteCodeCreateParam;
import com.niro.web.dto.param.InviteCodeQueryParam;
import com.niro.web.dto.param.InviteCodeUpdateParam;
import com.niro.web.service.InviteCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 邀请码管理接口
 */
@RestController
@RequestMapping("/invite-code")
@RequiredArgsConstructor
@Tag(name = "邀请码管理")
public class InviteCodeController {

    private final InviteCodeService inviteCodeService;

    @GetMapping("/page")
    @SaCheckLogin
    @SaCheckPermission(PermissionConstants.INVITE_CODE_MANAGE)
    @Operation(summary = "分页查询邀请码")
    public Page<InviteCodePageDTO> pageInviteCodes(@Valid InviteCodeQueryParam param) {
        return inviteCodeService.pageInviteCodes(param);
    }

    @GetMapping("/{id}")
    @SaCheckLogin
    @SaCheckPermission(PermissionConstants.INVITE_CODE_MANAGE)
    @Operation(summary = "查询邀请码详情")
    public InviteCodeDetailDTO getInviteCodeDetail(@PathVariable("id") Long id) {
        return inviteCodeService.getInviteCodeDetail(id);
    }

    @PostMapping("/create")
    @SaCheckLogin
    @SaCheckPermission(PermissionConstants.INVITE_CODE_MANAGE)
    @Operation(summary = "新建邀请码")
    public InviteCodeDetailDTO createInviteCode(@RequestBody @Valid InviteCodeCreateParam param) {
        return inviteCodeService.createInviteCode(StpUtil.getLoginIdAsLong(), param);
    }

    @PostMapping("/batch-create")
    @SaCheckLogin
    @SaCheckPermission(PermissionConstants.INVITE_CODE_MANAGE)
    @Operation(summary = "批量生成邀请码")
    public InviteCodeBatchCreateResultDTO batchCreateInviteCodes(@RequestBody @Valid InviteCodeBatchCreateParam param) {
        return inviteCodeService.batchCreateInviteCodes(StpUtil.getLoginIdAsLong(), param);
    }

    @PutMapping("/update")
    @SaCheckLogin
    @SaCheckPermission(PermissionConstants.INVITE_CODE_MANAGE)
    @Operation(summary = "更新邀请码")
    public InviteCodeDetailDTO updateInviteCode(@RequestBody @Valid InviteCodeUpdateParam param) {
        return inviteCodeService.updateInviteCode(StpUtil.getLoginIdAsLong(), param);
    }

    @PostMapping("/status/{id}/{status}")
    @SaCheckLogin
    @SaCheckPermission(PermissionConstants.INVITE_CODE_MANAGE)
    @Operation(summary = "更新邀请码状态")
    public void updateStatus(@PathVariable("id") Long id, @PathVariable("status") Integer status) {
        inviteCodeService.updateStatus(id, status);
    }

    @PostMapping("/batch-disable")
    @SaCheckLogin
    @SaCheckPermission(PermissionConstants.INVITE_CODE_MANAGE)
    @Operation(summary = "批量停用邀请码")
    public void batchDisable(@RequestBody List<Long> ids) {
        inviteCodeService.batchDisable(ids);
    }
}
