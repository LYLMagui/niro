package com.niro.web.constant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * C5 市场价格快照常量。
 */
public final class C5MarketPriceSnapshotConstants {

    private C5MarketPriceSnapshotConstants() {
    }

    /**
     * CS2 在 C5 平台使用的 appId。
     */
    public static final int APP_ID_CS2 = 730;

    /**
     * C5 市场查询默认页码。
     */
    public static final int DEFAULT_PAGE_NUM = 1;

    /**
     * C5 市场查询默认每页数量。
     */
    public static final int DEFAULT_PAGE_SIZE = 50;

    /**
     * 参考快照默认返回数量。
     */
    public static final int DEFAULT_LIMIT = 10;

    /**
     * 按磨损接近度返回的默认数量。
     */
    public static final int NEAREST_LIMIT = 5;

    /**
     * 参考快照最大返回数量。
     */
    public static final int MAX_LIMIT = 50;

    /**
     * 快照默认刷新间隔秒数。
     */
    public static final int REFRESH_INTERVAL_SECONDS = 300;

    /**
     * 手动刷新提升的刷新优先级。
     */
    public static final int MANUAL_REFRESH_PRIORITY = 100;

    /**
     * 手动刷新冷却秒数。
     */
    public static final int MANUAL_REFRESH_COOLDOWN_SECONDS = 60;

    /**
     * C5 短窗口限流后的重试秒数。
     */
    public static final int RATE_LIMIT_RETRY_SECONDS = 2;

    /**
     * C5 HTTP 频率限制状态码。
     */
    public static final int TOO_MANY_REQUESTS_STATUS_CODE = 429;

    /**
     * 定时扫描待刷新快照批量大小。
     */
    public static final int SCAN_BATCH_SIZE = 60;

    /**
     * 恢复超时刷新任务的批量大小。
     */
    public static final int TIMEOUT_RECOVER_BATCH_SIZE = 100;

    /**
     * C5 市场查询全局限流器 key。
     */
    public static final String LIMITER_KEY = "c5:market-price:products-search:rate-limit";

    /**
     * 手动刷新冷却 key 前缀。
     */
    public static final String MANUAL_REFRESH_THROTTLE_KEY_PREFIX = "c5:market-price:manual-refresh:";

    /**
     * C5 饰品最小磨损值。
     */
    public static final BigDecimal MIN_WEAR = BigDecimal.ZERO.setScale(8, RoundingMode.UNNECESSARY);

    /**
     * C5 饰品最大磨损值。
     */
    public static final BigDecimal MAX_WEAR = BigDecimal.ONE.setScale(8, RoundingMode.UNNECESSARY);

    /**
     * 默认历史时间。
     */
    public static final LocalDateTime EPOCH_TIME = LocalDateTime.of(1970, 1, 1, 0, 0);

    /**
     * 价格更新时间展示格式。
     */
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
}
