package com.niro.core.constant;

/**
 * MQ 相关常量
 *
 * @author niro
 * @since 2026-02-12
 */
public final class MqConstant {

    private MqConstant() {
        // 禁止实例化
    }

    /**
     * C5 订单 Topic
     */
    public static final String TOPIC_C5_ORDER = "niro-c5-order";

    /**
     * C5 订单详情同步 Tag
     */
    public static final String TAG_C5_ORDER_DETAIL_SYNC = "order-detail-sync";

    /**
     * C5 订单详情消费者组
     */
    public static final String CONSUMER_GROUP_C5_ORDER_DETAIL = "niro-c5-order-detail-consumer";

    /**
     * C5 订单状态同步 Tag
     */
    public static final String TAG_C5_ORDER_STATUS_SYNC = "order-status-sync";

    /**
     * C5 订单状态同步消费者组
     */
    public static final String CONSUMER_GROUP_C5_ORDER_STATUS_SYNC = "niro-c5-order-status-sync-consumer";

}
