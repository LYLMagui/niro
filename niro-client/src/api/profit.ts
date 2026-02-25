/**
 * 利润记录 API - localStorage 存储封装
 */
import type { ProfitRecord, ProfitSummary, ProfitRecordForm } from "@/types/profit";
import { calculateProfit, calculateBuyCost } from "@/types/profit";

const STORAGE_KEY = "niro-profit-records";

/**
 * 生成 UUID
 */
function generateId(): string {
  return crypto.randomUUID();
}

/**
 * 获取所有记录
 */
export function getRecords(): ProfitRecord[] {
  const data = localStorage.getItem(STORAGE_KEY);
  return data ? JSON.parse(data) : [];
}

/**
 * 保存记录到 localStorage
 */
function saveRecords(records: ProfitRecord[]): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(records));
}

/**
 * 新增记录
 */
export function addRecord(form: ProfitRecordForm): ProfitRecord {
  const records = getRecords();
  const profit = calculateProfit(form.buyPrice, form.quantity, form.sellTotal, form.cost);

  const newRecord: ProfitRecord = {
    ...form,
    id: generateId(),
    profit,
    createTime: new Date().toISOString(),
  };

  records.unshift(newRecord);
  saveRecords(records);
  return newRecord;
}

/**
 * 更新记录
 */
export function updateRecord(id: string, form: ProfitRecordForm): ProfitRecord | null {
  const records = getRecords();
  const index = records.findIndex((r) => r.id === id);
  if (index === -1) return null;

  const profit = calculateProfit(form.buyPrice, form.quantity, form.sellTotal, form.cost);

  const updated: ProfitRecord = {
    ...records[index],
    ...form,
    profit,
  };

  records[index] = updated;
  saveRecords(records);
  return updated;
}

/**
 * 删除记录
 */
export function deleteRecord(id: string): boolean {
  const records = getRecords();
  const index = records.findIndex((r) => r.id === id);
  if (index === -1) return false;

  records.splice(index, 1);
  saveRecords(records);
  return true;
}

/**
 * 获取统计汇总
 */
export function getSummary(records?: ProfitRecord[]): ProfitSummary {
  const data = records || getRecords();

  const totalBuyCost = data.reduce((sum, r) => sum + calculateBuyCost(r.buyPrice, r.quantity), 0);
  const totalProfit = data.reduce((sum, r) => sum + r.profit, 0);
  const profitRate = totalBuyCost > 0 ? (totalProfit / totalBuyCost) * 100 : 0;

  return {
    totalProfit: Math.round(totalProfit * 100) / 100,
    totalBuyCost: Math.round(totalBuyCost * 100) / 100,
    profitRate: Math.round(profitRate * 100) / 100,
    recordCount: data.length,
  };
}

/**
 * 导出数据为 JSON 字符串
 */
export function exportData(): string {
  return JSON.stringify(getRecords(), null, 2);
}

/**
 * 从 JSON 字符串导入数据
 */
export function importData(jsonString: string): boolean {
  try {
    const records = JSON.parse(jsonString);
    if (!Array.isArray(records)) return false;
    // 验证数据结构
    for (const record of records) {
      if (!record.goodsName || typeof record.buyPrice !== "number") {
        return false;
      }
    }
    saveRecords(records);
    return true;
  } catch {
    return false;
  }
}

/**
 * 批量删除记录
 */
export function deleteRecords(ids: string[]): number {
  const records = getRecords();
  const initialLength = records.length;
  const filtered = records.filter((r) => !ids.includes(r.id));
  saveRecords(filtered);
  return initialLength - filtered.length;
}

/**
 * 清空所有记录
 */
export function clearAllRecords(): void {
  localStorage.removeItem(STORAGE_KEY);
}
