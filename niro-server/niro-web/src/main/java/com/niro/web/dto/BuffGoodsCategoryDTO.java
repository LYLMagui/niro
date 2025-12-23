package com.niro.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 商品分类树节点 DTO
 *
 * @author liyl
 * @since 2025-12-23
 */
@Data
@Schema(description = "商品分类树节点")
public class BuffGoodsCategoryDTO {

    @Schema(description = "分类ID")
    private Long id;

    @Schema(description = "分类名称")
    private String name;

    @Schema(description = "内部标识")
    private String internalName;
    
    @Schema(description = "父级ID")
    private Long parentId;

    @Schema(description = "子分类列表")
    private List<BuffGoodsCategoryDTO> children;
    
    // TDesign Cascader 需要 value 和 label
    @Schema(description = "值 (同ID)")
    public Long getValue() {
        return id;
    }
    
    @Schema(description = "标签 (同Name)")
    public String getLabel() {
        return name;
    }
}
