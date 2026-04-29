package com.niro.web.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * C5 库存上架手续费 DTO。
 */
@Data
public class C5InventoryListingFeeDTO {

    /**
     * C5 扫货账号 ID。
     */
    private Long accountId;

    /**
     * 本地库存快照 ID。
     */
    private Long inventoryItemId;

    /**
     * Steam 资产 ID。
     */
    private String assetId;

    /**
     * C5 饰品类目 ID。
     */
    private String itemId;

    /**
     * 上架价格。
     */
    private BigDecimal price;

    /**
     * 手续费。
     */
    private BigDecimal fee;

    /**
     * 卖家实收价。
     */
    private BigDecimal sellerPrice;

    /**
     * 免手续费价格。
     */
    private BigDecimal freeFeePrice;

    /**
     * 收入字段。
     */
    private BigDecimal income;

    /**
     * 实收金额字段。
     */
    private BigDecimal actualAmount;

    /**
     * C5 原始返回数据。
     */
    private Map<String, Object> rawData;
}
