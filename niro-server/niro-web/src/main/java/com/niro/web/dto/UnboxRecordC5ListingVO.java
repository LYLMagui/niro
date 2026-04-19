package com.niro.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 开箱记录 C5 在售信息
 */
@Data
@Schema(description = "开箱记录 C5 在售信息")
public class UnboxRecordC5ListingVO {

    @Schema(description = "C5 商品ID")
    private String productId;

    @Schema(description = "价格")
    private BigDecimal price;

    @Schema(description = "卖家ID")
    private String sellerUid;

    @Schema(description = "卖家名称，当前回填卖家UID")
    private String sellerName;

    @Schema(description = "磨损")
    private BigDecimal wear;

    @Schema(description = "发货方式")
    private Integer delivery;

    @Schema(description = "图片地址")
    private String imageUrl;

    @Schema(description = "steam 市场 hashName")
    private String marketHashName;

    @Schema(description = "饰品名称")
    private String itemName;
}
