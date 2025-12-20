package com.niro.core.config;

import com.niro.core.interceptor.TokenResponseInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置类
 *
 * @author niro
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
                // 排除静态资源等不需要拦截的路径（可选）
                .excludePathPatterns("/static/**", "/assets/**", "/webjars/**");
    }
}
