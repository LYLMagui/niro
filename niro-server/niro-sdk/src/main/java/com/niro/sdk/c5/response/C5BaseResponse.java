package com.niro.sdk.c5.response;

import lombok.Data;

/**
 * C5 API 基础响应
 */
@Data
public class C5BaseResponse<T> {
    private boolean success;
    private T data;
    private Integer errorCode;
    private String errorMsg;
    private Object errorData;
    private String errorCodeStr;
}
