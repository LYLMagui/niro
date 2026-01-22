package com.niro.sdk.c5.request.purchase;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class C5PurchaseCancelRequest {
    /**
     * 求购id
     */
    private Long purchaseId;
}
