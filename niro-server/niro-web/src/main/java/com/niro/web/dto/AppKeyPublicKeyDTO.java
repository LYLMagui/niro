package com.niro.web.dto;

import lombok.Data;

/**
 * AppKey 字段级加密公钥 DTO。
 */
@Data
public class AppKeyPublicKeyDTO {

    /**
     * 加密算法。
     */
    private String algorithm;

    /**
     * Base64 编码的 SPKI 公钥。
     */
    private String publicKey;
}
