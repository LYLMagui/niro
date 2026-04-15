export type DraftHandlingStatus = "pending" | "discarded" | "stored" | "purchased";

export interface UnboxRecordItemDTO {
  id: number;
  sortNo: number;
  handlingStatus: DraftHandlingStatus;
  boxPurchasePrice: number;
  weaponName: string;
  inGamePrice: number;
  discount: number | null;
  actualSellPrice: number;
  note: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface UnboxRecordDTO {
  id: number;
  goodsId: number;
  boxName: string;
  unboxDate: string;
  defaultDiscount: number;
  note: string;
  createdAt?: string;
  updatedAt?: string;
  items: UnboxRecordItemDTO[];
}

export interface UnboxRecordItemParam {
  handlingStatus: DraftHandlingStatus;
  boxPurchasePrice: number;
  weaponName: string;
  inGamePrice: number;
  discount: number | null;
  actualSellPrice: number;
  note: string;
}

export interface UnboxRecordSaveParam {
  goodsId: number;
  unboxDate: string;
  defaultDiscount: number;
  note: string;
  items: UnboxRecordItemParam[];
}

export interface UnboxRecordOcrResult {
  weaponName?: string;
  inGamePrice?: number | string | null;
  errorMessage?: string;
  message?: string;
}
