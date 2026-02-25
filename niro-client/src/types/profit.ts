/**
 * 利润记录类型定义
 */

/**
 * 利润记录
 */
export interface ProfitRecord {
  id: string;
  time: string;
  goodsName: string;
  buyPrice: number;
  quantity: number;
  sellTotal: number;
  cost: number;
  profit: number;
  remark: string;
  createTime: string;
}

/**
 * 利润统计汇总
 */
export interface ProfitSummary {
  totalProfit: number;
  totalBuyCost: number;
  profitRate: number;
  recordCount: number;
}

/**
 * 利润记录表单数据
 */
export interface ProfitRecordForm {
  time: string;
  goodsName: string;
  buyPrice: number;
  quantity: number;
  sellTotal: number;
  cost: number;
  remark: string;
}

/**
 * 计算预估利润
 * 公式: IF(卖出总价=0, 成本*-1, ROUND((卖出总价 - 买入单价*数量 - 成本)*0.99, 2))
 */
export function calculateProfit(
  buyPrice: number,
  quantity: number,
  sellTotal: number,
  cost: number
): number {
  if (sellTotal === 0) {
    return cost * -1;
  }
  return Math.round((sellTotal - buyPrice * quantity - cost) * 0.99 * 100) / 100;
}

/**
 * 计算实际购入价
 * 公式: 买入单价 × 数量
 */
export function calculateBuyCost(buyPrice: number, quantity: number): number {
  return buyPrice * quantity;
}
