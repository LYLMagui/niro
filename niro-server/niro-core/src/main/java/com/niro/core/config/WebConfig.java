package com.niro.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
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
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Niro-Trace-Id", "niro-web-token", "niro-web-token-update")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info(">>>>>> 注册 Sa-Token 拦截器 (使用 SaRouter 匹配白名单) <<<<<<");
        
        // 注册 Sa-Token 拦截器
        registry.addInterceptor(new SaInterceptor(handle -> {
            // 登录校验 -- 拦截所有路由，并排除白名单
            SaRouter.match("/**")
                    .notMatch(API_WHITELIST)
                    .notMatch(EXCLUDE_STATIC_SOURCE_PATH)
                    .check(r -> StpUtil.checkLogin());
        })).addPathPatterns("/**");
        
        log.info(">>>>>> Sa-Token 拦截器注册完成 <<<<<<");
    }

    private static final String[] API_WHITELIST = {
           "/user/login",
           "/user/register",
           "/log/stream",
           "/buff/account/report/status",
           "/task/callback/status",
           "/test/**"
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
