<template>
  <div class="flex h-full flex-col overflow-hidden bg-gray-50 p-2 dark:bg-gray-900 md:p-4">
    <!-- Toolbar: v1.21.0 压缩版单行布局 -->
    <div class="mb-2 flex flex-wrap items-center justify-between gap-2 rounded-lg bg-white p-2 shadow-sm dark:bg-gray-800">
      <!-- 左侧：标题与状态 -->
      <div class="flex items-center space-x-3">
        <h2 class="text-base font-bold text-gray-800 dark:text-gray-200">全链路日志</h2>
        <div class="flex items-center space-x-1.5">
          <div 
            class="h-2 w-2 rounded-full" 
            :class="[
              isConnected ? 'bg-green-500 shadow-[0_0_8px_rgba(34,197,94,0.6)]' : 'bg-gray-400',
              isConnected ? 'animate-pulse' : ''
            ]"
          ></div>
          <span class="text-xs text-gray-500">{{ isConnected ? '实时监听中' : '连接已断开' }}</span>
        </div>
      </div>

      <!-- 中间：响应式过滤搜索框 -->
      <div class="flex flex-1 items-center justify-center px-4 max-w-2xl">
        <t-input 
          v-model="filterKeyword" 
          placeholder="搜索账号、TraceId、关键词..." 
          size="small"
          clearable 
          class="w-full"
        >
          <template #prefixIcon><search-icon class="text-gray-400" /></template>
        </t-input>
      </div>

      <!-- 右侧：功能按钮组与性能反馈 -->
      <div class="flex items-center space-x-2">
        <div class="mr-4 hidden flex-col items-end text-[10px] leading-tight text-gray-400 lg:flex">
          <div class="flex items-center">
            <span>当前显示: {{ filteredLogs.length }} / {{ displayLogs.length }} 行</span>
            <div v-if="isConnected" class="ml-1.5 h-1 w-1 rounded-full bg-green-500 animate-ping"></div>
          </div>
          <span class="opacity-70">Buffer: 500 lines</span>
        </div>

        <t-button size="small" variant="text" @click="onlyErrors = !onlyErrors" :theme="onlyErrors ? 'danger' : 'default'">
          <template #icon><info-circle-icon v-if="onlyErrors" /><help-circle-icon v-else /></template>
          仅看错误
        </t-button>
        
        <t-divider layout="vertical" />
        
        <t-button size="small" variant="text" @click="clearLogs">
          <template #icon><clear-icon /></template>
        </t-button>
        
        <t-button size="small" :theme="isConnected ? 'danger' : 'primary'" variant="base" @click="toggleConnection">
          <template #icon><refresh-icon :class="{ 'animate-spin': isConnecting }" /></template>
        </t-button>
        
        <!-- 分屏切换按钮 (仅宽屏显示) -->
        <t-button size="small" variant="text" class="hidden xl:inline-flex" @click="showErrorWindow = !showErrorWindow" :theme="showErrorWindow ? 'primary' : 'default'">
          <template #icon><view-module-icon /></template>
        </t-button>
      </div>
    </div>

    <!-- 主体内容：分屏模式支持 -->
    <div class="relative flex flex-1 gap-2 overflow-hidden">
      <!-- 左侧/主体：终端日志窗口 -->
      <div class="flex flex-1 flex-col overflow-hidden rounded-lg bg-gray-900 shadow-xl border border-gray-800">
        <div
          ref="logContainerRef"
          class="terminal-container flex-1 overflow-y-auto overflow-x-hidden p-3 font-mono text-[13px] leading-relaxed"
        >
          <div v-if="displayLogs.length === 0" class="flex h-full flex-col items-center justify-center text-gray-600">
            <div class="mb-2 h-12 w-12 rounded-full bg-gray-800 flex items-center justify-center">
              <terminal-icon size="24" />
            </div>
            <p>等待实时日志流接入...</p>
          </div>
          
          <div
            v-for="(log, index) in filteredLogs"
            :key="index"
            class="log-line group relative border-l-2 border-transparent py-0.5 pl-3 transition-all hover:bg-white/5"
            :class="{
              'border-blue-500 bg-blue-500/5': log.traceId && log.traceId === filterKeyword,
              'border-green-500 bg-green-500/5': isSuccessLog(log.message)
            }"
          >
            <!-- 时间戳 -->
            <span class="mr-3 select-none text-gray-600">{{ formatTime(log.timestamp) }}</span>
            
            <!-- 日志级别 -->
            <span :class="getLevelClass(log.level)" class="mr-3 inline-block w-12 font-bold select-none uppercase">
              {{ log.level }}
            </span>
            
            <!-- 内容解析渲染 -->
            <span :class="getMessageClass(log.message)" class="break-all whitespace-pre-wrap">
              <template v-for="(part, i) in formatMessage(log.message)" :key="i">
                <t-tag 
                  v-if="part.type === 'account'" 
                  size="small" 
                  :style="{ backgroundColor: getAccountColor(part.text), color: '#fff', borderColor: 'transparent' }"
                  class="mx-1 cursor-pointer hover:brightness-110 active:scale-95 transition-all"
                  @click="filterByKeyword(part.text)"
                >
                  {{ part.text }}
                </t-tag>
                <mark v-else-if="part.type === 'keyword'" class="rounded-sm bg-yellow-400/80 px-0.5 text-gray-900">
                  {{ part.text }}
                </mark>
                <span v-else-if="part.highlight" class="text-blue-400 font-medium underline decoration-dotted underline-offset-4">
                  {{ part.text }}
                </span>
                <span v-else>{{ part.text }}</span>
              </template>
            </span>
            
            <!-- 右侧悬浮 TraceID -->
            <div class="absolute right-2 top-0 hidden h-full items-center group-hover:flex">
              <t-tag 
                v-if="log.traceId" 
                size="extra-small" 
                variant="dark"
                class="cursor-pointer bg-gray-700 text-gray-300 hover:bg-blue-600 hover:text-white"
                @click="filterByKeyword(log.traceId)"
              >
                #{{ log.traceId.substring(0, 8) }}
              </t-tag>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧/悬浮：错误监控分窗 (宽屏模式) -->
      <transition name="slide-right">
        <div 
          v-if="showErrorWindow && errorLogs.length > 0" 
          class="hidden w-80 flex-col overflow-hidden rounded-lg bg-gray-900 border-l border-red-900/30 shadow-2xl xl:flex"
        >
          <div class="flex items-center justify-between bg-red-900/20 px-3 py-2 border-b border-red-900/30">
            <span class="text-xs font-bold text-red-400 flex items-center">
              <error-circle-filled-icon class="mr-1.5" /> 实时异常监控
            </span>
            <t-tag size="extra-small" theme="danger">{{ errorLogs.length }}</t-tag>
          </div>
          <div class="flex-1 overflow-auto p-2">
            <div 
              v-for="(log, idx) in errorLogs.slice(-20)" 
              :key="idx" 
              class="mb-2 rounded bg-red-500/5 p-2 text-[11px] border border-red-500/10 hover:bg-red-500/10 cursor-pointer"
              @click="filterByKeyword(log.traceId || log.message)"
            >
              <div class="mb-1 flex justify-between text-gray-500">
                <span>{{ formatTime(log.timestamp) }}</span>
                <span class="text-red-400">{{ log._account }}</span>
              </div>
              <div class="text-gray-300 line-clamp-2">{{ log.message }}</div>
            </div>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<script lang="ts">
export default {
  name: "Logs"
};
</script>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed, watch } from "vue";
import { 
  ClearIcon, RefreshIcon, SearchIcon, InfoCircleIcon, 
  HelpCircleIcon, ViewModuleIcon, TerminalIcon, ErrorCircleFilledIcon
} from "tdesign-icons-vue-next";
import { searchLogs, type LogItem } from "../api/log";
import dayjs from "dayjs";

const sseLogs = ref<LogItem[]>([]);
const searchedLogs = ref<LogItem[]>([]);
const searchTraceId = ref("");
const filterKeyword = ref("");
const onlyErrors = ref(false);
const isSearching = ref(false);
const isConnected = ref(false);
const isConnecting = ref(false);
const showErrorWindow = ref(true);
const logContainerRef = ref<HTMLElement | null>(null);
let eventSource: EventSource | null = null;

// 动态账号颜色生成
const getAccountColor = (name: string) => {
  if (!name) return '#4b5563';
  // 提取账号中的数字或标识
  const match = name.match(/(\d+)/);
  const id = match ? parseInt(match[1]) : name.length * 12345;
  const hues = [210, 260, 280, 310, 330, 10, 30]; // 蓝色、紫色、洋红、橙色系
  const hue = hues[id % hues.length];
  return `hsl(${hue}, 60%, 45%)`;
};

// 解析日志行
const parseLog = (log: LogItem) => {
  const msg = log.message || "";
  const accMatch = msg.match(/\[账号:\s*([^\s\[\]：:]+)\]/);
  const traceMatch = msg.match(/traceId:\s*([a-f0-9]{32})/i);
  
  return {
    ...log,
    _account: accMatch ? accMatch[1] : null,
    _traceId: log.traceId || (traceMatch ? traceMatch[1] : null)
  };
};

const displayLogs = computed(() => {
  const baseLogs = searchedLogs.value.length > 0 ? searchedLogs.value : sseLogs.value;
  return baseLogs.map(parseLog);
});

// 错误日志抽离
const errorLogs = computed(() => {
  return displayLogs.value.filter(log => log.level?.toUpperCase() === 'ERROR');
});

// 实时过滤逻辑
const filteredLogs = computed(() => {
  let logs = displayLogs.value;

  if (onlyErrors.value) {
    logs = logs.filter(log => log.level?.toUpperCase() === "ERROR");
  }

  if (filterKeyword.value) {
    const kw = filterKeyword.value.toLowerCase();
    logs = logs.filter(log => {
      return (
        log.message?.toLowerCase().includes(kw) ||
        log._account?.toLowerCase().includes(kw) ||
        log._traceId?.toLowerCase().includes(kw)
      );
    });
  }

  return logs;
});

// 格式化消息内容，提取账号和关键字
const formatMessage = (message: string) => {
  if (!message) return [];
  
  // 移除消息开头可能存在的冗余时间戳格式 (YYYY-MM-DD HH:mm:ss.SSS | )
  // 增加对多种空格/管道符变体的支持
  const cleanMessage = message.replace(/^\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3}\s*\|\s*/, "");
  
  const parts: { text: string; highlight: boolean; type?: 'account' | 'keyword' }[] = [];
  const kw = filterKeyword.value;
  
  // 1. 按账号正则切分 [账号: xxx]
  const accRegex = /(\[账号:\s*[^\s\[\]：:]+\])/g;
  let lastIndex = 0;
  let match;

  const rawParts: { text: string; type: 'text' | 'account' }[] = [];
  while ((match = accRegex.exec(cleanMessage)) !== null) {
    if (match.index > lastIndex) {
      rawParts.push({ text: cleanMessage.substring(lastIndex, match.index), type: 'text' });
    }
    rawParts.push({ text: match[0], type: 'account' });
    lastIndex = accRegex.lastIndex;
  }
  if (lastIndex < cleanMessage.length) {
    rawParts.push({ text: cleanMessage.substring(lastIndex), type: 'text' });
  }

  // 2. 二次切分关键字
  for (const part of rawParts) {
    if (part.type === 'account') {
      parts.push({ text: part.text, highlight: true, type: 'account' });
    } else {
      if (!kw || kw.length < 2) { // 关键字太短不触发标记
        parts.push({ text: part.text, highlight: false });
        continue;
      }

      const kwRegex = new RegExp(`(${kw.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")})`, "gi");
      let kwLastIndex = 0;
      let kwMatch;
      while ((kwMatch = kwRegex.exec(part.text)) !== null) {
        if (kwMatch.index > kwLastIndex) {
          parts.push({ text: part.text.substring(kwLastIndex, kwMatch.index), highlight: false });
        }
        parts.push({ text: kwMatch[0], highlight: true, type: 'keyword' });
        kwLastIndex = kwRegex.lastIndex;
      }
      if (kwLastIndex < part.text.length) {
        parts.push({ text: part.text.substring(kwLastIndex), highlight: false });
      }
    }
  }

  return parts.length > 0 ? parts : [{ text: message, highlight: false }];
};

const filterByKeyword = (val: string | null) => {
  if (!val) return;
  // 如果是完整的 [账号: xxx] 则提取内部名称
  const match = val.match(/\[账号:\s*([^\s\[\]：:]+)\]/);
  filterKeyword.value = match ? match[1] : val;
};

// 格式化时间
const formatTime = (ts: string) => {
  if (!ts) return "";
  return dayjs(ts).format("HH:mm:ss.SSS");
};

// 级别样式
const getLevelClass = (level: string) => {
  switch (level?.toUpperCase()) {
    case "ERROR": return "text-red-500";
    case "WARN": return "text-yellow-500";
    case "INFO": return "text-blue-400";
    case "DEBUG": return "text-gray-500";
    default: return "text-gray-600";
  }
};

const isSuccessLog = (message: string) => {
  return message && (message.includes("下单成功") || message.includes("购买成功") || message.includes("✅"));
};

const getMessageClass = (message: string) => {
  if (isSuccessLog(message)) return "text-green-400 font-medium";
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
  } catch (err) {
    console.error("Search logs failed:", err);
  } finally {
    isSearching.value = false;
  }
};

const clearLogs = () => {
  sseLogs.value = [];
  searchedLogs.value = [];
};

const scrollToBottom = async (smooth = false) => {
  await nextTick();
  if (logContainerRef.value) {
    const container = logContainerRef.value;
    // 使用 requestAnimationFrame 确保在 DOM 完全渲染后执行
    requestAnimationFrame(() => {
      container.scrollTop = container.scrollHeight;
    });
  }
};

watch(
  () => displayLogs.value,
  () => scrollToBottom(),
  { deep: true }
);

const connect = () => {
  if (isConnected.value) return;
  isConnecting.value = true;
  
  const baseApi = import.meta.env.VITE_BASE_API || "/dev-api";
  eventSource = new EventSource(`${baseApi}/log/stream`);

  eventSource.onopen = () => {
    isConnected.value = true;
    isConnecting.value = false;
  };

  eventSource.onmessage = (event) => {
    let logData: LogItem;
    try {
      logData = JSON.parse(event.data);
    } catch (e) {
      logData = { timestamp: new Date().toISOString(), level: "INFO", message: event.data };
    }
    sseLogs.value.push(logData);
    if (sseLogs.value.length > 500) sseLogs.value.shift();
  };

  eventSource.onerror = () => {
    isConnected.value = false;
    isConnecting.value = false;
    eventSource?.close();
    eventSource = null;
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

onMounted(() => connect());
onUnmounted(() => eventSource?.close());
</script>

<style scoped>
/* 自定义 Webkit 滚动条：更细更深 */
.terminal-container::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
.terminal-container::-webkit-scrollbar-track {
  background: transparent;
}
.terminal-container::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 10px;
}
.terminal-container::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.2);
}

/* 动画：分屏滑入 */
.slide-right-enter-active,
.slide-right-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.slide-right-enter-from,
.slide-right-leave-to {
  transform: translateX(100%);
  opacity: 0;
}

/* 覆盖 TDesign 样式微调 */
:deep(.t-card) {
  border-radius: 8px;
}
:deep(.t-input--size-s) {
  background-color: rgba(0, 0, 0, 0.03);
}
.dark :deep(.t-input--size-s) {
  background-color: rgba(255, 255, 255, 0.05);
}
</style>
