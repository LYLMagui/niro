/**
 * 开箱记录类型定义
 * 说明：表单字段均以数值型存储，方便后续统计运算
 */

/**
 * 购买状态枚举
 */
export type PurchaseStatus = "purchased" | "pending" | "abandoned";

/**
 * 属性类型（允许扩展，默认提供常用值）
 */
export type WeaponAttribute = "ST" | "普通" | "纪念" | "其他";

/**
 * 开箱记录
 */
export interface UnboxRecord {
  id: string;
  boxName: string;
  purchasePrice: number;
  screenshot: string;
  weaponName: string;
  wearValue: number;
  attribute: WeaponAttribute;
  steamPrice: number;
  actualPrice: number;
  platformPrice: number;
  discount: number;
  estimatedProfit: number;
  purchaseStatus: PurchaseStatus;
  profitRate: number;
  actualSellPrice: number;
  actualProfit: number;
  createdAt: string;
  updatedAt: string;
}

/**
 * 开箱记录表单输入
 */
export interface UnboxRecordForm {
  boxName: string;
  purchasePrice: number;
  screenshot: string;
  weaponName: string;
  wearValue: number;
  attribute: WeaponAttribute;
  steamPrice: number;
  platformPrice: number;
  discount: number;
  purchaseStatus: PurchaseStatus;
  actualSellPrice: number;
}

/**
 * 汇总数据
 */
export interface UnboxSummary {
  totalSteamCost: number;
  totalEstimatedProfit: number;
}

/**
 * 统一四舍五入
 */
export const formatDecimal = (value: number, decimals = 2): number =>
  Math.round(value * Math.pow(10, decimals)) / Math.pow(10, decimals);

/**
 * 计算实际购入价：Steam 价 × 折扣
 */
export const calculateActualPrice = (steamPrice: number, discount: number): number =>
  formatDecimal(steamPrice * discount);

/**
 * 计算预估利润：Steam 价 - 平台价
 */
export const calculateEstimatedProfit = (steamPrice: number, platformPrice: number): number =>
  formatDecimal(steamPrice - platformPrice);

/**
 * 计算利润率：预估利润 / 实际购入价 × 100
 */
export const calculateProfitRate = (estimatedProfit: number, actualPrice: number): number => {
  if (actualPrice === 0) return 0;
  return formatDecimal((estimatedProfit / actualPrice) * 100);
};

/**
 * 计算实际利润：实际出售价 - 实际购入价
 */
export const calculateActualProfit = (actualSellPrice: number, actualPrice: number): number =>
  formatDecimal(actualSellPrice - actualPrice);
