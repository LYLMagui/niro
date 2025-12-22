package com.niro.web.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * <p>
 * 商品传输对象
 * </p>
 *
 * @author liyl
 * @since 2025-12-22
 */
@Data
@Schema(description = "商品传输对象")
public class BuffGoodsDTO {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    /**
     * buff的商品id
     */
    @Schema(description = "buff的商品id")
    private Long goodsId;

    /**
     * 商品全名
     */
    @Schema(description = "商品全名")
    private String name;

    /**
     * 商品简称
     */
    @Schema(description = "商品简称")
    private String shortName;

    /**
     * 商品内部表示
     */
    @Schema(description = "商品内部表示")
    private String internalName;

    /**
     * 分类id
     */
    @Schema(description = "分类id")
    private Long categoryId;

    /**
     * 稀有度
     */
    @Schema(description = "稀有度")
    private String rarity;

    /**
     * 外观/磨损
     */
    @Schema(description = "外观/磨损")
    private String exterior;

    /**
     * steam市场hash名称
     */
    @Schema(description = "steam市场hash名称")
    private String marketHashName;

    /**
     * 图标url
     */
    @Schema(description = "图标url")
    private String iconUrl;

    /**
     * 原始图标url
     */
    @Schema(description = "原始图标url")
    private String originalIconUrl;
}