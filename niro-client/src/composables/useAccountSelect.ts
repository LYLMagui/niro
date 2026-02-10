import { computed, ref } from "vue";
import { settingsApi, type BuffAccount } from "@/api/settings";
import { taskApi } from "@/api/task";
import type { BuffScanTask } from "@/types/task";
import { PlatformEnum } from "@/enums/PlatformEnum";

/**
 * 账号获取 + 按平台/模式角色过滤 + 关联任务管理
 */
export function useAccountSelect(formData: { platform: string; runMode: string; id?: number }) {
    const accounts = ref<BuffAccount[]>([]);
    const accountsLoading = ref(false);

    const tradeTasks = ref<BuffScanTask[]>([]);
    const tradeTasksLoading = ref(false);

    const fetchAccounts = async () => {
        accountsLoading.value = true;
        try {
            accounts.value = await settingsApi.getBuffAccounts();
        } finally {
            accountsLoading.value = false;
        }
    };

    const fetchTradeTasks = async (goodsId?: number) => {
        tradeTasksLoading.value = true;
        try {
            tradeTasks.value = await taskApi.getTradeTasks(goodsId);
        } finally {
            tradeTasksLoading.value = false;
        }
    };

    /** 按平台 + 运行模式角色过滤可用账号 */
    const filteredAccounts = computed(() => {
        let list = accounts.value;

        if (formData.platform) {
            list = list.filter((a) => (a.platform || PlatformEnum.BUFF) === formData.platform);
        }
        if (!formData.runMode) return list;

        return list.filter((account) => {
            if (formData.runMode === "TRADE") return account.role === "TRADE" || account.role === "BOTH";
            if (formData.runMode === "SCAN") return account.role === "SCAN" || account.role === "BOTH";
            return true;
        });
    });

    return {
        accounts,
        accountsLoading,
        fetchAccounts,
        tradeTasks,
        tradeTasksLoading,
        fetchTradeTasks,
        filteredAccounts,
    };
}
