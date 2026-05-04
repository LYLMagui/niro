<template>
  <PageFrame desktop-outer-class="!p-0">
    <PageHeader title="控制面板">
      <template #icon>
        <svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z"
          />
        </svg>
      </template>
    </PageHeader>
    <div class="space-y-6 p-4">
      <!-- 顶部数据卡片区域，使用 Grid 布局 -->
      <div class="grid grid-cols-1 gap-6 md:grid-cols-4">
        <!-- 卡片：今日爬取数量 -->
        <t-card title="今日爬取">
          <div class="text-3xl font-bold text-blue-600">1,234</div>
          <div class="mt-2 text-sm text-gray-500">较昨日 +12%</div>
        </t-card>

        <!-- 卡片：下单成功数量 -->
        <t-card title="下单成功">
          <div class="text-3xl font-bold text-green-600">56</div>
          <div class="mt-2 text-sm text-gray-500">成功率 98%</div>
        </t-card>

        <!-- 卡片：今日发现 (监控模式命中) -->
        <t-card title="今日发现">
          <div class="text-3xl font-bold text-amber-500">{{ totalDiscoveryCount }}</div>
          <div class="mt-2 text-sm text-gray-500">符合条件但未下单</div>
        </t-card>

        <!-- 卡片：运行状态控制 -->
        <t-card title="运行状态">
          <div class="flex items-center gap-2">
            <!-- 状态指示灯 -->
            <div :class="['h-3 w-3 rounded-full', isRunning ? 'bg-green-500' : 'bg-red-500']"></div>
            <span class="text-xl font-bold">{{ isRunning ? "运行中" : "已停止" }}</span>
          </div>
          <div class="mt-4">
            <!-- 控制按钮 -->
            <t-button
              v-if="!isRunning && canStartScan"
              theme="primary"
              class="rounded transition-all duration-300"
              @click="handleGlobalStart"
            >
              启动任务
            </t-button>
            <t-button
              v-else-if="canStopScan"
              theme="danger"
              class="rounded transition-all duration-300"
              @click="handleGlobalStop"
            >
              停止任务
            </t-button>
          </div>
        </t-card>

        <!-- 卡片：网络延迟 -->
        <t-card title="当前延迟">
          <div class="text-3xl font-bold text-orange-500">230ms</div>
          <div class="mt-2 text-sm text-gray-500">网络状况良好</div>
        </t-card>
      </div>

      <!-- 底部表格：最新商品动态 -->
      <t-card :bordered="false" class="embedded-card">
        <template #title>
          <div class="flex items-center">
            <t-icon name="chart-bubble" class="mr-2 text-blue-600" />
            <span class="text-lg font-bold text-gray-800">最新商品动态</span>
          </div>
        </template>
        <t-table
          :data="data"
          :columns="columns"
          row-key="id"
          :pagination="{ total: 100, current: 1, pageSize: 10, size: 'small' }"
          hover
          :header-affixed-top="true"
          class="niro-unified-table w-full bg-white"
        >
          <template #empty>
            <t-empty icon="info-circle" description="暂无动态数据" />
          </template>

          <template #name="{ row }">
            <t-tooltip :content="row.name" placement="top-left">
              <div class="truncate font-medium text-gray-800">
                {{ row.name }}
              </div>
            </t-tooltip>
          </template>
          <!-- 自定义价格列 -->
          <template #price="{ row }">
            <span class="font-numeric text-transaction-green font-bold">¥{{ row.price }}</span>
          </template>
          <!-- 自定义磨损列 -->
          <template #float="{ row }">
            <span class="font-numeric text-gray-600">{{ row.float }}</span>
          </template>
          <!-- 自定义时间列 -->
          <template #time="{ row }">
            <span class="font-numeric text-xs text-gray-500">{{ row.time }}</span>
          </template>
          <!-- 自定义状态列渲染 -->
          <template #status="{ row }">
            <t-tag
              v-if="row.status === 'success'"
              theme="success"
              variant="light"
              class="compact-tag"
            >
              成功
            </t-tag>
            <t-tag v-else theme="warning" variant="light" class="compact-tag">处理中</t-tag>
          </template>
        </t-table>
      </t-card>
    </div>
  </PageFrame>
</template>

<script setup lang="ts">
import PageFrame from "@/components/PageFrame.vue";
import PageHeader from "@/components/PageHeader.vue";
import { computed, ref } from "vue";
import type { PrimaryTableCol } from "tdesign-vue-next";
import { PermissionConstant } from "@/constant/PermissionConstant";
import useNewPermission from "@/hooks/useNewPermission";

const { hasButtonPermission } = useNewPermission();
const isRunning = ref(false);
const totalDiscoveryCount = ref(0);
const canStartScan = computed(() => hasButtonPermission(PermissionConstant.TASK_SCAN_START));
const canStopScan = computed(() => hasButtonPermission(PermissionConstant.TASK_SCAN_STOP));

const handleGlobalStart = () => {
  isRunning.value = true;
};

const handleGlobalStop = () => {
  isRunning.value = false;
};

// 表格列配置
const columns: PrimaryTableCol[] = [
  { colKey: "name", title: "商品名称", ellipsis: true, align: "left" },
  { colKey: "price", title: "价格 (CNY)", width: 120, cell: "price", align: "right" },
  { colKey: "float", title: "磨损", width: 100, cell: "float", align: "left" },
  { colKey: "time", title: "捕获时间", width: 180, cell: "time", align: "left" },
  { colKey: "status", title: "状态", width: 100, cell: "status", align: "left" },
];

// 模拟表格数据
const data = ref([
  {
    id: 1,
    name: "AK-47 | Redline (Field-Tested)",
    price: "128.50",
    float: "0.1523",
    time: "2024-03-20 10:23:12",
    status: "success",
  },
  {
    id: 2,
    name: "AWP | Asiimov (Battle-Scarred)",
    price: "450.00",
    float: "0.4612",
    time: "2024-03-20 10:22:45",
    status: "pending",
  },
  {
    id: 3,
    name: "M4A4 | Asiimov (Field-Tested)",
    price: "680.00",
    float: "0.2845",
    time: "2024-03-20 10:21:30",
    status: "success",
  },
]);
</script>

<style scoped></style>
