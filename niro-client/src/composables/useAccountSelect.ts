import { computed, ref, type Ref } from "vue";
import { settingsApi, type BuffAccount } from "@/api/settings";
import { PlatformEnum } from "@/enums/PlatformEnum";

/**
 * 账号获取 + 按平台/模式角色过滤
 */
export function useAccountSelect(
  formData: { platform: string; runMode: string },
  options?: {
    canViewAccounts?: Ref<boolean>;
  }
) {
  const accounts = ref<BuffAccount[]>([]);
  const accountsLoading = ref(false);

  const fetchAccounts = async () => {
    if (options?.canViewAccounts && !options.canViewAccounts.value) {
      accounts.value = [];
      return;
    }
    accountsLoading.value = true;
    try {
      accounts.value = await settingsApi.getBuffAccounts();
    } finally {
      accountsLoading.value = false;
    }
  };

  const filteredAccounts = computed(() => {
    let list = accounts.value;

    if (formData.platform) {
      list = list.filter((item) => (item.platform || PlatformEnum.BUFF) === formData.platform);
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
    filteredAccounts,
  };
}
