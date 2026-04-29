import request from "@/utils/request";
import type {
  AppKeyPublicKeyResult,
  AppKeyRevealParam,
  AppKeyRevealResult,
  C5SnipingAccountListResult,
  C5SnipingAccountRefreshBalanceParam,
  C5SnipingAccountRefreshBalanceResult,
  C5SnipingAccountSaveParam,
} from "@/types/c5-sniping-account";

const baseUrl = "/api/c5/sniping/v2/accounts";

export const c5SnipingAccountApi = {
  getAccounts() {
    return request.get<C5SnipingAccountListResult>(baseUrl);
  },

  saveAccount(data: C5SnipingAccountSaveParam) {
    return request.post<unknown>(baseUrl, data);
  },

  refreshBalance(data: C5SnipingAccountRefreshBalanceParam) {
    return request.post<C5SnipingAccountRefreshBalanceResult[]>(`${baseUrl}/refresh-balance`, data);
  },

  getAppKeyPublicKey() {
    return request.get<AppKeyPublicKeyResult>(`${baseUrl}/app-key/public-key`);
  },

  revealAppKey(id: number, data: AppKeyRevealParam) {
    return request.post<AppKeyRevealResult>(`${baseUrl}/${id}/app-key/reveal`, data);
  },

  deleteAccount(id: number) {
    return request.delete<unknown>(`${baseUrl}/${id}`);
  },
};
