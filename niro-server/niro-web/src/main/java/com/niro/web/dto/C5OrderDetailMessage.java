package com.niro.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * C5 订单详情同步消息 DTO
 *
 * @author niro
 * @since 2026-02-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class C5OrderDetailMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * C5 订单号
     */
    private String orderId;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * C5 App Key
     */
    private String appKey;

    /**
     * 消息创建时间戳
     */
    private Long timestamp;

}
