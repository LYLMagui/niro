export enum C5SnipingAccountStatusEnum {
  NORMAL = "NORMAL",
  BANNED = "BANNED",
  MARKET_RESTRICTED = "MARKET_RESTRICTED",
  TRADE_RESTRICTED = "TRADE_RESTRICTED",
  INVALID = "INVALID",
  COOLING_DOWN = "COOLING_DOWN",
  SCANNING = "SCANNING",
}

export const C5SnipingAccountStatusMap = {
  [C5SnipingAccountStatusEnum.NORMAL]: { label: "在线", theme: "success" },
  [C5SnipingAccountStatusEnum.BANNED]: { label: "封禁", theme: "danger" },
  [C5SnipingAccountStatusEnum.MARKET_RESTRICTED]: { label: "市场受限", theme: "warning" },
  [C5SnipingAccountStatusEnum.TRADE_RESTRICTED]: { label: "交易受限", theme: "warning" },
  [C5SnipingAccountStatusEnum.INVALID]: { label: "失效", theme: "danger" },
  [C5SnipingAccountStatusEnum.COOLING_DOWN]: { label: "冷却中", theme: "warning" },
  [C5SnipingAccountStatusEnum.SCANNING]: { label: "扫描中", theme: "primary" },
};
