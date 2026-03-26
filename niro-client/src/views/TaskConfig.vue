<template>
  <div>
    <t-dialog
      v-model:visible="dialogVisible"
      :header="dialogTitle"
      :confirm-btn="{ content: '提交', loading: submitLoading }"
      width="640px"
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
            </t-form-item>

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
                :placeholder="goodsSelectPlaceholder"
                :loading="goodsLoading"
                :on-search="handleGoodsSearch"
                :disabled="!!formData.id || !canViewGoods"
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

            <t-form-item v-if="formData.runMode !== TaskRunModeEnum.SCAN" label="购买数量" name="buyCount">
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
                :disabled="!canViewAccounts"
                :current-task-id="formData.id"
                :placeholder="accountPlaceholder"
                @focus="fetchAccounts"
              />
              <template #tips>
                {{
                  !canViewAccounts
                    ? '当前账号没有账号列表权限，无法为任务绑定执行账号'
                    : '多选账号可实现多并发执行'
                }}
              </template>
            </t-form-item>

            <div class="rounded border border-amber-100 bg-amber-50 px-3 py-2 text-xs leading-6 text-amber-700">
              简化版已移除系统任务、关联下单和定时调度入口。任务提交后按默认策略立即生效。
            </div>
          </div>

          <div class="mt-2 flex justify-end gap-3 border-t border-gray-100 pt-2">
            <t-button variant="outline" theme="default" @click="dialogVisible = false">取消</t-button>
            <t-button
              v-permission="PermissionConstant.TASK_BUFF_LIST"
              theme="primary"
              type="submit"
              :loading="submitLoading"
            >
              提交
            </t-button>
          </div>
        </t-form>
      </div>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, toRef, watch } from "vue";
import { goodsApi } from "@/api/goods";
import type { GoodsSimple } from "@/types/goods";
import type { BuffScanTask } from "@/types/task";
import { PlatformEnum } from "@/enums/PlatformEnum";
import { TaskTypeEnum, TaskTypeMap } from "@/enums/TaskTypeEnum";
import { TaskRunModeEnum, TaskRunModeMap } from "@/enums/TaskRunModeEnum";
import AccountSelector from "@/components/task/AccountSelector.vue";
import { useGoodsSearch } from "@/composables/useGoodsSearch";
import { useAccountSelect } from "@/composables/useAccountSelect";
import { useTaskForm } from "@/composables/useTaskForm";
import { PermissionConstant } from "@/constant/PermissionConstant";
import { usePermission } from "@/hooks/usePermission";

interface TaskConfigExpose {
  handleAdd: (defaultMode?: "SCAN" | "TRADE" | "BOTH", platform?: string) => void;
  handleEdit: (row: BuffScanTask, platform?: string) => void;
  openWithGoods: (goods: GoodsSimple) => void;
}

interface FormInstance {
  clearValidate: () => void;
}

const emit = defineEmits<{
  success: [];
}>();

const dialogVisible = ref(false);
const dialogTitle = ref("新增任务");
const formRef = ref<FormInstance | null>(null);
const { hasPermission } = usePermission();

const canViewGoods = computed(() => hasPermission(PermissionConstant.GOODS_LIST));
const canViewAccounts = computed(() => hasPermission(PermissionConstant.ACCOUNT_LIST));
const goodsSelectPlaceholder = computed(() =>
  canViewGoods.value ? "输入商品名称搜索" : "当前账号无商品管理权限"
);
const accountPlaceholder = computed(() =>
  canViewAccounts.value ? "请选择执行账号" : "当前账号无账号列表权限"
);

const { formData, submitLoading, rules, resetForm, handleSubmit, c5Config } = useTaskForm(emit);

const { goodsLoading, goodsOptions, remoteSearchGoods, isWearable } = useGoodsSearch(
  toRef(formData, "goodsId"),
  { canViewGoods }
);

const { accounts, accountsLoading, fetchAccounts, filteredAccounts } = useAccountSelect(formData, {
  canViewAccounts,
});

const handleGoodsSearch = (keyword: string) => remoteSearchGoods(keyword);

watch(canViewGoods, (allowed) => {
  if (!allowed) {
    goodsOptions.value = [];
  }
});

watch(canViewAccounts, (allowed) => {
  if (!allowed) {
    formData.accountIds = [];
  }
});

watch(
  () => formData.runMode,
  (newVal) => {
    nextTick(() => formRef.value?.clearValidate());
    const filteredIds = formData.accountIds.filter((id) => {
      const account = accounts.value.find((item) => item.id === id);
      if (!account) return false;
      if (newVal === "TRADE") return account.role === "TRADE" || account.role === "BOTH";
      if (newVal === "SCAN") return account.role === "SCAN" || account.role === "BOTH";
      return true;
    });
    if (filteredIds.length !== formData.accountIds.length) {
      formData.accountIds = filteredIds;
    }
  }
);

const handleAdd = (defaultMode: "SCAN" | "TRADE" | "BOTH" = "SCAN", platform: string = PlatformEnum.BUFF) => {
  resetForm();
  formData.platform = platform;

  if (platform === PlatformEnum.C5) {
    formData.taskType = TaskTypeEnum.SNIPING;
    formData.runMode = "BOTH";
    formData.scanIntervalMin = 1;
    formData.scanIntervalMax = 1;
    formData.scanInterval = 1;
  } else {
    formData.runMode = defaultMode;
    formData.scanIntervalMin = 15;
    formData.scanIntervalMax = 20;
    formData.scanInterval = undefined;
  }

  dialogTitle.value = "新增任务";
  dialogVisible.value = true;
  nextTick(() => formRef.value?.clearValidate());
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
  formData.accountIds = row.accountIds ?? [];
  formData.targetTaskId = undefined;

  if (platform === PlatformEnum.C5 && row.extraConfig) {
    try {
      const parsed = JSON.parse(row.extraConfig) as {
        safeMargin?: number;
        anchorTierIndex?: number;
      };
      c5Config.safeMargin = (parsed.safeMargin ?? 0.03) * 100;
      c5Config.anchorTierIndex = parsed.anchorTierIndex ?? 2;
    } catch {
      c5Config.safeMargin = 3;
      c5Config.anchorTierIndex = 2;
    }
  } else {
    c5Config.safeMargin = 3;
    c5Config.anchorTierIndex = 2;
  }

  if (row.goodsId) {
    goodsOptions.value = [{ goodsId: row.goodsId, name: row.name }];
    if (canViewGoods.value) {
      goodsLoading.value = true;
      goodsApi
        .getSimpleList(row.name)
        .then((res) => {
          const match = res.find((item) => item.goodsId === row.goodsId);
          if (match) {
            goodsOptions.value = [match];
          }
        })
        .finally(() => {
          goodsLoading.value = false;
        });
    }
  }

  dialogTitle.value = "编辑任务";
  dialogVisible.value = true;
  nextTick(() => formRef.value?.clearValidate());
  fetchAccounts();
};

const onFormSubmit = async (context: { validateResult: true | unknown; firstError?: string }) => {
  const success = await handleSubmit(context);
  if (success) {
    dialogVisible.value = false;
  }
};

defineExpose<TaskConfigExpose>({ handleAdd, handleEdit, openWithGoods });
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
