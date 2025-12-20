package com.niro.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

/**
 * Web MVC 配置类
 *
 * @author liyl
 * @date 2025/12/20
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
    }

    private static final String[] API_WHITELIST = {
           
    };

    private static final String[] EXCLUDE_STATIC_SOURCE_PATH = {
            "/v3/api-docs/**",
            "/v3/api-docs/swagger-config",
            "/swagger-resources/**",
            "/swagger-ui.html",
            "/webjars/**",
            "/favicon.ico",
            "/error",
            "/doc.html",
            "/.well-known/appspecific/com.chrome.devtools.json"
    };

}
