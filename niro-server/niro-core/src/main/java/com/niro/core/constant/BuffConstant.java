package com.niro.core.constant;

/**
 * BUFF 业务相关常量
 * @author liyl
 * @date 2026-01-08
 */
public class BuffConstant {

    /**
     * 系统管理员用户ID
     */
    public static final Long ADMIN_USER_ID = 1L;

    /**
     * 系统同步任务默认扫描间隔（秒）：12小时
     */
    public static final Integer DEFAULT_SYNC_INTERVAL = 43200;

    /**
     * 任务状态：运行中
     */
    public static final Integer TASK_STATUS_RUNNING = 1;

    /**
     * 任务状态：已停止
     */
    public static final Integer TASK_STATUS_STOPPED = 0;
    
    /**
     * BUFF 游戏名称：CSGO
     */
    public static final String GAME_CSGO = "csgo";

}
