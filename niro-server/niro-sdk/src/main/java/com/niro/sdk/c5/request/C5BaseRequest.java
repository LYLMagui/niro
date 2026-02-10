package com.niro.sdk.c5.request;

import lombok.Data;

/**
 * C5 API 基础请求类
 *
 * @param <T> 响应数据类型
 */
@Data
public abstract class C5BaseRequest<T> {

    /**
     * 获取请求路径
     */
    public abstract String getPath();
}
