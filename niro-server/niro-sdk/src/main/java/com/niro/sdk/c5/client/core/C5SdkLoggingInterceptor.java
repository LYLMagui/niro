package com.niro.sdk.c5.client.core;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.MDC;

import java.io.IOException;

/**
 * C5 SDK 请求摘要日志拦截器。
 */
@Slf4j
final class C5SdkLoggingInterceptor implements Interceptor {

    private static final String MDC_C5_TRACE_ID = "c5TraceId";

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        C5RequestLogContext context = request.tag(C5RequestLogContext.class);
        if (context == null) {
            return chain.proceed(request);
        }

        String previousTraceId = MDC.get(MDC_C5_TRACE_ID);
        MDC.put(MDC_C5_TRACE_ID, context.traceId());
        try {
            log.info("C5 SDK request c5TraceId={}, method={}, endpoint={}, {}",
                    context.traceId(), context.method(), context.path(), context.paramSummary());
            return chain.proceed(request);
        } finally {
            if (previousTraceId == null) {
                MDC.remove(MDC_C5_TRACE_ID);
            } else {
                MDC.put(MDC_C5_TRACE_ID, previousTraceId);
            }
        }
    }
}
