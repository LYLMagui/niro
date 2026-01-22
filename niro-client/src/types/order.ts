export interface TradeOrderRecord {
  id: number;
  userId: number;
  taskId: number;
  accountId: number;
  platform: string;
  goodsName: string;
  marketHashName: string;
  goodsImg: string;
  orderId: string;
  price: number;
  paintwear: number;
  status: number; // 0-处理中, 1-成功, 2-失败, 3-取消
  errorMsg: string;
  errorCode: string;
  extraInfo: Record<string, any>;
  createTime: string;
  updateTime: string;
  accountName?: string;
  taskName?: string;
}

export interface OrderQueryParam {
  page: number;
  pageSize: number;
  platform?: string;
  status?: number;
  keyword?: string;
}

export interface OrderStats {
  todaySuccessCount: number;
  todayFailCount: number;
  todayTotalAmount: number;
}
