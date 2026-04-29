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
  encryptedC5AppKey?: string;
  steamTradeUrl: string;
  steamId?: string;
  concurrencyLimit?: number;
  maxInFlightAttempts?: number;
  remark?: string;
}

export interface AppKeyPublicKeyResult {
  algorithm: "RSA-OAEP-256";
  publicKey: string;
}

export interface AppKeyRevealParam {
  publicKey: string;
}

export interface AppKeyRevealResult {
  algorithm: "RSA-OAEP-256";
  encryptedC5AppKey: string;
}

export interface C5SnipingAccountRefreshBalanceParam {
  accountIds: number[];
}

export interface C5SnipingAccountRefreshBalanceResult {
  accountId: number;
  accountName?: string;
  success: boolean;
  balance?: number;
  moneyAmount?: number;
  pendingBalance?: number;
  depositAmount?: number;
  creditMoney?: number;
  creditDeposit?: number;
  message?: string;
}

export interface C5SnipingAccountListResult {
  records: C5SnipingAccount[];
  totalBalance: number;
}

export interface C5SnipingAccount {
  id?: number;
  userId?: number;
  accountName: string;
  c5AppKeyMasked?: string;
  hasC5AppKey?: boolean;
  steamTradeUrl: string;
  steamId?: string;
  concurrencyLimit?: number;
  maxInFlightAttempts?: number;
  status: C5SnipingAccountStatus;
  balance: number;
  moneyAmount?: number;
  pendingBalance?: number;
  depositAmount?: number;
  creditMoney?: number;
  creditDeposit?: number;
  totalBalance?: number;
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
