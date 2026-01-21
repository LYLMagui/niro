<template>
  <div class="flex h-full flex-col overflow-hidden p-6">
    <t-card :bordered="false" class="embedded-card flex flex-1 flex-col overflow-hidden shadow-sm">
      <template #title>
        <div class="flex items-center">
          <view-list-icon class="mr-2 text-blue-600" />
          <span class="text-lg font-bold text-gray-800">全链路日志</span>
          <div class="ml-4 flex items-center space-x-1.5">
            <div
              class="h-2 w-2 rounded-full"
              :class="[
                isConnected ? 'bg-green-500 shadow-[0_0_8px_rgba(34,197,94,0.6)]' : 'bg-gray-400',
                isConnected ? 'animate-pulse' : '',
              ]"
            ></div>
            <span class="text-xs text-gray-500">
              {{ isConnected ? "实时监听中" : "连接已断开" }}
            </span>
          </div>
        </div>
      </template>

      <!-- 搜索栏/工具栏 -->
      <div class="border-b border-gray-100 p-6">
        <t-row :gutter="16">
          <t-col :span="4">
            <t-tag-input
              v-model="filterKeywords"
              v-model:inputValue="filterInput"
              placeholder="搜索账号、TraceId、关键词..."
              clearable
              class="w-full"
            >
              <template #prefixIcon><search-icon class="text-gray-400" /></template>
            </t-tag-input>
          </t-col>
          <t-col :span="3">
            <div class="flex gap-4">
              <t-button
                :theme="isConnected ? 'danger' : 'primary'"
                size="medium"
                class="rounded-lg transition-all duration-300 hover:shadow active:shadow-none"
                @click="toggleConnection"
              >
                <template #icon>
                  <refresh-icon :class="{ 'animate-spin': isConnecting }" />
                </template>
                {{ isConnected ? "断开" : "连接" }}
              </t-button>
              <t-button
                theme="default"
                variant="base"
                size="medium"
                class="rounded-lg transition-all duration-300 hover:shadow active:shadow-none"
                @click="clearLogs"
              >
                <template #icon><clear-icon /></template>
                清空
              </t-button>
            </div>
          </t-col>
          <t-col :span="5">
            <div class="flex h-full w-full items-center justify-end gap-3">
              <div
                class="mr-4 hidden flex-col items-end text-[10px] leading-tight text-gray-400 lg:flex"
              >
                <div class="flex items-center">
                  <span>显示: {{ filteredLogs.length }}/{{ displayLogs.length }}</span>
                  <div
                    v-if="isConnected"
                    class="ml-1.5 h-1 w-1 animate-ping rounded-full bg-green-500"
                  ></div>
                </div>
                <span class="opacity-70">Buffer: 500 lines</span>
              </div>

              <t-button
                size="medium"
                variant="outline"
                :theme="onlyErrors ? 'danger' : 'default'"
                class="rounded-lg transition-all duration-300 hover:shadow active:shadow-none"
                @click="onlyErrors = !onlyErrors"
              >
                <template #icon>
                  <info-circle-icon v-if="onlyErrors" />
                  <help-circle-icon v-else />
                </template>
                仅看错误
              </t-button>

              <t-button
                size="medium"
                variant="base"
                class="hidden rounded-lg transition-all duration-300 hover:shadow active:shadow-none xl:inline-flex"
                :theme="showErrorWindow ? 'primary' : 'default'"
                @click="showErrorWindow = !showErrorWindow"
              >
                <template #icon><view-module-icon /></template>
                {{ showErrorWindow ? "关闭分屏" : "开启分屏" }}
              </t-button>
            </div>
          </t-col>
        </t-row>
      </div>

      <!-- 主体内容：分屏模式支持 -->
      <div class="relative flex min-h-0 flex-1 gap-2 overflow-hidden bg-white">
        <!-- 左侧/主体：终端日志窗口 -->
        <div class="flex min-h-0 flex-1 flex-col overflow-hidden bg-gray-900">
          <div
            ref="logContainerRef"
            class="terminal-container min-h-0 flex-1 overflow-x-hidden overflow-y-auto p-3 font-mono text-[13px] leading-relaxed"
          >
            <div
              v-if="displayLogs.length === 0"
              class="flex h-full flex-col items-center justify-center text-gray-600"
            >
              <div class="mb-2 flex h-12 w-12 items-center justify-center rounded-full bg-gray-800">
                <terminal-icon size="24" />
              </div>
              <p>等待实时日志流接入...</p>
            </div>

            <div
              v-for="(log, index) in filteredLogs"
              :key="index"
              class="log-line group relative border-l-2 border-transparent py-0.5 pl-3 transition-all hover:bg-white/5"
              :class="{
                'border-blue-500 bg-blue-500/5':
                  log.traceId &&
                  Array.isArray(filterKeywords) &&
                  filterKeywords.includes(log.traceId),
                'border-green-500 bg-green-500/5': isSuccessLog(log.message),
                'discovery-outline border-amber-500 bg-amber-500/5': log._isDiscovery,
              }"
            >
              <!-- 时间戳 -->
              <span class="mr-3 text-gray-600 select-none">{{ formatTime(log.timestamp) }}</span>

              <!-- 日志级别 -->
              <span
                :class="getLevelClass(log.level)"
                class="inline-block w-14 font-bold uppercase select-none"
              >
                {{ log.level }}
              </span>

              <!-- IP & TraceId 标签 -->
              <span v-if="log.ip" class="mr-2 inline-flex items-center">
                <span class="rounded bg-emerald-500/10 px-1.5 py-0.5 text-[10px] font-medium text-emerald-400/80 border border-emerald-500/20">
                  {{ log.ip }}
                </span>
              </span>
              <span v-if="log.traceId" class="mr-2 inline-flex items-center">
                <span 
                  class="cursor-pointer rounded bg-purple-500/10 px-1.5 py-0.5 text-[10px] font-medium text-purple-400/80 border border-purple-500/20 transition-colors hover:bg-purple-500/20 hover:text-purple-300"
                  @click="filterByKeyword(log.traceId)"
                >
                  {{ log.traceId }}
                </span>
              </span>

              <!-- 内容解析渲染 -->
              <span
                :class="[
                  getMessageClass(log),
                  { 'order-step-breathe': isOrderStep(log.message) }
                ]"
                class="inline-block rounded-sm px-1 break-all whitespace-pre-wrap transition-all"
              >
                <template v-for="(part, i) in formatMessage(log.message)" :key="i">
                  <span
                    v-if="part.highlight"
                    class="cursor-pointer px-1 py-0.5 transition-colors"
                    :class="{
                      'mx-0.5 rounded bg-blue-500/20 text-blue-400 hover:bg-blue-500/30':
                        part.type === 'account',
                      'mx-0.5 rounded bg-purple-500/20 text-purple-400 hover:bg-purple-500/30':
                        part.type === 'traceId',
                      'mx-0.5 rounded bg-emerald-500/20 text-emerald-400 hover:bg-emerald-500/30':
                        part.type === 'ip',
                      'bg-yellow-500/20 text-yellow-500': part.type === 'keyword',
                    }"
                    @click="
                      ['account', 'traceId', 'ip'].includes(part.type || '')
                        ? filterByKeyword(part.text)
                        : null
                    "
                  >
                    {{ part.text }}
                  </span>
                  <span
                    v-else
                    :class="
                      log._isDiscovery ? 'font-bold text-amber-400 not-italic' : 'text-gray-300'
                    "
                  >
                    {{ part.text }}
                  </span>
                </template>
              </span>
            </div>
          </div>
        </div>

        <!-- 右侧：侧边栏看板 (分屏模式) -->
        <transition name="slide-right">
          <div
            v-if="showErrorWindow"
            class="hidden w-80 flex-col overflow-hidden border-l border-gray-800 bg-gray-900 xl:flex"
          >
            <!-- 今日发现次数看板 -->
            <div class="border-b border-blue-900/30 bg-blue-900/10 px-4 py-4">
              <div class="mb-3 flex items-center justify-between">
                <span
                  class="flex items-center text-xs font-bold tracking-wider text-blue-400 uppercase"
                >
                  <t-icon name="search" class="mr-2" />
                  今日发现机会
                </span>
                <div class="flex items-baseline gap-1">
                  <span class="font-mono text-2xl font-black text-blue-400">
                    {{ discoveryCount }}
                  </span>
                  <span class="text-[10px] text-blue-500/60 uppercase">hits</span>
                </div>
              </div>
              <div class="rounded border border-blue-500/10 bg-blue-500/5 p-2">
                <div class="flex items-start gap-1.5 text-[11px] leading-relaxed text-blue-300/70">
                  <t-icon name="info-circle" size="14px" class="mt-0.5 flex-shrink-0" />
                  <span>
                    符合筛选条件但由于未配置下单账号，系统仅作实时提醒，请根据日志 TraceId
                    快速定位商品。
                  </span>
                </div>
              </div>
            </div>

            <!-- 实时异常监控 -->
            <div class="flex flex-1 flex-col overflow-hidden">
              <div
                class="flex items-center justify-between border-b border-red-900/30 bg-red-900/20 px-3 py-2"
              >
                <span
                  class="flex items-center text-xs font-bold tracking-wider text-red-400 uppercase"
                >
                  <error-circle-filled-icon class="mr-1.5" />
                  实时异常监控
                </span>
                <t-tag size="small" theme="danger" variant="dark">{{ errorLogs.length }}</t-tag>
              </div>
              <div class="flex-1 overflow-auto p-2">
                <div
                  v-if="errorLogs.length === 0"
                  class="flex h-full flex-col items-center justify-center text-gray-600 opacity-50"
                >
                  <t-icon name="check-circle" size="48px" class="mb-2" />
                  <span class="text-xs">暂无运行异常</span>
                </div>
                <div
                  v-for="(log, idx) in errorLogs.slice(-20)"
                  :key="idx"
                  class="mb-2 cursor-pointer rounded border border-red-500/10 bg-red-500/5 p-2 text-[11px] transition-colors hover:bg-red-500/10"
                  @click="filterByKeyword(log.traceId || log.message)"
                >
                  <div class="mb-1 flex justify-between font-mono text-gray-500">
                    <span>{{ formatTime(log.timestamp) }}</span>
                    <span class="font-bold text-red-400">{{ log._account || "SYSTEM" }}</span>
                  </div>
                  <div class="line-clamp-2 leading-relaxed text-gray-300">{{ log.message }}</div>
                </div>
              </div>
            </div>
          </div>
        </transition>
      </div>
    </t-card>
  </div>
</template>

<script lang="ts">
export default {
  name: "Logs",
};
</script>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed, watch } from "vue";
import {
  ClearIcon,
  RefreshIcon,
  SearchIcon,
  InfoCircleIcon,
  HelpCircleIcon,
  ViewModuleIcon,
  TerminalIcon,
  ErrorCircleFilledIcon,
  ViewListIcon,
} from "tdesign-icons-vue-next";
import { type LogItem } from "../api/log";
import dayjs from "dayjs";

const sseLogs = ref<LogItem[]>([]);
const searchedLogs = ref<LogItem[]>([]);
const discoveryCount = ref(0); // 发现次数统计

// 提示音逻辑
const playBeep = () => {
  if (document.visibilityState !== "visible") return;
  try {
    const audioCtx = new (window.AudioContext || (window as any).webkitAudioContext)();
    const oscillator = audioCtx.createOscillator();
    const gainNode = audioCtx.createGain();

    oscillator.connect(gainNode);
    gainNode.connect(audioCtx.destination);

    oscillator.type = "sine";
    oscillator.frequency.setValueAtTime(880, audioCtx.currentTime);
    gainNode.gain.setValueAtTime(0.1, audioCtx.currentTime);
    gainNode.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 0.1);

    oscillator.start();
    oscillator.stop(audioCtx.currentTime + 0.1);
  } catch (e) {
    console.warn("播放提示音失败", e);
  }
};

const onlyErrors = ref(false);
const filterKeywords = ref<string[]>([]);
const filterInput = ref("");
const isConnected = ref(false);
const isConnecting = ref(false);
const showErrorWindow = ref(true);
const logContainerRef = ref<HTMLElement | null>(null);
let eventSource: EventSource | null = null;

// 解析日志行 (仅做数据转换，无副作用)
const parseLog = (log: LogItem) => {
  const msg = log.message || "";
  const accMatch = msg.match(/\[账号:\s*([^\s[\]：:]+)\]/);
  const traceMatch = msg.match(/traceId:\s*([a-zA-Z0-9_-]{7,32})/i);

  // 识别“发现捡漏机会”标识
  const isDiscovery = msg.includes("🔍") || msg.includes("发现捡漏机会");

  return {
    ...log,
    _account: accMatch ? accMatch[1] : null,
    _traceId: log.traceId || (traceMatch ? traceMatch[1] : null),
    _ip: log.ip || (msg.match(/ip:\s*(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})/i)?.[1]),
    _isDiscovery: isDiscovery,
  };
};

const displayLogs = computed(() => {
  const baseLogs = searchedLogs.value.length > 0 ? searchedLogs.value : sseLogs.value;
  return baseLogs.map(parseLog);
});

// 错误日志抽离
const errorLogs = computed(() => {
  return displayLogs.value.filter((log) => log.level?.toUpperCase() === "ERROR");
});

// 实时过滤逻辑
const filteredLogs = computed(() => {
  let logs = displayLogs.value;

  if (onlyErrors.value) {
    logs = logs.filter((log) => log.level?.toUpperCase() === "ERROR");
  }

  const kws = Array.isArray(filterKeywords.value) ? [...filterKeywords.value] : [];
  const input = (filterInput.value || "").trim();
  if (input) {
    kws.push(input);
  }

  if (kws.length > 0) {
    logs = logs.filter((log) => {
      const content = (
        (log.message || "") +
        (log._account || "") +
        (log._traceId || "") +
        (log._ip || "")
      ).toLowerCase();

      // 所有关键字都必须包含 (AND 逻辑)
      return kws.every((kw) => kw && content.includes(kw.toLowerCase()));
    });
  }

  return logs;
});

// 格式化消息内容，提取账号和关键字
const formatMessage = (message: string) => {
  if (!message) return [];

  let cleanMessage = message
    // 再次加固：移除可能残余的类似 spiders.async_buff_spider:_trade_task_loop:436 - 的冗余信息
    .replace(/^[a-zA-Z0-9_.]+:([a-zA-Z0-9_]+):(\d+)\s*-\s*/, "")
    // 移除可能残余的 traceId/ip 标记格式（如果 messageContent 已经处理过，这里通常不会生效）
    .replace(/traceId:\s*([a-zA-Z0-9_-]{7,32})/gi, "[TraceId: $1]")
    .replace(/ip:\s*(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})/gi, "[IP: $1]");

  const kws = Array.isArray(filterKeywords.value) ? [...filterKeywords.value] : [];
  const input = (filterInput.value || "").trim();
  if (input) {
    kws.push(input);
  }

  // 过滤掉太短的关键字
  const activeKws = kws.filter((k) => k && typeof k === "string" && k.length >= 2);

  const parts: {
    text: string;
    highlight: boolean;
    type?: "account" | "keyword" | "traceId" | "ip";
  }[] = [];

  // 1. 按账号、TraceId 和 IP 正则切分
  const combinedRegex =
    /(\[账号:\s*[^\s[\]：:]+\]|\[TraceId:\s*[a-zA-Z0-9_-]{7,32}\]|\[IP:\s*\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\])/gi;
  let lastIndex = 0;
  let match;

  const rawParts: { text: string; type: "text" | "account" | "traceId" | "ip" }[] = [];
  while ((match = combinedRegex.exec(cleanMessage)) !== null) {
    if (match.index > lastIndex) {
      rawParts.push({ text: cleanMessage.substring(lastIndex, match.index), type: "text" });
    }
    const matchedText = match[0];
    if (matchedText.toLowerCase().includes("账号")) {
      rawParts.push({ text: matchedText, type: "account" });
    } else if (matchedText.toLowerCase().includes("traceid")) {
      rawParts.push({ text: matchedText, type: "traceId" });
    } else {
      rawParts.push({ text: matchedText, type: "ip" });
    }
    lastIndex = combinedRegex.lastIndex;
  }
  if (lastIndex < cleanMessage.length) {
    rawParts.push({ text: cleanMessage.substring(lastIndex), type: "text" });
  }

  // 2. 处理关键字高亮 (支持多个)
  for (const part of rawParts) {
    if (part.type === "account") {
      parts.push({ text: part.text, highlight: true, type: "account" });
    } else if (part.type === "traceId") {
      parts.push({ text: part.text, highlight: true, type: "traceId" });
    } else if (part.type === "ip") {
      parts.push({ text: part.text, highlight: true, type: "ip" });
    } else {
      if (activeKws.length === 0) {
        parts.push({ text: part.text, highlight: false });
        continue;
      }

      // 构建合并正则
      const pattern = activeKws.map((k) => k.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")).join("|");
      const kwRegex = new RegExp(`(${pattern})`, "gi");

      let kwLastIndex = 0;
      let kwMatch;
      while ((kwMatch = kwRegex.exec(part.text)) !== null) {
        if (kwMatch.index > kwLastIndex) {
          parts.push({ text: part.text.substring(kwLastIndex, kwMatch.index), highlight: false });
        }
        parts.push({ text: kwMatch[0], highlight: true, type: "keyword" });
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

  // 提取 [账号: 123]、[TraceId: abc] 或 [IP: 1.1.1.1] 中的值
  const match = val.match(/\[(?:账号|TraceId|IP):\s*([^\s[\]：:]+)\]/i);
  const target = match ? match[1] : val;

  if (!filterKeywords.value.includes(target)) {
    filterKeywords.value.push(target);
  }
};

// 格式化时间
const formatTime = (ts: string) => {
  if (!ts) return "";
  return dayjs(ts).format("YYYY-MM-DD HH:mm:ss.SSS");
};

// 级别样式
const getLevelClass = (level: string) => {
  switch (level?.toUpperCase()) {
    case "ERROR":
      return "text-red-500 font-black scale-110 origin-left";
    case "WARN":
    case "WARNING":
      return "text-amber-500 font-bold";
    case "INFO":
      return "text-blue-400 font-bold";
    case "DEBUG":
      return "text-gray-500 font-medium";
    default:
      return "text-gray-400";
  }
};

const isSuccessLog = (message: string) => {
  return (
    message &&
    (message.includes("下单成功") || message.includes("购买成功") || message.includes("✅"))
  );
};

const isOrderStep = (message: string) => {
  return message && /步骤\s*\d+\/\d+/.test(message);
};

const getMessageClass = (log: any) => {
  if (log._isDiscovery) return "text-amber-400 font-bold not-italic";
  if (isSuccessLog(log.message)) return "text-green-400 font-medium";
  if (isOrderStep(log.message)) return "text-blue-300 font-bold";
  return "text-gray-300";
};

const clearLogs = () => {
  sseLogs.value = [];
  searchedLogs.value = [];
  filterKeywords.value = [];
  filterInput.value = "";
};

const scrollToBottom = async () => {
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
    const rawData = event.data;

    if (!rawData) return;

    // 尝试解析文本行格式：2026-01-21 23:35:38.420 | INFO     | spiders.async_buff_spider:async_buy_v3:946 - traceId: ... | ip: ... | message
    const logPattern =
      /^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s*\|\s*(\w+)\s*\|\s*([^\s-]+:[^\s-]+:\d+)\s*-\s*(.*)$/;
    const match = rawData.match(logPattern);

    if (match) {
      const [, timestamp, level, location, rest] = match;
      // 提取 traceId (如果有)
      const traceMatch = rest.match(/traceId:\s*([a-zA-Z0-9_-]{7,32})/i);
      // 提取 ip (如果有)
      const ipMatch = rest.match(/ip:\s*(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})/i);
      
      // 提取真正的消息内容 (去掉 traceId 和 ip 部分)
      const messageContent = rest.replace(/traceId:\s*[a-zA-Z0-9_-]{7,32}\s*\|\s*/i, "").replace(/ip:\s*[\d.]+\s*\|\s*/i, "");

      logData = {
        timestamp: timestamp.trim(),
        level: level.trim().toUpperCase(),
        message: messageContent.trim(),
        traceId: traceMatch ? traceMatch[1] : "",
        ip: ipMatch ? ipMatch[1] : "",
      };
    } else {
      // 降级：如果不是标准格式，按纯文本处理
      logData = {
        timestamp: new Date().toISOString(),
        level: "INFO",
        message: rawData,
        traceId: "",
      };
    }

    // 检查是否为“发现捡漏机会”
    const msg = logData.message || "";
    if (msg.includes("🔍") || msg.includes("发现捡漏机会")) {
      discoveryCount.value++;
      playBeep();
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
.terminal-container::-webkit-scrollbar {
  width: 6px;
}
.terminal-container::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 3px;
}
.terminal-container::-webkit-scrollbar-track {
  background: transparent;
}

.discovery-outline {
  border-left-width: 4px !important;
  animation: discovery-pulse 2s infinite;
}

@keyframes discovery-pulse {
  0% {
    box-shadow: inset 0 0 0 0 rgba(245, 158, 11, 0.1);
  }
  50% {
    box-shadow: inset 0 0 10px 2px rgba(245, 158, 11, 0.2);
  }
  100% {
    box-shadow: inset 0 0 0 0 rgba(245, 158, 11, 0.1);
  }
}

.order-step-breathe {
  animation: order-step-breathe 3s infinite;
  background-color: rgba(59, 130, 246, 0.1);
}

@keyframes order-step-breathe {
  0% {
    background-color: rgba(59, 130, 246, 0.05);
    box-shadow: 0 0 0px rgba(59, 130, 246, 0);
  }
  50% {
    background-color: rgba(59, 130, 246, 0.2);
    box-shadow: 0 0 8px rgba(59, 130, 246, 0.3);
  }
  100% {
    background-color: rgba(59, 130, 246, 0.05);
    box-shadow: 0 0 0px rgba(59, 130, 246, 0);
  }
}

/* 动画：分屏滑入 */
.slide-right-enter-active,
.slide-right-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.slide-right-enter-from,
.slide-right-leave-to {
  opacity: 0;
  transform: translateX(100%);
}

/* 覆盖 TDesign 样式微调 */
:deep(.t-card) {
  border-radius: 8px;
}
:deep(.t-card__header) {
  padding: 16px 24px;
}
:deep(.t-card__body) {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}
:deep(.t-input--size-s) {
  background-color: rgba(0, 0, 0, 0.03);
}
.dark :deep(.t-input--size-s) {
  background-color: rgba(255, 255, 255, 0.05);
}
</style>
