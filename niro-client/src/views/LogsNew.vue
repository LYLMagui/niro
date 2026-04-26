<template>
  <PageFrame :is-mobile="false" desktop-outer-class="!p-0" desktop-content-class="px-4 pt-0 pb-0">
    <div class="flex h-full flex-col overflow-hidden">
      <t-card :bordered="false" class="embedded-card flex flex-1 flex-col overflow-hidden">
        <template #title>
          <div class="flex items-center">
            <bulletpoint-icon class="mr-2 text-blue-600" />
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

        <div class="border-b border-gray-100 p-3">
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
                  v-if="canOperateLogs"
                  :theme="isConnected ? 'danger' : 'primary'"
                  class="rounded transition-all duration-300"
                  @click="toggleConnection"
                >
                  <template #icon>
                    <refresh-icon :class="{ 'animate-spin': isConnecting }" />
                  </template>
                  {{ isConnected ? "断开" : "连接" }}
                </t-button>
                <t-button
                  v-if="canOperateLogs"
                  theme="default"
                  variant="base"
                  class="rounded transition-all duration-300"
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
                  v-if="canOperateLogs"
                  variant="outline"
                  :theme="onlyErrors ? 'danger' : 'default'"
                  class="rounded transition-all duration-300"
                  @click="onlyErrors = !onlyErrors"
                >
                  <template #icon>
                    <info-circle-icon v-if="onlyErrors" />
                    <help-circle-icon v-else />
                  </template>
                  仅看错误
                </t-button>

                <t-button
                  v-if="canOperateLogs"
                  variant="base"
                  class="hidden rounded transition-all duration-300 xl:inline-flex"
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

        <div class="relative flex min-h-0 flex-1 gap-2 overflow-hidden bg-white">
          <div class="flex min-h-0 flex-1 flex-col overflow-hidden bg-gray-900">
            <div
              ref="logContainerRef"
              class="terminal-container min-h-0 flex-1 overflow-x-hidden overflow-y-auto p-3 font-mono text-[13px] leading-relaxed"
            >
              <div
                v-if="displayLogs.length === 0"
                class="flex h-full flex-col items-center justify-center text-gray-600"
              >
                <div
                  class="mb-2 flex h-12 w-12 items-center justify-center rounded-full bg-gray-800"
                >
                  <terminal-icon size="24" />
                </div>
                <p>等待实时日志流接入...</p>
              </div>

              <div
                v-for="(log, index) in filteredLogs"
                :key="index"
                class="log-line group relative border-l-2 border-transparent py-0.5 pl-3 transition-all hover:bg-white/5"
                :class="{
                  'border-blue-500 bg-blue-500/5': hasHighlightedTrace(log),
                  'border-green-500 bg-green-500/5': isSuccessLog(log.message),
                  'discovery-outline border-amber-500 bg-amber-500/5': log._isDiscovery,
                }"
              >
                <span class="mr-3 text-gray-600 select-none">{{ formatTime(log.timestamp) }}</span>

                <span
                  :class="getLevelClass(log.level)"
                  class="inline-block w-14 font-bold uppercase select-none"
                >
                  {{ log.level }}
                </span>

                <span v-if="log.ip" class="mr-2 inline-flex items-center">
                  <span
                    class="cursor-pointer rounded border border-emerald-500/20 bg-emerald-500/10 px-2 py-0.5 text-[11px] font-medium text-emerald-400/90 transition-colors hover:bg-emerald-500/20 hover:text-emerald-300"
                    @click="filterByKeyword(log.ip)"
                  >
                    {{ log.ip }}
                  </span>
                </span>
                <span v-if="log.traceId" class="mr-2 inline-flex items-center">
                  <span
                    class="cursor-pointer rounded border border-purple-500/20 bg-purple-500/10 px-2 py-0.5 text-[11px] font-medium text-purple-400/90 transition-colors hover:bg-purple-500/20 hover:text-purple-300"
                    @click="filterByKeyword(log.traceId)"
                  >
                    {{ log.traceId }}
                  </span>
                </span>
                <span v-if="log.accountName" class="mr-2 inline-flex items-center">
                  <span
                    class="cursor-pointer rounded border border-blue-500/20 bg-blue-500/10 px-2 py-0.5 text-[11px] font-medium text-blue-400/90 transition-colors hover:bg-blue-500/20 hover:text-blue-300"
                    @click="filterByKeyword(log.accountName)"
                  >
                    {{ log.accountName }}
                  </span>
                </span>

                <span
                  :class="[
                    getMessageClass(log),
                    { 'order-step-breathe': isOrderStep(log.message) },
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

          <transition name="slide-right">
            <div
              v-if="showErrorWindow"
              class="hidden w-80 flex-col overflow-hidden border-l border-gray-800 bg-gray-900 xl:flex"
            >
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
                  <div
                    class="flex items-start gap-1.5 text-[11px] leading-relaxed text-blue-300/70"
                  >
                    <t-icon name="info-circle" size="14px" class="mt-0.5 flex-shrink-0" />
                    <span>
                      符合筛选条件但由于未配置下单账号，系统仅作实时提醒，请根据日志 TraceId
                      快速定位商品。
                    </span>
                  </div>
                </div>
              </div>

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
  </PageFrame>
</template>

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
  BulletpointIcon,
} from "tdesign-icons-vue-next";
import { type LogItem } from "../api/log";
import dayjs from "dayjs";
import useNewPermission from "@/hooks/useNewPermission";
import { useNewPermissionStore } from "@/store/new-permission";

defineOptions({
  name: "LogsNew",
});

const LOG_LIST_PERMISSION_CODE = "system:logs:list";

type AudioContextConstructor = typeof AudioContext;

type ParsedLog = Omit<LogItem, "_account"> & {
  _account: string | null;
  _traceId: string | null;
  _ip?: string;
  _isDiscovery: boolean;
};

function asString(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function asNullableString(value: unknown): string | null {
  return typeof value === "string" && value.length > 0 ? value : null;
}

function isTrue(value: unknown): boolean {
  return value === true;
}

function upper(value: unknown): string {
  return asString(value).toUpperCase();
}

type MessagePart = {
  text: string;
  highlight: boolean;
  type?: "account" | "keyword" | "traceId" | "ip";
};

const newPermissionStore = useNewPermissionStore();
const { hasButtonPermission } = useNewPermission();
const canOperateLogs = computed(() => hasButtonPermission(LOG_LIST_PERMISSION_CODE));
const sseLogs = ref<LogItem[]>([]);
const searchedLogs = ref<LogItem[]>([]);
const discoveryCount = ref(0);

const playBeep = () => {
  if (document.visibilityState !== "visible") return;
  try {
    const AudioContextClass =
      window.AudioContext ||
      (window as Window & { webkitAudioContext?: AudioContextConstructor }).webkitAudioContext;
    if (!AudioContextClass) {
      return;
    }
    const audioCtx = new AudioContextClass();
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
  } catch {
    return;
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

const parseLog = (log: LogItem): ParsedLog => {
  const message = asString(log.message);
  const accMatch = message.match(/\[账号:\s*([^\s[\]：:]+)\]/);
  const traceMatch = message.match(/traceId:\s*([a-zA-Z0-9_-]{7,32})/i);
  const ipMatch = message.match(/ip:\s*(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})/i);

  return {
    ...log,
    message,
    level: asString(log.level),
    timestamp: asString(log.timestamp),
    traceId: asString(log.traceId),
    ip: asString(log.ip),
    accountName: asString(log.accountName),
    _account: asNullableString(log.accountName) || (accMatch ? accMatch[1] : null),
    _traceId: asNullableString(log.traceId) || (traceMatch ? traceMatch[1] : null),
    _ip: asString(log.ip) || ipMatch?.[1] || "",
    _isDiscovery: message.includes("🔍") || message.includes("发现捡漏机会"),
  };
};

const displayLogs = computed<ParsedLog[]>(() => {
  const baseLogs = searchedLogs.value.length > 0 ? searchedLogs.value : sseLogs.value;
  return baseLogs.map(parseLog);
});

const errorLogs = computed<ParsedLog[]>(() => {
  return displayLogs.value.filter((log) => upper(log.level) === "ERROR");
});

const filteredLogs = computed<ParsedLog[]>(() => {
  let logs = displayLogs.value;

  if (onlyErrors.value) {
    logs = logs.filter((log) => upper(log.level) === "ERROR");
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

      return kws.every((kw) => kw && content.includes(kw.toLowerCase()));
    });
  }

  return logs;
});

const formatMessage = (message: unknown): MessagePart[] => {
  const text = asString(message);
  if (!text) return [];

  const cleanMessage = text
    .replace(/^[a-zA-Z0-9_.]+:([a-zA-Z0-9_]+):(\d+)\s*-\s*/, "")
    .replace(/traceId:\s*([a-zA-Z0-9_-]{7,32})/gi, "[TraceId: $1]")
    .replace(/ip:\s*(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})/gi, "[IP: $1]");

  const kws = Array.isArray(filterKeywords.value) ? [...filterKeywords.value] : [];
  const input = (filterInput.value || "").trim();
  if (input) {
    kws.push(input);
  }

  const activeKws = kws.filter((k) => k && typeof k === "string" && k.length >= 2);

  const parts: MessagePart[] = [];

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

  return parts.length > 0 ? parts : [{ text, highlight: false }];
};

const hasHighlightedTrace = (log: ParsedLog) => {
  const traceId = asString(log.traceId);
  return Boolean(traceId) && filterKeywords.value.includes(traceId);
};

const filterByKeyword = (val: unknown) => {
  const source = asString(val);
  if (!source) return;

  const match = source.match(/\[(?:账号|TraceId|IP):\s*([^\s[\]：:]+)\]/i);
  const target = match ? match[1] : source;

  if (!filterKeywords.value.includes(target)) {
    filterKeywords.value.push(target);
  }
};

const formatTime = (ts: unknown) => {
  const value = asString(ts);
  if (!value) return "";
  return dayjs(value).format("YYYY-MM-DD HH:mm:ss.SSS");
};

const getLevelClass = (level: unknown) => {
  switch (upper(level)) {
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

const isSuccessLog = (message: unknown) => {
  const text = asString(message);
  return text.includes("下单成功") || text.includes("购买成功") || text.includes("✅");
};

const isOrderStep = (message: unknown) => {
  return /步骤\s*\d+\/\d+/.test(asString(message));
};

const getMessageClass = (log: ParsedLog) => {
  if (isTrue(log._isDiscovery)) return "text-amber-400 font-bold not-italic";
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
  if (!canOperateLogs.value) {
    isConnected.value = false;
    isConnecting.value = false;
    eventSource?.close();
    eventSource = null;
    return;
  }
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

    const standardPattern =
      /^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s*\|\s*(\w+)\s*\|\s*([^|]*)\s*\|\s*([^|]*)\s*\|\s*([^|]*)\s*\|\s*(.*)$/;
    const simplePattern =
      /^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s*\|\s*(\w+)\s*\|\s*(.*)$/;

    const standardMatch = rawData.match(standardPattern);
    const simpleMatch = rawData.match(simplePattern);

    if (standardMatch) {
      const [, timestamp, level, ip, traceId, accountName, message] = standardMatch;
      const trimmedAccount = accountName.trim();
      logData = {
        timestamp: timestamp.trim(),
        level: level.trim().toUpperCase(),
        ip: ip.trim(),
        traceId: traceId.trim() === "-" || !traceId.trim() ? "" : traceId.trim(),
        accountName: trimmedAccount === "-" || !trimmedAccount ? "" : trimmedAccount,
        message: message.trim(),
      };
    } else if (simpleMatch) {
      const [, timestamp, level, message] = simpleMatch;
      logData = {
        timestamp: timestamp.trim(),
        level: level.trim().toUpperCase(),
        message: message.trim(),
        ip: "",
        traceId: "",
        accountName: "",
      };
    } else {
      logData = {
        timestamp: new Date().toLocaleTimeString(),
        level: "INFO",
        message: rawData,
        ip: "",
        traceId: "",
        accountName: "",
      };
    }

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
  if (!canOperateLogs.value) {
    return;
  }
  if (isConnected.value) {
    eventSource?.close();
    eventSource = null;
    isConnected.value = false;
  } else {
    connect();
  }
};

watch(
  canOperateLogs,
  (allowed) => {
    if (allowed) {
      connect();
      return;
    }
    eventSource?.close();
    eventSource = null;
    isConnected.value = false;
    isConnecting.value = false;
  },
  { immediate: true }
);

onMounted(() => {
  void newPermissionStore.loadButtonPermissions();
});

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
  background-color: rgba(59, 130, 246, 0.1);
  animation: order-step-breathe 3s infinite;
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

.slide-right-enter-active,
.slide-right-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.slide-right-enter-from,
.slide-right-leave-to {
  opacity: 0;
  transform: translateX(100%);
}

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
