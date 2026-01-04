package com.niro.core.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Web MVC 配置类
 *
 * @author liyl
 * @date 2025/12/20
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info(">>>>>> 开始注册 Sa-Token 拦截器 <<<<<<");
        // 注册 Sa-Token 拦截器，校验规则为 StpUtil.checkLogin() 登录校验。
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**")
                .excludePathPatterns(API_WHITELIST)
                .excludePathPatterns(EXCLUDE_STATIC_SOURCE_PATH);
        log.info(">>>>>> Sa-Token 拦截器注册完成 <<<<<<");
    }

    private static final String[] API_WHITELIST = {
           "/user/login",
           "/user/register",
           "/log/stream"
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
