package com.niro.sdk.c5.exception;

import lombok.Getter;

/**
 * C5 HTTP 协议层异常
 * <p>
 * 当远端返回非 2xx 状态码时抛出，携带 statusCode 与原始响应体（已截断），便于线上排障。
 */
@Getter
public class C5HttpException extends C5ApiException {

    /**
     * 远端返回的 HTTP 状态码
     */
    private final int statusCode;

    /**
     * 远端响应体（可能已被截断，仅用于诊断）
     */
    private final String responseBody;

    public C5HttpException(int statusCode, String responseBody, String message) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }
}
