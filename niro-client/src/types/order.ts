export interface TradeOrderRecord {
  id: number;
  userId: number;
  taskId: number;
  accountId: number;
  platform: string;
  goodsName: string;
  goodsId?: number;
  marketHashName: string;
  goodsImg: string;
  orderId: string;
  price: number;
  paintwear: number;
  status: number; // 0-处理中, 1-成功, 2-失败, 3-取消
  errorMsg: string;
  errorCode: string;
  extraInfo: Record<string, never>;
  createTime: string;
  updateTime: string;
  accountName?: string;
  taskName?: string;
}

export interface OrderQueryParam {
  page: number;
  pageSize: number;
  status?: number;
  keyword?: string;
  startDate?: string;
  endDate?: string;
  sortField?: string;
  sortOrder?: string;
}

export interface OrderStats {
  todaySuccessCount: number;
  todayFailCount: number;
  todayTotalAmount: number;
}

export interface PurchaseStatsQueryParam {
  keyword?: string;
  startDate?: string;
  endDate?: string;
}

export interface PurchaseStatsSummary {
  totalAmount: number;
  totalQuantity: number;
  avgPrice: number;
  goodsTypeCount: number;
}

export interface PurchaseStatsTrendItem {
  date: string;
  amount: number;
  quantity: number;
}

export interface PurchaseStatsGoodsItem {
  goodsName: string;
  goodsImg: string;
  totalQuantity: number;
  totalAmount: number;
  avgPrice: number;
  amountRatio: number;
  latestPurchaseDate: string;
}

export interface PurchaseStatsSplitItem {
  goodsName: string;
  goodsImg: string;
  date: string;
  totalQuantity: number;
  totalAmount: number;
  avgPrice: number;
}

/**
 * 库存看板项目
 */
export interface InventoryItem {
  id?: number;
  goodsName: string;
  marketHashName: string;
  goodsImg: string;
  price: number;
  quantity: number;
  totalAmount: number;
  purchaseDate: string;
  remark: string;
  platform: string;
}

/**
 * 库存看板查询参数
 */
export interface InventoryQueryParam {
  page?: number;
  pageSize?: number;
  keyword?: string;
  purchaseDateRange?: string[];
  startDate?: string;
  endDate?: string;
}
