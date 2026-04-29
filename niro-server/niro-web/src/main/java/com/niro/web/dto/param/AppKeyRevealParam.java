package com.niro.web.dto.param;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AppKey reveal 参数。
 */
@Data
public class AppKeyRevealParam {

    /**
     * Base64 编码的前端临时 SPKI 公钥。
     */
    @NotBlank(message = "公钥不能为空")
    private String publicKey;
}
