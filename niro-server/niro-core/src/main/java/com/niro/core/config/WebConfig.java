package com.niro.core.config;

import com.niro.core.interceptor.TokenResponseInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置类
 *
 * @author liyl
 * @date 2025/12/20
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final TokenResponseInterceptor tokenResponseInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Token 响应拦截器，拦截所有请求
        registry.addInterceptor(tokenResponseInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(API_WHITELIST)
                .excludePathPatterns(EXCLUDE_STATIC_SOURCE_PATH)
                ;
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
