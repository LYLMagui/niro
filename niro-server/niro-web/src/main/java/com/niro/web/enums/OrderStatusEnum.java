package com.niro.web.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单状态枚举
 *
 * @author niro
 * @since 2026-01-29
 */
@Getter
@AllArgsConstructor
public enum OrderStatusEnum {

    /**
     * 待支付/进行中
     */
    PENDING(0, "待支付"),

    /**
     * 成功
     */
    SUCCESS(1, "成功"),

    /**
     * 失败
     */
    FAILED(2, "失败"),

    /**
     * 失败 (C5)
     */
    FAILURE(11, "失败"),

    /**
     * 取消
     */
    CANCELLED(3, "取消");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String description;

    public static OrderStatusEnum getByCode(Integer code) {
        if (code == null) return null;
        for (OrderStatusEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
