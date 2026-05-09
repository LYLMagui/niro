package com.niro.sdk.c5.exception;

import com.niro.sdk.c5.enums.C5BusinessStatusEnum;

/**
 * C5 业务失败异常
 * <p>
 * 远端 HTTP 成功，但响应体 {@code success=false} 时抛出。
 * 批量部分成功接口可在调用点显式选择保留 data。
 */
public class C5BusinessException extends C5ApiException {

    public C5BusinessException(Integer errorCode, String errorMsg, Object errorData) {
        super(errorCode, C5BusinessStatusEnum.getDesc(errorCode, errorMsg), errorData);
    }
}
