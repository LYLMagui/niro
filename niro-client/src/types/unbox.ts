import type { PageResult } from "@/types/page";

export type DraftHandlingStatus = "pending" | "discarded" | "stored" | "purchased";

export interface UnboxRecordItemDTO {
  id: number;
  sortNo: number;
  handlingStatus: DraftHandlingStatus;
  boxPurchasePrice: number;
  weaponName: string;
  cs2GoodsId?: number;
  inGamePrice: number;
  discount: number | null;
  actualSellPrice: number;
  wear: number;
  exterior: number;
  note: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface UnboxRecordDTO {
  id: number;
  boxGoodsId: number;
  boxName: string;
  unboxDate: string;
  defaultDiscount: number;
  note: string;
  createdAt?: string;
  updatedAt?: string;
  items: UnboxRecordItemDTO[];
}

export interface UnboxRecordPageDTO {
  id: number;
  boxGoodsId: number;
  boxName: string;
  unboxDate: string;
  defaultDiscount: number;
  note: string;
  createdAt?: string;
  updatedAt?: string;
  totalCount: number;
  totalPurchaseCost: number;
  totalActualFee: number;
  totalActualNetProfit: number;
  totalActualProfitRate: number | null;
  status: string;
}

export interface UnboxRecordSummaryDTO {
  totalBatches: number;
  totalPurchaseCost: number;
  totalFee: number;
  totalActualNetProfit: number;
}

export type UnboxRecordPageResult = PageResult<UnboxRecordPageDTO>;

export interface UnboxRecordItemParam {
  handlingStatus: DraftHandlingStatus;
  boxPurchasePrice: number;
  weaponName: string;
  inGamePrice: number;
  discount: number | null;
  actualSellPrice: number;
  wear: number | null;
  exterior: number;
  note: string;
}

export interface UnboxRecordSaveParam {
  boxGoodsId: number;
  unboxDate: string;
  defaultDiscount: number;
  note: string;
  items: UnboxRecordItemParam[];
}

export interface UnboxRecordOcrResult {
  name?: string | null;
  price?: number | string | null;
  wear?: number | string | null;
  exterior?: number | string | null;
}

export interface UnboxRecordC5ListingQueryParam {
  cs2GoodsId: number;
  wearMin?: number | null;
  wearMax?: number | null;
  exterior?: number | null;
  pageNum: number;
  pageSize: number;
}

export interface UnboxRecordC5Listing {
  productId: string;
  price: number;
  sellerUid: string;
  sellerName: string;
  wear: number | null;
  delivery: number | null;
  imageUrl: string;
  marketHashName: string;
  itemName: string;
}

export interface UnboxRecordC5ListingPageResult {
  records: UnboxRecordC5Listing[];
  pageNum: number;
  pageSize: number;
  hasMore: boolean;
  snapshotStatus?: string;
  lastSuccessTime?: string;
  stale?: boolean;
  message?: string;
}
