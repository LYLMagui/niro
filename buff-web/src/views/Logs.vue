<template>
  <div class="flex h-full flex-col space-y-4">
    <!-- 头部工具栏 -->
    <div class="flex items-center justify-between">
      <h2 class="text-xl font-bold">系统日志</h2>
      <div class="space-x-2">
        <t-button theme="default" variant="outline" @click="clearLogs">
          <template #icon><clear-icon /></template>
          清空日志
        </t-button>
        <t-button theme="primary" @click="refreshLogs">
          <template #icon><refresh-icon /></template>
          刷新
        </t-button>
      </div>
    </div>

    <!-- 日志显示区域 -->
    <t-card :bordered="false" class="flex-1 overflow-hidden">
      <!-- 模拟终端风格的日志窗口 -->
      <div class="h-full overflow-auto rounded bg-gray-900 p-4 font-mono text-sm text-green-400">
        <div v-for="(log, index) in logs" :key="index" class="mb-1">
          <span class="text-gray-500">[{{ log.time }}]</span>
          <span :class="getLevelClass(log.level)" class="mx-2">[{{ log.level }}]</span>
          <span>{{ log.message }}</span>
        </div>
      </div>
    </t-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { ClearIcon, RefreshIcon } from 'tdesign-icons-vue-next';

// 模拟日志数据
const logs = ref([
  { time: '2024-03-20 10:00:01', level: 'INFO', message: 'System initialized successfully' },
  { time: '2024-03-20 10:00:02', level: 'INFO', message: 'Connected to Buff API' },
  { time: '2024-03-20 10:05:23', level: 'WARN', message: 'High latency detected: 500ms' },
  { time: '2024-03-20 10:10:45', level: 'ERROR', message: 'Failed to parse response: JSON error' },
  { time: '2024-03-20 10:11:00', level: 'INFO', message: 'Retrying connection...' },
]);

// 根据日志级别获取颜色样式
const getLevelClass = (level: string) => {
  switch (level) {
    case 'INFO':
      return 'text-blue-400';
    case 'WARN':
      return 'text-yellow-400';
    case 'ERROR':
      return 'text-red-500';
    default:
      return 'text-gray-400';
  }
};

// 清空日志
const clearLogs = () => {
  logs.value = [];
};

// 刷新日志
const refreshLogs = () => {
  // 模拟从后端获取新日志
  logs.value.push({
    time: new Date().toLocaleString(),
    level: 'INFO',
    message: 'Logs refreshed manually',
  });
};
</script>
