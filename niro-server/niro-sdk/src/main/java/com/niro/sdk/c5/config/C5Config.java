package com.niro.sdk.c5.config;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * C5 开放平台配置类
 */
@Data
@Accessors(chain = true)
public class C5Config {
    /**
     * API Key
     */
    private String apiKey;

    /**
     * Secret Key (用于签名)
     */
    private String secretKey;

    /**
     * API 基础地址
     */
    private String baseUrl = "https://openapi.c5game.com";
}
