package com.niro.web.constant;

/**
 * 交易订单链路常量。
 */
public final class TradeOrderConstants {

    private TradeOrderConstants() {
    }

    /**
     * Redis 订单上报队列 key。
     */
    public static final String ORDER_REPORT_QUEUE_KEY = "niro:order:report";
}
