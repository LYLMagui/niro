package com.niro.web.dto;

import lombok.Data;

/**
 * AppKey 明文 reveal 响应 DTO。
 */
@Data
public class AppKeyRevealDTO {

    /**
     * 加密算法。
     */
    private String algorithm;

    /**
     * 使用前端临时公钥加密后的 AppKey。
     */
    private String encryptedC5AppKey;
}
