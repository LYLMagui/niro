<template>
  <div class="space-y-6">
    <!-- 顶部数据卡片区域，使用 Grid 布局 -->
    <div class="grid grid-cols-1 gap-6 md:grid-cols-4">
      <!-- 卡片：今日爬取数量 -->
      <t-card title="今日爬取" hover-shadow>
        <div class="text-3xl font-bold text-blue-600">1,234</div>
        <div class="mt-2 text-sm text-gray-500">较昨日 +12%</div>
      </t-card>

      <!-- 卡片：下单成功数量 -->
      <t-card title="下单成功" hover-shadow>
        <div class="text-3xl font-bold text-green-600">56</div>
        <div class="mt-2 text-sm text-gray-500">成功率 98%</div>
      </t-card>

      <!-- 卡片：运行状态控制 -->
      <t-card title="运行状态" hover-shadow>
        <div class="flex items-center gap-2">
          <!-- 状态指示灯 -->
          <div :class="['h-3 w-3 rounded-full', isRunning ? 'bg-green-500' : 'bg-red-500']"></div>
          <span class="text-xl font-bold">{{ isRunning ? "运行中" : "已停止" }}</span>
        </div>
        <div class="mt-4">
          <!-- 控制按钮 -->
          <t-button
            v-if="!isRunning"
            theme="primary"
            size="medium"
            @click="startTask"
            class="rounded-lg transition-all duration-300 hover:shadow active:shadow-none"
          >
            启动任务
          </t-button>
          <t-button
            v-else
            theme="danger"
            size="medium"
            @click="stopTask"
            class="rounded-lg transition-all duration-300 hover:shadow active:shadow-none"
          >
            停止任务
          </t-button>
        </div>
      </t-card>

      <!-- 卡片：网络延迟 -->
      <t-card title="当前延迟" hover-shadow>
        <div class="text-3xl font-bold text-orange-500">230ms</div>
        <div class="mt-2 text-sm text-gray-500">网络状况良好</div>
      </t-card>
    </div>

    <!-- 底部表格：最新商品动态 -->
    <t-card :bordered="false" class="shadow-sm embedded-card">
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
        :pagination="{ total: 100, current: 1, pageSize: 10 }"
        class="embedded-table w-full"
      >
        <template #empty>
          <t-empty icon="info-circle" description="暂无动态数据" />
        </template>
        <!-- 自定义状态列渲染 -->
        <template #status="{ row }">
          <t-tag v-if="row.status === 'success'" theme="success" variant="light">成功</t-tag>
          <t-tag v-else theme="warning" variant="light">处理中</t-tag>
        </template>
      </t-table>
    </t-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useTaskStore } from "@/store/task";
import { storeToRefs } from "pinia";

// 使用 Pinia Store 管理任务状态
const taskStore = useTaskStore();
const { isRunning } = storeToRefs(taskStore); // 保持响应性
const { startTask, stopTask } = taskStore;

// 表格列配置
const columns = [
  { colKey: "id", title: "ID", width: 100 },
  { colKey: "name", title: "商品名称", ellipsis: true },
  { colKey: "price", title: "价格 (CNY)", width: 120 },
  { colKey: "float", title: "磨损", width: 100 },
  { colKey: "time", title: "捕获时间", width: 180 },
  { colKey: "status", title: "状态", width: 100 },
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

<style scoped>
/* 嵌入式卡片布局优化 */
.embedded-card :deep(.t-card__body) {
  padding: 0 !important;
}

.embedded-card :deep(.t-card__header) {
  padding: 16px 24px !important;
}

/* 嵌入式表格深度定制 */
:deep(.embedded-table) {
  border: none !important;
}

/* 表头背景色与标题栏衔接 */
:deep(.embedded-table .t-table__header tr) {
  background-color: #f8fafc !important;
}

:deep(.embedded-table .t-table__header th) {
  font-weight: 700 !important;
  color: #334155 !important;
  background-color: transparent !important;
  border-bottom: 1px solid #f1f5f9 !important;
  padding: 12px 16px !important;
  height: 48px;
}

:deep(.embedded-table .t-table__body td) {
  padding: 16px 16px !important;
  border-bottom: 1px solid #f1f5f9 !important;
}

/* 第一列和最后一列的 24px 边距对齐 */
:deep(.embedded-table th:first-child),
:deep(.embedded-table td:first-child) {
  padding-left: 24px !important;
}

:deep(.embedded-table th:last-child),
:deep(.embedded-table td:last-child) {
  padding-right: 24px !important;
}

:deep(.embedded-table .t-table__row--hover) {
  background-color: #f8fafc !important;
}
</style>
