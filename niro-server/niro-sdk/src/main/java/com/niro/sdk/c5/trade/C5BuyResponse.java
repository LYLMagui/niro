package com.niro.sdk.c5.trade;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class C5BuyResponse {
    private BigDecimal actualPay;
    private Integer delivery;
    private Long orderAssetId;
    private Long orderId;
    private Integer payStatus;
    private Integer orderStatus;
}
