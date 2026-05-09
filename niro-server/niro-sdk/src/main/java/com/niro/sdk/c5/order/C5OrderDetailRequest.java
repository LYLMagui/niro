package com.niro.sdk.c5.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * C5 订单详情查询请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class C5OrderDetailRequest {
    /**
     * C5 订单号
     */
    private String orderId;

    /**
     * 商户自定义单号
     */
    private String outTradeNo;
}
