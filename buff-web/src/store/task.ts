import { defineStore } from 'pinia';
import { ref } from 'vue';

// 定义任务状态 Store
export const useTaskStore = defineStore('task', () => {
  // 状态：任务是否正在运行
  const isRunning = ref(false);
  // 状态：上次运行时间
  const lastRunTime = ref<string>('');

  // 动作：启动任务
  function startTask() {
    isRunning.value = true;
    lastRunTime.value = new Date().toLocaleString();
  }

  // 动作：停止任务
  function stopTask() {
    isRunning.value = false;
  }

  return { isRunning, lastRunTime, startTask, stopTask };
});
