package com.niro.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 路由显示信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetaDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 路由标题
     */
    private String title;

    /**
     * 路由图标
     */
    private String icon;

    /**
     * 是否隐藏
     */
    private Boolean hidden;

    /**
     * 是否缓存
     */
    private Boolean keepAlive;
}
