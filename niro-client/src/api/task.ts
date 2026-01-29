import request from "@/utils/request";
import type { PageResult } from "@/types/goods";
import type { BuffScanTask, TaskQueryParam, TaskSaveParam } from "@/types/task";

export const taskApi = {
  /**
   * 分页获取任务列表
   */
  getPage(params: TaskQueryParam) {
    return request.get<PageResult<BuffScanTask>>("/task/page", { params });
  },

  /**
   * 新增任务
   */
  add(data: TaskSaveParam) {
    return request.post("/task/add", data);
  },

  /**
   * 更新任务
   */
  update(data: TaskSaveParam) {
    return request.put("/task/update", data);
  },

  /**
   * 删除任务
   */
  delete(id: number) {
    return request.delete(`/task/delete/${id}`);
  },

  /**
   * 更新状态
   */
  updateStatus(id: number, status: number, platform?: string) {
    return request.post(`/task/status/${id}/${status}`, null, { params: { platform } });
  },

  /**
   * 获取所有下单模式的任务列表
   */
  getTradeTasks(goodsId?: number) {
    return request.get<BuffScanTask[]>("/task/trade-tasks", { params: { goodsId } });
  },
};
