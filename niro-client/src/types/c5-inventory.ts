export type C5InventoryStatusFilter = "all" | "tradable" | "cooldown" | "selling";

export interface C5InventoryQueryParam {
  accountId?: number;
  keyword?: string;
  status?: C5InventoryStatusFilter;
  page: number;
  pageSize: number;
}

export interface C5InventoryRefreshParam {
  accountId: number;
}

export interface C5InventoryPageResult {
  records: C5InventoryItem[];
  total: number;
  itemTotal: number;
  current: number;
  size: number;
}

export interface C5InventoryItem {
  id: number;
  accountId?: number;
  accountName?: string;
  quantity?: number;
  steamId?: string;
  appId?: number;
  assetId?: string;
  inventoryStatus?: "IN_STOCK" | "REMOVED";
  lastSyncTime?: string;
  token?: string;
  styleToken?: string;
  c5Status?: number;
  tradableTime?: string;
  classId?: string;
  instanceId?: string;
  inspect?: string;
  itemId?: string;
  name?: string;
  shortName?: string;
  marketHashName?: string;
  imageUrl?: string;
  price?: number;
  ifTradable?: boolean;
  wear?: number;
  paintIndex?: number;
  paintSeed?: number;
  inspectImageUrl?: string;
  rarity?: string;
  rarityName?: string;
  rarityColor?: string;
  exterior?: string;
  exteriorName?: string;
  exteriorColor?: string;
  createTime?: string;
  updateTime?: string;
}

export interface C5InventoryRefreshResult {
  accountId: number;
  accountName?: string;
  total: number;
  addedCount: number;
  updatedCount: number;
  removedCount: number;
  syncTime?: string;
}

