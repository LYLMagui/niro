package com.niro.core.filter;

import cn.hutool.core.util.IdUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 全链路追踪过滤器
 * 优先级最高，确保在所有拦截器和异常处理器之前执行
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    private static final String TRACE_ID = "traceId";
    private static final String USER_ID = "userId";
    private static final String TRACE_ID_HEADER = "X-Niro-Trace-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        try {
            // 生成 TraceId 并放入 MDC
            String traceId = IdUtil.fastSimpleUUID();
            MDC.put(TRACE_ID, traceId);
            
            // 尝试获取当前登录用户 ID (通过反射避免直接依赖 satoken 引起核心包过大，或者直接导入)
            // 这里我们已经在 niro-core 引入了 sa-token 吗？查看 pom.xml
            
            // 设置响应头
            if (response instanceof HttpServletResponse httpResponse) {
                httpResponse.setHeader(TRACE_ID_HEADER, traceId);
            }
            
            chain.doFilter(request, response);
        } finally {
            // 请求结束后清理，防止线程池污染
            MDC.remove(TRACE_ID);
            MDC.remove(USER_ID);
        }
    }
}
