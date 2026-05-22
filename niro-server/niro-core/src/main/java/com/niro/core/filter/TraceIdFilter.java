package com.niro.core.filter;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.niro.core.constant.TraceConstant;

import cn.hutool.core.util.IdUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 全链路追踪过滤器
 * 优先级最高，确保在所有拦截器和异常处理器之前执行
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        try {
            // 生成 TraceId 并放入 MDC (全小写)
            String traceId = IdUtil.nanoId(12).toLowerCase();
            MDC.put(TraceConstant.TRACE_ID_MDC_KEY, traceId);
            
            // 尝试获取当前登录用户 ID (通过反射避免直接依赖 satoken 引起核心包过大，或者直接导入)
            // 这里我们已经在 niro-core 引入了 sa-token 吗？查看 pom.xml
            
            // 设置响应头
            if (response instanceof HttpServletResponse httpResponse) {
                httpResponse.setHeader(TraceConstant.TRACE_ID_RESPONSE_HEADER, traceId);
            }
            
            chain.doFilter(request, response);
        } finally {
            // 请求结束后清理，防止线程池污染
            MDC.remove(TraceConstant.TRACE_ID_MDC_KEY);
            MDC.remove(TraceConstant.USER_ID_MDC_KEY);
        }
    }
}
