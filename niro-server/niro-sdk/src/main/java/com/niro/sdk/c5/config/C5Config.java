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
     * App Key
     */
    private String appKey;

    /**
     * API 基础地址
     */
    private String baseUrl = "https://openapi.c5game.com";

    /**
     * 单次请求超时（秒），影响每个 OkHttp Call 的 timeout 设置
     */
    private int requestTimeoutSeconds = 60;
}
