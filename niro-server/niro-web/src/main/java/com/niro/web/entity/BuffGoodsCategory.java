package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品分类实体
 *
 * @author liyl
 * @since 2025-12-23
 */
@Data
@TableName("buff_goods_categories")
public class BuffGoodsCategory {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 分类名称
     */
    private String name;

    /**
     * 父级ID (0表示一级分类)
     */
    private Long parentId;

    /**
     * 内部标识
     */
    private String internalName;

    /**
     * 参数类型: type(对应category参数), weapon(对应weapon参数)
     */
    private String categoryType;

    /**
     * 完整内部标识
     */
    private String fullInternalName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
