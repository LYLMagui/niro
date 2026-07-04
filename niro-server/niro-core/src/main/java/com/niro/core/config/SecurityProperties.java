package com.niro.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 安全相关配置。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "niro.security")
public class SecurityProperties {

    /**
     * 允许跨域访问的来源列表。
     */
    private List<String> allowedOrigins = List.of("http://127.0.0.1:5173");
}
