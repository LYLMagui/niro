<template>
  <div class="schedule-group mt-1 w-full rounded-md border border-blue-100 bg-blue-50/50 p-1.5">
    <!-- 顶部标题 + 模式切换 -->
    <div class="mb-1 flex items-center justify-between">
      <div class="flex items-center gap-2 text-sm font-medium text-blue-700">
        <t-icon name="time-filled" />
        <span>{{ mode === "system" ? "运行计划" : "计划配置" }}</span>
      </div>
      <t-radio-group
        v-if="mode === 'scan'"
        v-model="uiState.isCycleMode"
        variant="default-filled"
        size="small"
      >
        <t-radio-button :value="false">持续执行</t-radio-button>
        <t-radio-button :value="true">周期循环</t-radio-button>
      </t-radio-group>
    </div>

    <!-- Cron 配置 -->
    <t-form-item
      :label="mode === 'system' ? '执行计划' : '启动时间'"
      name="cronExpression"
      label-width="90px"
      label-align="right"
    >
      <div class="flex items-center gap-4">
        <t-input
          :model-value="cronExpression"
          :disabled="uiState.isCronImmediate"
          placeholder="未配置"
          clearable
          :style="{ width: mode === 'system' ? '200px' : '180px' }"
          @update:model-value="emit('update:cronExpression', $event)"
          @blur="(v: any) => handleInputTrim(v)"
        >
          <template #suffix>
            <t-popup
              v-model:visible="cronVisible"
              placement="bottom-right"
              trigger="click"
              :overlay-inner-style="{ padding: 0 }"
            >
              <t-link
                theme="primary"
                variant="underline"
                :disabled="uiState.isCronImmediate"
              >
                <t-icon name="calendar" :class="mode === 'system' ? 'mr-1' : ''" />
                <template v-if="mode === 'system'">可视化</template>
              </t-link>
              <template #content>
                <div class="cron-popup-container">
                  <cron-editor
                    :model-value="cronExpression || ''"
                    @update:model-value="emit('update:cronExpression', $event)"
                    @confirm="cronVisible = false"
                    @cancel="cronVisible = false"
                  />
                </div>
              </template>
            </t-popup>
          </template>
        </t-input>
        <t-checkbox v-model="uiState.isCronImmediate">
          {{ mode === "system" ? "立即执行" : "立即启动" }}
        </t-checkbox>
      </div>
    </t-form-item>

    <!-- 运行时长 / 循环配置（仅扫货模式） -->
    <t-form-item
      v-if="mode === 'scan'"
      :label="uiState.isCycleMode ? '运行时长' : '持续时间'"
      name="durationMinutes"
      label-width="90px"
      label-align="right"
      class="mt-1"
    >
      <div class="flex items-center gap-4">
        <t-input-number
          v-model="uiState.durationValue"
          :disabled="uiState.isDurationUnlimited && !uiState.isCycleMode"
          :min="0"
          :step="1"
          theme="column"
          style="width: 90px"
        />
        <t-select
          v-model="uiState.durationUnit"
          :disabled="uiState.isDurationUnlimited && !uiState.isCycleMode"
          style="width: 80px"
        >
          <t-option label="分钟" value="m" />
          <t-option label="小时" value="h" />
          <t-option label="天" value="d" />
        </t-select>
        <t-checkbox v-if="!uiState.isCycleMode" v-model="uiState.isDurationUnlimited">
          不限时间
        </t-checkbox>

        <template v-if="uiState.isCycleMode">
          <span class="mx-3 font-bold text-orange-500">/</span>
          <t-tag theme="warning" variant="light" size="small" class="mr-3">暂停</t-tag>
          <t-input-number
            v-model="uiState.restValue"
            :min="1"
            :step="1"
            theme="column"
            style="width: 90px"
          />
          <t-select v-model="uiState.restUnit" style="width: 80px" class="ml-3">
            <t-option label="分钟" value="m" />
            <t-option label="小时" value="h" />
            <t-option label="天" value="d" />
          </t-select>
        </template>
      </div>
    </t-form-item>

    <!-- 扫描间隔（仅扫货模式） -->
    <t-form-item
      v-if="mode === 'scan'"
      label="扫描间隔"
      name="scanInterval"
      label-width="90px"
      label-align="right"
      class="mt-1"
    >
      <div class="flex items-center gap-4">
        <!-- C5 平台: 固定间隔 -->
        <template v-if="platform === PlatformEnum.C5">
          <t-input-number
            v-model="uiState.intervalMinValue"
            :min="1"
            :step="1"
            theme="column"
            style="width: 120px"
            @blur="emit('intervalMinBlur')"
            @change="(v: any) => { uiState.intervalMaxValue = Number(v); }"
          />
        </template>

        <!-- BUFF 平台: 范围间隔 -->
        <template v-else>
          <t-input-number
            v-model="uiState.intervalMinValue"
            :min="uiState.intervalUnit === 's' ? 15 : 1"
            :step="1"
            theme="column"
            style="width: 90px"
            @blur="emit('intervalMinBlur')"
          />
          <span class="text-gray-400">至</span>
          <t-input-number
            v-model="uiState.intervalMaxValue"
            :min="uiState.intervalUnit === 's' ? 15 : 1"
            :step="1"
            theme="column"
            style="width: 90px"
            @blur="emit('intervalMaxBlur')"
          />
        </template>

        <t-select
          v-model="uiState.intervalUnit"
          style="width: 80px"
          @change="emit('intervalUnitChange')"
        >
          <t-option label="秒" value="s" />
          <t-option label="分钟" value="m" />
          <t-option label="小时" value="h" />
          <t-option label="天" value="d" />
        </t-select>
        <t-tooltip
          :content="
            platform === PlatformEnum.C5
              ? 'C5平台限制: 10秒30次 (建议间隔 >= 1秒)'
              : '扫描间隔建议在 15-30 秒之间，过短容易触发风控。'
          "
        >
          <t-icon name="help-circle" class="ml-1 cursor-help text-gray-400" />
        </t-tooltip>
      </div>
    </t-form-item>

    <!-- 动态逻辑预览 -->
    <div
      v-if="mode === 'scan' || (cronExpression && !uiState.isCronImmediate)"
      class="mt-1 rounded border border-blue-50 bg-white/80 p-2 text-[12px] leading-relaxed text-blue-600 shadow-sm"
      :class="mode === 'system' ? 'p-1.5 text-[11px]' : ''"
    >
      <div class="flex items-start gap-1.5" :class="mode === 'system' ? 'gap-1' : ''">
        <t-icon name="info-circle" class="mt-0.5 shrink-0" />
        <div :class="mode === 'scan' ? 'font-medium' : ''">
          <template v-if="mode === 'scan'">{{ executionSummary }}</template>
          <template v-else>任务将根据 Cron 表达式定时触发执行</template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import CronEditor from "@/components/CronEditor.vue";
import { PlatformEnum } from "@/enums/PlatformEnum";
import type { UiState } from "@/composables/useUiState";

defineProps<{
  mode: "scan" | "system";
  uiState: UiState;
  cronExpression: string;
  platform?: string;
  executionSummary?: string;
}>();

const emit = defineEmits<{
  "update:cronExpression": [value: string];
  intervalMinBlur: [];
  intervalMaxBlur: [];
  intervalUnitChange: [];
}>();

const cronVisible = ref(false);

const handleInputTrim = (val: any) => {
  if (typeof val === "string") {
    emit("update:cronExpression", val.replace(/[\r\n]/g, "").trim());
  }
};
</script>

<style scoped>
.schedule-group :deep(.t-form__item) {
  margin-bottom: 8px;
}

.schedule-group :deep(.t-form__item:last-child) {
  margin-bottom: 8px;
}
</style>
