import { defineStore } from "pinia";
import { ref } from "vue";
import { taskApi } from "@/api/task";
import type { TaskItem } from "@/types/task";
import { TaskStatusEnum } from "@/enums/TaskStatusEnum";

// 定义任务状态 Store
export const useTaskStore = defineStore("task", () => {
  // 状态：任务是否正在运行
  const isRunning = ref(false);
  // 状态：正在运行的任务列表
  const runningTasks = ref<TaskItem[]>([]);
  // 状态：上次运行时间
  const lastRunTime = ref<string>("");

  // 动作：获取运行中的任务
  async function fetchRunningTasks() {
    try {
      const res = await taskApi.getPage({ page: 1, pageSize: 10, status: TaskStatusEnum.RUNNING });
      if (res && res.records) {
        runningTasks.value = res.records;
        isRunning.value = runningTasks.value.length > 0;
      }
    } catch (error) {
      console.error("获取运行中任务失败:", error);
    }
  }

  // 动作：启动任务
  async function startTask(id: number) {
    try {
      await taskApi.updateStatus(id, TaskStatusEnum.RUNNING);
      await fetchRunningTasks();
    } catch (error) {
      console.error("启动任务失败:", error);
    }
  }

  // 动作：停止任务
  async function stopTask(id: number) {
    try {
      await taskApi.updateStatus(id, TaskStatusEnum.STOPPED);
      await fetchRunningTasks();
    } catch (error) {
      console.error("停止任务失败:", error);
    }
  }

  return { isRunning, runningTasks, lastRunTime, fetchRunningTasks, startTask, stopTask };
});
