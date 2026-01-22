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
          @submit="handleSubmit"
        >
          <div class="mb-4 rounded-lg border border-gray-100 bg-gray-50/50 px-3 pt-3 pb-3">
            <t-form-item label="任务类型" name="taskType">
              <t-radio-group v-model="formData.taskType">
                <t-radio :value="0">炼金扫货</t-radio>
                <t-radio :value="1">站内倒卖</t-radio>
              </t-radio-group>
            </t-form-item>

            <t-form-item label="任务模式">
              <t-tag v-if="formData.runMode === 'SCAN'" theme="primary" variant="light-outline">
                仅扫描
              </t-tag>
              <t-tag
                v-else-if="formData.runMode === 'TRADE'"
                theme="warning"
                variant="light-outline"
              >
                仅下单
              </t-tag>
              <t-tag v-else theme="success" variant="light-outline">全能模式</t-tag>
              <template #tips>
                <span v-if="formData.runMode === 'TRADE'" class="text-orange-500">
                  此模式下，任务将基于现有扫描结果执行下单，不占用扫描频率
                </span>
              </template>
            </t-form-item>

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
              v-if="formData.runMode !== 'TRADE'"
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
                @focus="fetchTradeTasks"
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
              v-if="formData.taskType === 1 && formData.runMode !== 'TRADE'"
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
              v-if="formData.taskType === 0 && formData.runMode !== 'TRADE'"
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
              v-if="formData.taskType === 0 && formData.runMode !== 'TRADE'"
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

            <t-form-item label="执行账号" name="accountIds">
              <t-select
                v-model="formData.accountIds"
                multiple
                filterable
                placeholder="请选择执行账号"
                :loading="accountsLoading"
                style="width: 320px"
                :min-collapsed-num="2"
                @focus="fetchAccounts"
              >
                <t-option
                  v-for="item in filteredAccounts"
                  :key="item.id"
                  :value="item.id"
                  :label="item.accountName"
                  :disabled="!!item.boundTaskId && item.boundTaskId !== formData.id"
                >
                  <div class="flex w-full items-center justify-between overflow-hidden">
                    <div class="mr-2 flex flex-1 items-center gap-1.5 overflow-hidden">
                      <span class="shrink-0 font-medium">{{ item.accountName }}</span>
                      <t-tooltip
                        v-if="item.boundTaskId && item.boundTaskId !== formData.id"
                        :content="'已绑定任务: ' + item.boundTaskName"
                        placement="top"
                      >
                        <span class="truncate text-xs text-gray-400">
                          (已绑定: {{ item.boundTaskName }})
                        </span>
                      </t-tooltip>
                    </div>
                    <t-tag
                      v-if="item.status === 'NORMAL'"
                      theme="success"
                      variant="light"
                      size="small"
                      class="shrink-0"
                    >
                      在线
                    </t-tag>
                    <t-tag v-else theme="danger" variant="light" size="small" class="shrink-0">
                      异常
                    </t-tag>
                  </div>
                </t-option>
              </t-select>
              <template #tips>多选账号可实现多并发扫货，提高抢购成功率</template>
            </t-form-item>

            <t-form-item v-if="formData.runMode !== 'TRADE'" label-width="0">
              <div
                class="schedule-group mt-1 w-full rounded-md border border-blue-100 bg-blue-50/50 p-1.5"
              >
                <!-- 顶部模式切换 -->
                <div class="mb-1 flex items-center justify-between">
                  <div class="flex items-center gap-2 text-sm font-medium text-blue-700">
                    <t-icon name="time-filled" />
                    <span>计划配置</span>
                  </div>
                  <t-radio-group
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
                  label="启动时间"
                  name="cronExpression"
                  label-width="90px"
                  label-align="right"
                >
                  <div class="flex items-center gap-4">
                    <t-input
                      v-model="formData.cronExpression"
                      :disabled="uiState.isCronImmediate"
                      placeholder="未配置"
                      clearable
                      style="width: 180px"
                      @blur="(v: any) => handleInputTrim(v, formData, 'cronExpression')"
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
                            <t-icon name="calendar" />
                          </t-link>
                          <template #content>
                            <div class="cron-popup-container">
                              <cron-editor
                                :model-value="formData.cronExpression || ''"
                                @update:model-value="formData.cronExpression = $event"
                                @confirm="cronVisible = false"
                                @cancel="cronVisible = false"
                              />
                            </div>
                          </template>
                        </t-popup>
                      </template>
                    </t-input>
                    <t-checkbox v-model="uiState.isCronImmediate">立即启动</t-checkbox>
                  </div>
                </t-form-item>

                <!-- 运行时长 / 循环配置 -->
                <t-form-item
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

                <!-- 扫描间隔 -->
                <t-form-item
                  label="扫描间隔"
                  name="scanInterval"
                  label-width="90px"
                  label-align="right"
                  class="mt-1"
                >
                  <div class="flex items-center gap-4">
                    <t-input-number
                      v-model="uiState.intervalMinValue"
                      :min="uiState.intervalUnit === 's' ? 15 : 1"
                      :step="1"
                      theme="column"
                      style="width: 90px"
                      @blur="handleIntervalMinBlur"
                    />
                    <span class="text-gray-400">至</span>
                    <t-input-number
                      v-model="uiState.intervalMaxValue"
                      :min="uiState.intervalUnit === 's' ? 15 : 1"
                      :step="1"
                      theme="column"
                      style="width: 90px"
                      @blur="handleIntervalMaxBlur"
                    />
                    <t-select
                      v-model="uiState.intervalUnit"
                      style="width: 80px"
                      @change="handleIntervalUnitChange"
                    >
                      <t-option label="秒" value="s" />
                      <t-option label="分钟" value="m" />
                      <t-option label="小时" value="h" />
                      <t-option label="天" value="d" />
                    </t-select>
                    <t-tooltip content="扫描间隔建议在 15-30 秒之间，过短容易触发风控。">
                      <t-icon name="help-circle" class="ml-1 cursor-help text-gray-400" />
                    </t-tooltip>
                  </div>
                </t-form-item>

                <!-- 动态逻辑预览 -->
                <div
                  class="mt-1 rounded border border-blue-50 bg-white/80 p-2 text-[12px] leading-relaxed text-blue-600 shadow-sm"
                >
                  <div class="flex items-start gap-1.5">
                    <t-icon name="info-circle" class="mt-0.5 shrink-0" />
                    <div class="font-medium">{{ executionSummary }}</div>
                  </div>
                </div>
              </div>
            </t-form-item>
          </div>

          <!-- 自定义底部按钮，用于触发表单提交 -->
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
          @submit="handleSubmit"
        >
          <div class="mb-4 rounded-lg border border-gray-100 bg-gray-50/50 px-3 pt-3 pb-3">
            <t-form-item label="任务类型" name="taskType">
              <t-radio-group v-model="formData.taskType">
                <t-radio :value="2">系统-分类同步</t-radio>
                <t-radio :value="3">系统-商品同步</t-radio>
                <t-radio :value="4">系统-印花同步</t-radio>
                <t-radio :value="5">系统-分类商品同步</t-radio>
              </t-radio-group>
            </t-form-item>

            <t-form-item label="执行账号" name="accountIds">
              <t-select
                v-model="formData.accountIds"
                multiple
                filterable
                placeholder="请选择执行账号"
                :loading="accountsLoading"
                style="width: 320px"
                :min-collapsed-num="2"
                @focus="fetchAccounts"
              >
                <t-option
                  v-for="item in accounts"
                  :key="item.id"
                  :value="item.id"
                  :label="item.accountName"
                  :disabled="!!item.boundTaskId && item.boundTaskId !== formData.id"
                >
                  <div class="flex w-full items-center justify-between overflow-hidden">
                    <div class="mr-2 flex flex-1 items-center gap-1.5 overflow-hidden">
                      <span class="shrink-0 font-medium">{{ item.accountName }}</span>
                      <t-tooltip
                        v-if="item.boundTaskId && item.boundTaskId !== formData.id"
                        :content="'已绑定任务: ' + item.boundTaskName"
                        placement="top"
                      >
                        <span class="truncate text-xs text-gray-400">
                          (已绑定: {{ item.boundTaskName }})
                        </span>
                      </t-tooltip>
                    </div>
                    <t-tag
                      v-if="item.status === 'NORMAL'"
                      theme="success"
                      variant="light"
                      size="small"
                      class="shrink-0"
                    >
                      在线
                    </t-tag>
                    <t-tag v-else theme="danger" variant="light" size="small" class="shrink-0">
                      异常
                    </t-tag>
                  </div>
                </t-option>
              </t-select>
              <template #tips>系统任务建议绑定多个扫描账号以平衡负载</template>
            </t-form-item>

            <!-- 调度配置分组 -->
            <div
              class="schedule-group mt-0.5 rounded-md border border-blue-100 bg-blue-50/50 p-1.5"
            >
              <div class="mb-1 flex items-center gap-2 text-sm font-medium text-blue-700">
                <t-icon name="time-filled" />
                <span>运行计划</span>
              </div>

              <t-form-item
                label="执行计划"
                name="cronExpression"
                label-width="90px"
                label-align="right"
              >
                <div class="flex items-center gap-4">
                  <t-input
                    v-model="formData.cronExpression"
                    :disabled="uiState.isCronImmediate"
                    placeholder="未配置"
                    clearable
                    style="width: 200px"
                    @blur="(v: any) => handleInputTrim(v, formData, 'cronExpression')"
                  >
                    <template #suffix>
                      <t-popup
                        v-model:visible="systemCronVisible"
                        placement="bottom-right"
                        trigger="click"
                        :overlay-inner-style="{ padding: 0 }"
                      >
                        <t-link
                          theme="primary"
                          variant="underline"
                          :disabled="uiState.isCronImmediate"
                        >
                          <t-icon name="calendar" class="mr-1" />
                          可视化
                        </t-link>
                        <template #content>
                          <div class="cron-popup-container">
                            <cron-editor
                              :model-value="formData.cronExpression || ''"
                              @update:model-value="formData.cronExpression = $event"
                              @confirm="systemCronVisible = false"
                              @cancel="systemCronVisible = false"
                            />
                          </div>
                        </template>
                      </t-popup>
                    </template>
                  </t-input>
                  <t-checkbox v-model="uiState.isCronImmediate">立即执行</t-checkbox>
                </div>
              </t-form-item>

              <!-- 动态逻辑预览 -->
              <div
                v-if="formData.cronExpression && !uiState.isCronImmediate"
                class="mt-1 rounded border border-blue-50 bg-white/80 p-1.5 text-[11px] leading-relaxed text-blue-600 shadow-sm"
              >
                <div class="flex items-start gap-1">
                  <t-icon name="info-circle" class="mt-0.5 shrink-0" />
                  <div>任务将根据 Cron 表达式定时触发执行</div>
                </div>
              </div>
            </div>
          </div>

          <!-- 自定义底部按钮 -->
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
import { settingsApi, type BuffAccount } from "@/api/settings";
import { taskApi } from "@/api/task";
import CronEditor from "@/components/CronEditor.vue";
import type { GoodsSimple } from "@/types/goods";
import type { BuffScanTask, TaskSaveParam } from "@/types/task";
import cronParser from "cron-parser";
import { MessagePlugin, type FormRules, type SelectValue } from "tdesign-vue-next";
import { computed, nextTick, reactive, ref, watch } from "vue";

const emit = defineEmits(["success"]);

// --- 状态 ---
const accounts = ref<BuffAccount[]>([]);
const accountsLoading = ref(false);

const tradeTasks = ref<BuffScanTask[]>([]);
const tradeTasksLoading = ref(false);

const fetchTradeTasks = async () => {
  tradeTasksLoading.value = true;
  try {
    tradeTasks.value = await taskApi.getTradeTasks(formData.goodsId);
  } finally {
    tradeTasksLoading.value = false;
  }
};

const fetchAccounts = async () => {
  accountsLoading.value = true;
  try {
    accounts.value = await settingsApi.getBuffAccounts();
  } finally {
    accountsLoading.value = false;
  }
};

const cronVisible = ref(false);
const systemCronVisible = ref(false);

/**
 * 自动清除换行符和首尾空格
 */
const handleInputTrim = (val: any, target: any, key: string) => {
  if (typeof val === "string") {
    target[key] = val.replace(/[\r\n]/g, "").trim();
  }
};

// --- 表单 UI 状态 (用于单位换算) ---
const uiState = reactive({
  durationValue: 0,
  durationUnit: "m" as "m" | "h" | "d",
  intervalMinValue: 15,
  intervalMaxValue: 20,
  intervalUnit: "s" as "s" | "m" | "h" | "d",
  isCronImmediate: true,
  isDurationUnlimited: true,
  isCycleMode: false,
  restValue: 5,
  restUnit: "m" as "m" | "h" | "d",
});

// 单位换算系数 (基准: 分钟)
const DURATION_FACTORS = {
  m: 1,
  h: 60,
  d: 1440,
};

// 单位换算系数 (基准: 秒)
const INTERVAL_FACTORS = {
  s: 1,
  m: 60,
  h: 3600,
  d: 86400,
};

/**
 * 将存储值转换为最合适的 UI 显示值和单位
 */
const convertToUi = (value: number, factors: Record<string, number>) => {
  if (!value) return { value: 0, unit: Object.keys(factors)[0] };
  const units = (Object.keys(factors) as Array<keyof typeof factors>).reverse();
  for (const unit of units) {
    const factor = factors[unit];
    if (value % factor === 0) {
      return { value: value / factor, unit };
    }
  }
  return { value, unit: Object.keys(factors)[0] };
};

const executionSummary = computed(() => {
  const cron = formData.cronExpression?.trim();
  const duration = uiState.durationValue;
  const durationUnit = { m: "分钟", h: "小时", d: "天" }[uiState.durationUnit] || "分钟";
  const intervalMin = uiState.intervalMinValue;
  const intervalMax = uiState.intervalMaxValue;
  const intervalUnit = { s: "秒", m: "分钟", h: "小时", d: "天" }[uiState.intervalUnit] || "秒";

  let summary = "";

  // 判断是否为立即启动
  const isImmediate = !cron || cron === "* * * * * ?" || cron === "* * * * * *";

  if (isImmediate) {
    summary += `任务将立即启动。`;
  } else {
    try {
      const safeExpression = cron
        .replace(/\?/g, "*")
        .replace(/last\s+(\w+)/g, "$1L")
        .replace(/last/g, "L");

      const options: { currentDate: Date; tz: string; hasSeconds?: boolean } = {
        currentDate: new Date(),
        tz: "Asia/Shanghai",
      };
      if (safeExpression.split(" ").length >= 6) {
        options.hasSeconds = true;
      }

      const cp = cronParser as {
        parse: (
          _exp: string,
          _opts: { currentDate: Date; tz: string; hasSeconds?: boolean }
        ) => { next: () => { toDate: () => Date } };
      };
      const interval = cp.parse(safeExpression, options);
      const nextDate = interval.next().toDate();

      const pad = (n: number) => (n < 10 ? `0${n}` : n);
      const nextTimeStr =
        `${nextDate.getFullYear()}-${pad(nextDate.getMonth() + 1)}-${pad(nextDate.getDate())} ` +
        `${pad(nextDate.getHours())}:${pad(nextDate.getMinutes())}:${pad(nextDate.getSeconds())}`;

      summary += `任务预计于 [${nextTimeStr}] 启动。`;
    } catch {
      summary += `任务将在 Cron [${cron}] 触发时启动。`;
    }
  }

  const actionDesc = formData.taskType < 2 ? "采集价格" : "同步数据";
  if (formData.taskType < 2) {
    if (uiState.isCycleMode) {
      summary += `启动后将以 [工作 ${duration}${durationUnit} / 休息 ${uiState.restValue}${{ m: "分钟", h: "小时", d: "天" }[uiState.restUnit]}] 的周期循环运行。`;
      summary += `运行期间每隔 ${intervalMin}-${intervalMax} ${intervalUnit} 进行一次${actionDesc}。`;
    } else {
      summary += `启动后将持续运行 ${duration} ${durationUnit}，期间每隔 ${intervalMin}-${intervalMax} ${intervalUnit} 进行一次${actionDesc}。`;
    }
  } else {
    summary += `启动后将执行一次${actionDesc}。`;
  }

  return summary;
});

// --- 表单数据 ---
const dialogVisible = ref(false);
const systemDialogVisible = ref(false);
const dialogTitle = ref("新增任务");
const submitLoading = ref(false);
const formRef = ref();
const systemFormRef = ref();
const formData = reactive<any>({
  id: undefined,
  goodsId: undefined as unknown as number,
  maxPrice: 0,
  minPaintwear: 0,
  maxPaintwear: 1,
  buyCount: 1,
  cronExpression: "",
  durationMinutes: 0,
  restPeriod: 0,
  scanInterval: 15,
  scanIntervalMin: 15,
  scanIntervalMax: 20,
  taskType: 0,
  minProfit: 0,
  accountIds: [],
  runMode: "SCAN",
  targetTaskId: undefined,
});

// 账号过滤逻辑
const filteredAccounts = computed(() => {
  if (!formData.runMode) return accounts.value;
  return accounts.value.filter((account) => {
    if (formData.runMode === "TRADE") return account.role === "TRADE" || account.role === "BOTH";
    if (formData.runMode === "SCAN") return account.role === "SCAN" || account.role === "BOTH";
    return true; // BOTH 模式或默认
  });
});

const rules = computed(() => ({
  accountIds: [{ required: true, message: "请选择执行账号", type: "error", trigger: "change" }],
  targetTaskId: [
    {
      required: false,
      type: "error",
      trigger: "submit",
    },
  ],
  goodsId: [
    {
      validator: (val: any) => {
        if (formData.taskType < 2) return !!val;
        return true;
      },
      message: "请选择商品",
      type: "error",
      trigger: "submit",
    },
  ],
  maxPrice: [
    {
      validator: (val: any) => {
        if (formData.taskType === 0 && formData.runMode !== "TRADE") return !!val;
        return true;
      },
      message: "请输入最高价格",
      type: "error",
      trigger: "submit",
    },
  ],
  minProfit: [
    {
      validator: (val: any) => {
        if (formData.taskType === 1 && formData.runMode !== "TRADE")
          return val !== undefined && val !== null;
        return true;
      },
      message: "请输入最小预期利润",
      type: "error",
      trigger: "submit",
    },
  ],
  buyCount: [
    {
      validator: (val: any) => {
        if (formData.taskType < 2 && formData.runMode !== "SCAN" && formData.runMode !== "TRADE")
          return !!val;
        return true;
      },
      message: "请输入购买数量",
      type: "error",
      trigger: "submit",
    },
  ],
  scanInterval: [
    {
      validator: (val: any) => {
        if (formData.taskType >= 2 || formData.runMode === "TRADE") return true;
        return !!val;
      },
      message: "请输入扫描间隔",
      type: "error",
      trigger: "submit",
    },
    {
      validator: (val: any) => {
        if (formData.taskType >= 2 || formData.runMode === "TRADE") return true;
        return val >= 15;
      },
      message: "扫描间隔不得低于 15 秒",
      type: "error",
      trigger: "submit",
    },
  ],
}));

// 监听商品变更，清空关联的下单任务
watch(
  () => formData.goodsId,
  () => {
    formData.targetTaskId = undefined;
    tradeTasks.value = [];
  }
);

// 监听运行模式切换
watch(
  () => formData.runMode,
  (newVal) => {
    nextTick(() => {
      formRef.value?.clearValidate();
    });
    const filteredIds = formData.accountIds?.filter((id: number) => {
      const account = accounts.value.find((a) => a.id === id);
      if (!account) return false;
      if (newVal === "TRADE") return account.role === "TRADE" || account.role === "BOTH";
      if (newVal === "SCAN") return account.role === "SCAN" || account.role === "BOTH";
      return true;
    });
    if (filteredIds?.length !== formData.accountIds?.length) {
      formData.accountIds = filteredIds;
    }
  }
);

// --- 商品搜索 ---
const goodsLoading = ref(false);
const goodsOptions = ref<GoodsSimple[]>([]);

const remoteSearchGoods = async (keyword: string) => {
  if (!keyword) return;
  goodsLoading.value = true;
  try {
    const res = await goodsApi.getSimpleList(keyword);
    goodsOptions.value = res;
  } finally {
    goodsLoading.value = false;
  }
};

// --- 方法 ---
const handleIntervalUnitChange = (unit: SelectValue) => {
  const unitStr = unit as string;
  const min = formData.taskType < 2 && unitStr === "s" ? 15 : 1;
  if (uiState.intervalMinValue < min) {
    uiState.intervalMinValue = min;
  }
  if (uiState.intervalMaxValue < min) {
    uiState.intervalMaxValue = min;
  }
};

const handleIntervalMinBlur = () => {
  const min = formData.taskType < 2 && uiState.intervalUnit === "s" ? 15 : 1;
  if (uiState.intervalMinValue < min) {
    uiState.intervalMinValue = min;
  }
  if (uiState.intervalMinValue > uiState.intervalMaxValue) {
    uiState.intervalMaxValue = uiState.intervalMinValue;
  }
};

const handleIntervalMaxBlur = () => {
  const min = formData.taskType < 2 && uiState.intervalUnit === "s" ? 15 : 1;
  if (uiState.intervalMaxValue < min) {
    uiState.intervalMaxValue = min;
  }
  if (uiState.intervalMaxValue < uiState.intervalMinValue) {
    uiState.intervalMinValue = uiState.intervalMaxValue;
  }
};

watch(
  [
    () => uiState.intervalMinValue,
    () => uiState.intervalMaxValue,
    () => uiState.intervalUnit,
    () => uiState.durationValue,
    () => uiState.durationUnit,
  ],
  () => {
    if (formData.taskType < 2) {
      formData.scanIntervalMin = uiState.intervalMinValue * INTERVAL_FACTORS[uiState.intervalUnit];
      formData.scanIntervalMax = uiState.intervalMaxValue * INTERVAL_FACTORS[uiState.intervalUnit];
      formData.durationMinutes = uiState.durationValue * DURATION_FACTORS[uiState.durationUnit];
      formData.restPeriod = uiState.isCycleMode
        ? uiState.restValue * DURATION_FACTORS[uiState.restUnit]
        : 0;
    }
  },
  { immediate: true }
);

const resetForm = () => {
  Object.assign(formData, {
    id: undefined,
    goodsId: undefined,
    maxPrice: 0,
    minPaintwear: 0,
    maxPaintwear: 1,
    buyCount: 1,
    cronExpression: "",
    durationMinutes: 0,
    restPeriod: 0,
    scanInterval: 15,
    scanIntervalMin: 15,
    scanIntervalMax: 20,
    taskType: 0,
    minProfit: 0,
    accountIds: [],
    runMode: "BOTH",
    targetTaskId: undefined,
  });
  uiState.durationValue = 0;
  uiState.isDurationUnlimited = true;
  uiState.isCronImmediate = true;
  uiState.isCycleMode = false;
  uiState.intervalMinValue = 15;
  uiState.intervalMaxValue = 20;
  uiState.intervalUnit = "s";
  goodsOptions.value = [];
};

const handleAdd = (defaultMode: string = "SCAN") => {
  resetForm();
  formData.runMode = defaultMode;
  dialogTitle.value = "新增任务";
  dialogVisible.value = true;
  nextTick(() => {
    formRef.value?.clearValidate();
  });
  fetchAccounts();
};

const handleAddSystem = () => {
  resetForm();
  formData.taskType = 2;
  dialogTitle.value = "新增系统任务";
  systemDialogVisible.value = true;
  nextTick(() => {
    systemFormRef.value?.clearValidate();
  });
  fetchAccounts();
};

const openWithGoods = (goods: GoodsSimple) => {
  handleAdd("SCAN");
  formData.goodsId = goods.goodsId;
  goodsOptions.value = [goods];
  dialogTitle.value = `新增扫货任务 - ${goods.name}`;
};

const handleEdit = (row: BuffScanTask) => {
  resetForm();
  Object.assign(formData, row);

  // 初始化 UI 状态
  const duration = convertToUi(row.durationMinutes || 0, DURATION_FACTORS);
  uiState.durationValue = duration.value;
  uiState.durationUnit = duration.unit as any;
  uiState.isDurationUnlimited = !row.durationMinutes;

  const rest = convertToUi(row.restPeriod || 0, DURATION_FACTORS);
  uiState.restValue = rest.value;
  uiState.restUnit = rest.unit as any;
  uiState.isCycleMode = !!row.restPeriod;

  const intervalMin = convertToUi(row.scanIntervalMin || 15, INTERVAL_FACTORS);
  const intervalMax = convertToUi(row.scanIntervalMax || 20, INTERVAL_FACTORS);

  // 统一单位：取两者中较小的单位 (精度更高)，防止如 min=30s, max=60s(1m) 时显示为 30-1
  const unitOrder = ["s", "m", "h", "d"];
  const minIdx = unitOrder.indexOf(intervalMin.unit);
  const maxIdx = unitOrder.indexOf(intervalMax.unit);

  const finalUnit = minIdx <= maxIdx ? intervalMin.unit : intervalMax.unit;
  const factor = INTERVAL_FACTORS[finalUnit as keyof typeof INTERVAL_FACTORS];

  uiState.intervalUnit = finalUnit as any;
  uiState.intervalMinValue = (row.scanIntervalMin || 15) / factor;
  uiState.intervalMaxValue = (row.scanIntervalMax || 20) / factor;

  uiState.isCronImmediate = !row.cronExpression || row.cronExpression === "* * * * * ?";

  if (row.goodsId) {
    goodsOptions.value = [{ goodsId: row.goodsId, name: row.name } as any];
  }

  // 如果有关联任务，手动初始化选项，防止被 goodsId 的 watch 清空
  if (row.targetTaskId) {
    // 临时禁用 watch 效果或在 nextTick 中恢复
    nextTick(async () => {
      formData.targetTaskId = row.targetTaskId;
      // 尝试获取关联任务详情以回显名称 (如果有 targetTaskName 字段最好，没有则尝试搜索)
      if ((row as any).targetTaskName) {
        tradeTasks.value = [{ id: row.targetTaskId, name: (row as any).targetTaskName } as any];
      } else {
        await fetchTradeTasks();
      }
    });
  }

  dialogTitle.value = "编辑任务";
  if (row.taskType >= 2) {
    systemDialogVisible.value = true;
    nextTick(() => {
      systemFormRef.value?.clearValidate();
    });
  } else {
    dialogVisible.value = true;
    nextTick(() => {
      formRef.value?.clearValidate();
    });
  }
  fetchAccounts();
};

const handleSubmit = async ({ validateResult, firstError }: any) => {
  if (validateResult !== true) {
    MessagePlugin.warning(firstError || "表单校验未通过");
    return;
  }

  submitLoading.value = true;
  try {
    // 构造纯净的提交数据，过滤掉冗余字段 (如 goodsIconUrl, status 等)
    const data: any = {
      id: formData.id,
      runMode: formData.runMode,
      goodsId: formData.goodsId,
      maxPrice: formData.maxPrice,
      minPaintwear: formData.minPaintwear,
      maxPaintwear: formData.maxPaintwear,
      buyCount: formData.buyCount,
      durationMinutes: formData.durationMinutes,
      restPeriod: formData.restPeriod,
      scanInterval: formData.scanInterval,
      scanIntervalMin: formData.scanIntervalMin,
      scanIntervalMax: formData.scanIntervalMax,
      taskType: formData.taskType,
      minProfit: formData.minProfit,
      targetTaskId: formData.targetTaskId,
      accountIds: formData.accountIds,
      cronExpression: formData.cronExpression,
    };

    if (uiState.isCronImmediate) {
      data.cronExpression = "* * * * * ?";
    }
    if (uiState.isDurationUnlimited && !uiState.isCycleMode) {
      data.durationMinutes = 0;
    }

    if (data.id) {
      await taskApi.update(data);
      MessagePlugin.success("更新成功");
    } else {
      await taskApi.add(data);
      MessagePlugin.success("新增成功");
    }
    dialogVisible.value = false;
    systemDialogVisible.value = false;
    emit("success");
  } catch (error) {
    console.error(error);
  } finally {
    submitLoading.value = false;
  }
};

defineExpose({
  handleAdd,
  handleAddSystem,
  handleEdit,
  openWithGoods,
});
</script>

<style scoped>
/* 调整弹窗内边距，使其更紧凑 */
:deep(.task-edit-dialog .t-dialog__body) {
  padding: 4px 12px 8px;
  max-height: 80vh;
  overflow-y: auto;
}

:deep(.task-edit-dialog .t-dialog__header) {
  padding: 12px 12px 4px;
}

:deep(.task-edit-dialog .t-dialog) {
  max-height: 90vh;
}

/* 进一步压缩表单项间距 */
:deep(.compact-form .t-form__item) {
  margin-bottom: 12px; /* 适度增加间距，为错误提示预留空间 */
}

/* 当存在校验错误时，确保有足够间距且高度由内容撑开 */
:deep(.compact-form .t-form__item.t-is-error) {
  margin-bottom: 22px;
}

/* 压缩提示文本间距并确保不遮挡 */
:deep(.compact-form .t-form__item .t-input__tips) {
  margin-top: 4px;
  margin-bottom: 2px;
  line-height: 1.4;
  position: relative !important;
  display: block !important;
  min-height: auto !important;
}

/* 错误信息样式优化：确保在 tips 下方且有清晰间距 */
:deep(.compact-form .t-form__item .t-form__verify-message) {
  margin-top: 4px;
  margin-bottom: 2px;
  line-height: 1.4;
  position: relative !important;
  display: block !important;
  min-height: auto !important;
  font-size: 12px;
}

/* 如果 verify-message 紧跟在 tips 后面，增加额外间距 */
:deep(.compact-form .t-form__item .t-input__tips + .t-form__verify-message) {
  margin-top: 4px;
}

/* 针对 schedule-group 内部的 form-item 特殊处理 */
.schedule-group :deep(.t-form__item) {
  margin-bottom: 8px; /* 从 8px 增加到 12px */
}

/* 移除末尾元素的边距压缩，防止报错信息溢出背景框 */
.schedule-group :deep(.t-form__item:last-child) {
  margin-bottom: 8px;
}
</style>
