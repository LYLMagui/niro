export type C5SnipingAccountStatus =
  | "NORMAL"
  | "BANNED"
  | "MARKET_RESTRICTED"
  | "TRADE_RESTRICTED"
  | "INVALID"
  | "COOLING_DOWN"
  | "SCANNING";

export interface C5SnipingAccountSaveParam {
  id?: number;
  accountName: string;
  c5AppKey: string;
  steamTradeUrl: string;
  concurrencyLimit?: number;
  maxInFlightAttempts?: number;
  remark?: string;
}

export interface C5SnipingAccount {
  id?: number;
  userId?: number;
  accountName: string;
  c5AppKey: string;
  steamTradeUrl: string;
  concurrencyLimit?: number;
  maxInFlightAttempts?: number;
  status: C5SnipingAccountStatus;
  balance: number;
  pendingBalance?: number;
  lastCheckTime?: string;
  remark?: string;
  warningMsg?: string;
  todayScanCount?: number;
  tradeSuccessCount?: number;
  tradeTotalCount?: number;
  tradeSuccessRate?: number;
  boundTaskId?: number;
  boundTaskName?: string;
  createTime?: string;
  updateTime?: string;
}
