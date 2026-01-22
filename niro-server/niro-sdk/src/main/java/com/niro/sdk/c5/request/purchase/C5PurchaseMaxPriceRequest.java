package com.niro.sdk.c5.request.purchase;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class C5PurchaseMaxPriceRequest {
    /**
     * c5的itemId
     */
    private Long itemId;
    /**
     * 特殊款式：款式id
     */
    private Integer styleId;
}
