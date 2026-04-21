import { computed, reactive, ref } from "vue";
import { MessagePlugin } from "tdesign-vue-next";
import { taskApi } from "@/api/task";
import { PlatformEnum } from "@/enums/PlatformEnum";
import type { TaskSaveParam } from "@/types/task";

export interface FormData {
  id?: number;
  cs2GoodsId: number | undefined;
  maxPrice: number;
  minPaintwear: number;
  maxPaintwear: number;
  buyCount: number;
  cronExpression: string;
  durationMinutes: number;
  restPeriod: number;
  scanInterval: number;
  scanIntervalMin: number;
  scanIntervalMax: number;
  taskType: number;
  runMode: "SCAN" | "TRADE" | "BOTH";
  platform: string;
}

export function useTaskForm(emit: (event: "success") => void) {
  const formData = reactive<FormData>({
    id: undefined,
    cs2GoodsId: undefined,
    maxPrice: 0,
    minPaintwear: 0,
    maxPaintwear: 1,
    buyCount: 1,
    cronExpression: "",
    durationMinutes: 0,
    restPeriod: 0,
    scanInterval: 1,
    scanIntervalMin: 1,
    scanIntervalMax: 1,
    taskType: 0,
    runMode: "BOTH",
    platform: PlatformEnum.C5,
  });

  const submitLoading = ref(false);

  const resetForm = () => {
    Object.assign(formData, {
      id: undefined,
      cs2GoodsId: undefined,
      maxPrice: 0,
      minPaintwear: 0,
      maxPaintwear: 1,
      buyCount: 1,
      cronExpression: "",
      durationMinutes: 0,
      restPeriod: 0,
      scanInterval: 1,
      scanIntervalMin: 1,
      scanIntervalMax: 1,
      taskType: 0,
      runMode: "BOTH",
      platform: PlatformEnum.C5,
    });
  };

  const handleSubmit = async (context: any, uiState: any) => {
    const { validateResult, firstError } = context;
    if (validateResult !== true) {
      MessagePlugin.warning(firstError || "表单校验未通过");
      return false;
    }

    submitLoading.value = true;
    try {
      const data: TaskSaveParam = {
        id: formData.id,
        cs2GoodsId: formData.cs2GoodsId,
        maxPrice: formData.maxPrice,
        minPaintwear: formData.minPaintwear,
        maxPaintwear: formData.maxPaintwear,
        buyCount: formData.buyCount,
        cronExpression: formData.cronExpression,
        durationMinutes: formData.durationMinutes,
        restPeriod: formData.restPeriod,
        scanInterval: formData.scanInterval,
        scanIntervalMin: formData.scanIntervalMin,
        scanIntervalMax: formData.scanIntervalMax,
        taskType: formData.taskType,
        runMode: formData.runMode,
        platform: formData.platform,
      };

      if (uiState.isCronImmediate) data.cronExpression = "* * * * * ?";
      if (uiState.isDurationUnlimited && !uiState.isCycleMode) data.durationMinutes = 0;

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

  const rules = computed(() => ({
    cs2GoodsId: [
      {
        validator: (val: number) => !!val,
        message: "请选择商品",
        type: "error",
        trigger: "submit",
      },
    ],
    maxPrice: [
      {
        validator: (val: number) => !!val,
        message: "请输入最高价格",
        type: "error",
        trigger: "submit",
      },
    ],
    buyCount: [
      {
        validator: (val: number) => !!val,
        message: "请输入购买数量",
        type: "error",
        trigger: "submit",
      },
    ],
    scanInterval: [
      {
        validator: (val: number) => !!val,
        message: "请输入扫描间隔",
        type: "error",
        trigger: "submit",
      },
      {
        validator: (val: number) => val >= 1,
        message: "最小扫描间隔不能低于1秒",
        type: "error",
        trigger: "submit",
      },
    ],
  }));

  return {
    formData,
    submitLoading,
    rules: rules as never,
    resetForm,
    handleSubmit,
  };
}
