package com.niro.sdk.c5.inventory;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * C5 库存饰品上架请求。
 */
@Data
@Accessors(chain = true)
public class C5InventoryListingCreateRequest {

    /**
     * 上架饰品列表。
     */
    private List<ListingItem> dataList;

    /**
     * 上架饰品。
     */
    @Data
    @Accessors(chain = true)
    public static class ListingItem {

        /**
         * 饰品上架价格。
         */
        private BigDecimal price;

        /**
         * 描述。
         */
        private String description;

        /**
         * 是否允许还价：0 否，1 是。
         */
        private Integer acceptBargain;

        /**
         * 库存列表返回的 token 字段。
         */
        private String token;

        /**
         * 库存列表返回的 styleToken 字段。
         */
        private String styleToken;
    }
}
