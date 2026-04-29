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

export interface C5InventoryStatsResult {
  all: number;
  tradable: number;
  cooldown: number;
  selling: number;
  totalValue?: number;
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
  inventoryStatus?: "IN_STOCK" | "REMOVED" | "LISTING";
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
  itemType?: string;
  itemTypeName?: string;
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

export interface C5InventoryItemListParam {
  accountId: number;
  marketHashName?: string;
  name?: string;
  exteriorName?: string;
  ifTradable?: boolean;
  page: number;
  pageSize: number;
}

export interface C5InventoryAsset {
  id: number;
  accountId: number;
  accountName?: string;
  assetId: string;
  price?: number;
  wear?: number;
  ifTradable?: boolean;
  tradableTime?: string;
  inventoryStatus?: string;
  name?: string;
  marketHashName?: string;
  imageUrl?: string;
  lastSyncTime?: string;
  sellPrice?: string;
  listingFee?: number;
  listingSellerPrice?: number;
  listingFeeLoading?: boolean;
  listingFeeError?: boolean;
}

export interface C5InventoryAssetPageResult {
  records: C5InventoryAsset[];
  total: number;
  current: number;
  size: number;
}

export interface C5InventoryListingCreateParam {
  accountId: number;
  description?: string;
  acceptBargain: 0 | 1;
  items: Array<{
    inventoryItemId: number;
    price: number;
  }>;
}

export interface C5InventoryListingResult {
  accountId: number;
  shopOn?: boolean;
  succeed: number;
  failed: number;
  successList: Array<{
    assetId: string;
    productId: string;
  }>;
  failedList: string[];
  highPriceItemIdList: string[];
  priceCheckResult?: unknown;
}

export interface C5InventoryMarketReferenceParam {
  accountId: number;
  marketHashName: string;
  wear?: number;
  wearMin?: number;
  wearMax?: number;
  pageNum?: number;
  pageSize?: number;
}

export interface C5InventoryMarketReference {
  productId?: string;
  price: number;
  delivery?: number;
  acceptBargain?: boolean;
  imageUrl?: string;
  sellerUid?: string;
  assetId?: string;
  wear?: number;
  marketHashName?: string;
}

export interface C5InventoryMarketReferencePageResult {
  records: C5InventoryMarketReference[];
  pageNum: number;
  pageSize: number;
  hasMore: boolean;
  wearMin?: number;
  wearMax?: number;
}

export interface C5InventoryListingFeeCalculateParam {
  accountId: number;
  inventoryItemId: number;
  price: number;
}

export interface C5InventoryListingFeeBatchCalculateParam {
  accountId: number;
  items: Array<{
    inventoryItemId: number;
    price: number;
  }>;
}

export interface C5InventoryListingFeeResult {
  accountId: number;
  inventoryItemId?: number;
  assetId?: string;
  itemId?: string;
  price: number;
  fee?: number;
  sellerPrice?: number;
  freeFeePrice?: number;
  income?: number;
  actualAmount?: number;
  rawData?: Record<string, unknown>;
}

