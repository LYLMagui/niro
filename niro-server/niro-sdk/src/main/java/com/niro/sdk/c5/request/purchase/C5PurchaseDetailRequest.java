package com.niro.sdk.c5.request.purchase;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class C5PurchaseDetailRequest {
    /**
     * 求购记录id
     */
    private String purchaseId;
    /**
     * 商户单号
     */
    private String outTradeNo;
}
