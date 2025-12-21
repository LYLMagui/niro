package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 *
 *
 * @author liyl
 * @date 2025/12/21
 */
@Data
@TableName("buff_goods")
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
     * 创建时间
     */
    private LocalDateTime createTime;
    /**
     * 修改时间
     */
    private LocalDateTime updateTime;
    
}
