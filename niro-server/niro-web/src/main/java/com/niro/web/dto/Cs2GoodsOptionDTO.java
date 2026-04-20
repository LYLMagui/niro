package com.niro.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * CS2 商品选择项
 */
@Data
@Schema(description = "CS2 商品选择项")
public class Cs2GoodsOptionDTO {

    @Schema(description = "商品主键ID")
    private Long id;

    @Schema(description = "商品展示名称")
    private String displayName;

    @Schema(description = "Steam 市场 hash 名称")
    private String marketHashName;

    @Schema(description = "商品类型")
    private String itemType;

    @Schema(description = "武器类型")
    private String weaponType;

    @Schema(description = "稀有度")
    private String rarity;

    @Schema(description = "外观名称")
    private String exteriorName;

    @Schema(description = "是否存在外观维度")
    private Boolean hasExterior;

    @Schema(description = "商品图片地址")
    private String imageUrl;
}
