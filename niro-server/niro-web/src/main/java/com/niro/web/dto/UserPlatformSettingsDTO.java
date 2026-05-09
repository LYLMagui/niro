package com.niro.web.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class UserPlatformSettingsDTO {
    /**
     * 主键ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 收款方式
     */
    private String paymentMethod;

    /**
     * C5 App Key 脱敏展示
     */
    private String c5AppKeyMasked;

    /**
     * 是否已配置 C5 App Key
     */
    private Boolean hasC5AppKey;

    /**
     * C5交易链接
     */
    private String c5TradeUrl;

    /**
     * Steam交易链接
     */
    private String steamTradeUrl;
}
