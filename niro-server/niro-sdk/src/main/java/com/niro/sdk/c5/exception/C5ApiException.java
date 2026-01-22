package com.niro.sdk.c5.exception;

import lombok.Getter;

/**
 * C5 API 调用异常
 */
@Getter
public class C5ApiException extends RuntimeException {
    private final Integer errorCode;
    private final String errorMsg;
    private final Object errorData;

    public C5ApiException(Integer errorCode, String errorMsg) {
        super(String.format("C5 API Error [%d]: %s", errorCode, errorMsg));
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
        this.errorData = null;
    }

    public C5ApiException(Integer errorCode, String errorMsg, Object errorData) {
        super(String.format("C5 API Error [%d]: %s", errorCode, errorMsg));
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
        this.errorData = errorData;
    }

    public C5ApiException(String message) {
        super(message);
        this.errorCode = -1;
        this.errorMsg = message;
        this.errorData = null;
    }

    public C5ApiException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = -1;
        this.errorMsg = message;
        this.errorData = null;
    }
}
