package com.niro.web.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * C5 扫货 2.0 命中决策结果枚举。
 */
@Getter
@AllArgsConstructor
public enum C5SnipingHitDecisionResultEnum {

    /**
     * 初始命中。
     */
    HIT("HIT", "命中"),

    /**
     * 无购买名额。
     */
    NO_BUY_SLOT("NO_BUY_SLOT", "无购买名额"),

    /**
     * 账号在途名额不足。
     */
    NO_ACCOUNT_IN_FLIGHT_SLOT("NO_ACCOUNT_IN_FLIGHT_SLOT", "账号在途名额不足"),

    /**
     * 重复下单跳过。
     */
    SKIPPED_DUPLICATE("SKIPPED_DUPLICATE", "重复下单跳过"),

    /**
     * 下单成功。
     */
    BUY_SUCCESS("BUY_SUCCESS", "下单成功"),

    /**
     * 下单失败。
     */
    BUY_FAILED("BUY_FAILED", "下单失败");

    /**
     * 决策结果编码。
     */
    @EnumValue
    @JsonValue
    private final String code;

    /**
     * 决策结果说明。
     */
    private final String description;
}
