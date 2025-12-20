package com.niro.core.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Token响应头拦截器
 *
 * @author liyl
 * @date 2025/12/20
 */
@Component
public class TokenResponseInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 获取当前会话的 Token 值
        String tokenValue = StpUtil.getTokenValue();
        
        // Token存在，则写入响应头
        if (tokenValue != null) {
            response.setHeader("Authorization", "Bearer " + tokenValue);
        }
        return true;
    }
}
