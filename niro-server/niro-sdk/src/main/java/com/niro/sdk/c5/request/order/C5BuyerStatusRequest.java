package com.niro.sdk.c5.request.order;

import com.niro.sdk.c5.request.C5BaseRequest;
import com.niro.sdk.c5.response.order.C5BuyerStatusResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import java.util.List;

/**
 * 批量查询买家订单状态请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class C5BuyerStatusRequest extends C5BaseRequest<C5BuyerStatusResponse> {

    /**
     * 订单ID列表 (C5 orderId)
     */
    private List<String> orderIds;

    /**
     * 商户订单号列表 (与 orderIds 二选一)
     */
    private List<String> outTradeNos;

    @Override
    public String getPath() {
        return "/merchant/order/v2/buyer/status";
    }
}
