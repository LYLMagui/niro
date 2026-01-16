package com.niro.core.advice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.niro.core.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 统一响应体封装
 * 
 * @author liyl
 * @date 2025/12/20
 */
@RestControllerAdvice(basePackages = "com.niro")
@RequiredArgsConstructor
public class ResponseAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 如果不需要进行封装的，可以添加逻辑判断
        // 例如：如果不希望封装 String 类型，或者已经封装了 Result 类型
        // 排除 SSE 流
        if (SseEmitter.class.isAssignableFrom(returnType.getParameterType())) {
            return false;
        }
        return true;
    }

    @SneakyThrows
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        
        // 如果已经是 Result 类型，直接返回
        if (body instanceof Result) {
            return body;
        }
        
        // 如果是 String 类型，需要手动序列化，否则会报 ClassCastException
        if (body instanceof String) {
            return objectMapper.writeValueAsString(Result.success(body));
        }
        
        // 其他类型，统一封装成 Result
        return Result.success(body);
    }
}
