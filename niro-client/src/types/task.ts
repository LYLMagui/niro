export interface BuffScanTask {
  id: number;
  name: string;
  goodsId: number;
  goodsName: string;
  goodsIconUrl: string;
  maxPrice: number;
  minPaintwear: number;
  maxPaintwear: number;
  buyCount: number;
  successCount: number;
  status: number; // 0-停止, 1-运行中, 2-已完成, 3-异常
  cronExpression?: string;
  durationMinutes?: number;
  scanInterval?: number;
  createTime: string;
  updateTime: string;
}

export interface TaskQueryParam {
  pageNo: number;
  pageSize: number;
  name?: string;
  status?: number;
}

export interface TaskSaveParam {
  id?: number;
  goodsId: number;
  maxPrice: number;
  minPaintwear: number;
  maxPaintwear: number;
  buyCount: number;
  cronExpression?: string;
  durationMinutes?: number;
  scanInterval?: number;
}
