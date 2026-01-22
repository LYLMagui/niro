package com.niro.sdk.c5.request.account;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class C5AccountBalanceRequest {
    /**
     * 账户类型 0-账户余额 1-求购余额 默认账户余额
     */
    private Integer accountType;
}
