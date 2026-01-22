import request from "@/utils/request";
import type { PageResult } from "@/types/goods";
import type { TradeOrderRecord, OrderQueryParam, OrderStats } from "@/types/order";

export const orderApi = {
  /**
   * 分页查询订单记录
   */
  getPage(params: OrderQueryParam) {
    return request.get<PageResult<TradeOrderRecord>>("/order/record/page", { params });
  },
};
