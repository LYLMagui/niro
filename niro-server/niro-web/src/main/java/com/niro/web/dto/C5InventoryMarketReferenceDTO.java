package com.niro.web.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * C5 同平台在售参考 DTO。
 */
@Data
public class C5InventoryMarketReferenceDTO {

    /**
     * C5 在售商品 ID。
     */
    private String productId;

    /**
     * C5 在售价格。
     */
    private BigDecimal price;

    /**
     * 发货方式：1 人工，2 自动。
     */
    private Integer delivery;

    /**
     * 是否支持议价。
     */
    private Boolean acceptBargain;

    /**
     * 商品图片。
     */
    private String imageUrl;

    /**
     * 卖家 UID。
     */
    private String sellerUid;

    /**
     * Steam 资产 ID。
     */
    private String assetId;

    /**
     * 挂单磨损。
     */
    private BigDecimal wear;

    /**
     * Steam 市场 Hash 名称。
     */
    private String marketHashName;
}
