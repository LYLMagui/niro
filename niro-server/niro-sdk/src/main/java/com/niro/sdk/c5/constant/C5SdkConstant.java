package com.niro.sdk.c5.constant;

/**
 * C5 SDK 通用语义常量。
 */
public final class C5SdkConstant {

    /**
     * C5 SDK 在 MDC 中记录链路追踪 ID 的键名。
     */
    public static final String MDC_TRACE_ID = "c5TraceId";

    /**
     * C5 API 异常消息格式模板。
     */
    public static final String API_ERROR_MESSAGE_TEMPLATE = "C5 API Error [%d]: %s";

    /**
     * 未携带远端错误码时使用的默认错误码。
     */
    public static final int DEFAULT_ERROR_CODE = -1;

    private C5SdkConstant() {
    }
}
