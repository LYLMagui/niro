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
  wear: number;
  exterior: number;
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
  wear: number | null;
  exterior: number;
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
  price?: number | string | null;
  wear?: number | string | null;
}
