package com.niro.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 路由配置信息 - 大厂规范版本
 * 规范说明：
 * 1. path: 统一使用相对路径（如 dashboard, task/manager/buff）
 * 2. component: 组件映射名，前端据此加载对应组件
 * 3. name: 路由唯一标识，用于 keep-alive 和面包屑
 * 4. meta: 元数据，包含标题、图标、缓存等
 */
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RouterDTO implements Serializable {
    private static final long serialVersionUID = 1L;

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
     * 重定向
     */
    private String redirect;

    /**
     * 元数据
     */
    private MetaDTO meta;

    /**
     * 子路由
     */
    private List<RouterDTO> children;
}
