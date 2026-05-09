package com.niro.sdk.c5.exception;

/**
 * C5 序列化 / 反序列化异常
 * <p>
 * 用于包装请求体序列化失败或响应体反序列化失败场景。
 */
public class C5SerializationException extends C5ApiException {

    public C5SerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
