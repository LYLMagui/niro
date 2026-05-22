package com.niro.web.constant;

/**
 * C5 订单同步相关常量。
 */
public final class C5OrderSyncConstants {

    private C5OrderSyncConstants() {
    }

    /**
     * 交易订单中的 C5 平台标识。
     */
    public static final String PLATFORM_C5 = "C5";

    /**
     * C5 订单列表同步每页拉取数量。
     */
    public static final int ORDER_SYNC_PAGE_SIZE = 100;

    /**
     * C5 订单同步服务全局限流器 key。
     */
    public static final String API_LIMITER_KEY = "niro:limiter:c5:api";

    /**
     * C5 订单详情同步消费者限流器 key。
     */
    public static final String ORDER_DETAIL_LIMITER_KEY = "niro:limiter:c5:order:detail";

    /**
     * C5 订单状态同步消费者限流器 key。
     */
    public static final String ORDER_STATUS_SYNC_LIMITER_KEY = "niro:limiter:c5:order:status-sync";

    /**
     * C5 API 令牌获取超时时间，单位秒。
     */
    public static final int LIMITER_ACQUIRE_TIMEOUT_SECONDS = 5;

    /**
     * 按用户维度串行化手动同步任务的锁 key 前缀。
     */
    public static final String USER_SYNC_LOCK_KEY_PREFIX = "niro:lock:c5:order-sync:user:";

    /**
     * 全量同步任务的锁 key 前缀。
     */
    public static final String ALL_SYNC_LOCK_KEY_PREFIX = "niro:lock:c5:order-sync:all:";

    /**
     * 手动同步提交冷却 key 前缀。
     */
    public static final String USER_SYNC_SUBMIT_KEY_PREFIX = "niro:cooldown:c5:order-sync:user:";

    /**
     * 同步锁等待时长，单位秒。
     */
    public static final long SYNC_LOCK_WAIT_SECONDS = 0L;

    /**
     * 同步锁租约时长，单位秒。
     */
    public static final long SYNC_LOCK_LEASE_SECONDS = 300L;

    /**
     * 手动同步提交冷却时长，单位秒。
     */
    public static final long SYNC_SUBMIT_COOLDOWN_SECONDS = 60L;
}
