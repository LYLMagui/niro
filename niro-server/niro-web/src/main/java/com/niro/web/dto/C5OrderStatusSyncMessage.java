package com.niro.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * C5 订单状态同步消息 DTO
 * <p>
 * 用于定时任务发送 MQ 消息，异步同步订单状态
 * </p>
 *
 * @author niro
 * @since 2026-02-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class C5OrderStatusSyncMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单记录 ID
     */
    private Long recordId;

    /**
     * C5 订单号
     */
    private String orderId;

    /**
     * 系统订单号（outTradeNo）
     */
    private String orderNo;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 商品名称
     */
    private String marketHashName;

    /**
     * 当前本地状态
     */
    private Integer currentStatus;

    /**
     * 消息创建时间戳
     */
    private Long timestamp;

}
