export enum BuffAccountStatusEnum {
  NORMAL = "NORMAL",
  BANNED = "BANNED",
  MARKET_RESTRICTED = "MARKET_RESTRICTED",
  TRADE_RESTRICTED = "TRADE_RESTRICTED",
  INVALID = "INVALID",
  COOLING_DOWN = "COOLING_DOWN",
  SCANNING = "SCANNING",
}

export const BuffAccountStatusMap = {
  [BuffAccountStatusEnum.NORMAL]: { label: "在线", theme: "success" },
  [BuffAccountStatusEnum.BANNED]: { label: "封禁", theme: "danger" },
  [BuffAccountStatusEnum.MARKET_RESTRICTED]: { label: "市场受限", theme: "warning" },
  [BuffAccountStatusEnum.TRADE_RESTRICTED]: { label: "交易受限", theme: "warning" },
  [BuffAccountStatusEnum.INVALID]: { label: "失效", theme: "danger" },
  [BuffAccountStatusEnum.COOLING_DOWN]: { label: "冷却中", theme: "warning" },
  [BuffAccountStatusEnum.SCANNING]: { label: "扫描中", theme: "primary" },
};
