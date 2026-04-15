<template>
  <div class="space-y-6 p-6">
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

      <!-- 卡片：今日发现 (监控模式命中) -->
      <t-card title="今日发现" hover-shadow>
        <div class="text-3xl font-bold text-amber-500">{{ totalDiscoveryCount }}</div>
        <div class="mt-2 text-sm text-gray-500">符合条件但未下单</div>
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
            v-permission="PermissionConstant.TASK_BUFF_LIST"
            theme="primary"
            class="rounded-lg transition-all duration-300 hover:shadow active:shadow-none"
            @click="handleGlobalStart"
          >
            启动任务
          </t-button>
          <t-button
            v-else
            v-permission="PermissionConstant.TASK_BUFF_LIST"
            theme="danger"
            class="rounded-lg transition-all duration-300 hover:shadow active:shadow-none"
            @click="handleGlobalStop"
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

    <!-- 正在运行的任务 (智行风格进度条) -->
    <div v-if="canViewTaskDashboard && runningTasks.length > 0" class="space-y-4">
      <div class="flex items-center justify-between">
        <h3 class="flex items-center text-lg font-bold text-gray-800">
          <t-icon name="control-platform" class="mr-2 text-blue-600" />
          运行中的任务
        </h3>
        <t-link
          v-permission="PermissionConstant.TASK_BUFF_LIST"
          theme="primary"
          @click="$router.push('/task/manager/buff')"
        >
          查看全部
        </t-link>
      </div>
      <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
        <TaskProgressCard
          v-for="task in runningTasks"
          :key="task.id"
          :task="task"
          @stop="stopTask"
        />
      </div>
    </div>

    <!-- 底部表格：最新商品动态 -->
    <t-card :bordered="false" class="embedded-card shadow-sm">
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
</template>

<script setup lang="ts">
import { ref, computed, watch } from "vue";
import { useTaskStore } from "@/store/task";
import { storeToRefs } from "pinia";
import type { PrimaryTableCol } from "tdesign-vue-next";
import TaskProgressCard from "@/components/TaskProgressCard.vue";
import { PermissionConstant } from "@/constant/PermissionConstant";
import { usePermission } from "@/hooks/usePermission";

// 使用 Pinia Store 管理任务状态
const taskStore = useTaskStore();
const { isRunning, runningTasks } = storeToRefs(taskStore); // 保持响应性
const { fetchRunningTasks, startTask, stopTask } = taskStore;
const { hasPermission } = usePermission();
const canViewTaskDashboard = computed(() => hasPermission(PermissionConstant.TASK_BUFF_LIST));

// 计算总发现次数 (仅监控模式命中)
const totalDiscoveryCount = computed(() => {
  if (!canViewTaskDashboard.value) {
    return 0;
  }
  return runningTasks.value.reduce((total, task) => {
    return total + (task.stats?.discovery_count || 0);
  }, 0);
});

// 临时处理全局启动/停止 (目前后端需要 ID，这里先留空或处理首个任务)
const handleGlobalStart = () => {
  if (!canViewTaskDashboard.value) {
    return;
  }
  if (runningTasks.value.length > 0) {
    startTask(runningTasks.value[0].id);
  }
};

const handleGlobalStop = () => {
  if (!canViewTaskDashboard.value) {
    return;
  }
  if (runningTasks.value.length > 0) {
    stopTask(runningTasks.value[0].id);
  }
};

watch(
  canViewTaskDashboard,
  (allowed) => {
    if (allowed) {
      fetchRunningTasks();
    }
  },
  { immediate: true }
);

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
