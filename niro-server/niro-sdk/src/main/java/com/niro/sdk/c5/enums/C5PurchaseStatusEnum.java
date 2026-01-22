package com.niro.sdk.c5.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum C5PurchaseStatusEnum {
    PURCHASING(1, "求购中"),
    FINISHED(2, "已结束"),
    CANCELLED(3, "已取消"),
    HIDDEN_EQUAL_SUPPLY(4, "因求购数量与供应数量相等而隐藏"),
    HIDDEN_INSUFFICIENT_BALANCE(11, "因求购余额而不足而隐藏"),
    HIDDEN_LOWER_PRICE(12, "因相同饰品有更高求购价格而隐藏"),
    HIDDEN_BANNED(13, "因账号被封禁而隐藏"),
    HIDDEN_PRICE_HIGHER_THAN_SELL(14, "因求购价格比在售最低价高而隐藏");

    private final int code;
    private final String desc;
}
