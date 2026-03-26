<template>
  <t-card
    :bordered="false"
    class="task-progress-card group relative flex flex-col overflow-visible rounded-2xl border border-gray-100 bg-white p-6 transition-all duration-300 hover:-translate-y-1 hover:shadow-lg"
  >
    <!-- Header: 商品/任务信息 + 操作区 -->
    <div class="flex items-start justify-between p-3">
      <div class="flex items-center gap-3">
        <!-- 商品图片 -->
        <div
          class="relative h-12 w-12 flex-shrink-0 overflow-hidden rounded-lg border border-gray-100 p-1 shadow-sm"
        >
          <t-image
            :src="task.goodsIconUrl"
            class="h-full w-full object-cover"
            :lazy="true"
            fit="contain"
          />
        </div>
        <!-- 标题与类型 -->
        <div class="flex flex-col">
          <div class="flex items-center gap-2">
            <h3 class="max-w-[180px] truncate text-lg font-bold text-gray-900" :title="task.name">
              {{ task.name }}
            </h3>
          </div>
          <div class="flex items-center gap-2 text-xs">
            <t-tag size="small" variant="light" theme="primary" class="px-1.5 py-0.5">
              {{ taskTypeName }}
            </t-tag>
            <span class="text-gray-400">ID: {{ task.id }}</span>
          </div>
        </div>
      </div>

      <!-- 右侧操作区 -->
      <div class="flex items-center gap-2">
        <!-- 状态标签 -->
        <t-tag :theme="statusTheme" variant="light-outline" class="font-medium">
          <template #icon>
            <span :class="['mr-1 inline-block h-1.5 w-1.5 rounded-full', statusDotClass]"></span>
          </template>
          {{ statusText }}
        </t-tag>

        <!-- 操作按钮 -->
        <div v-permission="PermissionConstant.TASK_BUFF_LIST" class="flex items-center gap-1 pl-2">
          <t-button
            v-if="task.status === 0 || task.status === 3"
            theme="primary"
            shape="circle"
            variant="text"
            @click.stop="$emit('start', task.id)"
          >
            <template #icon><t-icon name="play-circle" size="large" /></template>
          </t-button>

          <t-button
            v-if="task.status === 1 || task.status === 4"
            theme="danger"
            size="small"
            variant="text"
            @click.stop="$emit('stop', task.id)"
          >
            <template #icon><t-icon name="stop-circle" size="large" /></template>
            停止
          </t-button>

          <t-dropdown :options="moreOptions" @click="handleMoreClick">
            <t-button variant="text" shape="circle">
              <template #icon><t-icon name="more" /></template>
            </t-button>
          </t-dropdown>
        </div>
      </div>
    </div>

    <!-- Body: 关键指标卡片 -->
    <div class="mt-4 grid grid-cols-3 divide-x divide-gray-200 rounded-xl bg-gray-50/80 p-3">
      <!-- 指标1: 进度 -->
      <div class="flex flex-col items-center justify-center px-2">
        <div class="text-lg font-bold text-gray-900">
          <span>{{ task.successCount }}/{{ task.buyCount }}</span>
        </div>
        <!-- 进度条可视化 -->
        <div class="mt-1 w-full max-w-[80px]">
          <t-progress
            theme="line"
            stroke-width="2px"
            :percentage="progressPercentage"
            :show-label="false"
            :status="progressStatus"
          />
        </div>
        <div class="mt-0.5 text-[10px] text-gray-500">完成进度</div>
      </div>

      <!-- 指标2: 价格/利润 -->
      <div class="flex flex-col items-center justify-center overflow-hidden px-2">
        <div class="w-full truncate text-center text-lg font-bold text-blue-600">
          <template v-if="task.taskType === 2">{{ task.minProfit }}%</template>
          <template v-else>¥{{ task.maxPrice }}</template>
        </div>
        <div class="text-[10px] text-gray-500">
          {{ task.taskType === 2 ? "预期利润" : "限制价格" }}
        </div>
      </div>

      <!-- 指标3: 延迟/间隔 -->
      <div class="flex flex-col items-center justify-center overflow-hidden px-2">
        <div class="flex w-full items-center justify-center gap-1 text-lg font-bold text-gray-900">
          <t-icon name="time" size="small" class="flex-shrink-0 text-gray-400" />
          <span class="truncate">{{ scanIntervalShortText }}</span>
        </div>
        <div class="text-[10px] text-gray-500">扫描间隔</div>
      </div>
    </div>

    <!-- Footer: 账号与步骤 -->
    <div class="mt-4 flex items-center justify-between p-1">
      <!-- 左侧：账号头像组 -->
      <div class="flex -space-x-2 pl-1">
        <t-tooltip v-for="(name, index) in displayAccountNames" :key="index" :content="name">
          <t-avatar
            size="24px"
            :style="{ border: '2px solid #fff', backgroundColor: '#f3f4f6', color: '#374151' }"
            class="cursor-pointer text-[10px] font-bold ring-2 ring-white transition-transform hover:z-10 hover:scale-110"
          >
            {{ name.charAt(0).toUpperCase() }}
          </t-avatar>
        </t-tooltip>
        <div
          v-if="hiddenAccountCount > 0"
          class="flex h-6 w-6 items-center justify-center rounded-full border-2 border-white bg-gray-100 text-[10px] text-gray-500 ring-2 ring-white"
        >
          +{{ hiddenAccountCount }}
        </div>
      </div>

      <!-- 右侧：步骤条或状态信息 -->
      <div class="flex flex-1 justify-center pl-4">
        <t-steps
          :current="currentStep"
          theme="dot"
          size="small"
          class="compact-steps max-w-[400px]"
        >
          <t-step-item title="排队" />
          <t-step-item title="扫描" />
          <t-step-item title="下单" />
        </t-steps>
      </div>
    </div>
  </t-card>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { BuffScanTask } from "@/types/task";
import { TaskStatusEnum, TaskStatusMap } from "@/enums/TaskStatusEnum";
import { PermissionConstant } from "@/constant/PermissionConstant";

import { TaskTypeEnum, TaskTypeMap } from "@/enums/TaskTypeEnum";

const props = defineProps<{
  task: BuffScanTask & { accountNames?: string[] };
}>();

const emit = defineEmits(["start", "stop", "edit", "delete"]);

// 任务类型名称
const taskTypeName = computed(() => {
  return TaskTypeMap[props.task.taskType as TaskTypeEnum] || "普通任务";
});

// 进度百分比
const progressPercentage = computed(() => {
  if (!props.task.buyCount) return 0;
  return Math.min(Math.round((props.task.successCount / props.task.buyCount) * 100), 100);
});

// 进度状态 (TDesign Progress Status)
const progressStatus = computed(() => {
  switch (props.task.status) {
    case TaskStatusEnum.RUNNING:
    case TaskStatusEnum.SYSTEM_RUNNING:
      return "active";
    case TaskStatusEnum.COMPLETED:
      return "success";
    case TaskStatusEnum.ERROR:
      return "error";
    default:
      return undefined;
  }
});

// 状态文字
const statusText = computed(() => {
  const config = TaskStatusMap[props.task.status as TaskStatusEnum];
  return config ? config.label : "未知";
});

// 状态主题色 (Tag Theme)
const statusTheme = computed<any>(() => {
  const config = TaskStatusMap[props.task.status as TaskStatusEnum];
  return config ? config.color : "default";
});

// 状态圆点颜色 (Dot Class)
const statusDotClass = computed(() => {
  switch (props.task.status) {
    case TaskStatusEnum.RUNNING:
      return "bg-green-500 animate-pulse";
    case TaskStatusEnum.SYSTEM_RUNNING:
      return "bg-orange-500 animate-pulse";
    case TaskStatusEnum.COMPLETED:
      return "bg-blue-500";
    case TaskStatusEnum.ERROR:
      return "bg-red-500";
    default:
      return "bg-gray-400";
  }
});

// 模拟当前步骤逻辑 (实际应由后端推送实时状态)
const currentStep = computed(() => {
  if (props.task.status === TaskStatusEnum.STOPPED) return -1;
  if (props.task.status === TaskStatusEnum.COMPLETED) return 4;
  if (props.task.status === TaskStatusEnum.SYSTEM_RUNNING) return 3;
  // 运行中状态下，根据一些逻辑模拟步骤
  return 1; // 默认在扫描中
});

// 扫描间隔描述
const scanIntervalShortText = computed(() => {
  if (props.task.scanIntervalMin && props.task.scanIntervalMax) {
    return `${props.task.scanIntervalMin}-${props.task.scanIntervalMax}s`;
  }
  return `${props.task.scanInterval || 15}s`;
});

// 账号显示逻辑 (最多显示 5 个)
const displayAccountNames = computed(() => {
  return props.task.accountNames?.slice(0, 5) || [];
});

const hiddenAccountCount = computed(() => {
  return (props.task.accountNames?.length || 0) - 5;
});

// 更多操作
const moreOptions = computed(() => [
  {
    content: "编辑任务",
    value: "edit",
    disabled: [TaskStatusEnum.RUNNING, TaskStatusEnum.SYSTEM_RUNNING].includes(props.task.status),
  },
  {
    content: "删除任务",
    value: "delete",
    theme: "danger" as any,
    disabled: [TaskStatusEnum.RUNNING, TaskStatusEnum.SYSTEM_RUNNING].includes(props.task.status),
  },
]);

const handleMoreClick = (data: any) => {
  if (data.value === "edit") emit("edit", props.task.id);
  if (data.value === "delete") emit("delete", props.task.id);
};
</script>

<style scoped>
/* 覆盖 TDesign Steps 样式，使其更紧凑 */
:deep(.compact-steps .t-steps-item__title) {
  margin-bottom: 0 !important;
  font-size: 12px;
  line-height: 1.2;
  color: #8c8c8c;
}

:deep(.compact-steps .t-steps-item--process .t-steps-item__title) {
  font-weight: bold;
  color: #0052d9;
}

:deep(.compact-steps .t-steps-item__icon) {
  width: 16px;
  min-width: 16px;
  height: 16px;
  font-size: 10px;
}

:deep(.compact-steps .t-steps-item__inner) {
  padding-bottom: 0 !important;
}

/* 移除多余的边距 */
:deep(.t-card__body) {
  padding: 0;
}
</style>
