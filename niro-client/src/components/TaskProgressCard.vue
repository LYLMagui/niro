<template>
  <t-card :bordered="false" class="task-progress-card shadow-sm hover:shadow-md transition-shadow duration-300">
    <div class="flex items-center space-x-6">
      <!-- 左侧：状态环 -->
      <div class="flex-shrink-0 relative">
        <t-progress
          theme="circle"
          :percentage="isSystemTask ? (task.stats?.percentage || 0) : progressPercentage"
          :status="progressStatus"
          :size="80"
          :stroke-width="6"
        >
          <template #label>
            <div class="flex flex-col items-center">
              <span class="text-xs text-gray-400">{{ statusText }}</span>
              <span v-if="isSystemTask" class="text-lg font-bold" :class="statusColorClass">
                {{ task.stats?.finished || 0 }}/{{ task.stats?.total || 0 }}
              </span>
              <span v-else class="text-lg font-bold" :class="statusColorClass">
                {{ task.successCount }}/{{ task.buyCount }}
              </span>
            </div>
          </template>
        </t-progress>
        <!-- 呼吸灯效果，仅在运行中显示 -->
        <div v-if="task.status === 1" class="absolute -top-1 -right-1">
          <span class="relative flex h-3 w-3">
            <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
            <span class="relative inline-flex rounded-full h-3 w-3 bg-green-500"></span>
          </span>
        </div>
      </div>

      <!-- 右侧：任务详情与步骤 -->
      <div class="flex-grow space-y-3">
        <div class="flex items-center justify-between">
          <div class="flex items-center space-x-2">
            <t-avatar :image="task.goodsIconUrl" size="small" shape="round" />
            <span class="text-base font-bold text-gray-800 truncate max-w-[200px]">{{ task.name }}</span>
            <t-tag size="small" variant="light-outline" theme="primary">{{ taskTypeName }}</t-tag>
          </div>
          <div class="flex items-center space-x-2">
            <t-button v-if="task.status === 0" theme="primary" variant="text" size="small" @click="$emit('start', task.id)">
              <template #icon><t-icon name="play-circle" /></template>
              启动
            </t-button>
            <t-button v-if="task.status === 1" theme="danger" variant="text" size="small" @click="$emit('stop', task.id)">
              <template #icon><t-icon name="stop-circle" /></template>
              停止
            </t-button>
            <t-dropdown :options="moreOptions" @click="handleMoreClick">
              <t-button variant="text" shape="square" size="small">
                <t-icon name="more" />
              </t-button>
            </t-dropdown>
          </div>
        </div>

        <!-- 步骤条：智行风格，紧凑横向 -->
        <t-steps v-if="!isSystemTask" :current="currentStep" theme="dot" size="small" class="custom-steps">
          <t-step-item title="排队" />
          <t-step-item title="扫描" />
          <t-step-item title="匹配" />
          <t-step-item title="下单" />
          <t-step-item title="成功" />
        </t-steps>

        <!-- 系统任务进度条 -->
        <div v-else class="space-y-2 py-1">
          <div class="flex items-center justify-between text-xs text-gray-500">
            <span>分片处理进度</span>
            <span class="text-blue-600 font-bold">TPS: {{ task.stats?.tps || 0 }}条/秒</span>
          </div>
          <div class="flex flex-wrap gap-2">
            <div v-for="(name, index) in task.accountNames" :key="index" class="flex-1 min-w-[100px]">
              <div class="flex items-center justify-between text-[10px] mb-0.5">
                <span class="truncate max-w-[60px]">{{ name }}</span>
                <span>{{ getAccountProgress(name) }}%</span>
              </div>
              <t-progress 
                :percentage="getAccountProgress(name)" 
                size="small" 
                :show-info="false" 
                :stroke-width="4"
                theme="line"
                :color="getAvatarColor(index)"
              />
            </div>
          </div>
        </div>

        <!-- 底部：账号状态与统计 -->
        <div class="flex items-center justify-between pt-2 border-t border-gray-50">
          <div class="flex -space-x-2 overflow-hidden">
            <t-tooltip v-for="(name, index) in task.accountNames" :key="index" :content="name">
              <t-avatar
                size="20px"
                :style="{ backgroundColor: getAvatarColor(index), border: '2px solid #fff' }"
                class="cursor-pointer hover:z-10 transition-transform hover:scale-110"
              >
                {{ name.charAt(0) }}
              </t-avatar>
            </t-tooltip>
            <span v-if="(task.accountNames?.length || 0) > 3" class="pl-3 text-xs text-gray-400">
              等 {{ task.accountNames?.length || 0 }} 个账号
            </span>
          </div>
          <div class="flex items-center space-x-4 text-xs text-gray-500">
            <span class="flex items-center"><t-icon name="time" class="mr-1" />{{ scanIntervalText }}</span>
            <span class="flex items-center text-blue-600 font-medium">
              <t-icon name="money" class="mr-1" />
              <template v-if="task.taskType === 2">利: {{ task.minProfit }}%</template>
              <template v-else>≤ ¥{{ task.maxPrice }}</template>
            </span>
          </div>
        </div>
      </div>
    </div>
  </t-card>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { BuffScanTask } from '@/types/task';
import type { DropdownOption } from 'tdesign-vue-next';

const props = defineProps<{
  task: BuffScanTask & { accountNames?: string[] };
}>();

const isSystemTask = computed(() => props.task.taskType >= 2);

const getAccountProgress = (accountName: string) => {
  if (!props.task.stats?.account_stats) return 0;
  return props.task.stats.account_stats[accountName]?.percentage || 0;
};

const emit = defineEmits(['start', 'stop', 'edit', 'delete']);

// 任务类型名称
const taskTypeName = computed(() => {
  const types: Record<number, string> = {
    1: '炼金扫货',
    2: '站内倒卖',
    10: '系统任务',
    11: '同步任务'
  };
  return types[props.task.taskType] || '普通任务';
});

// 进度百分比
const progressPercentage = computed(() => {
  if (!props.task.buyCount) return 0;
  return Math.min(Math.round((props.task.successCount / props.task.buyCount) * 100), 100);
});

// 状态文字
const statusText = computed(() => {
  switch (props.task.status) {
    case 0: return '已停止';
    case 1: return '运行中';
    case 2: return '已完成';
    case 3: return '异常';
    case 4: return '正在处理';
    default: return '未知';
  }
});

// 状态颜色
const statusColorClass = computed(() => {
  switch (props.task.status) {
    case 0: return 'text-gray-400';
    case 1: return 'text-green-500';
    case 2: return 'text-blue-500';
    case 3: return 'text-red-500';
    default: return 'text-gray-400';
  }
});

// TDesign Progress 状态
const progressStatus = computed(() => {
  switch (props.task.status) {
    case 1: return 'active';
    case 2: return 'success';
    case 3: return 'error';
    default: return undefined;
  }
});

// 模拟当前步骤逻辑 (实际应由后端推送实时状态)
const currentStep = computed(() => {
  if (props.task.status === 0) return -1;
  if (props.task.status === 2) return 4;
  if (props.task.status === 4) return 3;
  // 运行中状态下，根据一些逻辑模拟步骤
  return 1; // 默认在扫描中
});

// 扫描间隔描述
const scanIntervalText = computed(() => {
  if (props.task.scanIntervalMin && props.task.scanIntervalMax) {
    return `${props.task.scanIntervalMin}-${props.task.scanIntervalMax}s`;
  }
  return `${props.task.scanInterval || 15}s`;
});

// 更多操作
const moreOptions = computed(() => [
  { 
    content: '编辑任务', 
    value: 'edit',
    disabled: [1, 4].includes(props.task.status)
  },
  { 
    content: '删除任务', 
    value: 'delete', 
    theme: 'danger' as any,
    disabled: [1, 4].includes(props.task.status)
  },
]);

const handleMoreClick = (data: any) => {
  if (data.value === 'edit') emit('edit', props.task.id);
  if (data.value === 'delete') emit('delete', props.task.id);
};

// 生成头像颜色
const colors = ['#0052D9', '#00A870', '#ED7B2F', '#E34D59', '#662D91'];
const getAvatarColor = (index: number) => colors[index % colors.length];

</script>

<style scoped>
.task-progress-card {
  border-radius: 12px;
  background: #ffffff;
}

:deep(.custom-steps .t-steps-item__title) {
  font-size: 12px;
  color: #8c8c8c;
}

:deep(.custom-steps .t-steps-item--process .t-steps-item__title) {
  color: #0052d9;
  font-weight: bold;
}

:deep(.t-steps-item__dot) {
  width: 6px;
  height: 6px;
}
</style>
