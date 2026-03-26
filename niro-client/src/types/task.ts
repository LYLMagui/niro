export interface BuffScanTask {
  id: number;
  name: string;
  goodsId?: number;
  goodsName?: string;
  goodsIconUrl?: string;
  marketHashName?: string;
  maxPrice?: number;
  minPaintwear?: number;
  maxPaintwear?: number;
  buyCount: number;
  successCount: number;
  status: number;
  cronExpression?: string;
  durationMinutes?: number;
  restPeriod?: number;
  scanInterval?: number;
  scanIntervalMin?: number;
  scanIntervalMax?: number;
  taskType: number;
  minProfit?: number;
  accountIds?: number[];
  accountNames?: string[];
  createTime: string;
  updateTime: string;
  finishTime?: string;
  runMode?: "SCAN" | "TRADE" | "BOTH";
  platform?: string;
  extraConfig?: string;
}

export interface TaskQueryParam {
  page: number;
  pageSize: number;
  keyword?: string;
  status?: number;
  runMode?: "SCAN" | "TRADE" | "BOTH";
  taskTypes?: number[];
  platform?: string;
}

export interface TaskSaveParam {
  id?: number;
  goodsId?: number;
  maxPrice?: number;
  minPaintwear?: number;
  maxPaintwear?: number;
  buyCount?: number;
  cronExpression?: string;
  durationMinutes?: number;
  restPeriod?: number;
  scanInterval?: number;
  scanIntervalMin?: number;
  scanIntervalMax?: number;
  taskType?: number;
  minProfit?: number;
  accountIds?: number[];
  runMode?: "SCAN" | "TRADE" | "BOTH";
  platform?: string;
  extraConfig?: string;
}
