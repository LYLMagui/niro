package com.niro.core.constant;

/**
 * 链路追踪相关常量
 *
 * @author niro
 * @since 2026-05-21
 */
public final class TraceConstant {

    private TraceConstant() {
        // 禁止实例化
    }

    /**
     * MDC 中保存 TraceId 的键名
     */
    public static final String TRACE_ID_MDC_KEY = "traceId";

    /**
     * MDC 中保存用户 ID 的键名
     */
    public static final String USER_ID_MDC_KEY = "userId";

    /**
     * HTTP 响应头中的 TraceId 头名称
     */
    public static final String TRACE_ID_RESPONSE_HEADER = "X-Niro-Trace-Id";

    /**
     * RocketMQ 消息头中的 TraceId 头名称
     */
    public static final String TRACE_ID_MESSAGE_HEADER = "TRACE_ID";
}
