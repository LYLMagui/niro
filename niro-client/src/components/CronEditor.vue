<!--
 * @author liyl
 * @date 2025-12-27
 * @description 基于 TDesign 的可视化 Cron 表达式编辑器 (仿 xxl-job 风格)
 -->
<template>
  <div class="cron-editor">
    <div class="editor-content">
      <t-tabs v-model="activeTab" theme="normal">
        <t-tab-panel value="second">
          <template #label>
            <div class="flex items-center gap-1.5">
              <t-icon name="time" size="14px" />
              秒
            </div>
          </template>
          <div class="tab-pane-content">
            <t-radio-group v-model="second.type" direction="vertical">
              <div :class="['radio-item-wrapper', { active: second.type === 'every' }]">
                <t-radio value="every">每秒钟</t-radio>
              </div>
              <div :class="['radio-item-wrapper', { active: second.type === 'specific' }]">
                <t-radio value="specific">
                  <span class="mr-1">指定</span>
                  <t-select
                    v-model="second.specific"
                    multiple
                    placeholder="请选择"
                    size="small"
                    class="w-48"
                    :options="secondOptions"
                  />
                </t-radio>
              </div>
              <div :class="['radio-item-wrapper', { active: second.type === 'range' }]">
                <t-radio value="range">
                  <span class="mr-1">周期从</span>
                  <t-input-number
                    v-model="second.rangeStart"
                    :min="0"
                    :max="(second.rangeEnd as number) - 1"
                    size="small"
                    class="mx-1"
                  />
                  <span class="mx-1">-</span>
                  <t-input-number
                    v-model="second.rangeEnd"
                    :min="(second.rangeStart as number) + 1"
                    :max="59"
                    size="small"
                    class="mx-1"
                  />
                  <span class="ml-1">秒</span>
                </t-radio>
              </div>
              <div :class="['radio-item-wrapper', { active: second.type === 'startAt' }]">
                <t-radio value="startAt">
                  <span class="mr-1">从</span>
                  <t-input-number
                    v-model="second.startAt"
                    :min="0"
                    :max="59"
                    size="small"
                    class="mx-1"
                  />
                  <span class="mx-1">秒开始，每</span>
                  <t-input-number
                    v-model="second.stepValue"
                    :min="1"
                    :max="59"
                    size="small"
                    class="mx-1"
                  />
                  <span class="ml-1">秒执行一次</span>
                </t-radio>
              </div>
            </t-radio-group>
          </div>
        </t-tab-panel>

        <t-tab-panel value="minute">
          <template #label>
            <div class="flex items-center gap-1.5">
              <t-icon name="precise-monitor" size="14px" />
              分
            </div>
          </template>
          <div class="tab-pane-content">
            <t-radio-group v-model="minute.type" direction="vertical">
              <div :class="['radio-item-wrapper', { active: minute.type === 'every' }]">
                <t-radio value="every">每分钟</t-radio>
              </div>
              <div :class="['radio-item-wrapper', { active: minute.type === 'specific' }]">
                <t-radio value="specific">
                  <span class="mr-1">指定</span>
                  <t-select
                    v-model="minute.specific"
                    multiple
                    placeholder="请选择"
                    size="small"
                    class="w-48"
                    :options="minuteOptions"
                  />
                </t-radio>
              </div>
              <div :class="['radio-item-wrapper', { active: minute.type === 'range' }]">
                <t-radio value="range">
                  <span class="mr-1">周期从</span>
                  <t-input-number
                    v-model="minute.rangeStart"
                    :min="0"
                    :max="(minute.rangeEnd as number) - 1"
                    size="small"
                    class="mx-1"
                  />
                  <span class="mx-1">-</span>
                  <t-input-number
                    v-model="minute.rangeEnd"
                    :min="(minute.rangeStart as number) + 1"
                    :max="59"
                    size="small"
                    class="mx-1"
                  />
                  <span class="ml-1">分</span>
                </t-radio>
              </div>
              <div :class="['radio-item-wrapper', { active: minute.type === 'startAt' }]">
                <t-radio value="startAt">
                  <span class="mr-1">从</span>
                  <t-input-number
                    v-model="minute.startAt"
                    :min="0"
                    :max="59"
                    size="small"
                    class="mx-1"
                  />
                  <span class="mx-1">分开始，每</span>
                  <t-input-number
                    v-model="minute.stepValue"
                    :min="1"
                    :max="59"
                    size="small"
                    class="mx-1"
                  />
                  <span class="ml-1">分执行一次</span>
                </t-radio>
              </div>
            </t-radio-group>
          </div>
        </t-tab-panel>

        <t-tab-panel value="hour">
          <template #label>
            <div class="flex items-center gap-1.5">
              <t-icon name="time-filled" size="14px" />
              时
            </div>
          </template>
          <div class="tab-pane-content">
            <t-radio-group v-model="hour.type" direction="vertical">
              <div :class="['radio-item-wrapper', { active: hour.type === 'every' }]">
                <t-radio value="every">每小时</t-radio>
              </div>
              <div :class="['radio-item-wrapper', { active: hour.type === 'specific' }]">
                <t-radio value="specific">
                  <span class="mr-1">指定</span>
                  <t-select
                    v-model="hour.specific"
                    multiple
                    placeholder="请选择"
                    size="small"
                    class="w-48"
                    :options="hourOptions"
                  />
                </t-radio>
              </div>
              <div :class="['radio-item-wrapper', { active: hour.type === 'range' }]">
                <t-radio value="range">
                  <span class="mr-1">周期从</span>
                  <t-input-number
                    v-model="hour.rangeStart"
                    :min="0"
                    :max="(hour.rangeEnd as number) - 1"
                    size="small"
                    class="mx-1"
                  />
                  <span class="mx-1">-</span>
                  <t-input-number
                    v-model="hour.rangeEnd"
                    :min="(hour.rangeStart as number) + 1"
                    :max="23"
                    size="small"
                    class="mx-1"
                  />
                  <span class="ml-1">时</span>
                </t-radio>
              </div>
              <div :class="['radio-item-wrapper', { active: hour.type === 'startAt' }]">
                <t-radio value="startAt">
                  <span class="mr-1">从</span>
                  <t-input-number
                    v-model="hour.startAt"
                    :min="0"
                    :max="23"
                    size="small"
                    class="mx-1"
                  />
                  <span class="mx-1">时开始，每</span>
                  <t-input-number
                    v-model="hour.stepValue"
                    :min="1"
                    :max="23"
                    size="small"
                    class="mx-1"
                  />
                  <span class="ml-1">小时执行一次</span>
                </t-radio>
              </div>
            </t-radio-group>
          </div>
        </t-tab-panel>

        <t-tab-panel value="day">
          <template #label>
            <div class="flex items-center gap-1.5">
              <t-icon name="calendar" size="14px" />
              日
            </div>
          </template>
          <div class="tab-pane-content">
            <t-radio-group v-model="day.type" direction="vertical">
              <div :class="['radio-item-wrapper', { active: day.type === 'every' }]">
                <t-radio value="every">每日</t-radio>
              </div>
              <div :class="['radio-item-wrapper', { active: day.type === 'specific' }]">
                <t-radio value="specific">
                  <span class="mr-1">指定</span>
                  <t-select
                    v-model="day.specific"
                    multiple
                    placeholder="请选择"
                    size="small"
                    class="w-48"
                    :options="dayOptions"
                  />
                </t-radio>
              </div>
              <div :class="['radio-item-wrapper', { active: day.type === 'range' }]">
                <t-radio value="range">
                  <span class="mr-1">周期从</span>
                  <t-input-number
                    v-model="day.rangeStart"
                    :min="1"
                    :max="(day.rangeEnd as number) - 1"
                    size="small"
                    class="mx-1"
                  />
                  <span class="mx-1">-</span>
                  <t-input-number
                    v-model="day.rangeEnd"
                    :min="(day.rangeStart as number) + 1"
                    :max="31"
                    size="small"
                    class="mx-1"
                  />
                  <span class="ml-1">日</span>
                </t-radio>
              </div>
              <div :class="['radio-item-wrapper', { active: day.type === 'startAt' }]">
                <t-radio value="startAt">
                  <span class="mr-1">从</span>
                  <t-input-number
                    v-model="day.startAt"
                    :min="1"
                    :max="31"
                    size="small"
                    class="mx-1"
                  />
                  <span class="mx-1">日开始，每</span>
                  <t-input-number
                    v-model="day.stepValue"
                    :min="1"
                    :max="31"
                    size="small"
                    class="mx-1"
                  />
                  <span class="ml-1">日执行一次</span>
                </t-radio>
              </div>
              <div :class="['radio-item-wrapper', { active: day.type === 'last' }]">
                <t-radio value="last">本月最后一天</t-radio>
              </div>
              <div :class="['radio-item-wrapper', { active: day.type === 'none' }]">
                <t-radio value="none">不指定</t-radio>
              </div>
            </t-radio-group>
          </div>
        </t-tab-panel>

        <t-tab-panel value="month">
          <template #label>
            <div class="flex items-center gap-1.5">
              <t-icon name="view-list" size="14px" />
              月
            </div>
          </template>
          <div class="tab-pane-content">
            <t-radio-group v-model="month.type" direction="vertical">
              <div :class="['radio-item-wrapper', { active: month.type === 'every' }]">
                <t-radio value="every">每月</t-radio>
              </div>
              <div :class="['radio-item-wrapper', { active: month.type === 'specific' }]">
                <t-radio value="specific">
                  <span class="mr-1">指定</span>
                  <t-select
                    v-model="month.specific"
                    multiple
                    placeholder="请选择"
                    size="small"
                    class="w-48"
                    :options="monthOptions"
                  />
                </t-radio>
              </div>
              <div :class="['radio-item-wrapper', { active: month.type === 'range' }]">
                <t-radio value="range">
                  <span class="mr-1">周期从</span>
                  <t-input-number
                    v-model="month.rangeStart"
                    :min="1"
                    :max="(month.rangeEnd as number) - 1"
                    size="small"
                    class="mx-1"
                  />
                  <span class="mx-1">-</span>
                  <t-input-number
                    v-model="month.rangeEnd"
                    :min="(month.rangeStart as number) + 1"
                    :max="12"
                    size="small"
                    class="mx-1"
                  />
                  <span class="ml-1">月</span>
                </t-radio>
              </div>
              <div :class="['radio-item-wrapper', { active: month.type === 'startAt' }]">
                <t-radio value="startAt">
                  <span class="mr-1">从</span>
                  <t-input-number
                    v-model="month.startAt"
                    :min="1"
                    :max="12"
                    size="small"
                    class="mx-1"
                  />
                  <span class="mx-1">月开始，每</span>
                  <t-input-number
                    v-model="month.stepValue"
                    :min="1"
                    :max="12"
                    size="small"
                    class="mx-1"
                  />
                  <span class="ml-1">月执行一次</span>
                </t-radio>
              </div>
            </t-radio-group>
          </div>
        </t-tab-panel>

        <t-tab-panel value="week">
          <template #label>
            <div class="flex items-center gap-1.5">
              <t-icon name="view-module" size="14px" />
              周
            </div>
          </template>
          <div class="tab-pane-content">
            <t-radio-group v-model="week.type" direction="vertical">
              <div :class="['radio-item-wrapper', { active: week.type === 'every' }]">
                <t-radio value="every">每周</t-radio>
              </div>
              <div :class="['radio-item-wrapper', { active: week.type === 'specific' }]">
                <t-radio value="specific">
                  <span class="mr-1">指定</span>
                  <t-select
                    v-model="week.specific"
                    multiple
                    placeholder="请选择"
                    size="small"
                    class="w-64"
                    :options="weekOptions"
                  />
                </t-radio>
              </div>
              <div :class="['radio-item-wrapper', { active: week.type === 'range' }]">
                <t-radio value="range">
                  <span class="mr-1">周期从</span>
                  <t-select v-model="week.rangeStart" size="small" class="mx-1 w-20">
                    <t-option
                      v-for="w in weekOptions"
                      :key="w.value"
                      :value="w.value"
                      :label="w.label"
                    />
                  </t-select>
                  <span class="mx-1">-</span>
                  <t-select v-model="week.rangeEnd" size="small" class="mx-1 w-20">
                    <t-option
                      v-for="w in weekOptions"
                      :key="w.value"
                      :value="w.value"
                      :label="w.label"
                    />
                  </t-select>
                </t-radio>
              </div>
              <div :class="['radio-item-wrapper', { active: week.type === 'startAt' }]">
                <t-radio value="startAt">
                  <span class="mr-1">从</span>
                  <t-select v-model="week.startAt" size="small" class="mx-1 w-20">
                    <t-option
                      v-for="w in weekOptions"
                      :key="w.value"
                      :value="w.value"
                      :label="w.label"
                    />
                  </t-select>
                  <span class="mx-1">开始，每</span>
                  <t-input-number
                    v-model="week.stepValue"
                    :min="1"
                    :max="4"
                    size="small"
                    class="mx-1"
                  />
                  <span class="ml-1">天执行一次</span>
                </t-radio>
              </div>
              <div :class="['radio-item-wrapper', { active: week.type === 'last' }]">
                <t-radio value="last">
                  <span class="mr-1">本月最后一个</span>
                  <t-select v-model="week.lastValue" size="small" class="mx-1 w-20">
                    <t-option
                      v-for="w in weekOptions"
                      :key="w.value"
                      :value="w.value"
                      :label="w.label"
                    />
                  </t-select>
                </t-radio>
              </div>
              <div :class="['radio-item-wrapper', { active: week.type === 'none' }]">
                <t-radio value="none">不指定</t-radio>
              </div>
            </t-radio-group>
          </div>
        </t-tab-panel>
      </t-tabs>

      <!-- 最近5次运行时间 -->
      <div class="preview-section mt-1 px-5 py-3">
        <div class="mb-2 flex items-center justify-between">
          <div class="flex items-center gap-2">
            <div class="h-4 w-1 rounded-full bg-blue-600"></div>
            <div class="text-sm font-bold text-gray-800">执行预览</div>
          </div>
          <t-link theme="primary" size="small" @click="calcNextExecutions">
            <template #prefix-icon><t-icon name="refresh" /></template>
            刷新预览
          </t-link>
        </div>

        <div
          class="cron-string-box mt-4 flex items-center justify-between rounded-lg px-4 py-3 shadow-sm"
        >
          <div class="flex flex-col">
            <div class="mb-1 flex items-center gap-2">
              <span class="text-[10px] font-bold tracking-widest text-blue-400 uppercase">
                Cron Expression
              </span>
              <t-tag
                v-if="!cronString || cronString === '* * * * * ?'"
                theme="success"
                variant="light"
                size="small"
              >
                立即启动
              </t-tag>
            </div>
            <div class="flex items-center gap-2">
              <span class="font-mono text-base font-bold tracking-tight text-blue-700">
                {{ !cronString || cronString === "* * * * * ?" ? "立即启动" : cronString }}
              </span>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <t-button
              variant="text"
              shape="square"
              size="small"
              :disabled="!cronString || cronString === '* * * * * ?'"
              class="hover:bg-blue-50"
              @click="handleCopy"
            >
              <t-icon
                name="copy"
                :class="
                  !cronString || cronString === '* * * * * ?' ? 'text-gray-300' : 'text-blue-500'
                "
              />
            </t-button>
            <t-button
              variant="outline"
              theme="default"
              size="small"
              class="reset-btn"
              @click="handleReset"
            >
              <t-icon name="refresh" />
              重置
            </t-button>
          </div>
        </div>

        <div class="execution-list-container rounded-lg border border-gray-100 bg-gray-50/50 p-2">
          <ul
            v-if="!isImmediate && nextExecutions.length"
            class="space-y-1 text-[12px] text-gray-600"
          >
            <li v-for="(time, index) in nextExecutions" :key="index" class="execution-item">
              <span class="font-mono">{{ time }}</span>
            </li>
          </ul>
          <div v-else class="py-2 text-center text-[12px] text-gray-400 italic">
            {{
              isImmediate
                ? "任务将立即启动，不设定时循环"
                : nextExecutionsError || '点击"刷新预览"获取未来运行时间点'
            }}
          </div>
        </div>
      </div>
    </div>

    <!-- 底部按钮 -->
    <div
      class="flex items-center justify-end gap-2 border-t border-gray-50 bg-gray-50/30 px-5 py-2"
    >
      <div class="flex-1"></div>
      <t-button variant="outline" theme="default" size="small" @click="handleCancel">取消</t-button>
      <t-button variant="base" theme="primary" size="small" @click="handleConfirm">
        保存配置
      </t-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { MessagePlugin } from "tdesign-vue-next";
import { computed, onMounted, reactive, ref, watch } from "vue";
import cronParser from "cron-parser";

const props = defineProps<{
  modelValue: string;
}>();

const emit = defineEmits(["update:modelValue", "change", "confirm", "cancel"]);

const activeTab = ref("second");
const isImmediate = ref(true); // 是否为立即启动
const nextExecutions = ref<string[]>([]);
const nextExecutionsError = ref("");
const openedTime = ref(new Date(0)); // 初始化为 0，确保第一次计算时会更新为当前时间

const secondOptions = Array.from({ length: 60 }, (_, i) => ({
  label: i < 10 ? `0${i}` : `${i}`,
  value: i,
}));
const minuteOptions = Array.from({ length: 60 }, (_, i) => ({
  label: i < 10 ? `0${i}` : `${i}`,
  value: i,
}));
const hourOptions = Array.from({ length: 24 }, (_, i) => ({
  label: i < 10 ? `0${i}` : `${i}`,
  value: i,
}));
const dayOptions = Array.from({ length: 31 }, (_, i) => ({ label: `${i + 1}`, value: i + 1 }));
const monthOptions = Array.from({ length: 12 }, (_, i) => ({ label: `${i + 1}月`, value: i + 1 }));
const weekOptions = [
  { label: "周日", value: "sun" },
  { label: "周一", value: "mon" },
  { label: "周二", value: "tue" },
  { label: "周三", value: "wed" },
  { label: "周四", value: "thu" },
  { label: "周五", value: "fri" },
  { label: "周六", value: "sat" },
];

interface CronState {
  type: string;
  startAt: string | number;
  rangeStart: string | number;
  rangeEnd: string | number;
  stepValue: number;
  specific: any[];
  lastValue: string | number;
}

const createInitialState = (unit: string): CronState => {
  const isWeek = unit === "week";
  const isTime = unit === "second" || unit === "minute" || unit === "hour";
  const isDayOrMonth = unit === "day" || unit === "month";

  return {
    type: isTime ? "every" : isWeek ? "none" : "every",
    startAt: isWeek ? "mon" : isDayOrMonth ? 1 : 0,
    rangeStart: isTime ? 0 : isWeek ? "mon" : 1,
    rangeEnd: isTime ? 1 : isWeek ? "wed" : 2,
    stepValue: 1,
    specific: [] as any[],
    lastValue: "sun",
  };
};

const second = reactive<CronState>(createInitialState("second"));
const minute = reactive<CronState>(createInitialState("minute"));
const hour = reactive<CronState>(createInitialState("hour"));
const day = reactive<CronState>(createInitialState("day"));
const month = reactive<CronState>(createInitialState("month"));
const week = reactive<CronState>(createInitialState("week"));

const formatUnit = (state: any, unit: string, noneSymbol = "*") => {
  const { type, startAt, rangeStart, rangeEnd, stepValue, specific, lastValue } = state;
  const isTime = ["second", "minute", "hour"].includes(unit);
  const isDayOrMonth = ["day", "month"].includes(unit);

  switch (type) {
    case "every":
      return "*";
    case "none":
      return noneSymbol;
    case "startAt":
      return `${startAt}/${stepValue}`;
    case "range":
      return `${rangeStart}-${rangeEnd}`;
    case "specific":
      if (specific.length)
        return [...specific]
          .sort((a, b) => {
            if (typeof a === "string" && typeof b === "string") return a.localeCompare(b);
            return (a as number) - (b as number);
          })
          .join(",");
      // 如果是特定点但没选，时间单位回退到 0，日期单位回退到 1，月回退到 1
      return isTime ? "0" : isDayOrMonth ? "1" : "*";
    case "last":
      return unit === "day" ? "last" : `last ${lastValue}`;
    default:
      return "*";
  }
};

// 专门用于计算各部分展示的 computed
const cronParts = computed(() => ({
  second: formatUnit(second, "second"),
  minute: formatUnit(minute, "minute"),
  hour: formatUnit(hour, "hour"),
  day: formatUnit(day, "day", "*"),
  month: formatUnit(month, "month"),
  week: formatUnit(week, "week", "*"),
}));

const cronString = computed(() => {
  const { second, minute, hour, day, month, week } = cronParts.value;

  // 判断是否为每秒执行 (* * * * * ?)
  const isEverySecond =
    second === "*" &&
    minute === "*" &&
    hour === "*" &&
    day === "*" &&
    month === "*" &&
    week === "?";

  if (isEverySecond || isImmediate.value) {
    return ""; // 立即启动，返回空字符串
  }

  // 按顺序组合
  const parts = [second, minute, hour, day, month, week];

  // 过滤空值并用空格连接
  return parts.filter((p) => p !== undefined && p !== "").join(" ");
});

const calcNextExecutions = () => {
  try {
    let expression = cronString.value.trim();
    if (!expression) return;

    executeParse(expression);
  } catch (error) {
    console.error("Cron 解析失败:", error);
    nextExecutions.value = [];
    const message = error instanceof Error ? error.message : String(error);
    nextExecutionsError.value = `表达式无效: ${message.split("\n")[0] || message}`;
  }
};

// 抽取实际解析逻辑，避免逻辑混乱
const executeParse = (expression: string) => {
  const safeExpression = expression
    .replace(/\?/g, "*")
    .replace(/last\s+(\w+)/g, "$1L")
    .replace(/last/g, "L");

  const options: { currentDate: Date; tz: string; hasSeconds?: boolean } = {
    currentDate: new Date(),
    tz: "Asia/Shanghai",
  };

  const parts = safeExpression.split(" ");
  if (parts.length >= 6) {
    options.hasSeconds = true;
  }

  const cp = cronParser as {
    parse: (
      _exp: string,
      _opts: { currentDate: Date; tz: string; hasSeconds?: boolean }
    ) => {
      next: () => { toDate?: () => Date; value?: { toDate?: () => Date } };
    };
  };
  const interval = cp.parse(safeExpression, options);

  const times: string[] = [];
  for (let i = 0; i < 5; i++) {
    try {
      const nextIteration = interval.next();
      const dateObj = nextIteration.toDate ? nextIteration : nextIteration.value || nextIteration;

      if (!dateObj || typeof dateObj.toDate !== "function") {
        break;
      }

      const date = dateObj.toDate();
      const pad = (n: number) => (n < 10 ? `0${n}` : n);
      times.push(
        `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
          `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
      );
    } catch {
      break;
    }
  }

  nextExecutions.value = times;
  nextExecutionsError.value = times.length ? "" : "无法计算后续执行时间";
};

// 监听各部分变化，如果用户手动修改了，则关闭“立即启动”标记
const ignoreWatch = ref(false);
watch(
  [second, minute, hour, day, month, week],
  () => {
    if (ignoreWatch.value) return;
    if (isImmediate.value) {
      isImmediate.value = false;
    }
  },
  { deep: true }
);

watch(
  cronString,
  (val) => {
    emit("update:modelValue", val.trim());
    emit("change", val.trim());
    calcNextExecutions();
  },
  { immediate: true }
);

// 监听各单位的范围变化，确保 rangeStart < rangeEnd
const units = [
  { ref: second, name: "second", min: 0, max: 59 },
  { ref: minute, name: "minute", min: 0, max: 59 },
  { ref: hour, name: "hour", min: 0, max: 23 },
  { ref: day, name: "day", min: 1, max: 31 },
  { ref: month, name: "month", min: 1, max: 12 },
];

units.forEach((unit) => {
  watch(
    () => unit.ref.rangeStart,
    (newStart) => {
      if (unit.ref.type === "range" && (newStart as number) >= (unit.ref.rangeEnd as number)) {
        unit.ref.rangeEnd = Math.min((newStart as number) + 1, unit.max);
      }
    }
  );
  watch(
    () => unit.ref.rangeEnd,
    (newEnd) => {
      if (unit.ref.type === "range" && (newEnd as number) <= (unit.ref.rangeStart as number)) {
        unit.ref.rangeStart = Math.max((newEnd as number) - 1, unit.min);
      }
    }
  );
});

// 周的范围特殊处理
watch([() => week.rangeStart, () => week.rangeEnd], ([start, end]) => {
  if (week.type !== "range") return;
  const weekValues = weekOptions.map((o) => o.value);
  const startIndex = weekValues.indexOf(start as string);
  const endIndex = weekValues.indexOf(end as string);
  if (startIndex >= endIndex) {
    if (startIndex < 6) {
      week.rangeEnd = weekValues[startIndex + 1];
    } else {
      week.rangeStart = weekValues[5];
      week.rangeEnd = weekValues[6];
    }
  }
});

// 初始化修正：day 和 week 至少有一个应该是 * (every) 才能让任务运行
onMounted(() => {
  openedTime.value = new Date();

  if (props.modelValue) {
    parseCron(props.modelValue);
  } else {
    // 只有在没有传入值（即新建）时，才应用“每日执行”的默认状态
    day.type = "every";
  }

  calcNextExecutions();
});

const handleReset = () => {
  ignoreWatch.value = true;
  isImmediate.value = true;
  Object.assign(second, createInitialState("second"));
  Object.assign(minute, createInitialState("minute"));
  Object.assign(hour, createInitialState("hour"));
  Object.assign(day, createInitialState("day"));
  Object.assign(month, createInitialState("month"));
  Object.assign(week, createInitialState("week"));

  // 重置后默认切换回第一个标签页
  activeTab.value = "second";
  MessagePlugin.success(`已重置为默认配置（立即启动）`);

  // 延迟恢复监听，确保同步的 Object.assign 触发的 watcher 被跳过
  setTimeout(() => {
    ignoreWatch.value = false;
  }, 0);
};

const handleCopy = () => {
  navigator.clipboard.writeText(cronString.value);
  MessagePlugin.success("复制成功");
};

const handleConfirm = () => {
  emit("update:modelValue", cronString.value);
  emit("confirm", cronString.value);
};

const handleCancel = () => {
  emit("cancel");
};

const parseCron = (cron: string) => {
  ignoreWatch.value = true;
  // 如果传入为空字符串，显式设置为每秒执行的 UI 状态
  if (!cron || cron === "* * * * * ?" || cron === "* * * * * *") {
    isImmediate.value = true;
    // 重置所有部分为默认的“每”状态，即 * * * * * ?
    Object.assign(second, createInitialState("second"));
    Object.assign(minute, createInitialState("minute"));
    Object.assign(hour, createInitialState("hour"));
    Object.assign(day, createInitialState("day"));
    Object.assign(month, createInitialState("month"));
    Object.assign(week, createInitialState("week"));

    setTimeout(() => {
      ignoreWatch.value = false;
    }, 0);
    return;
  }
  isImmediate.value = false;
  if (cron === cronString.value) {
    setTimeout(() => {
      ignoreWatch.value = false;
    }, 0);
    return;
  }
  const parts = cron.split(" ");
  if (parts.length < 6) {
    setTimeout(() => {
      ignoreWatch.value = false;
    }, 0);
    return;
  }

  const parsePart = (part: string, state: any, unit: string) => {
    const weekMap = ["sun", "mon", "tue", "wed", "thu", "fri", "sat", "sun"];
    const mapWeek = (val: string) => {
      if (!isNaN(Number(val))) {
        const n = Number(val);
        return weekMap[n] || val; // 兼容 0-7
      }
      return val.toLowerCase();
    };

    if (part === "*") {
      state.type = "every";
    } else if (part === "?" || part === "") {
      state.type = "none";
    } else if (part.includes("-")) {
      const parts = part.split("-");
      state.type = "range";
      if (unit === "week") {
        state.rangeStart = mapWeek(parts[0]);
        state.rangeEnd = mapWeek(parts[1]);
      } else {
        state.rangeStart = Number(parts[0]);
        state.rangeEnd = Number(parts[1]);
      }
    } else if (part.includes("/")) {
      const parts = part.split("/");
      state.type = "startAt";
      if (unit === "week") {
        state.startAt = mapWeek(parts[0]);
      } else {
        state.startAt = Number(parts[0]);
      }
      state.stepValue = Number(parts[1]);
    } else if (part.includes(",")) {
      state.type = "specific";
      state.specific = part.split(",").map((p) => {
        if (unit === "week") return mapWeek(p);
        return isNaN(Number(p)) ? p : Number(p);
      });
    } else if (part.startsWith("last") || part.endsWith("L")) {
      state.type = "last";
      if (part === "last" || part === "L") {
        state.lastValue = "sun"; // 默认值，仅对 week 有意义
      } else {
        const val = part.replace("last", "").replace("L", "").trim();
        if (unit === "week") {
          state.lastValue = mapWeek(val);
        } else {
          state.lastValue = Number(val);
        }
      }
    } else if (!isNaN(Number(part)) || (unit === "week" && /^[A-Za-z]{3}$/.test(part))) {
      state.type = "specific";
      if (unit === "week") {
        state.specific = [mapWeek(part)];
      } else {
        state.specific = [Number(part)];
      }
    }
  };

  parsePart(parts[0], second, "second");
  parsePart(parts[1], minute, "minute");
  parsePart(parts[2], hour, "hour");
  parsePart(parts[3], day, "day");
  parsePart(parts[4], month, "month");
  parsePart(parts[5], week, "week");

  setTimeout(() => {
    ignoreWatch.value = false;
  }, 0);
};

watch(
  () => props.modelValue,
  (val) => {
    if (val && val !== cronString.value) {
      parseCron(val);
    }
  },
  { immediate: true }
);
</script>

<style scoped>
.cron-editor {
  display: flex;
  flex-direction: column;
  width: 520px;
  max-height: 70vh; /* 进一步降低最大高度，确保在小屏幕下也不会溢出 */
  overflow: hidden;
  background: #fff;
  border-radius: 4px; /* 还原为较小的圆角，匹配 TDesign 默认风格 */
}

.editor-content {
  flex: 1;
  overflow-y: auto;
  scrollbar-width: thin;
}

.editor-content::-webkit-scrollbar {
  width: 5px;
}

.editor-content::-webkit-scrollbar-thumb {
  background-color: #e5e7eb;
  border-radius: 10px;
}

.tab-pane-content {
  height: 180px; /* 设置固定高度，防止切换 Tab 时高度抖动 */
  padding: 8px 12px;
  overflow-y: auto;
  background-color: #fcfcfc;
}

/* 优化单选框选项 */
.radio-item-wrapper {
  width: 100%;
  padding: 2px 8px; /* 极致压缩垂直内边距 */
  border: 1px solid transparent;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.radio-item-wrapper:hover {
  background-color: #f3f4f6;
}

.radio-item-wrapper.active {
  background-color: #f0f7ff;
  border-color: #e0eeff;
}

.cron-editor :deep(.t-radio) {
  display: flex;
  align-items: center;
  width: 100%;
}

.cron-editor :deep(.t-radio__label) {
  display: flex;
  align-items: center;
  margin-left: 8px;
  font-size: 13px;
  color: #374151;
  white-space: nowrap;
}

.cron-editor :deep(.t-select) {
  width: auto !important;
  min-width: 120px;
}

.cron-editor :deep(.t-radio-group) {
  display: flex;
  flex-direction: column;
  gap: 0px; /* 彻底移除间距，靠 wrapper 的 padding 控制 */
  width: 100%;
}

/* 预览区域样式 */
.preview-section {
  background-color: #fff;
  border-top: 1px solid #f3f4f6;
}

.cron-string-box {
  background: linear-gradient(to right, #eff6ff, #f8faff);
  border: 1px dashed #bfdbfe;
}

.execution-list-container {
  max-height: 80px; /* 进一步降低预览列表高度 */
  overflow-y: auto;
}

.execution-item {
  position: relative;
  padding-left: 16px;
  line-height: 1.4; /* 压缩行高 */
}

.execution-item::before {
  position: absolute;
  top: 50%;
  left: 4px;
  width: 4px;
  height: 4px;
  content: "";
  background-color: #3b82f6;
  border-radius: 50%;
  transform: translateY(-50%);
}

.cron-editor :deep(.t-tabs__nav-container) {
  background-color: #f9fafb;
  border-bottom: 1px solid #f3f4f6;
}

.cron-editor :deep(.t-tabs__item) {
  height: 36px; /* 极致压缩 Tab 高度 */
  font-weight: 500;
}

.cron-editor :deep(.t-tabs__item.t-is-active) {
  color: #0052d9;
}
</style>
