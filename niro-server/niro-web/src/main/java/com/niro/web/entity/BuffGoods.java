package com.niro.web.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import org.apache.ibatis.type.JdbcType;

import lombok.Data;

/**
 *
 *
 * @author liyl
 * @date 2025/12/21
 */
@Data
@TableName(value = "buff_goods", autoResultMap = true)
public class BuffGoods {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * buff的商品id
     */
    private Long goodsId;
    /**
     * 商品全名
     */
    private String name;
    /**
     * 商品简称
     */
    private String shortName;
    /**
     * 商品内部表示
     */
    private String internalName;
    /**
     * 分类id
     */
    private Long categoryId;
    /**
     * 稀有度
     */
    private String rarity;
    /**
     * 外观/磨损
     */
    private String exterior;
    /**
     * steam市场hash名称
     */
    private String marketHashName;
    /**
     * 图标url
     */
    private String iconUrl;
    /**
     * 原始图标url
     */
    private String originalIconUrl;

    /**
     * 标签JSON数据
     */
    @TableField(typeHandler = JacksonTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Object tags;

    /**
     * 最后同步版本标识
     */
    private String lastSyncTag;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    /**
     * 修改时间
     */
    private LocalDateTime updateTime;
}
