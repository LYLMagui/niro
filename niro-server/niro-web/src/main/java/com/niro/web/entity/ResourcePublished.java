package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 新权限资源已发布表
 */
@Data
@TableName("resource_published")
public class ResourcePublished implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String resourceKey;

    private String resourceType;

    private Long parentResourceId;

    private String pageKey;

    private String title;

    private String icon;

    private Integer sortOrder;

    private Boolean hidden;

    private String permissionCode;

    private String buttonGroup;

    private String remark;

    private Integer status;

    private String createdBy;

    private LocalDateTime createdAt;

    private String updatedBy;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer delFlag;
}
