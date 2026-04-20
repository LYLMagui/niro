<template>
  <t-dialog
    :visible.sync="visible"
    :header="dialogTitle"
    :width="dialogWidth"
    :attach="dialogAttach"
    :dialogStyle="{ padding: 0, boxShadow: '0 10px 30px rgba(15, 23, 42, 0.12)' }"
    :showInAttachedElement="showInAttachedElement"
    :close-on-overlay-click="true"
    :placement="'center'"
    @close="closeDialog"
  >
    <div class="dialog-shell">
      <t-form
        ref="formRef"
        :data="formData"
        :rules="rules"
        :label-width="110"
        class="compact-form"
        label-align="right"
        scroll-to-first-error="smooth"
        validation-trigger="submit"
        prevent-submit-default
        @submit="onFormSubmit"
      >
        <div class="form-container">
          <t-form-item label="选择商品：" name="cs2GoodsId" requiredMark>
            <t-select
              v-model="formData.cs2GoodsId"
              filterable
              :placeholder="goodsSelectPlaceholder"
              :loading="goodsLoading"
              :on-search="handleGoodsSearch"
              :disabled="goodsSelectionLocked || !!formData.id || !canViewGoods"
              class="task-config-select"
            >
              <t-option
                v-for="item in goodsOptions"
                :key="item.id"
                :value="item.id"
                :label="item.displayName"
              >
                {{ item.displayName }}
              </t-option>
            </t-select>
          </t-form-item>

          <t-form-item requiredMark label="最高价格：" name="maxPrice">
            <t-input-number
              v-model="formData.maxPrice"
              :min="0.01"
              :step="0.1"
              :decimal-places="2"
              suffix="元"
              theme="column"
              class="task-config-input task-config-input--price"
            />
          </t-form-item>

          <t-form-item v-if="isWearable" label="磨损范围：" name="wear">
            <div
              class="task-config-range flex items-center gap-3"
              :class="{ 'task-config-range--disabled': !isWearable }"
            >
              <t-input-number
                v-model="formData.minPaintwear"
                :min="0"
                :max="1"
                :step="0.001"
                :decimal-places="3"
                :disabled="!isWearable"
                placeholder="最小"
                theme="column"
                class="task-config-range__input"
              />
              <span class="task-config-range__separator text-gray-400">至</span>
              <t-input-number
                v-model="formData.maxPaintwear"
                :min="0"
                :max="1"
                :step="0.001"
                :decimal-places="3"
                :disabled="!isWearable"
                placeholder="最大"
                theme="column"
                class="task-config-range__input"
              />
            </div>
          </t-form-item>

          <t-form-item requiredMark label="购买数量：" name="buyCount">
            <t-input-number
              v-model="formData.buyCount"
              :min="1"
              :step="1"
              theme="column"
              class="task-config-input"
            />
          </t-form-item>

          <t-form-item requiredMark label="扫描频率：" name="scanInterval">
            <div class="task-config-interval flex items-center gap-4">
              <t-input-number
                v-model="uiState.intervalMinValue"
                :min="1"
                :step="1"
                theme="column"
                class="task-config-input"
                @blur="handleIntervalMinBlur"
                @change="handleScanIntervalChange"
              />
              <t-select
                v-model="uiState.intervalUnit"
                class="task-config-unit-select"
                @change="handleIntervalUnitChange"
              >
                <t-option label="秒" value="s" />
                <t-option label="分钟" value="m" />
                <t-option label="小时" value="h" />
                <t-option label="天" value="d" />
              </t-select>
              <t-tooltip content="C5 平台限制：建议扫描间隔不低于 1 秒。">
                <t-icon name="help-circle" class="task-config-help cursor-help text-gray-400" />
              </t-tooltip>
            </div>
          </t-form-item>
        </div>
      </t-form>
    </div>

    <template #footer>
      <t-button variant="outline" theme="default" @click="closeDialog">取消</t-button>
      <t-button
        v-if="canManageTask"
        theme="primary"
        :loading="submitLoading"
        @click="submitTaskForm()"
      >
        提交
      </t-button>
    </template>
  </t-dialog>
</template>

<script setup lang="ts">
import type { Cs2GoodsOption } from "@/types/cs2-goods";
import type { TaskItem } from "@/types/task";
import { useWindowSize } from "@vueuse/core";
import { computed, nextTick, ref, toRef, watch } from "vue";
import { PlatformEnum } from "@/enums/PlatformEnum";
import { TaskTypeEnum } from "@/enums/TaskTypeEnum";
import {
  useUiState,
  convertToUi,
  DURATION_FACTORS,
  INTERVAL_FACTORS,
} from "@/composables/useUiState";
import { useCs2GoodsSearch } from "@/composables/useCs2GoodsSearch";
import { useTaskForm } from "@/composables/useTaskForm";
import { PermissionConstant } from "@/constant/PermissionConstant";
import { usePermission } from "@/hooks/usePermission";

const emit = defineEmits<{
  (event: "success"): void;
  (event: "open-change"): void;
}>();

const visible = ref(false);
const dialogTitle = ref("新增任务");
const formRef = ref<{
  clearValidate: (fields?: string[]) => void;
  submit: (options?: { showErrorMessage?: boolean }) => void;
} | null>(null);
const goodsSelectionLocked = ref(false);
const { width } = useWindowSize();
const isMobile = computed(() => width.value <= 768);
const dialogAttach = computed(() => (isMobile.value ? "body" : undefined));
const showInAttachedElement = computed(() => !isMobile.value);
const { hasPermission } = usePermission();
const canViewGoods = computed(() => hasPermission(PermissionConstant.GOODS_LIST));
const canManageTask = computed(() => hasPermission(PermissionConstant.TASK_C5_LIST));

const goodsSelectPlaceholder = computed(() =>
  canViewGoods.value ? "输入商品名称搜索" : "当前账号无商品管理权限"
);

const { formData, submitLoading, rules, resetForm, handleSubmit } = useTaskForm(emit);
const dialogWidth = computed(() => "min(620px, calc(100vw - 32px))");

const { uiState, c5Config, handleIntervalMinBlur, handleIntervalUnitChange, syncFromUiState } =
  useUiState(formData);

const { goodsLoading, goodsOptions, remoteSearchGoods, selectedGoods, isWearable } =
  useCs2GoodsSearch(toRef(formData, "cs2GoodsId"), {
    canViewGoods,
    scene: "task",
  });

const handleGoodsSearch = (keyword: string) => remoteSearchGoods(keyword);

const handleScanIntervalChange = (value: string | number) => {
  uiState.intervalMaxValue = Number(value);
};

const clearFieldValidate = (fields: string[]) => {
  formRef.value?.clearValidate(fields);
};

const applyWearableState = (wearable: boolean) => {
  if (!wearable) {
    formData.minPaintwear = 0;
    formData.maxPaintwear = 1;
  }
};

const hydrateGoodsOption = (row: TaskItem) => {
  if (!row.cs2GoodsId) {
    goodsOptions.value = [];
    return;
  }

  const fallbackGoods: Cs2GoodsOption = {
    id: row.cs2GoodsId,
    displayName: row.goodsDisplayName || row.name,
    marketHashName: row.marketHashName,
    itemType: row.itemType,
    hasExterior: row.hasExterior ?? true,
    imageUrl: row.goodsIconUrl,
  };
  goodsOptions.value = [fallbackGoods];
  applyWearableState(fallbackGoods.hasExterior ?? true);
};

const resetFormState = () => {
  goodsSelectionLocked.value = false;
};

const closeDialog = () => {
  resetFormState();
  visible.value = false;
};

watch(
  selectedGoods,
  (goods) => {
    if (goods) {
      clearFieldValidate(["cs2GoodsId"]);
      applyWearableState(goods.hasExterior ?? true);
    }
  },
  { immediate: true }
);

watch(canViewGoods, (allowed) => {
  if (!allowed) {
    goodsOptions.value = [];
  }
});

watch(
  () => formData.maxPrice,
  (maxPrice) => {
    if (maxPrice) {
      clearFieldValidate(["maxPrice"]);
    }
  }
);

watch(
  () => formData.buyCount,
  (buyCount) => {
    if (buyCount) {
      clearFieldValidate(["buyCount"]);
    }
  }
);

watch(
  () => uiState.intervalMinValue,
  (interval) => {
    if (interval) {
      clearFieldValidate(["scanInterval"]);
    }
  }
);

const handleAdd = () => {
  resetForm();
  resetFormState();
  formData.platform = PlatformEnum.C5;
  formData.taskType = TaskTypeEnum.SNIPING;
  formData.runMode = "BOTH";
  uiState.intervalMinValue = 1;
  uiState.intervalMaxValue = 1;
  uiState.intervalUnit = "s";
  dialogTitle.value = "新增任务";
  visible.value = true;
  nextTick(() => formRef.value?.clearValidate());
};

const openWithGoods = (goods: Cs2GoodsOption) => {
  handleAdd();
  formData.cs2GoodsId = goods.id;
  goodsOptions.value = [goods];
  applyWearableState(goods.hasExterior ?? true);
  dialogTitle.value = `新增扫货任务 - ${goods.displayName}`;
};

const handleEdit = (row: TaskItem, platform: string = PlatformEnum.C5) => {
  resetForm();
  resetFormState();
  formData.id = row.id;
  formData.cs2GoodsId = row.cs2GoodsId;
  formData.maxPrice = row.maxPrice ?? 0;
  formData.minPaintwear = row.minPaintwear ?? 0;
  formData.maxPaintwear = row.maxPaintwear ?? 1;
  formData.buyCount = row.buyCount;
  formData.cronExpression = row.cronExpression ?? "";
  formData.durationMinutes = row.durationMinutes ?? 0;
  formData.restPeriod = row.restPeriod ?? 0;
  formData.scanInterval = row.scanInterval ?? 1;
  formData.scanIntervalMin = row.scanIntervalMin ?? 15;
  formData.scanIntervalMax = row.scanIntervalMax ?? 20;
  formData.taskType = row.taskType;
  formData.runMode = row.runMode ?? "BOTH";
  formData.platform = platform;

  if (platform === PlatformEnum.C5 && row.extraConfig) {
    try {
      const parsed = JSON.parse(row.extraConfig);
      c5Config.safeMargin = (parsed.safeMargin ?? 0.03) * 100;
      c5Config.anchorTierIndex =
        parsed.anchorTierIndex !== undefined ? parsed.anchorTierIndex + 1 : 2;
      c5Config.minConcurrency = parsed.minConcurrency ?? 5;
    } catch (e) {
      console.error("解析 C5 配置失败", e);
    }
  } else {
    c5Config.safeMargin = (row.safetyMargin ?? 0.03) * 100;
    c5Config.anchorTierIndex = row.ladderStep ?? 2;
    c5Config.minConcurrency = 5;
  }

  const duration = convertToUi(row.durationMinutes || 0, DURATION_FACTORS);
  uiState.durationValue = duration.value;
  uiState.durationUnit = duration.unit as "m" | "h" | "d";
  uiState.isDurationUnlimited = !row.durationMinutes;

  const rest = convertToUi(row.restPeriod || 0, DURATION_FACTORS);
  uiState.restValue = rest.value;
  uiState.restUnit = rest.unit as "m" | "h" | "d";
  uiState.isCycleMode = !!row.restPeriod;

  const intervalMin = convertToUi(row.scanIntervalMin || 15, INTERVAL_FACTORS);
  const intervalMax = convertToUi(row.scanIntervalMax || 20, INTERVAL_FACTORS);
  const unitOrder = ["s", "m", "h", "d"];
  const minIdx = unitOrder.indexOf(intervalMin.unit);
  const maxIdx = unitOrder.indexOf(intervalMax.unit);
  const finalUnit = minIdx <= maxIdx ? intervalMin.unit : intervalMax.unit;
  const factor = INTERVAL_FACTORS[finalUnit];

  uiState.intervalUnit = finalUnit as "s" | "m" | "h" | "d";
  uiState.intervalMinValue = (row.scanIntervalMin || 15) / factor;
  uiState.intervalMaxValue = (row.scanIntervalMax || 20) / factor;
  uiState.isCronImmediate = !row.cronExpression || row.cronExpression === "* * * * * ?";

  hydrateGoodsOption(row);

  dialogTitle.value = "编辑任务";
  visible.value = true;
  nextTick(() => formRef.value?.clearValidate());
  syncFromUiState();
};

const handleCopy = (row: TaskItem, platform: string = PlatformEnum.C5) => {
  handleEdit(row, platform);
  formData.id = undefined;
  goodsSelectionLocked.value = true;
  dialogTitle.value = "复制任务";
};

const onFormSubmit = async (context: any) => {
  const success = await handleSubmit(context, uiState, c5Config);
  if (success) {
    closeDialog();
  }
};

const submitTaskForm = () => {
  formRef.value?.submit({ showErrorMessage: true });
};

defineExpose({ handleAdd, handleEdit, handleCopy, openWithGoods });
</script>

<style scoped>
.dialog-shell {
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #fff;
}

.form-container {
  max-height: min(640px, calc(100vh - 260px));
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 20px 24px;
  background: #fff;
}

.task-config-select {
  width: min(100%, 360px);
}

.task-config-input {
  width: 120px;
}

.task-config-input--price {
  width: 140px;
}

.task-config-unit-select {
  width: 96px;
}

.task-config-help {
  flex-shrink: 0;
}

.task-config-range__input {
  width: 110px;
}

.task-config-range--disabled {
  opacity: 0.6;
}

.task-config-range--disabled :deep(.t-input-number) {
  cursor: not-allowed;
}

:deep(.compact-form .t-form__item) {
  margin-bottom: 16px;
}

:deep(.compact-form .t-form__item.t-is-error) {
  margin-bottom: 28px;
}

:deep(.compact-form .t-form__item .t-form__controls-content) {
  display: flex;
  flex-direction: column;
  align-items: stretch;
}

:deep(.compact-form .t-form__label) {
  padding-right: 0 !important;
}

:deep(.compact-form .t-form__item .t-input__tips) {
  position: relative !important;
  display: block !important;
  min-height: auto !important;
  margin-top: 6px;
  margin-bottom: 0;
  line-height: 1.5;
}

:deep(.compact-form .t-form__item .t-form__verify-message) {
  position: relative !important;
  display: block !important;
  min-height: auto !important;
  margin-top: 6px;
  margin-bottom: 0;
  font-size: 12px;
  line-height: 1.5;
}

:deep(.compact-form .t-form__item .t-input__tips + .t-form__verify-message) {
  margin-top: 6px;
}

@media (max-width: 768px) {
  .form-container {
    padding: 16px;
  }

  :deep(.compact-form .t-form__item) {
    margin-bottom: 14px;
  }

  :deep(.compact-form .t-form__label) {
    width: 88px !important;
    padding-right: 8px;
  }

  .task-config-select,
  .task-config-input,
  .task-config-input--price,
  .task-config-unit-select,
  .task-config-range__input {
    width: 100%;
  }

  .task-config-range,
  .task-config-interval {
    flex-wrap: wrap;
    gap: 10px;
  }

  .task-config-range__separator {
    width: 100%;
    line-height: 1;
  }
}

@media (max-width: 640px) {
  .form-container {
    padding: 14px;
  }

  :deep(.compact-form .t-form__item) {
    display: flex;
    flex-direction: column;
    align-items: stretch;
  }

  :deep(.compact-form .t-form__label) {
    width: 100% !important;
    min-width: 0 !important;
    padding-right: 0;
    margin-bottom: 8px;
    text-align: left;
    line-height: 1.5;
  }

  :deep(.compact-form .t-form__controls) {
    width: 100%;
    max-width: none;
    margin-left: 0 !important;
  }

  :deep(.compact-form .t-form__controls-content) {
    width: 100%;
  }

  :deep(.compact-form .t-input-number),
  :deep(.compact-form .t-select),
  :deep(.compact-form .t-input),
  :deep(.compact-form .t-input-number__inner) {
    width: 100%;
    max-width: 100%;
  }

  .task-config-range,
  .task-config-interval {
    align-items: stretch;
  }

  .task-config-help {
    align-self: flex-start;
  }
}
</style>
