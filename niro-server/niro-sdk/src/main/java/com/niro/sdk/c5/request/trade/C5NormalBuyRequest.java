package com.niro.sdk.c5.request.trade;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class C5NormalBuyRequest {
    /**
     * 商户单号
     */
    private String outTradeNo;
    /**
     * 交易链接
     */
    private String tradeUrl;
    /**
     * 在售id
     */
    private Long productId;
    /**
     * 购买价格
     */
    private BigDecimal buyPrice;
}
