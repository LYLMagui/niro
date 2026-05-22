package com.niro.core.constant;

import java.util.Set;

/**
 * 日志脱敏字段配置
 *
 * @author niro
 * @since 2026-05-20
 */
public final class LogSanitizeConstant {

    private LogSanitizeConstant() {
    }

    /**
     * 命中后整体替换为脱敏占位符的字段集合，字段名统一按小写比较
     */
    public static final Set<String> FULL_MASK_FIELDS = Set.of(
            "password",
            "passwd",
            "pwd",
            "token",
            "accesstoken",
            "refreshtoken",
            "cookie",
            "setcookie",
            "authorization",
            "appkey",
            "app-key",
            "appsecret",
            "secret",
            "apisecret",
            "sign",
            "signature",
            "privatekey",
            "steamcookie"
    );

    /**
     * 命中后执行部分脱敏的字段集合，字段名统一按小写比较
     */
    public static final Set<String> PARTIAL_MASK_FIELDS = Set.of(
            "mobile",
            "phone",
            "phonenumber",
            "email",
            "idcard",
            "idnumber"
    );

    /**
     * 完全脱敏时使用的占位符
     */
    public static final String FULL_MASK = "***";

    /**
     * 单次日志正文允许记录的最大长度
     */
    public static final int LOG_BODY_MAX_LENGTH = 2048;
}
