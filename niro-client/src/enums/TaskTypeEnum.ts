export enum TaskTypeEnum {
  SNIPING = 0,
  FLIPPING = 1,
  SYNC_CATEGORY = 2,
  SYNC_GOODS = 3,
  SYNC_STICKER = 4,
  SYNC_CATEGORY_GOODS = 5,
}

export function isSystemTask(code?: number): boolean {
  if (code === undefined || code === null) return false;
  return code >= 2;
}

export const TaskTypeMap = {
  [TaskTypeEnum.SNIPING]: "炼金扫货",
  [TaskTypeEnum.FLIPPING]: "站内倒卖",
  [TaskTypeEnum.SYNC_CATEGORY]: "系统-分类同步",
  [TaskTypeEnum.SYNC_GOODS]: "系统-商品全量同步",
  [TaskTypeEnum.SYNC_STICKER]: "系统-印花同步",
  [TaskTypeEnum.SYNC_CATEGORY_GOODS]: "系统-分类商品同步",
};
