<template>
  <div class="flex h-full flex-col space-y-4 p-4">
    <!-- 头部工具栏 -->
    <div class="flex items-center justify-between">
      <div class="flex items-center space-x-4">
        <h2 class="text-xl font-bold">全链路追踪日志</h2>
        <div class="flex w-80 items-center space-x-2">
          <t-input v-model="searchTraceId" placeholder="输入 TraceId 进行全链路追踪" clearable @enter="handleSearch">
            <template #prefixIcon><search-icon /></template>
          </t-input>
          <t-button @click="handleSearch" :loading="isSearching">查询</t-button>
        </div>
      </div>
      <div class="space-x-2">
        <t-button theme="default" variant="outline" @click="clearLogs">
          <template #icon><clear-icon /></template>
          清空屏幕
        </t-button>
        <t-button :theme="isConnected ? 'danger' : 'primary'" @click="toggleConnection">
          <template #icon><refresh-icon :class="{ 'animate-spin': isConnecting }" /></template>
          {{ isConnected ? "实时心跳" : "开启监听" }}
        </t-button>
      </div>
    </div>

    <!-- 日志显示区域 -->
    <t-card :bordered="false" class="flex flex-1 flex-col overflow-hidden">
      <!-- 模拟终端风格的日志窗口 -->
      <div
        ref="logContainerRef"
        class="h-[calc(100vh-200px)] overflow-auto rounded bg-gray-900 p-4 font-mono text-sm leading-6"
      >
        <div v-if="displayLogs.length === 0" class="mt-10 text-center text-gray-500">
          暂无日志，请输入 TraceId 查询或开启实时心跳...
        </div>
        
        <div
          v-for="(log, index) in displayLogs"
          :key="index"
          class="group border-l-2 border-transparent py-0.5 pl-2 transition-all hover:bg-gray-800"
          :class="{
            'border-blue-500 bg-blue-900/10': log.traceId && log.traceId === searchTraceId,
            'border-green-500 bg-green-900/10': isSuccessLog(log.message)
          }"
        >
          <!-- 时间戳和级别 -->
          <span class="mr-2 text-gray-500">{{ formatTime(log.timestamp) }}</span>
          <span :class="getLevelClass(log.level)" class="mr-2 font-bold">[{{ log.level }}]</span>
          
          <!-- 服务标识 -->
          <span v-if="log.service" class="mr-2 text-purple-400">[{{ log.service }}]</span>
          
          <!-- 核心内容 -->
          <span :class="getMessageClass(log.message)" class="break-all whitespace-pre-wrap">{{ log.message }}</span>
          
          <!-- TraceID 标识 (仅在非搜索状态下显示) -->
          <t-tag v-if="log.traceId && log.traceId !== searchTraceId" size="small" theme="primary" variant="light" class="ml-2 opacity-0 group-hover:opacity-100">
            {{ log.traceId }}
          </t-tag>
        </div>
      </div>
    </t-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed } from "vue";
import { ClearIcon, RefreshIcon, SearchIcon } from "tdesign-icons-vue-next";
import { searchLogs, type LogItem } from "../api/log";
import dayjs from "dayjs";

const sseLogs = ref<LogItem[]>([]);
const searchedLogs = ref<LogItem[]>([]);
const searchTraceId = ref("");
const isSearching = ref(false);
const isConnected = ref(false);
const isConnecting = ref(false);
const logContainerRef = ref<HTMLElement | null>(null);
let eventSource: EventSource | null = null;

// 合并显示的日志
const displayLogs = computed(() => {
  if (searchedLogs.value.length > 0) {
    return searchedLogs.value;
  }
  return sseLogs.value;
});

// 格式化时间
const formatTime = (ts: string) => {
  if (!ts) return "";
  return dayjs(ts).format("HH:mm:ss.SSS");
};

// 获取日志级别样式
const getLevelClass = (level: string) => {
  switch (level?.toUpperCase()) {
    case "ERROR": return "text-red-500";
    case "WARN": return "text-yellow-400";
    case "INFO": return "text-blue-400";
    case "DEBUG": return "text-gray-400";
    default: return "text-gray-500";
  }
};

// 判断是否为下单成功日志
const isSuccessLog = (message: string) => {
  return message && (message.includes("下单成功") || message.includes("购买成功") || message.includes("✅"));
};

// 获取消息内容样式
const getMessageClass = (message: string) => {
  if (isSuccessLog(message)) {
    return "text-[#2ba471] font-bold"; // 使用 v1.18.3 提到的“成交绿”
  }
  return "text-gray-300";
};

const handleSearch = async () => {
  if (!searchTraceId.value) {
    searchedLogs.value = [];
    return;
  }
  
  isSearching.value = true;
  try {
    const res = await searchLogs(searchTraceId.value);
    searchedLogs.value = res;
    await scrollToBottom();
  } catch (err) {
    console.error("Search logs failed:", err);
  } finally {
    isSearching.value = false;
  }
};

const clearLogs = () => {
  sseLogs.value = [];
  searchedLogs.value = [];
  searchTraceId.value = "";
};

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
  sseLogs.value.push({
    timestamp: new Date().toISOString(),
    level: "INFO",
    message: ">>> 正在连接日志服务..."
  });

  const baseApi = import.meta.env.VITE_BASE_API || "/dev-api";
  eventSource = new EventSource(`${baseApi}/log/stream`);

  eventSource.onopen = () => {
    isConnected.value = true;
    isConnecting.value = false;
    sseLogs.value.push({
      timestamp: new Date().toISOString(),
      level: "INFO",
      message: ">>> 连接成功，开始监听心跳日志..."
    });
    scrollToBottom();
  };

  eventSource.onmessage = (event) => {
    // 假设心跳日志也是简单的字符串或 JSON
    let logData: LogItem;
    try {
      logData = JSON.parse(event.data);
    } catch (e) {
      logData = {
        timestamp: new Date().toISOString(),
        level: "INFO",
        message: event.data
      };
    }
    
    sseLogs.value.push(logData);
    if (sseLogs.value.length > 500) {
      sseLogs.value.shift();
    }
    if (searchedLogs.value.length === 0) {
      scrollToBottom();
    }
  };

  eventSource.onerror = (err) => {
    console.error("SSE Error:", err);
    isConnected.value = false;
    isConnecting.value = false;
    eventSource?.close();
    eventSource = null;
    scrollToBottom();
  };
};

const toggleConnection = () => {
  if (isConnected.value) {
    eventSource?.close();
    eventSource = null;
    isConnected.value = false;
  } else {
    connect();
  }
};

onMounted(() => {
  // 默认不自动连接，等待用户点击或搜索
});

onUnmounted(() => {
  eventSource?.close();
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
