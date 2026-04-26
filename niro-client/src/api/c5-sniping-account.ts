import request from "@/utils/request";
import type {
  C5SnipingAccount,
  C5SnipingAccountRefreshBalanceParam,
  C5SnipingAccountRefreshBalanceResult,
  C5SnipingAccountSaveParam,
} from "@/types/c5-sniping-account";

const baseUrl = "/api/c5/sniping/v2/accounts";

export const c5SnipingAccountApi = {
  getAccounts() {
    return request.get<C5SnipingAccount[]>(baseUrl);
  },

  saveAccount(data: C5SnipingAccountSaveParam) {
    return request.post<unknown>(baseUrl, data);
  },

  refreshBalance(data: C5SnipingAccountRefreshBalanceParam) {
    return request.post<C5SnipingAccountRefreshBalanceResult[]>(`${baseUrl}/refresh-balance`, data);
  },

  deleteAccount(id: number) {
    return request.delete<unknown>(`${baseUrl}/${id}`);
  },
};
