package com.niro.web.dto.newpermission;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 新权限角色预览视图
 */
@Data
public class NewPermissionRolePreviewDTO {
    private Long roleId;
    private String roleName;
    private List<String> visiblePages = new ArrayList<>();
    private List<String> visibleMenus = new ArrayList<>();
    private List<String> enabledButtons = new ArrayList<>();
    private String homePageKey;
    private String homePageTitle;
    private List<NewPermissionResourceDTO> navigationTree = new ArrayList<>();
    private List<PagePreviewDTO> accessiblePages = new ArrayList<>();
    private List<PageButtonPreviewDTO> pageButtons = new ArrayList<>();

    /**
     * 可进入页面预览项
     */
    @Data
    public static class PagePreviewDTO {
        private Long resourceId;
        private String resourceKey;
        private String title;
        private String pageKey;
    }

    /**
     * 页面按钮预览项
     */
    @Data
    public static class PageButtonPreviewDTO {
        private Long resourceId;
        private String resourceKey;
        private String title;
        private String pageKey;
        private List<ButtonPreviewDTO> buttons = new ArrayList<>();
    }

    /**
     * 按钮预览项
     */
    @Data
    public static class ButtonPreviewDTO {
        private Long resourceId;
        private String resourceKey;
        private String title;
        private String permissionCode;
        private String buttonGroup;
    }
}
