package com.niro.sdk.c5.constant;

import java.util.Set;

/**
 * C5 HTTP 请求常量。
 */
public final class C5HttpConstant {

    /**
     * C5 平台 App Key 查询参数名。
     */
    public static final String QUERY_APP_KEY = "app-key";

    /**
     * 日志输出时需要脱敏的参数 key。
     */
    public static final Set<String> SENSITIVE_KEYS = Set.of(
            QUERY_APP_KEY, "appKey", "appkey", "token", "apisecret", "secret", "sign", "signature", "password"
    );

    /**
     * 响应体和异常 body 的日志截断长度。
     */
    public static final int LOG_BODY_LIMIT = 1024;
    /**
     * C5 请求链路追踪 header 名。
     */
    public static final String HEADER_TRACE = "X-Niro-Trace";
    /**
     * HTTP Accept header 名。
     */
    public static final String HEADER_ACCEPT = "Accept";
    /**
     * HTTP Content-Type header 名。
     */
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    /**
     * JSON 请求和响应的 Content-Type 值。
     */
    public static final String CONTENT_TYPE_JSON = "application/json";
    /**
     * HTTP 成功状态码最小值，包含该值。
     */
    public static final int HTTP_OK_MIN = 200;
    /**
     * HTTP 成功状态码最大值，不包含该值。
     */
    public static final int HTTP_OK_MAX = 300;
    /**
     * 请求超时时间下限，避免配置值小于 1 秒。
     */
    public static final int MIN_TIMEOUT_SECONDS = 1;
    /**
     * 日志 traceId 截取长度。
     */
    public static final int TRACE_ID_LENGTH = 8;
    /**
     * 纳秒转毫秒的换算系数。
     */
    public static final long NANOS_PER_MILLI = 1_000_000L;

    private C5HttpConstant() {
    }
}
