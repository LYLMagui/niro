<template>
  <div class="flex h-full flex-col space-y-4 p-4">
    <!-- 头部工具栏 -->
    <div class="flex items-center justify-between">
      <h2 class="text-xl font-bold">扫货任务日志 (实时)</h2>
      <div class="space-x-2">
        <t-button theme="default" variant="outline" @click="clearLogs">
          <template #icon><clear-icon /></template>
          清空屏幕
        </t-button>
        <t-button :theme="isConnected ? 'danger' : 'primary'" @click="toggleConnection">
          <template #icon><refresh-icon :class="{ 'animate-spin': isConnecting }" /></template>
          {{ isConnected ? '停止监听' : '开始监听' }}
        </t-button>
      </div>
    </div>

    <!-- 日志显示区域 -->
    <t-card :bordered="false" class="flex-1 overflow-hidden flex flex-col">
      <!-- 模拟终端风格的日志窗口 -->
      <div 
        ref="logContainerRef"
        class="h-[calc(100vh-200px)] overflow-auto rounded bg-gray-900 p-4 font-mono text-sm leading-6"
      >
        <div v-if="logs.length === 0" class="text-gray-500 text-center mt-10">
          暂无日志或等待连接...
        </div>
        <div v-for="(log, index) in logs" :key="index" class="whitespace-pre-wrap break-all hover:bg-gray-800">
           <!-- 简单高亮处理 -->
          <span v-if="log.includes('ERROR')" class="text-red-500">{{ log }}</span>
          <span v-else-if="log.includes('WARN')" class="text-yellow-400">{{ log }}</span>
          <span v-else-if="log.includes('INFO')" class="text-blue-400">{{ log }}</span>
          <span v-else class="text-gray-300">{{ log }}</span>
        </div>
      </div>
    </t-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from "vue";
import { ClearIcon, RefreshIcon } from "tdesign-icons-vue-next";
import { MessagePlugin } from "tdesign-vue-next";

const logs = ref<string[]>([]);
const isConnected = ref(false);
const isConnecting = ref(false);
const logContainerRef = ref<HTMLElement | null>(null);
let eventSource: EventSource | null = null;

// 自动滚动到底部
const scrollToBottom = async () => {
  await nextTick();
  if (logContainerRef.value) {
    logContainerRef.value.scrollTop = logContainerRef.value.scrollHeight;
  }
};

const connect = () => {
  if (isConnected.value) return;
  
  isConnecting.value = true;
  logs.value.push(">>> 正在连接日志服务...");
  
  // 使用 /dev-api 前缀，由 Vite 代理转发到后端
  eventSource = new EventSource("/dev-api/log/stream");

  eventSource.onopen = () => {
    isConnected.value = true;
    isConnecting.value = false;
    logs.value.push(">>> 连接成功，开始监听日志...");
    scrollToBottom();
  };

  eventSource.onmessage = (event) => {
    logs.value.push(event.data);
    // 限制日志条数，防止内存溢出 (例如保留最近 2000 行)
    if (logs.value.length > 2000) {
      logs.value.shift();
    }
    scrollToBottom();
  };

  eventSource.onerror = (err) => {
    console.error("SSE Error:", err);
    if (eventSource?.readyState === EventSource.CLOSED) {
      logs.value.push(">>> 连接已关闭");
      isConnected.value = false;
      isConnecting.value = false;
      eventSource.close();
      eventSource = null;
    } else {
       // 尝试重连中...
       // logs.value.push(">>> 连接中断，尝试重连...");
    }
    scrollToBottom();
  };
};

const disconnect = () => {
  if (eventSource) {
    eventSource.close();
    eventSource = null;
  }
  isConnected.value = false;
  isConnecting.value = false;
  logs.value.push(">>> 监听已停止");
  scrollToBottom();
};

const toggleConnection = () => {
  if (isConnected.value) {
    disconnect();
  } else {
    connect();
  }
};

const clearLogs = () => {
  logs.value = [];
};

onMounted(() => {
  connect();
});

onUnmounted(() => {
  disconnect();
});
</script>

<style scoped>
/* 自定义滚动条样式 */
::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}
::-webkit-scrollbar-track {
  background: #1f2937; 
}
::-webkit-scrollbar-thumb {
  background: #4b5563; 
  border-radius: 4px;
}
::-webkit-scrollbar-thumb:hover {
  background: #6b7280; 
}
</style>
