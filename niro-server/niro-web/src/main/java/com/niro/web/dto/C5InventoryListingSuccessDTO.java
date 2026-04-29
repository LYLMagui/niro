package com.niro.web.dto;

import lombok.Data;

/**
 * C5 库存上架成功项 DTO。
 */
@Data
public class C5InventoryListingSuccessDTO {

    /**
     * 饰品唯一 ID。
     */
    private String assetId;

    /**
     * 上架后的在售 ID。
     */
    private String productId;
}
