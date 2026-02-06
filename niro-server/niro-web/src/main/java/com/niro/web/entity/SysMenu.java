package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单权限表
 */
@Data
@TableName("sys_menu")
public class SysMenu implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 菜单ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 菜单名称
     */
    private String title;

    /**
     * 父菜单ID
     */
    private Long parentId;

    /**
     * 路由名称
     */
    private String name;

    /**
     * 路由路径
     */
    private String path;

    /**
     * 组件路径
     */
    private String component;

    /**
     * 菜单图标
     */
    private String icon;

    /**
     * 显示顺序
     */
    private Integer sortOrder;

    /**
     * 菜单类型（0目录 1菜单 2按钮）
     */
    private Integer type;

    /**
     * 权限标识
     */
    private String permission;

    /**
     * 是否隐藏（false显示 true隐藏）
     */
    private Boolean hidden;

    /**
     * 是否缓存（false不缓存 true缓存）
     */
    private Boolean keepAlive;

    /**
     * 重定向地址
     */
    private String redirect;

    /**
     * 逻辑删除标识（0正常 1删除）
     */
    @TableLogic
    private Integer delFlag;

    /**
     * 子菜单
     */
    @TableField(exist = false)
    private List<SysMenu> children = new ArrayList<>();
}
