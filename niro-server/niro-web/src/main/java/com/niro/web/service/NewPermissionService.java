package com.niro.web.service;

import com.niro.web.dto.newpermission.NewPermissionNavigationDTO;
import com.niro.web.dto.newpermission.NewPermissionPublishResultDTO;
import com.niro.web.dto.newpermission.NewPermissionRolePreviewDTO;
import com.niro.web.dto.newpermission.NewPermissionResourceDTO;
import com.niro.web.dto.param.newpermission.NewPermissionPublishParam;
import com.niro.web.dto.param.newpermission.NewPermissionResourceSaveParam;
import com.niro.web.dto.param.newpermission.NewPermissionRoleResourceSaveParam;

import java.util.List;

/**
 * 新权限系统服务
 */
public interface NewPermissionService {

    List<NewPermissionResourceDTO> listDraftResources();

    NewPermissionResourceDTO getDraftResource(Long id);

    NewPermissionResourceDTO saveDraftResource(NewPermissionResourceSaveParam param);

    List<Long> listDraftRoleResourceIds(Long roleId);

    void saveDraftRoleResources(Long roleId, NewPermissionRoleResourceSaveParam param);

    NewPermissionRolePreviewDTO previewRole(Long roleId);

    NewPermissionPublishResultDTO validatePublish(NewPermissionPublishParam param);

    NewPermissionPublishResultDTO publish(NewPermissionPublishParam param);

    NewPermissionNavigationDTO getPublishedNavigation();

    List<String> listPublishedButtonPermissions();

    List<String> listPublishedButtonPermissionsByUserId(Long userId);
}
