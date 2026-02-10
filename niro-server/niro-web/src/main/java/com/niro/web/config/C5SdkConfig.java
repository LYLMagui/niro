package com.niro.web.config;

import com.niro.sdk.c5.client.C5ApiClient;
import com.niro.sdk.c5.config.C5Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * C5 SDK 配置类
 * <p>
 * 仅保留基础配置，客户端实例由业务层根据用户配置动态创建。
 * </p>
 *
 * @author niro
 */
@Configuration
public class C5SdkConfig {

    @Value("${c5.base-url:https://openapi.c5game.com}")
    private String baseUrl;

    /**
     * 提供默认的 Base URL 配置，供业务层参考
     */
    @Bean
    public String c5BaseUrl() {
        return baseUrl;
    }

    // 移除静态 Bean，避免循环依赖和配置固化
    /*
    @Value("${c5.api-key:}")
    private String apiKey;

    @Bean
    public C5Config c5Config() {
        return new C5Config()
                .setAppKey(apiKey)
                .setBaseUrl(baseUrl);
    }

    @Bean
    public C5ApiClient c5ApiClient(C5Config c5Config) {
        return new C5ApiClient(c5Config);
    }
    */
}