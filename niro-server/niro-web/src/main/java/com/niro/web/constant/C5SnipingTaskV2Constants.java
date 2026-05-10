package com.niro.web.constant;

import java.math.BigDecimal;

/**
 * C5 扫货 2.0 常量。
 */
public final class C5SnipingTaskV2Constants {

    private C5SnipingTaskV2Constants() {
    }

    /**
     * CS2 在 C5 平台使用的 appId。
     */
    public static final int APP_ID_CS2 = 730;

    /**
     * C5 挂单查询默认页码。
     */
    public static final int LISTING_PAGE_NUM = 1;

    /**
     * C5 挂单查询每页数量。
     */
    public static final int LISTING_PAGE_SIZE = 20;

    /**
     * 日志和错误信息最大长度。
     */
    public static final int MESSAGE_MAX_LENGTH = 500;

    /**
     * 账号下单失败后的短冷却秒数。
     */
    public static final long ACCOUNT_COOLDOWN_SECONDS = 30L;

    /**
     * 账号在途下单数控制锁 key 前缀。
     */
    public static final String ACCOUNT_IN_FLIGHT_LOCK_KEY_PREFIX = "niro:c5:sniping:v2:account-in-flight:";

    /**
     * 账号在途下单锁等待秒数。
     */
    public static final long ACCOUNT_IN_FLIGHT_LOCK_WAIT_SECONDS = 0L;

    /**
     * INIT 下单尝试超时秒数。
     */
    public static final long INIT_ATTEMPT_TTL_SECONDS = 60L;

    /**
     * C5 搜索接口的全局最高价格上限。
     */
    public static final BigDecimal GLOBAL_MAX_PRICE = new BigDecimal("999999");

    /**
     * 本地命中去重窗口毫秒数。
     */
    public static final long DEDUP_TTL_MS = 60_000L;

    /**
     * 本地拼接 key 的分隔符。
     */
    public static final String KEY_SEPARATOR = ":";

    /**
     * CS2 商品不存在错误信息。
     */
    public static final String ERROR_GOODS_NOT_FOUND = "CS2商品不存在或未启用";

    /**
     * CS2 商品 MarketHashName 为空错误信息。
     */
    public static final String ERROR_GOODS_MARKET_HASH_NAME_EMPTY = "CS2商品 MarketHashName 为空";

    /**
     * C5 扫货账号不可用错误信息。
     */
    public static final String ERROR_ACCOUNT_UNAVAILABLE = "C5扫货账号不存在或不可用";

    /**
     * C5 AppKey 未配置错误信息。
     */
    public static final String ERROR_ACCOUNT_APP_KEY_EMPTY = "账号未配置 C5 AppKey";

    /**
     * Steam 交易链接未配置错误信息。
     */
    public static final String ERROR_ACCOUNT_STEAM_TRADE_URL_EMPTY = "账号未配置 Steam 交易链接";

    /**
     * 扫货执行异常默认信息。
     */
    public static final String ERROR_EXECUTE_ONE_CYCLE = "扫货执行异常";

    /**
     * 购买名额已满原因。
     */
    public static final String REASON_NO_BUY_SLOT = "购买名额已满";

    /**
     * 重复下单尝试原因。
     */
    public static final String REASON_DUPLICATE_ATTEMPT = "重复下单尝试";

    /**
     * 下单初始化异常默认原因。
     */
    public static final String REASON_INIT_ERROR = "下单尝试初始化异常";

    /**
     * C5 批量下单响应为空原因。
     */
    public static final String REASON_EMPTY_BUY_RESPONSE = "C5批量下单响应为空";

    /**
     * C5 未返回成功项原因。
     */
    public static final String REASON_NO_SUCCESS_ITEM = "C5未返回成功项";

    /**
     * C5 下单失败默认原因。
     */
    public static final String REASON_BUY_FAILED = "C5下单失败";

    /**
     * 账号在途下单数已达上限原因。
     */
    public static final String REASON_ACCOUNT_IN_FLIGHT_LIMIT_REACHED = "账号在途下单数已达上限";

    /**
     * 预占名额结算失败错误前缀。
     */
    public static final String ERROR_RESERVED_SLOT_SETTLE_FAILED_PREFIX = "C5扫货2.0预占名额结算失败: attemptId=";
}
