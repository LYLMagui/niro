<template>
  <div class="flex h-full flex-col overflow-hidden p-6">
    <t-card :bordered="false" class="embedded-card flex flex-1 flex-col overflow-hidden shadow-sm">
      <template #title>
        <div class="flex items-center justify-between gap-4">
          <div class="flex items-center">
            <bulletpoint-icon class="mr-2 text-blue-600" />
            <span class="text-lg font-bold text-gray-800">日志检索</span>
          </div>
          <t-tag theme="warning" variant="light">简化版仅保留 TraceId 查询</t-tag>
        </div>
      </template>

      <div class="border-b border-gray-100 p-6">
        <div class="flex flex-wrap items-center gap-3">
          <t-input
            v-model="traceId"
            placeholder="请输入 TraceId"
            clearable
            class="w-[360px]"
            @enter="handleSearch"
          >
            <template #prefixIcon><search-icon class="text-gray-400" /></template>
          </t-input>
          <t-button
            v-permission="PermissionConstant.LOG_LIST"
            theme="primary"
            :loading="loading"
            @click="handleSearch"
          >
            查询
          </t-button>
          <t-button v-permission="PermissionConstant.LOG_LIST" variant="outline" @click="handleReset">
            清空
          </t-button>
          <span class="text-xs text-gray-400">不再提供实时流、分屏和提示音。</span>
        </div>
      </div>

      <div class="min-h-0 flex-1 overflow-auto bg-[#fafafa] p-6">
        <div v-if="!canViewLogs" class="rounded border border-dashed border-gray-300 bg-white p-10 text-center text-gray-400">
          当前账号没有日志查看权限
        </div>

        <div
          v-else-if="logs.length === 0"
          class="rounded border border-dashed border-gray-300 bg-white p-10 text-center text-gray-400"
        >
          输入 TraceId 后查询日志
        </div>

        <div v-else class="space-y-3">
          <div class="text-xs text-gray-500">共找到 {{ logs.length }} 条日志</div>
          <div
            v-for="(log, index) in logs"
            :key="`${log.timestamp}-${index}`"
            class="rounded border border-gray-200 bg-white p-4 shadow-sm"
          >
            <div class="mb-2 flex flex-wrap items-center gap-2 text-xs">
              <t-tag :theme="getLevelTheme(log.level)" variant="light">{{ log.level }}</t-tag>
              <span class="text-gray-500">{{ log.timestamp || '-' }}</span>
              <t-tag v-if="log.traceId" theme="primary" variant="light">{{ log.traceId }}</t-tag>
              <t-tag v-if="log.accountName" theme="default" variant="light">{{ log.accountName }}</t-tag>
              <t-tag v-if="log.ip" theme="success" variant="light">{{ log.ip }}</t-tag>
            </div>
            <div class="whitespace-pre-wrap break-all text-sm leading-6 text-gray-700">
              {{ log.message || '-' }}
            </div>
          </div>
        </div>
      </div>
    </t-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { BulletpointIcon, SearchIcon } from "tdesign-icons-vue-next";
import { MessagePlugin } from "tdesign-vue-next";
import { searchLogs, type LogItem } from "@/api/log";
import { PermissionConstant } from "@/constant/PermissionConstant";
import { usePermission } from "@/hooks/usePermission";

const { hasPermission } = usePermission();
const canViewLogs = computed(() => hasPermission(PermissionConstant.LOG_LIST));
const traceId = ref("");
const loading = ref(false);
const logs = ref<LogItem[]>([]);

const getLevelTheme = (level: string) => {
  switch (level?.toUpperCase()) {
    case "ERROR":
      return "danger";
    case "WARN":
    case "WARNING":
      return "warning";
    case "INFO":
      return "primary";
    default:
      return "default";
  }
};

const handleSearch = async () => {
  if (!canViewLogs.value) {
    logs.value = [];
    return;
  }

  const keyword = traceId.value.trim();
  if (!keyword) {
    MessagePlugin.warning("请输入 TraceId");
    return;
  }

  loading.value = true;
  try {
    logs.value = await searchLogs(keyword);
  } catch (error) {
    console.error("查询日志失败", error);
  } finally {
    loading.value = false;
  }
};

const handleReset = () => {
  traceId.value = "";
  logs.value = [];
};
</script>
