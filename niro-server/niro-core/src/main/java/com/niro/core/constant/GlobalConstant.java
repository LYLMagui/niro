package com.niro.core.constant;

/**
 * 全局业务常量
 *
 * @author niro
 * @since 2026-01-29
 */
public class GlobalConstant {

    /**
     * 系统管理员用户ID
     */
    public static final Long ADMIN_USER_ID = 1L;

    /**
     * 游戏名称：CSGO
     */
    public static final String GAME_CSGO = "csgo";

    /**
     * 默认同步间隔（秒）：12小时
     */
    public static final Integer DEFAULT_SYNC_INTERVAL = 43200;

    /**
     * Token 请求头名称
     */
    public static final String TOKEN_HEADER = "Authorization";

    /**
     * Token 前缀
     */
    public static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 默认分页大小
     */
    public static final Integer DEFAULT_PAGE_SIZE = 10;
}
