package com.niro.web.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * C5 市场价格快照刷新状态。
 */
@Getter
@AllArgsConstructor
public enum C5MarketPriceSnapshotStatusEnum {

    /**
     * 等待刷新。
     */
    PENDING,

    /**
     * 刷新中。
     */
    REFRESHING,

    /**
     * 最近刷新成功。
     */
    SUCCESS,

    /**
     * 最近刷新失败。
     */
    FAILED,

    /**
     * 已停用。
     */
    DISABLED
}
