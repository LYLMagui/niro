package com.niro.core.interceptor;

import cn.hutool.core.util.IdUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 全链路追踪拦截器
 */
public class TraceIdInterceptor implements HandlerInterceptor {

    private static final String TRACE_ID = "traceId";
    private static final String TRACE_ID_HEADER = "X-Niro-Trace-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 生成 TraceId 并存入 MDC
        String traceId = IdUtil.fastSimpleUUID();
        MDC.put(TRACE_ID, traceId);
        
        // 在响应 Header 中返回 TraceId
        response.addHeader(TRACE_ID_HEADER, traceId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清理 MDC，防止内存泄漏
        MDC.remove(TRACE_ID);
    }
}
