package com.niro.web.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.niro.web.constant.PermissionConstants;
import com.niro.web.dto.newpermission.NewPermissionNavigationDTO;
import com.niro.web.dto.newpermission.NewPermissionPublishResultDTO;
import com.niro.web.dto.newpermission.NewPermissionRolePreviewDTO;
import com.niro.web.dto.newpermission.NewPermissionResourceDTO;
import com.niro.web.dto.param.newpermission.NewPermissionPublishParam;
import com.niro.web.dto.param.newpermission.NewPermissionResourceSaveParam;
import com.niro.web.dto.param.newpermission.NewPermissionRoleResourceSaveParam;
import com.niro.web.service.NewPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 新权限系统 v2 接口
 */
@RestController
@RequestMapping("/api/v2/permission")
@RequiredArgsConstructor
@Tag(name = "新权限系统", description = "新权限系统资源、授权、发布与导航接口")
public class NewPermissionController {

    private final NewPermissionService newPermissionService;

    @GetMapping("/resources")
    @SaCheckPermission(PermissionConstants.PERMISSION_RESOURCE_READ)
    @Operation(summary = "查询草稿资源树")
    public List<NewPermissionResourceDTO> listDraftResources() {
        return newPermissionService.listDraftResources();
    }

    @GetMapping("/resources/{id}")
    @SaCheckPermission(PermissionConstants.PERMISSION_RESOURCE_READ)
    @Operation(summary = "查询草稿资源详情")
    public NewPermissionResourceDTO getDraftResource(@PathVariable Long id) {
        return newPermissionService.getDraftResource(id);
    }

    @PostMapping("/resources")
    @SaCheckPermission(PermissionConstants.PERMISSION_RESOURCE_SAVE)
    @Operation(summary = "保存草稿资源")
    public NewPermissionResourceDTO saveDraftResource(@RequestBody NewPermissionResourceSaveParam param) {
        return newPermissionService.saveDraftResource(param);
    }

    @GetMapping("/roles/{roleId}/resources")
    @SaCheckPermission(PermissionConstants.PERMISSION_ROLE_AUTH_READ)
    @Operation(summary = "查询草稿角色授权")
    public List<Long> listDraftRoleResourceIds(@PathVariable Long roleId) {
        return newPermissionService.listDraftRoleResourceIds(roleId);
    }

    @PutMapping("/roles/{roleId}/resources")
    @SaCheckPermission(PermissionConstants.PERMISSION_ROLE_AUTH_SAVE)
    @Operation(summary = "保存草稿角色授权")
    public void saveDraftRoleResources(@PathVariable Long roleId, @RequestBody NewPermissionRoleResourceSaveParam param) {
        newPermissionService.saveDraftRoleResources(roleId, param);
    }

    @GetMapping("/roles/{roleId}/preview")
    @SaCheckPermission(PermissionConstants.PERMISSION_ROLE_PREVIEW)
    @Operation(summary = "预览角色权限")
    public NewPermissionRolePreviewDTO previewRole(@PathVariable Long roleId) {
        return newPermissionService.previewRole(roleId);
    }

    @PostMapping("/publish/validate")
    @SaCheckPermission(PermissionConstants.PERMISSION_PUBLISH_VALIDATE)
    @Operation(summary = "发布前校验")
    public NewPermissionPublishResultDTO validatePublish(@RequestBody NewPermissionPublishParam param) {
        return newPermissionService.validatePublish(param);
    }

    @PostMapping("/publish")
    @SaCheckPermission(PermissionConstants.PERMISSION_PUBLISH)
    @Operation(summary = "执行发布")
    public NewPermissionPublishResultDTO publish(@RequestBody NewPermissionPublishParam param) {
        return newPermissionService.publish(param);
    }

    @GetMapping("/navigation")
    @Operation(summary = "查询已发布导航")
    public NewPermissionNavigationDTO getPublishedNavigation() {
        return newPermissionService.getPublishedNavigation();
    }

    @GetMapping("/buttons")
    @Operation(summary = "查询当前登录用户已发布按钮权限")
    public List<String> listPublishedButtonPermissions() {
        return newPermissionService.listPublishedButtonPermissions();
    }
}
