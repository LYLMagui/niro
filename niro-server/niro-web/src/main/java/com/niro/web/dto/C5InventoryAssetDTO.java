package com.niro.web.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * C5 库存真实资产明细 DTO。
 */
@Data
public class C5InventoryAssetDTO {

    /**
     * 本地库存快照 ID。
     */
    private Long id;

    /**
     * C5 扫货账号 ID。
     */
    private Long accountId;

    /**
     * C5 扫货账号名称。
     */
    private String accountName;

    /**
     * Steam 资产 ID。
     */
    private String assetId;

    /**
     * C5 返回价格。
     */
    private BigDecimal price;

    /**
     * 磨损值。
     */
    private BigDecimal wear;

    /**
     * 是否可交易。
     */
    private Boolean ifTradable;

    /**
     * 可交易时间。
     */
    private String tradableTime;

    /**
     * 库存状态。
     */
    private String inventoryStatus;

    /**
     * 商品名称。
     */
    private String name;

    /**
     * Steam 市场 Hash 名称。
     */
    private String marketHashName;

    /**
     * 商品图片。
     */
    private String imageUrl;

    /**
     * 最近同步时间。
     */
    private LocalDateTime lastSyncTime;
}
