package com.niro.web.dto;

import com.niro.web.enums.PaymentMethodEnum;
import lombok.Data;

/**
 * 用户Buff配置DTO
 *
 * @author liyl
 * @since 2025-12-24
 */
@Data
public class UserBuffSettingsDTO {

    /**
     * ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * Buff平台Cookie (脱敏或完整返回视需求而定，此处暂完整返回以便回显)
     */
    private String buffCookie;

    /**
     * 支付方式
     */
    private PaymentMethodEnum paymentMethod;
}
