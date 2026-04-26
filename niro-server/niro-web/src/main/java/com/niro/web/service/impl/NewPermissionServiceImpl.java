package com.niro.web.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.niro.core.util.Assert;
import cn.dev33.satoken.stp.StpUtil;
import com.niro.web.constant.UserConstants;
import com.niro.web.dto.newpermission.NewPermissionNavigationDTO;
import com.niro.web.dto.newpermission.NewPermissionPublishResultDTO;
import com.niro.web.dto.newpermission.NewPermissionRolePreviewDTO;
import com.niro.web.dto.newpermission.NewPermissionResourceDTO;
import com.niro.web.dto.param.newpermission.NewPermissionPublishParam;
import com.niro.web.dto.param.newpermission.NewPermissionResourceSaveParam;
import com.niro.web.dto.param.newpermission.NewPermissionRoleResourceSaveParam;
import com.niro.web.entity.ResourceDraft;
import com.niro.web.entity.ResourcePublished;
import com.niro.web.entity.RoleResourceDraft;
import com.niro.web.entity.RoleResourcePublished;
import com.niro.web.entity.SysRole;
import com.niro.web.enums.NewPermissionResourceTypeEnum;
import com.niro.web.manager.ResourceDraftMapperManager;
import com.niro.web.manager.ResourcePublishedMapperManager;
import com.niro.web.manager.RoleResourceDraftMapperManager;
import com.niro.web.manager.RoleResourcePublishedMapperManager;
import com.niro.web.service.NewPermissionService;
import com.niro.web.service.SysRoleService;
import com.niro.web.service.SysUserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 新权限系统服务实现
 */
@Service
@RequiredArgsConstructor
public class NewPermissionServiceImpl implements NewPermissionService {

    private final ResourceDraftMapperManager resourceDraftMapperManager;
    private final RoleResourceDraftMapperManager roleResourceDraftMapperManager;
    private final ResourcePublishedMapperManager resourcePublishedMapperManager;
    private final RoleResourcePublishedMapperManager roleResourcePublishedMapperManager;
    private final SysRoleService sysRoleService;
    private final SysUserRoleService sysUserRoleService;

    @Override
    public List<NewPermissionResourceDTO> listDraftResources() {
        return buildTree(resourceDraftMapperManager.listActiveResources());
    }

    @Override
    public NewPermissionResourceDTO getDraftResource(Long id) {
        Assert.notNull(id, "资源ID不能为空");
        return toResourceDTO(findDraftById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NewPermissionResourceDTO saveDraftResource(NewPermissionResourceSaveParam param) {
        Assert.notNull(param, "请求参数不能为空");
        Assert.notBlank(param.getResourceKey(), "资源唯一键不能为空");
        Assert.notBlank(param.getResourceType(), "资源类型不能为空");
        Assert.notBlank(param.getTitle(), "资源标题不能为空");
        Assert.isTrue(isValidResourceType(param.getResourceType()), "资源类型非法");
        Assert.isTrue(resourceKeyUnused(param.getId(), param.getResourceKey()), "资源唯一键已存在");

        ResourceDraft resource = param.getId() == null
                ? new ResourceDraft()
                : findDraftById(param.getId());

        resource.setResourceKey(param.getResourceKey());
        resource.setResourceType(param.getResourceType());
        resource.setParentResourceId(normalizeParentResourceId(param.getParentResourceId()));
        resource.setPageKey(defaultString(param.getPageKey()));
        resource.setTitle(param.getTitle());
        resource.setIcon(defaultString(param.getIcon()));
        resource.setSortOrder(defaultInteger(param.getSortOrder()));
        resource.setHidden(defaultBoolean(param.getHidden()));
        resource.setPermissionCode(defaultString(param.getPermissionCode()));
        resource.setButtonGroup(defaultString(param.getButtonGroup()));
        resource.setRemark(defaultString(param.getRemark()));
        resource.setStatus(defaultInteger(param.getStatus(), 1));
        resource.setUpdatedBy(currentUserName());
        resource.setUpdatedAt(now());
        if (resource.getId() == null) {
            resource.setCreatedBy(currentUserName());
            resource.setCreatedAt(now());
            resource.setDelFlag(0);
        }
        Assert.isTrue(resourceDraftMapperManager.saveOrUpdate(resource), "保存资源失败");
        ensureAdminDraftRoleResources();
        return toResourceDTO(resourceDraftMapperManager.getById(resource.getId()));
    }

    @Override
    public List<Long> listDraftRoleResourceIds(Long roleId) {
        Assert.notNull(roleId, "角色ID不能为空");
        SysRole role = sysRoleService.getById(roleId);
        Assert.notNull(role, "角色不存在");
        if (isAdminRole(role)) {
            return ensureAdminDraftRoleResources(role).stream()
                    .map(ResourceDraft::getId)
                    .toList();
        }
        return roleResourceDraftMapperManager.listByRoleId(roleId).stream()
                .map(RoleResourceDraft::getResourceId)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDraftRoleResources(Long roleId, NewPermissionRoleResourceSaveParam param) {
        Assert.notNull(roleId, "角色ID不能为空");
        Assert.notNull(param, "请求参数不能为空");
        SysRole role = sysRoleService.getById(roleId);
        Assert.notNull(role, "角色不存在");
        Assert.isFalse(isAdminRole(role), "超级管理员角色授权不可修改");
        Assert.notEmpty(param.getResourceIds(), "资源ID列表不能为空");

        Set<Long> resourceIds = new LinkedHashSet<>(param.getResourceIds());
        List<ResourceDraft> resources = resourceDraftMapperManager.lambdaQuery()
                .in(ResourceDraft::getId, resourceIds)
                .eq(ResourceDraft::getDelFlag, 0)
                .list();
        Assert.isTrue(resources.size() == resourceIds.size(), "存在无效资源");

        roleResourceDraftMapperManager.deleteByRoleId(roleId);

        List<RoleResourceDraft> bindings = buildDraftBindings(roleId, resourceIds);
        Assert.isTrue(roleResourceDraftMapperManager.saveBatch(bindings), "保存角色授权失败");
    }

    @Override
    public NewPermissionRolePreviewDTO previewRole(Long roleId) {
        Assert.notNull(roleId, "角色ID不能为空");
        SysRole role = sysRoleService.getById(roleId);
        Assert.notNull(role, "角色不存在");

        List<ResourceDraft> resources = resourceDraftMapperManager.listActiveResources();
        Set<Long> resourceIds = new LinkedHashSet<>(listDraftRoleResourceIds(roleId));
        Map<Long, ResourceDraft> resourceMap = resources.stream()
                .collect(Collectors.toMap(ResourceDraft::getId, resource -> resource));
        Set<Long> visibleResourceIds = collectVisibleResourceIds(resourceIds, resourceMap);
        List<ResourceDraft> visibleResources = resources.stream()
                .filter(resource -> visibleResourceIds.contains(resource.getId()))
                .filter(resource -> !NewPermissionResourceTypeEnum.BUTTON.getCode().equals(resource.getResourceType()))
                .toList();
        List<ResourceDraft> accessiblePages = resources.stream()
                .filter(resource -> visibleResourceIds.contains(resource.getId()))
                .filter(this::isEnabledPage)
                .toList();

        NewPermissionRolePreviewDTO preview = new NewPermissionRolePreviewDTO();
        preview.setRoleId(roleId);
        preview.setRoleName(role.getRoleName());
        preview.setVisiblePages(accessiblePages.stream()
                .map(ResourceDraft::getPageKey)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList());
        preview.setVisibleMenus(visibleResources.stream()
                .filter(resource -> NewPermissionResourceTypeEnum.MENU.getCode().equals(resource.getResourceType()))
                .map(ResourceDraft::getTitle)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList());
        preview.setEnabledButtons(resources.stream()
                .filter(resource -> resourceIds.contains(resource.getId()))
                .filter(resource -> NewPermissionResourceTypeEnum.BUTTON.getCode().equals(resource.getResourceType()))
                .map(ResourceDraft::getPermissionCode)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList());
        preview.setNavigationTree(buildTree(visibleResources));
        preview.setAccessiblePages(accessiblePages.stream()
                .map(this::toPagePreview)
                .toList());
        preview.setPageButtons(buildPageButtonPreviews(resources, resourceIds, accessiblePages, resourceMap));

        ResourceDraft homePage = accessiblePages.stream()
                .findFirst()
                .orElse(null);
        preview.setHomePageKey(homePage == null ? "" : defaultString(homePage.getPageKey()));
        preview.setHomePageTitle(homePage == null ? "" : defaultString(homePage.getTitle()));
        return preview;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NewPermissionPublishResultDTO validatePublish(NewPermissionPublishParam param) {
        Assert.notNull(param, "请求参数不能为空");

        ensureAdminDraftRoleResources();

        List<String> errors = collectPublishErrors();
        NewPermissionPublishResultDTO result = new NewPermissionPublishResultDTO();
        result.setConfigVersion(calculateDraftConfigVersion());
        result.setPublishedAt(now().toString());
        if (CollUtil.isNotEmpty(errors)) {
            result.setSuccess(Boolean.FALSE);
            result.setMessage(String.join("；", errors));
            return result;
        }

        result.setSuccess(Boolean.TRUE);
        result.setMessage("校验通过");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NewPermissionPublishResultDTO publish(NewPermissionPublishParam param) {
        NewPermissionPublishResultDTO validated = validatePublish(param);
        Assert.isTrue(Boolean.TRUE.equals(validated.getSuccess()), validated.getMessage());

        resourcePublishedMapperManager.deleteAll();
        roleResourcePublishedMapperManager.deleteAll();

        List<ResourceDraft> draftResources = resourceDraftMapperManager.listActiveResources();
        if (CollUtil.isNotEmpty(draftResources)) {
            List<ResourcePublished> publishedResources = draftResources.stream()
                    .map(this::toPublishedResource)
                    .toList();
            Assert.isTrue(resourcePublishedMapperManager.saveBatch(publishedResources), "发布资源失败");
            fixPublishedParentResourceIds(draftResources);
        }

        Map<String, Long> resourceIdMap = resourcePublishedMapperManager.listActiveResources().stream()
                .collect(Collectors.toMap(ResourcePublished::getResourceKey, ResourcePublished::getId));
        Map<Long, ResourceDraft> draftResourceMap = resourceDraftMapperManager.listActiveResources().stream()
                .collect(Collectors.toMap(ResourceDraft::getId, resource -> resource));

        List<RoleResourceDraft> draftBindings = roleResourceDraftMapperManager.lambdaQuery()
                .eq(RoleResourceDraft::getDelFlag, 0)
                .list();
        List<RoleResourcePublished> publishedBindings = new ArrayList<>();
        LocalDateTime now = now();
        String user = currentUserName();
        for (RoleResourceDraft draftBinding : draftBindings) {
            ResourceDraft draftResource = draftResourceMap.get(draftBinding.getResourceId());
            if (draftResource == null) {
                continue;
            }
            Long publishedResourceId = resourceIdMap.get(draftResource.getResourceKey());
            if (publishedResourceId == null) {
                continue;
            }
            publishedBindings.add(buildPublishedBinding(draftBinding.getRoleId(), publishedResourceId, now, user));
        }
        if (CollUtil.isNotEmpty(publishedBindings)) {
            Assert.isTrue(roleResourcePublishedMapperManager.saveBatch(publishedBindings), "发布授权失败");
        }

        NewPermissionPublishResultDTO result = new NewPermissionPublishResultDTO();
        result.setSuccess(Boolean.TRUE);
        result.setMessage("发布成功");
        result.setConfigVersion(validated.getConfigVersion());
        result.setPublishedAt(validated.getPublishedAt());
        return result;
    }

    @Override
    public NewPermissionNavigationDTO getPublishedNavigation() {
        List<ResourcePublished> resources = resourcePublishedMapperManager.listActiveResources();
        List<ResourcePublished> visibleResources = filterNavigationResourcesForCurrentUser(resources);
        NewPermissionNavigationDTO navigation = new NewPermissionNavigationDTO();
        navigation.setConfigVersion(calculatePublishedConfigVersion(resources));
        navigation.setPublishedAt(resolvePublishedAt(resources));
        navigation.setMenus(buildTree(visibleResources));
        return navigation;
    }

    @Override
    public List<String> listPublishedButtonPermissions() {
        return listPublishedButtonPermissionsByUserId(StpUtil.getLoginIdAsLong());
    }

    @Override
    public List<String> listPublishedButtonPermissionsByUserId(Long userId) {
        List<Long> roleIds = listNormalRoleIdsByUserId(userId);
        if (CollUtil.isEmpty(roleIds)) {
            return List.of();
        }

        List<RoleResourcePublished> bindings = roleResourcePublishedMapperManager.lambdaQuery()
                .in(RoleResourcePublished::getRoleId, roleIds)
                .eq(RoleResourcePublished::getDelFlag, 0)
                .list();
        if (CollUtil.isEmpty(bindings)) {
            return List.of();
        }

        List<Long> resourceIds = bindings.stream()
                .map(RoleResourcePublished::getResourceId)
                .distinct()
                .toList();
        List<ResourcePublished> resources = resourcePublishedMapperManager.lambdaQuery()
                .in(ResourcePublished::getId, resourceIds)
                .eq(ResourcePublished::getDelFlag, 0)
                .list();
        return resources.stream()
                .filter(resource -> NewPermissionResourceTypeEnum.BUTTON.getCode().equals(resource.getResourceType()))
                .filter(resource -> Objects.equals(resource.getStatus(), 1))
                .map(ResourcePublished::getPermissionCode)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
    }

    private ResourceDraft findDraftById(Long id) {
        ResourceDraft resource = resourceDraftMapperManager.getById(id);
        Assert.notNull(resource, "资源不存在");
        Assert.isTrue(Objects.equals(resource.getDelFlag(), 0), "资源不存在");
        return resource;
    }

    private boolean resourceKeyUnused(Long id, String resourceKey) {
        ResourceDraft resource = resourceDraftMapperManager.findByResourceKey(resourceKey);
        return resource == null || Objects.equals(resource.getId(), id);
    }

    private NewPermissionResourceDTO toResourceDTO(ResourceDraft resource) {
        if (resource == null) {
            return null;
        }
        NewPermissionResourceDTO dto = new NewPermissionResourceDTO();
        dto.setId(resource.getId());
        dto.setResourceKey(resource.getResourceKey());
        dto.setResourceType(resource.getResourceType());
        dto.setParentResourceId(resource.getParentResourceId());
        dto.setPageKey(resource.getPageKey());
        dto.setTitle(resource.getTitle());
        dto.setIcon(resource.getIcon());
        dto.setSortOrder(resource.getSortOrder());
        dto.setHidden(resource.getHidden());
        dto.setPermissionCode(resource.getPermissionCode());
        dto.setButtonGroup(resource.getButtonGroup());
        dto.setRemark(resource.getRemark());
        dto.setStatus(resource.getStatus());
        return dto;
    }

    private NewPermissionResourceDTO toResourceDTO(ResourcePublished resource) {
        if (resource == null) {
            return null;
        }
        NewPermissionResourceDTO dto = new NewPermissionResourceDTO();
        dto.setId(resource.getId());
        dto.setResourceKey(resource.getResourceKey());
        dto.setResourceType(resource.getResourceType());
        dto.setParentResourceId(resource.getParentResourceId());
        dto.setPageKey(resource.getPageKey());
        dto.setTitle(resource.getTitle());
        dto.setIcon(resource.getIcon());
        dto.setSortOrder(resource.getSortOrder());
        dto.setHidden(resource.getHidden());
        dto.setPermissionCode(resource.getPermissionCode());
        dto.setButtonGroup(resource.getButtonGroup());
        dto.setRemark(resource.getRemark());
        dto.setStatus(resource.getStatus());
        return dto;
    }

    private ResourcePublished toPublishedResource(ResourceDraft resource) {
        ResourcePublished published = new ResourcePublished();
        published.setResourceKey(resource.getResourceKey());
        published.setResourceType(resource.getResourceType());
        published.setParentResourceId(0L);
        published.setPageKey(resource.getPageKey());
        published.setTitle(resource.getTitle());
        published.setIcon(resource.getIcon());
        published.setSortOrder(resource.getSortOrder());
        published.setHidden(resource.getHidden());
        published.setPermissionCode(resource.getPermissionCode());
        published.setButtonGroup(resource.getButtonGroup());
        published.setRemark(resource.getRemark());
        published.setStatus(resource.getStatus());
        published.setCreatedBy(resource.getCreatedBy());
        published.setCreatedAt(resource.getCreatedAt());
        published.setUpdatedBy(resource.getUpdatedBy());
        published.setUpdatedAt(resource.getUpdatedAt());
        published.setDelFlag(0);
        return published;
    }

    private void fixPublishedParentResourceIds(List<ResourceDraft> draftResources) {
        Map<Long, String> draftIdToKey = draftResources.stream()
                .collect(Collectors.toMap(ResourceDraft::getId, ResourceDraft::getResourceKey));
        Map<String, ResourcePublished> publishedMap = resourcePublishedMapperManager.listActiveResources().stream()
                .collect(Collectors.toMap(ResourcePublished::getResourceKey, resource -> resource));
        for (ResourceDraft draftResource : draftResources) {
            ResourcePublished publishedResource = publishedMap.get(draftResource.getResourceKey());
            if (publishedResource == null) {
                continue;
            }
            String parentKey = draftIdToKey.get(draftResource.getParentResourceId());
            ResourcePublished parent = parentKey == null ? null : publishedMap.get(parentKey);
            Long parentResourceId = parent == null ? 0L : parent.getId();
            if (Objects.equals(publishedResource.getParentResourceId(), parentResourceId)) {
                continue;
            }
            publishedResource.setParentResourceId(parentResourceId);
            Assert.isTrue(resourcePublishedMapperManager.updateById(publishedResource), "更新发布资源父级失败");
        }
    }

    private List<NewPermissionResourceDTO> buildTree(List<? extends Object> resources) {
        Map<Long, NewPermissionResourceDTO> nodeMap = new LinkedHashMap<>();
        List<NewPermissionResourceDTO> roots = new ArrayList<>();
        for (Object item : resources) {
            NewPermissionResourceDTO dto = item instanceof ResourceDraft draft ? toResourceDTO(draft) : toResourceDTO((ResourcePublished) item);
            if (dto == null) {
                continue;
            }
            nodeMap.put(dto.getId(), dto);
        }
        for (NewPermissionResourceDTO dto : nodeMap.values()) {
            Long parentId = dto.getParentResourceId();
            if (parentId == null || parentId == 0 || !nodeMap.containsKey(parentId)) {
                roots.add(dto);
                continue;
            }
            nodeMap.get(parentId).getChildren().add(dto);
        }
        return roots;
    }

    private String currentUserName() {
        return String.valueOf(StpUtil.getLoginIdAsLong());
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private Integer defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private Integer defaultInteger(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }

    private Boolean defaultBoolean(Boolean value) {
        return value != null && value;
    }

    private Long normalizeParentResourceId(Long parentResourceId) {
        return parentResourceId == null ? 0L : parentResourceId;
    }

    private boolean isValidResourceType(String resourceType) {
        return NewPermissionResourceTypeEnum.PAGE.getCode().equals(resourceType)
                || NewPermissionResourceTypeEnum.MENU.getCode().equals(resourceType)
                || NewPermissionResourceTypeEnum.BUTTON.getCode().equals(resourceType);
    }

    private SysRole getAdminRole() {
        return sysRoleService.lambdaQuery()
                .eq(SysRole::getRoleKey, UserConstants.ADMIN_ROLE_KEY)
                .eq(SysRole::getDelFlag, 0)
                .one();
    }

    private boolean isAdminRole(SysRole role) {
        return role != null && UserConstants.ADMIN_ROLE_KEY.equals(role.getRoleKey());
    }

    private List<ResourceDraft> ensureAdminDraftRoleResources() {
        return ensureAdminDraftRoleResources(getAdminRole());
    }

    private List<ResourceDraft> ensureAdminDraftRoleResources(SysRole adminRole) {
        if (!isAdminRole(adminRole)) {
            return List.of();
        }
        List<ResourceDraft> resources = resourceDraftMapperManager.listActiveResources();
        roleResourceDraftMapperManager.deleteByRoleId(adminRole.getRoleId());
        if (CollUtil.isEmpty(resources)) {
            return resources;
        }
        List<RoleResourceDraft> bindings = buildDraftBindings(
                adminRole.getRoleId(),
                resources.stream().map(ResourceDraft::getId).collect(Collectors.toCollection(LinkedHashSet::new))
        );
        Assert.isTrue(roleResourceDraftMapperManager.saveBatch(bindings), "补齐超级管理员草稿授权失败");
        return resources;
    }

    private List<RoleResourceDraft> buildDraftBindings(Long roleId, Set<Long> resourceIds) {
        List<RoleResourceDraft> bindings = new ArrayList<>();
        LocalDateTime now = now();
        String user = currentUserName();
        for (Long resourceId : resourceIds) {
            RoleResourceDraft binding = new RoleResourceDraft();
            binding.setRoleId(roleId);
            binding.setResourceId(resourceId);
            binding.setCreatedBy(user);
            binding.setCreatedAt(now);
            binding.setUpdatedBy(user);
            binding.setUpdatedAt(now);
            binding.setDelFlag(0);
            bindings.add(binding);
        }
        return bindings;
    }

    private RoleResourcePublished buildPublishedBinding(Long roleId, Long resourceId, LocalDateTime now, String user) {
        RoleResourcePublished publishedBinding = new RoleResourcePublished();
        publishedBinding.setRoleId(roleId);
        publishedBinding.setResourceId(resourceId);
        publishedBinding.setCreatedBy(user);
        publishedBinding.setCreatedAt(now);
        publishedBinding.setUpdatedBy(user);
        publishedBinding.setUpdatedAt(now);
        publishedBinding.setDelFlag(0);
        return publishedBinding;
    }

    private List<Long> listNormalRoleIdsByUserId(Long userId) {
        List<Long> roleIds = sysUserRoleService.listRoleIdsByUserId(userId);
        if (CollUtil.isEmpty(roleIds)) {
            return List.of();
        }
        return sysRoleService.lambdaQuery()
                .in(SysRole::getRoleId, roleIds)
                .eq(SysRole::getStatus, UserConstants.ROLE_STATUS_NORMAL)
                .eq(SysRole::getDelFlag, 0)
                .list()
                .stream()
                .map(SysRole::getRoleId)
                .toList();
    }

    private String resolvePublishedAt(List<ResourcePublished> resources) {
        List<LocalDateTime> timestamps = new ArrayList<>();
        resources.stream()
                .map(ResourcePublished::getUpdatedAt)
                .filter(Objects::nonNull)
                .forEach(timestamps::add);
        roleResourcePublishedMapperManager.lambdaQuery()
                .eq(RoleResourcePublished::getDelFlag, 0)
                .list()
                .stream()
                .map(RoleResourcePublished::getUpdatedAt)
                .filter(Objects::nonNull)
                .forEach(timestamps::add);
        return timestamps.stream()
                .max(LocalDateTime::compareTo)
                .map(LocalDateTime::toString)
                .orElse("");
    }

    private List<ResourcePublished> filterNavigationResourcesForCurrentUser(List<ResourcePublished> resources) {
        Long userId = StpUtil.getLoginIdAsLong();
        List<Long> roleIds = listNormalRoleIdsByUserId(userId);
        if (CollUtil.isEmpty(roleIds) || CollUtil.isEmpty(resources)) {
            return List.of();
        }

        List<RoleResourcePublished> bindings = roleResourcePublishedMapperManager.lambdaQuery()
                .in(RoleResourcePublished::getRoleId, roleIds)
                .eq(RoleResourcePublished::getDelFlag, 0)
                .list();
        if (CollUtil.isEmpty(bindings)) {
            return List.of();
        }

        Map<Long, ResourcePublished> resourceMap = resources.stream()
                .collect(Collectors.toMap(ResourcePublished::getId, resource -> resource));
        Set<Long> selectedIds = new LinkedHashSet<>();
        for (RoleResourcePublished binding : bindings) {
            ResourcePublished resource = resourceMap.get(binding.getResourceId());
            if (resource == null || Objects.equals(resource.getStatus(), 0)) {
                continue;
            }
            if (NewPermissionResourceTypeEnum.BUTTON.getCode().equals(resource.getResourceType())) {
                continue;
            }
            selectedIds.add(resource.getId());
            appendParentResources(resource, resourceMap, selectedIds);
        }

        return resources.stream()
                .filter(resource -> selectedIds.contains(resource.getId()))
                .filter(resource -> !NewPermissionResourceTypeEnum.BUTTON.getCode().equals(resource.getResourceType()))
                .toList();
    }

    private void appendParentResources(ResourcePublished resource, Map<Long, ResourcePublished> resourceMap, Set<Long> selectedIds) {
        Long parentId = resource.getParentResourceId();
        while (parentId != null && parentId > 0) {
            ResourcePublished parent = resourceMap.get(parentId);
            if (parent == null) {
                return;
            }
            selectedIds.add(parent.getId());
            parentId = parent.getParentResourceId();
        }
    }

    private String calculatePublishedConfigVersion(List<ResourcePublished> resources) {
        List<RoleResourcePublished> bindings = roleResourcePublishedMapperManager.lambdaQuery()
                .eq(RoleResourcePublished::getDelFlag, 0)
                .list();
        return buildConfigVersion(buildPublishedFingerprint(resources, bindings));
    }

    private String calculateDraftConfigVersion() {
        List<ResourceDraft> draftResources = resourceDraftMapperManager.listActiveResources();
        List<RoleResourceDraft> draftBindings = roleResourceDraftMapperManager.lambdaQuery()
                .eq(RoleResourceDraft::getDelFlag, 0)
                .list();
        return buildConfigVersion(buildDraftFingerprint(draftResources, draftBindings));
    }

    private String buildPublishedFingerprint(List<ResourcePublished> resources, List<RoleResourcePublished> bindings) {
        String resourceFingerprint = resources.stream()
                .sorted((left, right) -> defaultString(left.getResourceKey()).compareTo(defaultString(right.getResourceKey())))
                .map(resource -> String.join("|",
                        defaultString(resource.getResourceKey()),
                        defaultString(resource.getResourceType()),
                        String.valueOf(resource.getParentResourceId()),
                        defaultString(resource.getPageKey()),
                        defaultString(resource.getTitle()),
                        defaultString(resource.getIcon()),
                        String.valueOf(resource.getSortOrder()),
                        String.valueOf(Boolean.TRUE.equals(resource.getHidden())),
                        defaultString(resource.getPermissionCode()),
                        defaultString(resource.getButtonGroup()),
                        String.valueOf(resource.getStatus())))
                .collect(Collectors.joining("\n"));

        Map<Long, String> idToKey = resources.stream()
                .collect(Collectors.toMap(ResourcePublished::getId, ResourcePublished::getResourceKey));
        String bindingFingerprint = bindings.stream()
                .sorted((left, right) -> {
                    int roleCompare = left.getRoleId().compareTo(right.getRoleId());
                    if (roleCompare != 0) {
                        return roleCompare;
                    }
                    return defaultString(idToKey.get(left.getResourceId()))
                            .compareTo(defaultString(idToKey.get(right.getResourceId())));
                })
                .map(binding -> binding.getRoleId() + "|" + defaultString(idToKey.get(binding.getResourceId())))
                .collect(Collectors.joining("\n"));
        return resourceFingerprint + "\n---\n" + bindingFingerprint;
    }

    private String buildDraftFingerprint(List<ResourceDraft> resources, List<RoleResourceDraft> bindings) {
        String resourceFingerprint = resources.stream()
                .sorted((left, right) -> defaultString(left.getResourceKey()).compareTo(defaultString(right.getResourceKey())))
                .map(resource -> String.join("|",
                        defaultString(resource.getResourceKey()),
                        defaultString(resource.getResourceType()),
                        String.valueOf(resource.getParentResourceId()),
                        defaultString(resource.getPageKey()),
                        defaultString(resource.getTitle()),
                        defaultString(resource.getIcon()),
                        String.valueOf(resource.getSortOrder()),
                        String.valueOf(Boolean.TRUE.equals(resource.getHidden())),
                        defaultString(resource.getPermissionCode()),
                        defaultString(resource.getButtonGroup()),
                        String.valueOf(resource.getStatus())))
                .collect(Collectors.joining("\n"));

        Map<Long, String> idToKey = resources.stream()
                .collect(Collectors.toMap(ResourceDraft::getId, ResourceDraft::getResourceKey));
        String bindingFingerprint = bindings.stream()
                .sorted((left, right) -> {
                    int roleCompare = left.getRoleId().compareTo(right.getRoleId());
                    if (roleCompare != 0) {
                        return roleCompare;
                    }
                    return defaultString(idToKey.get(left.getResourceId()))
                            .compareTo(defaultString(idToKey.get(right.getResourceId())));
                })
                .map(binding -> binding.getRoleId() + "|" + defaultString(idToKey.get(binding.getResourceId())))
                .collect(Collectors.joining("\n"));
        return resourceFingerprint + "\n---\n" + bindingFingerprint;
    }

    private String buildConfigVersion(String fingerprint) {
        return "v" + UUID.nameUUIDFromBytes(fingerprint.getBytes(StandardCharsets.UTF_8));
    }

    private List<String> collectPublishErrors() {
        List<String> errors = new ArrayList<>();
        List<ResourceDraft> resources = resourceDraftMapperManager.listActiveResources();
        if (CollUtil.isEmpty(resources)) {
            errors.add("至少需要配置一个资源");
            return errors;
        }

        Map<Long, ResourceDraft> resourceMap = resources.stream()
                .collect(Collectors.toMap(ResourceDraft::getId, resource -> resource));
        Set<String> permissionCodes = new LinkedHashSet<>();
        Set<String> pageKeys = new LinkedHashSet<>();
        for (ResourceDraft resource : resources) {
            if (NewPermissionResourceTypeEnum.PAGE.getCode().equals(resource.getResourceType())) {
                if (StrUtil.isBlank(resource.getPageKey())) {
                    errors.add("页面资源必须绑定 pageKey: " + resource.getResourceKey());
                } else if (!pageKeys.add(resource.getPageKey())) {
                    errors.add("页面 pageKey 重复: " + resource.getPageKey());
                }
            }
            if (NewPermissionResourceTypeEnum.MENU.getCode().equals(resource.getResourceType())) {
                validateMenuPageChain(resource, resourceMap, errors);
            }
            if (NewPermissionResourceTypeEnum.BUTTON.getCode().equals(resource.getResourceType())) {
                if (StrUtil.isBlank(resource.getPermissionCode())) {
                    errors.add("按钮资源必须填写权限码: " + resource.getResourceKey());
                } else if (!permissionCodes.add(resource.getPermissionCode())) {
                    errors.add("按钮权限码重复: " + resource.getPermissionCode());
                }
                validateButtonParentChain(resource, resourceMap, errors);
            }
        }

        for (ResourceDraft resource : resources) {
            if (resource.getParentResourceId() != null && resource.getParentResourceId() > 0
                    && !resourceMap.containsKey(resource.getParentResourceId())) {
                errors.add("存在孤儿资源: " + resource.getResourceKey());
            }
        }

        long pageCount = resources.stream()
                .filter(resource -> NewPermissionResourceTypeEnum.PAGE.getCode().equals(resource.getResourceType()))
                .count();
        if (pageCount == 0) {
            errors.add("至少需要配置一个页面资源");
        }

        List<ResourceDraft> enabledPages = resources.stream()
                .filter(this::isEnabledPage)
                .toList();
        if (enabledPages.isEmpty()) {
            errors.add("至少需要一个启用的页面资源作为首页候选");
        }

        List<SysRole> roles = sysRoleService.lambdaQuery()
                .eq(SysRole::getStatus, UserConstants.ROLE_STATUS_NORMAL)
                .eq(SysRole::getDelFlag, 0)
                .list();
        if (CollUtil.isNotEmpty(roles)) {
            List<Long> roleIds = roles.stream()
                    .map(SysRole::getRoleId)
                    .toList();
            Map<Long, Set<Long>> roleResourceIdMap = roleResourceDraftMapperManager.lambdaQuery()
                    .in(RoleResourceDraft::getRoleId, roleIds)
                    .eq(RoleResourceDraft::getDelFlag, 0)
                    .list()
                    .stream()
                    .collect(Collectors.groupingBy(
                            RoleResourceDraft::getRoleId,
                            LinkedHashMap::new,
                            Collectors.mapping(RoleResourceDraft::getResourceId, Collectors.toCollection(LinkedHashSet::new))
                    ));
            for (SysRole role : roles) {
                Set<Long> authorizedResourceIds = roleResourceIdMap.getOrDefault(role.getRoleId(), Set.of());
                Set<Long> visibleResourceIds = collectVisibleResourceIds(authorizedResourceIds, resourceMap);
                boolean hasHomePage = resources.stream()
                        .filter(this::isEnabledPage)
                        .anyMatch(resource -> visibleResourceIds.contains(resource.getId()));
                if (!hasHomePage) {
                    errors.add("角色发布后无可达首页: " + defaultString(role.getRoleName()));
                }
            }
        }

        return errors.stream().distinct().toList();
    }

    private Set<Long> collectVisibleResourceIds(Set<Long> selectedResourceIds, Map<Long, ResourceDraft> resourceMap) {
        Set<Long> visibleResourceIds = new LinkedHashSet<>();
        for (Long resourceId : selectedResourceIds) {
            ResourceDraft resource = resourceMap.get(resourceId);
            if (resource == null || Objects.equals(resource.getStatus(), 0)) {
                continue;
            }
            if (NewPermissionResourceTypeEnum.BUTTON.getCode().equals(resource.getResourceType())) {
                continue;
            }
            visibleResourceIds.add(resourceId);
            appendParentResources(resource, resourceMap, visibleResourceIds);
        }
        return visibleResourceIds;
    }

    private void appendParentResources(ResourceDraft resource, Map<Long, ResourceDraft> resourceMap, Set<Long> selectedIds) {
        Long parentId = resource.getParentResourceId();
        while (parentId != null && parentId > 0) {
            ResourceDraft parent = resourceMap.get(parentId);
            if (parent == null) {
                return;
            }
            selectedIds.add(parent.getId());
            parentId = parent.getParentResourceId();
        }
    }

    private boolean isEnabledPage(ResourceDraft resource) {
        return resource != null
                && NewPermissionResourceTypeEnum.PAGE.getCode().equals(resource.getResourceType())
                && Objects.equals(resource.getStatus(), 1)
                && StrUtil.isNotBlank(resource.getPageKey());
    }

    private NewPermissionRolePreviewDTO.PagePreviewDTO toPagePreview(ResourceDraft resource) {
        NewPermissionRolePreviewDTO.PagePreviewDTO preview = new NewPermissionRolePreviewDTO.PagePreviewDTO();
        preview.setResourceId(resource.getId());
        preview.setResourceKey(resource.getResourceKey());
        preview.setTitle(resource.getTitle());
        preview.setPageKey(resource.getPageKey());
        return preview;
    }

    private List<NewPermissionRolePreviewDTO.PageButtonPreviewDTO> buildPageButtonPreviews(
            List<ResourceDraft> resources,
            Set<Long> selectedResourceIds,
            List<ResourceDraft> accessiblePages,
            Map<Long, ResourceDraft> resourceMap
    ) {
        Map<Long, NewPermissionRolePreviewDTO.PageButtonPreviewDTO> pageButtonMap = new LinkedHashMap<>();
        for (ResourceDraft page : accessiblePages) {
            NewPermissionRolePreviewDTO.PageButtonPreviewDTO pagePreview = new NewPermissionRolePreviewDTO.PageButtonPreviewDTO();
            pagePreview.setResourceId(page.getId());
            pagePreview.setResourceKey(page.getResourceKey());
            pagePreview.setTitle(page.getTitle());
            pagePreview.setPageKey(page.getPageKey());
            pageButtonMap.put(page.getId(), pagePreview);
        }
        for (ResourceDraft resource : resources) {
            if (!selectedResourceIds.contains(resource.getId())) {
                continue;
            }
            if (!NewPermissionResourceTypeEnum.BUTTON.getCode().equals(resource.getResourceType())) {
                continue;
            }
            Long pageId = resolveAncestorPageId(resource, resourceMap);
            if (pageId == null) {
                continue;
            }
            NewPermissionRolePreviewDTO.PageButtonPreviewDTO pagePreview = pageButtonMap.get(pageId);
            if (pagePreview == null) {
                continue;
            }
            NewPermissionRolePreviewDTO.ButtonPreviewDTO buttonPreview = new NewPermissionRolePreviewDTO.ButtonPreviewDTO();
            buttonPreview.setResourceId(resource.getId());
            buttonPreview.setResourceKey(resource.getResourceKey());
            buttonPreview.setTitle(resource.getTitle());
            buttonPreview.setPermissionCode(resource.getPermissionCode());
            buttonPreview.setButtonGroup(resource.getButtonGroup());
            pagePreview.getButtons().add(buttonPreview);
        }
        return pageButtonMap.values().stream()
                .filter(pagePreview -> CollUtil.isNotEmpty(pagePreview.getButtons()))
                .toList();
    }

    private Long resolveAncestorPageId(ResourceDraft resource, Map<Long, ResourceDraft> resourceMap) {
        Long currentId = resource.getParentResourceId();
        while (currentId != null && currentId > 0) {
            ResourceDraft current = resourceMap.get(currentId);
            if (current == null) {
                return null;
            }
            if (NewPermissionResourceTypeEnum.PAGE.getCode().equals(current.getResourceType())) {
                return current.getId();
            }
            currentId = current.getParentResourceId();
        }
        return null;
    }

    private void validateMenuPageChain(ResourceDraft menu, Map<Long, ResourceDraft> resourceMap, List<String> errors) {
        if (menu.getParentResourceId() == null || menu.getParentResourceId() <= 0) {
            return;
        }

        ResourceDraft current = menu;
        Set<Long> visitedIds = new LinkedHashSet<>();
        while (current.getParentResourceId() != null && current.getParentResourceId() > 0) {
            Long parentId = current.getParentResourceId();
            if (!visitedIds.add(parentId)) {
                errors.add("菜单资源父级链路存在循环: " + menu.getResourceKey());
                return;
            }
            ResourceDraft parent = resourceMap.get(parentId);
            if (parent == null) {
                errors.add("菜单资源父级不存在: " + menu.getResourceKey());
                return;
            }
            if (NewPermissionResourceTypeEnum.BUTTON.getCode().equals(parent.getResourceType())) {
                errors.add("菜单资源不能挂在按钮下: " + menu.getResourceKey());
                return;
            }
            if (!Objects.equals(parent.getStatus(), 1)) {
                errors.add("菜单资源父级未启用: " + menu.getResourceKey());
            }
            current = parent;
        }
    }

    private void validateButtonParentChain(ResourceDraft button, Map<Long, ResourceDraft> resourceMap, List<String> errors) {
        if (button.getParentResourceId() == null || button.getParentResourceId() <= 0) {
            errors.add("按钮资源必须挂在有效页面或菜单下: " + button.getResourceKey());
            return;
        }

        ResourceDraft current = button;
        Set<Long> visitedIds = new LinkedHashSet<>();
        while (current.getParentResourceId() != null && current.getParentResourceId() > 0) {
            Long parentId = current.getParentResourceId();
            if (!visitedIds.add(parentId)) {
                errors.add("按钮资源父级链路存在循环: " + button.getResourceKey());
                return;
            }
            ResourceDraft parent = resourceMap.get(parentId);
            if (parent == null || Objects.equals(parent.getDelFlag(), 1)) {
                errors.add("按钮资源父级不存在: " + button.getResourceKey());
                return;
            }
            if (!Objects.equals(parent.getStatus(), 1)) {
                errors.add("按钮资源父级未启用: " + button.getResourceKey());
            }
            if (NewPermissionResourceTypeEnum.BUTTON.getCode().equals(parent.getResourceType())) {
                errors.add("按钮资源不能挂在按钮下: " + button.getResourceKey());
                return;
            }
            if (NewPermissionResourceTypeEnum.PAGE.getCode().equals(parent.getResourceType())) {
                if (StrUtil.isBlank(parent.getPageKey())) {
                    errors.add("按钮资源挂载的页面缺少 pageKey: " + button.getResourceKey());
                }
                return;
            }
            current = parent;
        }
        errors.add("按钮资源必须能归属到有效页面: " + button.getResourceKey());
    }
}
