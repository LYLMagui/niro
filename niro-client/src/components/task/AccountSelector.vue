<template>
  <t-select
    :model-value="modelValue"
    multiple
    filterable
    :placeholder="placeholder || '请选择执行账号'"
    :loading="loading"
    :disabled="disabled"
    style="width: 320px"
    :min-collapsed-num="2"
    :tips="tips"
    @change="(val: any) => emit('update:modelValue', val)"
    @focus="emit('focus')"
  >
    <t-option
      v-for="item in accounts"
      :key="item.id"
      :value="item.id"
      :label="item.accountName"
      :disabled="!!item.boundTaskId && item.boundTaskId !== currentTaskId"
    >
      <div class="flex w-full items-center justify-between overflow-hidden">
        <div class="mr-2 flex flex-1 items-center gap-1.5 overflow-hidden">
          <span class="shrink-0 font-medium">{{ item.accountName }}</span>
          <t-tooltip
            v-if="item.boundTaskId && item.boundTaskId !== currentTaskId"
            :content="'已绑定任务: ' + item.boundTaskName"
            placement="top"
          >
            <span class="truncate text-xs text-gray-400">(已绑定: {{ item.boundTaskName }})</span>
          </t-tooltip>
        </div>
        <t-tag
          v-if="item.status === BuffAccountStatusEnum.NORMAL"
          :theme="BuffAccountStatusMap[BuffAccountStatusEnum.NORMAL].theme as any"
          variant="light"
          size="small"
          class="shrink-0"
        >
          {{ BuffAccountStatusMap[BuffAccountStatusEnum.NORMAL].label }}
        </t-tag>
        <t-tag
          v-else
          :theme="
            (BuffAccountStatusMap[item.status as BuffAccountStatusEnum]?.theme as any) || 'danger'
          "
          variant="light"
          size="small"
          class="shrink-0"
        >
          {{ BuffAccountStatusMap[item.status as BuffAccountStatusEnum]?.label || "异常" }}
        </t-tag>
      </div>
    </t-option>
  </t-select>
</template>

<script setup lang="ts">
import type { BuffAccount } from "@/api/settings";
import { BuffAccountStatusEnum, BuffAccountStatusMap } from "@/enums/BuffAccountStatusEnum";

defineProps<{
  modelValue: number[];
  accounts: BuffAccount[];
  loading: boolean;
  currentTaskId?: number;
  tips?: string;
  placeholder?: string;
  disabled?: boolean;
}>();

const emit = defineEmits<{
  "update:modelValue": [ids: number[]];
  focus: [];
}>();
</script>
