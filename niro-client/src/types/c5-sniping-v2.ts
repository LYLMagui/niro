export type C5SnipingTaskV2StopMode = "BUY_COUNT" | "BALANCE_GUARD";
export type C5SnipingTaskV2BalanceGuardMode = "MAX_PRICE" | "RESERVE_BALANCE";
export type C5SnipingTaskV2Status = "DRAFT" | "RUNNING" | "STOPPED" | "COMPLETED" | "ERROR";

export interface C5SnipingTaskV2EventPayload {
  taskId?: number;
  eventType: string;
  occurredAt: string;
  hitRecordId?: number;
  attemptId?: number;
  taskStatus?: C5SnipingTaskV2Status | string;
  finishedAt?: string;
  stopRequested?: boolean;
  successBuyCount?: number;
  reservedBuyCount?: number;
  hitCount?: number;
  lastErrorMessage?: string;
  message?: string;
  accountBalance?: C5SnipingTaskV2AccountBalance;
}

export interface C5SnipingTaskV2Item {
  id: number;
  accountId: number;
  cs2GoodsId: number;
  name?: string;
  goodsDisplayName?: string;
  goodsIconUrl?: string;
  marketHashName?: string;
  hasExterior?: boolean;
  maxPrice?: number;
  minPaintwear?: number;
  maxPaintwear?: number;
  stopMode?: string;
  targetBuyCount?: number;
  balanceGuardMode?: string;
  reserveBalance?: number;
  priority?: number;
  scanIntervalMs?: number;
  taskStatus?: string;
  successBuyCount?: number;
  reservedBuyCount?: number;
  hitCount?: number;
  lastErrorMessage?: string;
  stopRequested?: boolean;
  stopRequestedAt?: string;
  createTime?: string;
  updateTime?: string;
  finishedAt?: string;
}

export interface C5SnipingTaskV2QueryParam {
  page: number;
  pageSize: number;
  keyword?: string;
  taskStatus?: string;
  accountId?: number;
}

export interface C5SnipingTaskV2AccountBalance {
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

export interface C5SnipingTaskV2SaveParam {
  id?: number;
  copySourceTaskId?: number;
  accountId?: number;
  cs2GoodsId?: number;
  name?: string;
  maxPrice?: number;
  minPaintwear?: number;
  maxPaintwear?: number;
  stopMode?: C5SnipingTaskV2StopMode;
  targetBuyCount?: number;
  balanceGuardMode?: C5SnipingTaskV2BalanceGuardMode;
  reserveBalance?: number;
  priority?: number;
  scanIntervalMs?: number;
}

export interface C5SnipingHitRecordV2Item {
  id: number;
  listingId?: string;
  listingPrice?: number;
  paintwear?: number;
  decisionResult?: string;
  buyFailureReason?: string;
  hitAt?: string;
  createTime?: string;
}

export interface C5SnipingBuyAttemptV2Item {
  id: number;
  listingId?: string;
  outTradeNo?: string;
  idempotencyKey?: string;
  attemptStatus?: string;
  slotReserved?: boolean;
  slotReleased?: boolean;
  orderRecordId?: number;
  inFlightAmount?: number;
  remoteCheckedAt?: string;
  failureCode?: string;
  failureMessage?: string;
  createdAt?: string;
  finishedAt?: string;
}
