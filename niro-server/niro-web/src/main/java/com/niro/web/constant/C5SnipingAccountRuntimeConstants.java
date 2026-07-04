package com.niro.web.constant;

/**
 * C5 扫货账号运行态默认配置常量。
 */
public final class C5SnipingAccountRuntimeConstants {

    private C5SnipingAccountRuntimeConstants() {
    }

    /**
     * 账号默认并发上限。
     */
    public static final int DEFAULT_CONCURRENCY_LIMIT = 5;

    /**
     * 账号默认最大在途下单尝试数。
     */
    public static final int DEFAULT_MAX_IN_FLIGHT_ATTEMPTS = 1;
}
