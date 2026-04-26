package com.niro.web.dto.newpermission;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 新权限导航视图
 */
@Data
public class NewPermissionNavigationDTO {
    private String configVersion;
    private String publishedAt;
    private List<NewPermissionResourceDTO> menus = new ArrayList<>();
}
