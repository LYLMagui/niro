package com.niro.sdk.c5.request.purchase;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class C5PurchaseCreateRequest {
    /**
     * item id
     */
    private String itemId;
    /**
     * 款式id
     */
    private Integer styleId;
    /**
     * 求购单价
     */
    private BigDecimal price;
    /**
     * 求购数量
     */
    private Integer num;
    /**
     * 收货steamId
     */
    private String receiveSteamId;
    /**
     * 商户订单号，不可重复
     */
    private String outTradeNo;
}
