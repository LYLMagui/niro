package com.niro.sdk.c5.exception;

import lombok.Getter;

/**
 * C5 API 调用异常基类
 * <p>
 * 所有 C5 SDK 抛出的运行期异常都继承自该类，统一持有 {@code errorCode}、{@code errorMsg}、{@code errorData}
 * 三个核心字段，便于上层做分支处理与日志输出。
 */
@Getter
public class C5ApiException extends RuntimeException {

    /** 未携带远端错误码时使用的占位值。 */
    private static final int DEFAULT_ERROR_CODE = -1;

    private final Integer errorCode;
    private final String errorMsg;
    private final Object errorData;

    public C5ApiException(Integer errorCode, String errorMsg) {
        this(errorCode, errorMsg, null);
    }

    public C5ApiException(Integer errorCode, String errorMsg, Object errorData) {
        super(String.format("C5 API Error [%d]: %s", errorCode, errorMsg));
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
        this.errorData = errorData;
    }

    public C5ApiException(String message) {
        super(message);
        this.errorCode = DEFAULT_ERROR_CODE;
        this.errorMsg = message;
        this.errorData = null;
    }

    public C5ApiException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = DEFAULT_ERROR_CODE;
        this.errorMsg = message;
        this.errorData = null;
    }
}
