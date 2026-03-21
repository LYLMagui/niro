package com.niro.web.dto.param;

import lombok.Data;

/**
 * Menu create request.
 */
@Data
public class CreateMenuParam {

    private Long parentId;

    private String title;

    private String name;

    private String path;

    private String component;

    private String icon;

    private Integer sortOrder;

    private Integer type;

    private String permission;

    private Integer status;

    private Boolean hidden;

    private Boolean keepAlive;

    private String redirect;
}