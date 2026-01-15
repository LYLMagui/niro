package com.niro.web.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * BUFF账号状态枚举
 *
 * @author niro
 * @since 2026-01-15
 */
@Getter
@AllArgsConstructor
public enum BuffAccountStatusEnum {

    /**
     * 正常
     */
    NORMAL("NORMAL", "正常"),

    /**
     * 封禁
     */
    BANNED("BANNED", "封禁"),

    /**
     * 市场访问限制
     */
    MARKET_RESTRICTED("MARKET_RESTRICTED", "市场访问限制"),

    /**
     * 下单限制
     */
    TRADE_RESTRICTED("TRADE_RESTRICTED", "下单限制"),

    /**
     * Cookie失效
     */
    INVALID("INVALID", "失效");

    @EnumValue
    @JsonValue
    private final String code;

    private final String description;
}
