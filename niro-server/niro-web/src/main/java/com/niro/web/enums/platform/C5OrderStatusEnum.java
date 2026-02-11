package com.niro.web.enums.platform;

import com.niro.web.enums.OrderStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * C5 订单状态枚举
 */
@Getter
@AllArgsConstructor
public enum C5OrderStatusEnum {

    WAIT_SHIP(1, "待发货", OrderStatusEnum.PENDING),
    SHIPPING(2, "发货中", OrderStatusEnum.PENDING),
    WAIT_RECEIVE(3, "待收货", OrderStatusEnum.PENDING),
    FINISHED(10, "已完成", OrderStatusEnum.SUCCESS),
    FINISHED_200(200, "已完成", OrderStatusEnum.SUCCESS),
    CANCELLED(11, "已取消", OrderStatusEnum.CANCELLED);

    private final Integer c5Code;
    private final String description;
    private final OrderStatusEnum mappingStatus;

    /**
     * 将 C5 状态码映射为内部订单状态
     *
     * @param c5Status C5 平台状态码
     * @return 内部订单状态枚举
     */
    public static OrderStatusEnum mapToInternalStatus(Integer c5Status) {
        if (c5Status == null) {
            return OrderStatusEnum.PENDING;
        }
        for (C5OrderStatusEnum value : values()) {
            if (value.getC5Code().equals(c5Status)) {
                return value.getMappingStatus();
            }
        }
        // 默认返回待支付/进行中
        return OrderStatusEnum.PENDING;
    }
}
