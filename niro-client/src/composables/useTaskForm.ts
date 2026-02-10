import { computed, reactive, ref } from "vue";
import { MessagePlugin } from "tdesign-vue-next";
import { taskApi } from "@/api/task";
import { PlatformEnum } from "@/enums/PlatformEnum";
import { isSystemTask } from "@/enums/TaskTypeEnum";

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
    scanInterval: number;
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

export function useTaskForm(emit: (event: "success", ...args: any[]) => void) {
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
    };

    const handleSubmit = async (context: any, uiState: any, c5Config: any) => {
        const { validateResult, firstError } = context;
        if (validateResult !== true) {
            MessagePlugin.warning(firstError || "表单校验未通过");
            return false;
        }

        submitLoading.value = true;
        try {
            const data: any = {
                ...formData,
                safetyMargin: c5Config.safeMargin / 100,
                ladderStep: c5Config.anchorTierIndex,
            };

            if (formData.platform === PlatformEnum.C5) {
                data.extraConfig = JSON.stringify({
                    ...c5Config,
                    safeMargin: c5Config.safeMargin / 100,
                });
            }

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
        accountIds: [{ required: false, message: "请选择执行账号", type: "error", trigger: "change" }],
        targetTaskId: [{ required: false, type: "error", trigger: "submit" }],
        goodsId: [
            {
                validator: (val: number) => {
                    if (!isSystemTask(formData.taskType)) return !!val;
                    return true;
                },
                message: "请选择商品",
                type: "error",
                trigger: "submit",
            },
        ],
        maxPrice: [
            {
                validator: (val: number) => {
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
                validator: (val: number) => {
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
                validator: (val: number) => {
                    if (
                        formData.taskType < 2 &&
                        formData.runMode !== "SCAN" &&
                        formData.runMode !== "TRADE"
                    )
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
                validator: (val: number) => {
                    if (formData.taskType >= 2 || formData.runMode === "TRADE") return true;
                    return !!val;
                },
                message: "请输入扫描间隔",
                type: "error",
                trigger: "submit",
            },
            {
                validator: (val: number) => {
                    if (formData.taskType >= 2 || formData.runMode === "TRADE") return true;
                    if (formData.platform === PlatformEnum.C5) return val >= 1;
                    return val >= 15;
                },
                message: () =>
                    formData.platform === PlatformEnum.C5
                        ? "最小扫描间隔不能低于1秒"
                        : "最小扫描间隔不能低于15秒",
                type: "error",
                trigger: "submit",
            },
        ],
    }));

    return {
        formData,
        submitLoading,
        rules: rules as any,
        resetForm,
        handleSubmit,
    };
}
