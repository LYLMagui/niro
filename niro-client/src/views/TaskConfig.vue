<template>
  <div>
    <!-- 扫货任务对话框 -->
    <t-dialog
      v-model:visible="dialogVisible"
      :header="dialogTitle"
      :confirm-btn="{ content: '提交', loading: submitLoading }"
      :width="uiState.isCycleMode ? '720px' : '640px'"
      placement="center"
      class="task-edit-dialog"
      :footer="false"
      destroy-on-close
    >
      <div class="form-container">
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
          <div class="mb-4 rounded-lg border border-gray-100 bg-gray-50/50 px-3 pt-3 pb-3">
            <t-form-item
              v-if="formData.platform !== PlatformEnum.C5"
              label="任务类型"
              name="taskType"
            >
              <t-radio-group v-model="formData.taskType">
                <t-radio :value="TaskTypeEnum.SNIPING">
                  {{ TaskTypeMap[TaskTypeEnum.SNIPING] }}
                </t-radio>
                <t-radio :value="TaskTypeEnum.FLIPPING">
                  {{ TaskTypeMap[TaskTypeEnum.FLIPPING] }}
                </t-radio>
              </t-radio-group>
            </t-form-item>

            <t-form-item v-if="formData.platform !== PlatformEnum.C5" label="任务模式">
              <t-tag
                v-if="formData.runMode === TaskRunModeEnum.SCAN"
                theme="primary"
                variant="light-outline"
              >
                {{ TaskRunModeMap[TaskRunModeEnum.SCAN] }}
              </t-tag>
              <t-tag
                v-else-if="formData.runMode === TaskRunModeEnum.TRADE"
                theme="warning"
                variant="light-outline"
              >
                {{ TaskRunModeMap[TaskRunModeEnum.TRADE] }}
              </t-tag>
              <t-tag v-else theme="success" variant="light-outline">
                {{ TaskRunModeMap[TaskRunModeEnum.BOTH] }}
              </t-tag>
              <template #tips>
                <span v-if="formData.runMode === TaskRunModeEnum.TRADE" class="text-orange-500">
                  此模式下，任务将基于现有扫描结果执行下单，不占用扫描频率
                </span>
              </template>
            </t-form-item>

            <!-- C5 专属策略配置 -->
            <template v-if="formData.platform === PlatformEnum.C5">
              <t-form-item label="安全边际">
                <t-input-number
                  v-model="c5Config.safeMargin"
                  :min="0"
                  :max="50"
                  :step="0.1"
                  :decimal-places="1"
                  suffix="%"
                  theme="column"
                  style="width: 160px"
                />
                <span class="ml-2 text-xs text-gray-400">建议 3%-5%</span>
              </t-form-item>
              <t-form-item label="锚定阶梯">
                <t-select v-model="c5Config.anchorTierIndex" style="width: 160px">
                  <t-option :value="1" label="最低价 (Top 1)" />
                  <t-option :value="2" label="次低价 (Top 2)" />
                  <t-option :value="3" label="第3阶梯 (Top 3)" />
                </t-select>
              </t-form-item>
            </template>

            <t-form-item label="选择商品" name="goodsId">
              <t-select
                v-model="formData.goodsId"
                filterable
                placeholder="输入商品名称搜索"
                :loading="goodsLoading"
                :on-search="remoteSearchGoods"
                :disabled="!!formData.id"
                style="width: 320px"
              >
                <t-option
                  v-for="item in goodsOptions"
                  :key="item.goodsId"
                  :value="item.goodsId"
                  :label="item.name"
                >
                  {{ item.name }}
                </t-option>
              </t-select>
            </t-form-item>

            <t-form-item
              v-if="
                formData.runMode !== TaskRunModeEnum.TRADE && formData.platform !== PlatformEnum.C5
              "
              label="关联下单任务"
              name="targetTaskId"
            >
              <t-select
                v-model="formData.targetTaskId"
                filterable
                placeholder="请选择关联的下单任务 (可选)"
                :loading="tradeTasksLoading"
                :disabled="!formData.goodsId"
                style="width: 320px"
                @focus="fetchTradeTasks(formData.goodsId)"
              >
                <t-option
                  v-for="item in tradeTasks"
                  :key="item.id"
                  :value="item.id"
                  :label="item.name"
                >
                  <div class="flex w-full items-center justify-between">
                    <span class="font-medium">{{ item.name }}</span>
                    <t-tag v-if="item.status === 1" theme="success" variant="light" size="small">
                      运行中
                    </t-tag>
                    <t-tag v-else theme="default" variant="light" size="small">停止</t-tag>
                  </div>
                </t-option>
              </t-select>
              <template #tips>
                {{
                  formData.goodsId
                    ? "仅显示相同商品的任务。选择后，扫描结果将自动路由给该任务执行购买"
                    : "请先选择商品以关联对应的下单任务"
                }}
              </template>
            </t-form-item>

            <t-form-item
              v-if="
                formData.taskType === TaskTypeEnum.FLIPPING &&
                formData.runMode !== TaskRunModeEnum.TRADE
              "
              label="预期利润"
              name="minProfit"
            >
              <t-input-number
                v-model="formData.minProfit"
                :min="0"
                :step="1"
                :decimal-places="2"
                suffix="元"
                theme="column"
                style="width: 160px"
              />
              <span class="ml-2 text-xs text-gray-400">低于此利润将不购买 (已扣除手续费)</span>
            </t-form-item>

            <t-form-item
              v-if="
                formData.taskType === TaskTypeEnum.SNIPING &&
                formData.runMode !== TaskRunModeEnum.TRADE
              "
              label="最高价格"
              name="maxPrice"
            >
              <t-input-number
                v-model="formData.maxPrice"
                :min="0.01"
                :step="0.1"
                :decimal-places="2"
                suffix="元"
                theme="column"
                style="width: 140px"
              />
            </t-form-item>

            <t-form-item
              v-if="
                formData.taskType === TaskTypeEnum.SNIPING &&
                formData.runMode !== TaskRunModeEnum.TRADE &&
                isWearable
              "
              label="磨损范围"
              name="wear"
            >
              <div class="flex items-center gap-3">
                <t-input-number
                  v-model="formData.minPaintwear"
                  :min="0"
                  :max="1"
                  :step="0.001"
                  :decimal-places="3"
                  placeholder="最小"
                  theme="column"
                  style="width: 110px"
                />
                <span class="text-gray-400">至</span>
                <t-input-number
                  v-model="formData.maxPaintwear"
                  :min="0"
                  :max="1"
                  :step="0.001"
                  :decimal-places="3"
                  placeholder="最大"
                  theme="column"
                  style="width: 110px"
                />
              </div>
            </t-form-item>

            <t-form-item
              v-if="formData.runMode !== 'SCAN' || formData.targetTaskId"
              label="购买数量"
              name="buyCount"
            >
              <t-input-number
                v-model="formData.buyCount"
                :min="1"
                :step="1"
                theme="column"
                style="width: 120px"
              />
            </t-form-item>

            <t-form-item
              v-if="formData.platform !== PlatformEnum.C5"
              label="执行账号"
              name="accountIds"
            >
              <AccountSelector
                v-model="formData.accountIds"
                :accounts="filteredAccounts"
                :loading="accountsLoading"
                :current-task-id="formData.id"
                @focus="fetchAccounts"
              />
              <template #tips>多选账号可实现多并发扫货，提高抢购成功率</template>
            </t-form-item>

            <t-form-item v-if="formData.runMode !== TaskRunModeEnum.TRADE" label-width="0">
              <ScheduleConfig
                mode="scan"
                :ui-state="uiState"
                :cron-expression="formData.cronExpression"
                :platform="formData.platform"
                :execution-summary="executionSummary"
                @update:cron-expression="formData.cronExpression = $event"
                @interval-min-blur="handleIntervalMinBlur"
                @interval-max-blur="handleIntervalMaxBlur"
                @interval-unit-change="handleIntervalUnitChange"
              />
            </t-form-item>
          </div>

          <!-- 底部按钮 -->
          <div class="mt-2 flex justify-end gap-3 border-t border-gray-100 pt-2">
            <t-button variant="outline" theme="default" @click="dialogVisible = false">
              取消
            </t-button>
            <t-button theme="primary" type="submit" :loading="submitLoading">提交</t-button>
          </div>
        </t-form>
      </div>
    </t-dialog>

    <!-- 系统任务对话框 -->
    <t-dialog
      v-model:visible="systemDialogVisible"
      :header="dialogTitle"
      :confirm-btn="{ content: '提交', loading: submitLoading }"
      width="600px"
      placement="center"
      class="task-edit-dialog"
      :footer="false"
      destroy-on-close
    >
      <div class="form-container">
        <t-form
          ref="systemFormRef"
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
          <div class="mb-4 rounded-lg border border-gray-100 bg-gray-50/50 px-3 pt-3 pb-3">
            <t-form-item label="任务类型" name="taskType">
              <t-radio-group v-model="formData.taskType">
                <t-radio :value="TaskTypeEnum.SYNC_CATEGORY">
                  {{ TaskTypeMap[TaskTypeEnum.SYNC_CATEGORY] }}
                </t-radio>
                <t-radio :value="TaskTypeEnum.SYNC_GOODS">
                  {{ TaskTypeMap[TaskTypeEnum.SYNC_GOODS] }}
                </t-radio>
                <t-radio :value="TaskTypeEnum.SYNC_STICKER">
                  {{ TaskTypeMap[TaskTypeEnum.SYNC_STICKER] }}
                </t-radio>
                <t-radio :value="TaskTypeEnum.SYNC_CATEGORY_GOODS">
                  {{ TaskTypeMap[TaskTypeEnum.SYNC_CATEGORY_GOODS] }}
                </t-radio>
              </t-radio-group>
            </t-form-item>

            <t-form-item label="执行账号" name="accountIds">
              <AccountSelector
                v-model="formData.accountIds"
                :accounts="accounts"
                :loading="accountsLoading"
                :current-task-id="formData.id"
                @focus="fetchAccounts"
              />
              <template #tips>系统任务建议绑定多个扫描账号以平衡负载</template>
            </t-form-item>

            <!-- 调度配置 -->
            <div class="mt-0.5">
              <ScheduleConfig
                mode="system"
                :ui-state="uiState"
                :cron-expression="formData.cronExpression"
                @update:cron-expression="formData.cronExpression = $event"
              />
            </div>
          </div>

          <!-- 底部按钮 -->
          <div class="mt-2 flex justify-end gap-3 border-t border-gray-100 pt-2">
            <t-button variant="outline" theme="default" @click="systemDialogVisible = false">
              取消
            </t-button>
            <t-button theme="primary" type="submit" :loading="submitLoading">提交</t-button>
          </div>
        </t-form>
      </div>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import { goodsApi } from "@/api/goods";
import type { GoodsSimple } from "@/types/goods";
import type { BuffScanTask } from "@/types/task";
import { nextTick, ref, toRef, watch } from "vue";
import { PlatformEnum } from "@/enums/PlatformEnum";
import { TaskTypeEnum, TaskTypeMap } from "@/enums/TaskTypeEnum";
import { TaskRunModeEnum, TaskRunModeMap } from "@/enums/TaskRunModeEnum";
import AccountSelector from "@/components/task/AccountSelector.vue";
import ScheduleConfig from "@/components/task/ScheduleConfig.vue";
import {
  useUiState,
  convertToUi,
  DURATION_FACTORS,
  INTERVAL_FACTORS,
} from "@/composables/useUiState";
import { useGoodsSearch } from "@/composables/useGoodsSearch";
import { useAccountSelect } from "@/composables/useAccountSelect";
import { useTaskForm } from "@/composables/useTaskForm";

const emit = defineEmits(["success"]);

// --- Composables & State ---
const dialogVisible = ref(false);
const systemDialogVisible = ref(false);
const dialogTitle = ref("新增任务");
const formRef = ref();
const systemFormRef = ref();

const { formData, submitLoading, rules, resetForm, handleSubmit } = useTaskForm(emit);

const {
  uiState,
  c5Config,
  handleIntervalMinBlur,
  handleIntervalMaxBlur,
  handleIntervalUnitChange,
  syncFromUiState,
  executionSummary,
} = useUiState(formData as any);

const { goodsLoading, goodsOptions, remoteSearchGoods, isWearable } = useGoodsSearch(
  toRef(formData, "goodsId") as any
);

const {
  accounts,
  accountsLoading,
  fetchAccounts,
  tradeTasks,
  tradeTasksLoading,
  fetchTradeTasks,
  filteredAccounts,
} = useAccountSelect(formData as any);

// --- Watchers ---
watch(
  () => formData.goodsId,
  () => {
    formData.targetTaskId = undefined;
    tradeTasks.value = [];
  }
);

watch(
  () => formData.runMode,
  (newVal) => {
    nextTick(() => formRef.value?.clearValidate());
    const filteredIds = (formData.accountIds as number[])?.filter((id: number) => {
      const account = accounts.value.find((a) => a.id === id);
      if (!account) return false;
      if (newVal === "TRADE") return account.role === "TRADE" || account.role === "BOTH";
      if (newVal === "SCAN") return account.role === "SCAN" || account.role === "BOTH";
      return true;
    });
    if (filteredIds?.length !== (formData.accountIds as number[])?.length) {
      formData.accountIds = filteredIds;
    }
  }
);

// --- Methods (Exposed) ---
const handleAdd = (defaultMode: string = "SCAN", platform: string = PlatformEnum.BUFF) => {
  resetForm();
  formData.platform = platform;

  if (platform === PlatformEnum.C5) {
    formData.taskType = TaskTypeEnum.SNIPING;
    formData.runMode = "BOTH";
    uiState.intervalMinValue = 1;
    uiState.intervalMaxValue = 1;
    uiState.intervalUnit = "s";
  } else {
    formData.runMode = defaultMode as any;
    uiState.intervalMinValue = 15;
    uiState.intervalMaxValue = 20;
    uiState.intervalUnit = "s";
  }

  dialogTitle.value = "新增任务";
  dialogVisible.value = true;
  nextTick(() => formRef.value?.clearValidate());
  fetchAccounts();
};

const handleAddSystem = () => {
  resetForm();
  formData.taskType = TaskTypeEnum.SYNC_CATEGORY;
  dialogTitle.value = "新增系统任务";
  systemDialogVisible.value = true;
  nextTick(() => systemFormRef.value?.clearValidate());
  fetchAccounts();
};

const openWithGoods = (goods: GoodsSimple) => {
  handleAdd("SCAN");
  formData.goodsId = goods.goodsId;
  goodsOptions.value = [goods];
  dialogTitle.value = `新增扫货任务 - ${goods.name}`;
};

const handleEdit = (row: BuffScanTask, platform: string = PlatformEnum.BUFF) => {
  resetForm();
  Object.assign(formData, row);
  formData.platform = platform;
  if (!formData.accountIds) formData.accountIds = [];

  // 解析 C5 配置
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
    c5Config.safeMargin = ((row as any).safetyMargin ?? 0.03) * 100;
    c5Config.anchorTierIndex = (row as any).ladderStep ?? 2;
    c5Config.minConcurrency = 5;
  }

  // 初始化 UI 状态
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

  if (row.goodsId) {
    goodsOptions.value = [{ goodsId: row.goodsId, name: row.name } as GoodsSimple];
    goodsLoading.value = true;
    goodsApi
      .getSimpleList(row.name)
      .then((res) => {
        const match = res.find((g) => g.goodsId === row.goodsId);
        if (match) goodsOptions.value = [match];
      })
      .catch((e) => console.error("Fetch goods info failed", e))
      .finally(() => (goodsLoading.value = false));
  }

  if (row.targetTaskId) {
    nextTick(async () => {
      formData.targetTaskId = row.targetTaskId;
      if ((row as any).targetTaskName) {
        tradeTasks.value = [{ id: row.targetTaskId, name: (row as any).targetTaskName } as never];
      } else {
        await fetchTradeTasks(row.goodsId);
      }
    });
  }

  dialogTitle.value = "编辑任务";
  if (row.taskType >= 2) {
    systemDialogVisible.value = true;
    nextTick(() => systemFormRef.value?.clearValidate());
  } else {
    dialogVisible.value = true;
    nextTick(() => formRef.value?.clearValidate());
  }
  fetchAccounts();
  syncFromUiState();
};

const onFormSubmit = async (context: any) => {
  const success = await handleSubmit(context, uiState, c5Config);
  if (success) {
    dialogVisible.value = false;
    systemDialogVisible.value = false;
  }
};

defineExpose({ handleAdd, handleAddSystem, handleEdit, openWithGoods });
</script>

<style scoped>
:deep(.task-edit-dialog .t-dialog__body) {
  max-height: 80vh;
  padding: 4px 12px 8px;
  overflow-y: auto;
}

:deep(.task-edit-dialog .t-dialog__header) {
  padding: 12px 12px 4px;
}

:deep(.task-edit-dialog .t-dialog) {
  max-height: 90vh;
}

:deep(.compact-form .t-form__item) {
  margin-bottom: 12px;
}

:deep(.compact-form .t-form__item.t-is-error) {
  margin-bottom: 22px;
}

:deep(.compact-form .t-form__item .t-input__tips) {
  position: relative !important;
  display: block !important;
  min-height: auto !important;
  margin-top: 4px;
  margin-bottom: 2px;
  line-height: 1.4;
}

:deep(.compact-form .t-form__item .t-form__verify-message) {
  position: relative !important;
  display: block !important;
  min-height: auto !important;
  margin-top: 4px;
  margin-bottom: 2px;
  font-size: 12px;
  line-height: 1.4;
}

:deep(.compact-form .t-form__item .t-input__tips + .t-form__verify-message) {
  margin-top: 4px;
}
</style>
