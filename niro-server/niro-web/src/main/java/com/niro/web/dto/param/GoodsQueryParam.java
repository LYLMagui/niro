package com.niro.web.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 商品分页查询参数
 * </p>
 *
 * @author liyl
 * @since 2025-12-22
 */
@Data
@Schema(description = "商品分页查询参数")
public class GoodsQueryParam {

    @Schema(description = "当前页码", defaultValue = "1")
    private Long page = 1L;

    @Schema(description = "每页大小", defaultValue = "10")
    private Long pageSize = 10L;

    @Schema(description = "商品ID (精确匹配)")
    private Long goodsId;

    @Schema(description = "商品名称/简称")
    private String name;

    @Schema(description = "磨损/外观 (例如: 久经沙场)")
    private String exterior;

    @Schema(description = "分类ID")
    private Long categoryId;
}
