package com.niro.sdk.c5.request.order;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量查询买家订单状态请求
 */
@Data
@Accessors(chain = true)
public class C5BuyerStatusRequest {

    /**
     * 页码 (从1开始)
     */
    private Integer pageNum = 1;

    /**
     * 每页数量，最大100条
     */
    private Integer pageSize = 100;

    /**
     * 状态过滤：1待发货 2发货中 3待收货 10已完成 11已取消
     */
    private Integer status;

    /**
     * 订单ID列表 (C5 orderId)
     */
    private List<String> orderIds;

    /**
     * 商户订单号列表 (与 orderIds 二选一)
     */
    private List<String> outTradeNos;

}
