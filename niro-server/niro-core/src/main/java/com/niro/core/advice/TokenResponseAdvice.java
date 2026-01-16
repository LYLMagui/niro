package com.niro.core.advice;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 统一响应增强，用于在响应头中添加Token
 *
 * @author liyl
 * @date 2025/12/20
 */
@ControllerAdvice
public class TokenResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 排除 SSE 流
        if (SseEmitter.class.isAssignableFrom(returnType.getParameterType())) {
            return false;
        }
        // 只有返回Result类型的接口才进行处理，或者可以全部处理
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        
        // 获取 HttpServletResponse
        if (response instanceof ServletServerHttpResponse) {
            HttpServletResponse servletResponse = ((ServletServerHttpResponse) response).getServletResponse();
            
            // 获取当前 Token
            try {
                String tokenValue = StpUtil.getTokenValue();
                if (tokenValue != null) {
                    // 这里的 key 必须和前端 request 中设置的 key 一致
                    servletResponse.setHeader("niro-web-token", "Bearer " + tokenValue);
                    servletResponse.setHeader("niro-web-token-update", tokenValue);
                    servletResponse.addHeader("Access-Control-Expose-Headers", "niro-web-token, niro-web-token-update");
                }
            } catch (Exception ignored) {
                // 忽略异常，可能是未登录状态调用
            }
        }
        
        return body;
    }
}
