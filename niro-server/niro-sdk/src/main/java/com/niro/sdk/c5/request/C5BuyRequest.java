package com.niro.sdk.c5.request;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class C5BuyRequest {
    private String outTradeNo;
    private String tradeUrl;
    private Long productId;
    private BigDecimal buyPrice;
}
