package com.niro.core.result;

/**
 * 状态码常量
 * @author liyl
 * @date 2025-12-19
 */
public final class StatusCode {

    private StatusCode() {}

    /**
     * 操作成功
     */
    public static final int SUCCESS_CODE = 0;

    /**
     * 服务器异常
     */
    public static final int FAIL_CODE = 500;

    /**
     * 鉴权未通过
     */
    public static final int UNAUTHORIZED_CODE = 401;

    /**
     * 权限不足
     */
    public static final int FORBIDDEN_CODE = 403;

    /**
     * 资源未找到
     */
    public static final int NOT_FOUND_CODE = 404;

    /**
     * token已过期
     */
    public static final int TOKEN_EXPIRED_CODE = 10001;

}
