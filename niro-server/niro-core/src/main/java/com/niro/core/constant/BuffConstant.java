package com.niro.core.constant;

/**
 * BUFF 业务相关常量
 * @author liyl
 * @date 2026-01-08
 */
public class BuffConstant {

    /**
     * Redis 任务优先级队列 Key
     */
    public static final String REDIS_TASK_QUEUE_HIGH = "niro:tasks:priority:high";
    public static final String REDIS_TASK_QUEUE_MEDIUM = "niro:tasks:priority:medium";
    public static final String REDIS_TASK_QUEUE_LOW = "niro:tasks:priority:low";

    /**
     * Redis 任务状态回调队列 Key
     */
    public static final String REDIS_QUEUE_TASK_STATUS = "niro:queue:task:status";

    /**
     * Redis 任务停止信号 Key 前缀
     */
    public static final String REDIS_TASK_STOP_SIGNAL_PREFIX = "niro:task:stop:";

    /**
     * Redis 任务状态 Key 前缀
     */
    public static final String REDIS_TASK_STATUS_PREFIX = "niro:task:status:";

    /**
     * Redis 任务状态变更订阅频道
     */
    public static final String REDIS_TASK_STATUS_PUBSUB = "niro:task:pubsub:status";

    /**
     * Redis 任务心跳 Hash Key
     */
    public static final String REDIS_TASK_HEARTBEAT_HASH = "niro:task:heartbeat";

    /**
     * Redis 任务统计信息 Key 前缀
     */
    public static final String REDIS_TASK_STATS_PREFIX = "niro:stats:task:";

    /**
     * Redis 任务执行锁 Key 前缀
     */
    public static final String REDIS_TASK_LOCK_PREFIX = "niro:lock:task:";

    /**
     * Redis 账号信息 Hash Key
     */
    public static final String REDIS_ACCOUNT_INFO_HASH = "niro:account:info";
}
