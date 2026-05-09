package com.niro.web.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支付方式枚举
 *
 * @author liyl
 * @since 2025-12-24
 */
@Getter
@AllArgsConstructor
public enum PaymentMethodEnum {

    BALANCE("BALANCE", "余额/网易支付"),
    ALIPAY("ALIPAY", "支付宝"),
    WECHAT("WECHAT", "微信");

    @EnumValue
    @JsonValue
    private final String code;
    
    private final String desc;
}
