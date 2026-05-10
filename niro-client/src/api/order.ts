import request from "@/utils/request";
import type { PageResult } from "@/types/page";
import type {
  TradeOrderRecord,
  OrderQueryParam,
  InventoryItem,
  PurchaseStatsGoodsItem,
  PurchaseStatsQueryParam,
  PurchaseStatsSplitItem,
  PurchaseStatsSummary,
  PurchaseStatsTrendItem,
} from "@/types/order";

export const orderApi = {
  /**
   * 分页查询订单记录
   */
  getPage(params: OrderQueryParam) {
    return request.get<PageResult<TradeOrderRecord>>("/order/record/page", { params });
  },

  /**
   * 删除订单记录
   */
  delete(id: number) {
    return request.delete(`/order/record/${id}`);
  },

  /**
   * 批量删除订单记录
   */
  batchDelete(ids: number[]) {
    return request.post("/order/record/batch-delete", { ids });
  },

  /**
   * 更新订单记录
   */
  update(data: any) {
    return request.put("/order/record", data);
  },

  /**
   * 获取库存看板数据
   */
  getInventory(params?: { keyword?: string; startDate?: string; endDate?: string }) {
    return request.get<InventoryItem[]>("/order/record/inventory", { params });
  },

  /**
   * 获取购买统计汇总
   */
  getPurchaseStatsSummary(params?: PurchaseStatsQueryParam) {
    return request.get<PurchaseStatsSummary>("/order/record/purchase-stats/summary", { params });
  },

  /**
   * 获取购买统计趋势
   */
  getPurchaseStatsTrend(params?: PurchaseStatsQueryParam) {
    return request.get<PurchaseStatsTrendItem[]>("/order/record/purchase-stats/trend", { params });
  },

  /**
   * 获取购买统计商品明细
   */
  getPurchaseStatsItems(params?: PurchaseStatsQueryParam) {
    return request.get<PurchaseStatsGoodsItem[]>("/order/record/purchase-stats/items", { params });
  },

  /**
   * 获取购买统计按时间拆分明细
   */
  getPurchaseStatsSplitItems(params?: PurchaseStatsQueryParam) {
    return request.get<PurchaseStatsSplitItem[]>("/order/record/purchase-stats/split-items", {
      params,
    });
  },

  /**
   * 手动触发 C5 订单同步
   */
  triggerC5Sync(accountId: number, daysBefore = 1) {
    return request.post<string>("/api/c5/order-sync/trigger", null, {
      params: { accountId, daysBefore },
    });
  },
};
