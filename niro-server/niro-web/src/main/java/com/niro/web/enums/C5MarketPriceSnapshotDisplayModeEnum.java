package com.niro.web.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * C5 市场价格参考展示模式。
 */
@Getter
@AllArgsConstructor
public enum C5MarketPriceSnapshotDisplayModeEnum {

    /**
     * 价格优先。
     */
    PRICE_LOWEST,

    /**
     * 磨损接近优先。
     */
    WEAR_NEAREST
}
