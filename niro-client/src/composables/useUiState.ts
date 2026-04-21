import { computed, reactive, watch } from "vue";
import { PlatformEnum } from "@/enums/PlatformEnum";
import cronParser from "cron-parser";

/** 单位换算系数 (基准: 分钟) */
export const DURATION_FACTORS: Record<string, number> = { m: 1, h: 60, d: 1440 };

/** 单位换算系数 (基准: 秒) */
export const INTERVAL_FACTORS: Record<string, number> = { s: 1, m: 60, h: 3600, d: 86400 };

export interface UiState {
  durationValue: number;
  durationUnit: "m" | "h" | "d";
  intervalMinValue: number;
  intervalMaxValue: number;
  intervalUnit: "s" | "m" | "h" | "d";
  isCronImmediate: boolean;
  isDurationUnlimited: boolean;
  isCycleMode: boolean;
  restValue: number;
  restUnit: "m" | "h" | "d";
}

/** 将存储值转换为最合适的 UI 显示值和单位 */
export function convertToUi(value: number, factors: Record<string, number>) {
  if (!value) return { value: 0, unit: Object.keys(factors)[0] };
  const units = Object.keys(factors).reverse();
  for (const unit of units) {
    if (value % factors[unit] === 0) {
      return { value: value / factors[unit], unit };
    }
  }
  return { value, unit: Object.keys(factors)[0] };
}

/**
 * UI 状态管理：时间单位换算、间隔校验、执行摘要
 */
export function useUiState(formData: {
  taskType: number;
  cronExpression: string;
  platform: string;
  scanIntervalMin: number;
  scanIntervalMax: number;
  scanInterval: number | undefined;
  durationMinutes: number;
  restPeriod: number;
}) {
  const uiState = reactive<UiState>({
    durationValue: 0,
    durationUnit: "m",
    intervalMinValue: 15,
    intervalMaxValue: 20,
    intervalUnit: "s",
    isCronImmediate: true,
    isDurationUnlimited: true,
    isCycleMode: false,
    restValue: 5,
    restUnit: "m",
  });

  const getMinInterval = () => {
    if (formData.platform === PlatformEnum.C5) return 1;
    return formData.taskType < 2 && uiState.intervalUnit === "s" ? 15 : 1;
  };

  const handleIntervalUnitChange = () => {
    const min = getMinInterval();
    if (uiState.intervalMinValue < min) uiState.intervalMinValue = min;
    if (uiState.intervalMaxValue < min) uiState.intervalMaxValue = min;
  };

  const handleIntervalMinBlur = () => {
    const min = getMinInterval();
    if (uiState.intervalMinValue < min) uiState.intervalMinValue = min;
    if (uiState.intervalMinValue > uiState.intervalMaxValue) {
      uiState.intervalMaxValue = uiState.intervalMinValue;
    }
  };

  const handleIntervalMaxBlur = () => {
    const min = getMinInterval();
    if (uiState.intervalMaxValue < min) uiState.intervalMaxValue = min;
    if (uiState.intervalMaxValue < uiState.intervalMinValue) {
      uiState.intervalMinValue = uiState.intervalMaxValue;
    }
  };

  /** 将 UI 层的值和单位同步回 formData 的秒/分钟字段 */
  const syncFromUiState = () => {
    if (formData.taskType < 2) {
      formData.scanIntervalMin = uiState.intervalMinValue * INTERVAL_FACTORS[uiState.intervalUnit];
      formData.scanIntervalMax = uiState.intervalMaxValue * INTERVAL_FACTORS[uiState.intervalUnit];

      if (formData.scanIntervalMin === formData.scanIntervalMax) {
        formData.scanInterval = formData.scanIntervalMin;
      } else {
        formData.scanInterval = undefined;
      }

      formData.durationMinutes = uiState.durationValue * DURATION_FACTORS[uiState.durationUnit];
      formData.restPeriod = uiState.isCycleMode
        ? uiState.restValue * DURATION_FACTORS[uiState.restUnit]
        : 0;
    }
  };

  /** 自动同步 UI 状态到表单数据 */
  watch(
    [
      () => uiState.intervalMinValue,
      () => uiState.intervalMaxValue,
      () => uiState.intervalUnit,
      () => uiState.durationValue,
      () => uiState.durationUnit,
    ],
    () => syncFromUiState(),
    { immediate: true }
  );

  /** 执行摘要（自然语言描述调度逻辑） */
  const executionSummary = computed(() => {
    const cron = formData.cronExpression?.trim();
    const duration = uiState.durationValue;
    const durationUnit = { m: "分钟", h: "小时", d: "天" }[uiState.durationUnit] || "分钟";
    const intervalMin = uiState.intervalMinValue;
    const intervalMax = uiState.intervalMaxValue;
    const intervalUnit = { s: "秒", m: "分钟", h: "小时", d: "天" }[uiState.intervalUnit] || "秒";

    let summary = "";
    const isImmediate = !cron || cron === "* * * * * ?" || cron === "* * * * * *";

    if (isImmediate) {
      summary += "任务将立即启动。";
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
        if (safeExpression.split(" ").length >= 6) options.hasSeconds = true;

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
      const intervalDesc =
        intervalMin === intervalMax
          ? `${intervalMin} ${intervalUnit}`
          : `${intervalMin}-${intervalMax} ${intervalUnit}`;

      if (uiState.isCycleMode) {
        const restUnit = { m: "分钟", h: "小时", d: "天" }[uiState.restUnit];
        summary += `启动后将以 [工作 ${duration}${durationUnit} / 休息 ${uiState.restValue}${restUnit}] 的周期循环运行。`;
        summary += `运行期间每隔 ${intervalDesc} 进行一次${actionDesc}。`;
      } else {
        summary += `启动后将持续运行 ${duration} ${durationUnit}，期间每隔 ${intervalDesc} 进行一次${actionDesc}。`;
      }
    } else {
      summary += `启动后将执行一次${actionDesc}。`;
    }

    return summary;
  });

  /**
   * 自动清除换行符和首尾空格
   */
  const handleInputTrim = (
    val: string | InputEvent,
    target: Record<string, string>,
    key: string
  ) => {
    if (typeof val === "string") {
      target[key] = val.replace(/[\r\n]/g, "").trim();
    }
  };

  return {
    uiState,
    getMinInterval,
    handleIntervalUnitChange,
    handleIntervalMinBlur,
    handleIntervalMaxBlur,
    syncFromUiState,
    executionSummary,
    handleInputTrim,
    convertToUi,
    DURATION_FACTORS,
    INTERVAL_FACTORS,
  };
}
