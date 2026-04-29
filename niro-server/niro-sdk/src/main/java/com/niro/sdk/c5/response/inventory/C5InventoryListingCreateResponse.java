package com.niro.sdk.c5.response.inventory;

import lombok.Data;

import java.util.List;

/**
 * C5 库存饰品上架响应。
 */
@Data
public class C5InventoryListingCreateResponse {

    /**
     * 店铺是否开启。
     */
    private Boolean shopOn;

    /**
     * 成功数量。
     */
    private Integer succeed;

    /**
     * 失败数量。
     */
    private Integer failed;

    /**
     * 失败列表。
     */
    private List<String> failedList;

    /**
     * 成功列表。
     */
    private List<SuccessItem> successList;

    /**
     * 定价过高饰品列表。
     */
    private List<String> highPriceItemIdList;

    /**
     * 价格检查结果。
     */
    private Object priceCheckResult;

    /**
     * 上架成功饰品。
     */
    @Data
    public static class SuccessItem {

        /**
         * 饰品唯一 ID。
         */
        private String assetId;

        /**
         * 上架后的在售 ID。
         */
        private String productId;
    }
}
