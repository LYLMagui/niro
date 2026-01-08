package com.niro.web.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BUFF印花价值DTO
 *
 * @author liyl
 * @date 2026/01/08
 */
@Data
public class BuffStickerDTO {

    /**
     * 主键ID
     */
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
     * 印花本体市场底价
     */
    private BigDecimal price;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 最后更新时间
     */
    private LocalDateTime updateTime;
}
