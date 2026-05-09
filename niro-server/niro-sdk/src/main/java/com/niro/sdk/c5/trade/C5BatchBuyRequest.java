package com.niro.sdk.c5.trade;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

@Data
@Accessors(chain = true)
public class C5BatchBuyRequest {
    /**
     * 交易链接
     */
    private String tradeUrl;
    /**
     * 商品列表
     */
    private List<BatchProduct> productList;

    @Data
    public static class BatchProduct {
        /**
         * 在售id
         */
        private Long productId;
        /**
         * 购买价格
         */
        private BigDecimal buyPrice;
        /**
         * 商户单号
         */
        private String outTradeNo;
    }
}
