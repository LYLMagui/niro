import { computed, reactive, ref } from "vue";
import { MessagePlugin, type FormRule, type FormRules } from "tdesign-vue-next";
import { taskApi } from "@/api/task";
import { PlatformEnum } from "@/enums/PlatformEnum";

export interface FormData {
  id?: number;
  goodsId: number | undefined;
  maxPrice: number;
  minPaintwear: number;
  maxPaintwear: number;
  buyCount: number;
  cronExpression: string;
  durationMinutes: number;
  restPeriod: number;
  scanInterval: number | undefined;
  scanIntervalMin: number;
  scanIntervalMax: number;
  taskType: number;
  minProfit: number;
  accountIds: number[];
  runMode: "SCAN" | "TRADE" | "BOTH";
  targetTaskId: number | undefined;
  platform: string;
  extraConfig?: string;
  name?: string;
}

interface SubmitContext {
  validateResult: true | unknown;
  firstError?: string;
}

interface C5Config {
  safeMargin: number;
  anchorTierIndex: number;
}

export function useTaskForm(emit: (event: "success") => void) {
  const formData = reactive<FormData>({
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
    runMode: "SCAN",
    targetTaskId: undefined,
    platform: PlatformEnum.BUFF,
  });

  const c5Config = reactive<C5Config>({
    safeMargin: 3,
    anchorTierIndex: 2,
  });

  const submitLoading = ref(false);

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
      platform: PlatformEnum.BUFF,
    });
    c5Config.safeMargin = 3;
    c5Config.anchorTierIndex = 2;
  };

  const handleSubmit = async (context: SubmitContext) => {
    const { validateResult, firstError } = context;
    if (validateResult !== true) {
      MessagePlugin.warning(firstError || "表单校验未通过");
      return false;
    }

    submitLoading.value = true;
    try {
      const data = {
        ...formData,
        targetTaskId: undefined,
        cronExpression: "",
        durationMinutes: 0,
        restPeriod: 0,
        safetyMargin: c5Config.safeMargin / 100,
        ladderStep: c5Config.anchorTierIndex,
        scanIntervalMin:
          formData.platform === PlatformEnum.C5
            ? Math.max(formData.scanIntervalMin || 1, 1)
            : Math.max(formData.scanIntervalMin || 15, 15),
        scanIntervalMax:
          formData.platform === PlatformEnum.C5
            ? Math.max(formData.scanIntervalMax || formData.scanIntervalMin || 1, 1)
            : Math.max(formData.scanIntervalMax || formData.scanIntervalMin || 20, 15),
      };

      data.scanInterval =
        data.scanIntervalMin === data.scanIntervalMax ? data.scanIntervalMin : undefined;

      if (formData.platform === PlatformEnum.C5) {
        data.extraConfig = JSON.stringify({
          safeMargin: c5Config.safeMargin / 100,
          anchorTierIndex: c5Config.anchorTierIndex,
        });
      }

      if (data.id) {
        await taskApi.update(data);
        MessagePlugin.success("更新成功");
      } else {
        await taskApi.add(data);
        MessagePlugin.success("新增成功");
      }
      emit("success");
      return true;
    } catch (error) {
      console.error(error);
      return false;
    } finally {
      submitLoading.value = false;
    }
  };

  const rules = computed<FormRules<FormData>>(() => ({
    accountIds: [
      {
        required: false,
        message: "请选择执行账号",
        type: "error",
        trigger: "change",
      } satisfies FormRule,
    ],
    goodsId: [
      {
        validator: (val: number | undefined) => !!val,
        message: "请选择商品",
        type: "error",
        trigger: "submit",
      } satisfies FormRule,
    ],
    maxPrice: [
      {
        validator: (val: number) => {
          if (formData.taskType === 0 && formData.runMode !== "TRADE") {
            return !!val;
          }
          return true;
        },
        message: "请输入最高价格",
        type: "error",
        trigger: "submit",
      } satisfies FormRule,
    ],
    minProfit: [
      {
        validator: (val: number) => {
          if (formData.taskType === 1 && formData.runMode !== "TRADE") {
            return val !== undefined && val !== null;
          }
          return true;
        },
        message: "请输入最小预期利润",
        type: "error",
        trigger: "submit",
      } satisfies FormRule,
    ],
    buyCount: [
      {
        validator: (val: number) => {
          if (formData.taskType < 2 && formData.runMode !== "SCAN") {
            return !!val;
          }
          return true;
        },
        message: "请输入购买数量",
        type: "error",
        trigger: "submit",
      } satisfies FormRule,
    ],
  }));

  return {
    formData,
    c5Config,
    submitLoading,
    rules,
    resetForm,
    handleSubmit,
  };
}
