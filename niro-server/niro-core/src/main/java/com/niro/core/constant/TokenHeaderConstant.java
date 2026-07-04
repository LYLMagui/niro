package com.niro.core.constant;

/**
 * Token 响应头相关常量
 *
 * @author niro
 * @since 2026-05-21
 */
public final class TokenHeaderConstant {

    private TokenHeaderConstant() {
        // 禁止实例化
    }

    /**
     * 响应头中的带 Bearer 前缀的访问令牌头名称
     */
    public static final String WEB_TOKEN_HEADER = "niro-web-token";

    /**
     * 响应头中的原始访问令牌刷新头名称
     */
    public static final String WEB_TOKEN_UPDATE_HEADER = "niro-web-token-update";
}
