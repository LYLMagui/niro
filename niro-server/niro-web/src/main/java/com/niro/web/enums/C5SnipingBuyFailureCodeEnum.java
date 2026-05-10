package com.niro.web.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * C5 扫货 2.0 下单失败码枚举。
 */
@Getter
@AllArgsConstructor
public enum C5SnipingBuyFailureCodeEnum {

    /**
     * 下单初始化异常。
     */
    INIT_ERROR("INIT_ERROR", "下单初始化异常"),

    /**
     * 普通异常失败。
     */
    EXCEPTION("EXCEPTION", "普通异常失败"),

    /**
     * C5 响应为空。
     */
    EMPTY_RESPONSE("EMPTY_RESPONSE", "C5 响应为空");

    /**
     * 失败码。
     */
    @EnumValue
    @JsonValue
    private final String code;

    /**
     * 失败码说明。
     */
    private final String description;
}
