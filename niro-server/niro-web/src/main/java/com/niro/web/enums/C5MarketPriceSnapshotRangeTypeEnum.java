package com.niro.web.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * C5 市场价格快照区间类型。
 */
@Getter
@AllArgsConstructor
public enum C5MarketPriceSnapshotRangeTypeEnum {

    /**
     * 不筛磨损。
     */
    ALL,

    /**
     * 指定磨损区间。
     */
    WEAR
}
