package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BUFF印花元数据及价值表
 *
 * @author liyl
 * @date 2026/01/08
 */
@Data
@TableName("buff_sticker")
public class BuffSticker {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * BUFF平台印花唯一标识ID
     */
    private Long stickerId;
    /**
     * 印花中文名称
     */
    private String name;
    /**
     * 印花图片预览链接
     */
    private String imageUrl;
    /**
     * 印花本体市场底价(用于计算溢价)
     */
    private BigDecimal price;
    /**
     * 在售数量
     */
    private Integer sellNum;
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    /**
     * 最后更新时间
     */
    private LocalDateTime updateTime;
}
