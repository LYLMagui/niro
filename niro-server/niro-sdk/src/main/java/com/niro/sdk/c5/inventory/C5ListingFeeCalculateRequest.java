package com.niro.sdk.c5.inventory;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * C5 上架手续费计算请求。
 */
@Data
@Accessors(chain = true)
public class C5ListingFeeCalculateRequest {

    /**
     * 手续费计算饰品列表。
     */
    private List<CalculateItem> dataList;

    /**
     * 手续费计算饰品。
     */
    @Data
    @Accessors(chain = true)
    public static class CalculateItem {

        /**
         * 饰品出售价格。
         */
        private BigDecimal price;

        /**
         * 库存列表返回的 styleToken 字段。
         */
        private String styleToken;

        /**
         * 库存列表返回的 token 字段。
         */
        private String token;
    }
}
