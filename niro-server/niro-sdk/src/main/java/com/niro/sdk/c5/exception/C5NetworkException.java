package com.niro.sdk.c5.exception;

import lombok.Getter;

/**
 * C5 网络层异常
 * <p>
 * 用于包装 IO 失败、连接超时、请求中断等场景。{@code retryable} 字段提示调用方该错误是否值得重试。
 */
@Getter
public class C5NetworkException extends C5ApiException {

    /**
     * 是否建议重试。连接超时、IO 异常通常 true；线程中断 false。
     */
    private final boolean retryable;

    public C5NetworkException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }
}
