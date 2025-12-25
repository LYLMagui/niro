package com.niro.web.dto.param;

import com.niro.web.enums.PaymentMethodEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户Buff配置保存参数
 *
 * @author liyl
 * @since 2025-12-24
 */
@Data
public class UserBuffSettingsParam {

    /**
     * Buff平台Cookie
     */
    @NotBlank(message = "Cookie不能为空")
    private String buffCookie;

    /**
     * 支付方式
     */
    @NotNull(message = "支付方式不能为空")
    private PaymentMethodEnum paymentMethod;
}
