export interface TaskItem {
  id: number;
  name: string;
  goodsId?: number;
  goodsName?: string;
  goodsIconUrl?: string;
  marketHashName?: string;
  parentCategoryName?: string;
  maxPrice?: number;
  minPaintwear?: number;
  maxPaintwear?: number;
  buyCount: number;
  successCount: number;
  status: number; // 0-停止, 1-运行中, 2-已完成, 3-异常
  cronExpression?: string;
  durationMinutes?: number;
  restPeriod?: number;
  scanInterval?: number;
  scanIntervalMin?: number;
  scanIntervalMax?: number;
  taskType: number;
  minProfit?: number;
  accountNames?: string[];
  stats?: {
    total: number;
    finished: number;
    percentage: number;
    discovery_count?: number;
    tps: number;
    update_time: number;
    pending_categories: number[];
    account_stats?: Record<
      string,
      {
        total: number;
        finished: number;
        percentage: number;
        tps: number;
      }
    >;
  };
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
  runMode?: "SCAN" | "TRADE" | "BOTH";
  platform?: string;
}
