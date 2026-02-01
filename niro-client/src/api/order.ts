import request from "@/utils/request";
import type { PageResult } from "@/types/goods";
import type { TradeOrderRecord, OrderQueryParam } from "@/types/order";

export const orderApi = {
  /**
   * 分页查询订单记录
   */
  getPage(params: OrderQueryParam) {
    return request.get<PageResult<TradeOrderRecord>>("/order/record/page", { params });
  },

  /**
   * 获取 C5 订单详情
   */
  getC5Detail(orderId: string) {
    return request.get<any>(`/order/record/c5/detail/${orderId}`);
  },

  /**
   * 删除订单记录
   */
  delete(id: number) {
    return request.delete(`/order/record/${id}`);
  },

  /**
   * 更新订单记录
   */
  update(data: any) {
    return request.put("/order/record", data);
  },
};
