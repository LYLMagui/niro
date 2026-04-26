package com.niro.web.dto.newpermission;

import lombok.Data;

/**
 * 新权限发布结果
 */
@Data
public class NewPermissionPublishResultDTO {
    private Boolean success;
    private String message;
    private String configVersion;
    private String publishedAt;
}
