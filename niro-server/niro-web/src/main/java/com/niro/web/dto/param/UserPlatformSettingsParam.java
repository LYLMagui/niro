package com.niro.web.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class UserPlatformSettingsParam {
    /**
     * 收款方式
     */
    @Schema(description = "收款方式")
    private String paymentMethod;

    /**
     * 前端使用 RSA 公钥加密后的 C5 App Key
     */
    @Schema(description = "前端使用RSA公钥加密后的C5 App Key")
    private String encryptedC5AppKey;

    /**
     * C5交易链接
     */
    @Schema(description = "C5交易链接")
    private String c5TradeUrl;

    /**
     * Steam交易链接
     */
    @Schema(description = "Steam交易链接")
    private String steamTradeUrl;
}
