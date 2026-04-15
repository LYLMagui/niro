package com.niro.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 商品简单信息传输对象 (用于下拉选择)
 * </p>
 *
 * @author liyl
 * @since 2025-12-22
 */
@Data
@Schema(description = "商品简单信息传输对象")
public class BuffGoodsSimpleDTO {

    @Schema(description = "商品主键ID")
    private Long id;

    @Schema(description = "Buff商品ID")
    private Long goodsId;

    @Schema(description = "商品名称")
    private String name;

    @Schema(description = "父级分类名称")
    private String parentCategoryName;
}
