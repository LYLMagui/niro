import request from "@/utils/request";

export interface UserPlatformSettings {
  id?: number;
  userId?: number;
  paymentMethod: "BALANCE" | "ALIPAY" | "WECHAT";
  encryptedC5AppKey?: string;
  c5AppKeyMasked?: string;
  hasC5AppKey?: boolean;
  steamTradeUrl?: string;
  wecomCorpid?: string;
  wecomCorpsecret?: string;
  wecomAgentid?: string;
  wecomTouser?: string;
  emailEnabled?: boolean;
  emailHost?: string;
  emailPort?: number;
  emailAccount?: string;
  emailPassword?: string;
  emailReceiver?: string;
}

export interface AppKeyPublicKeyResult {
  algorithm: "RSA-OAEP-256";
  publicKey: string;
}

export type BuffAccountRole = "SCAN" | "TRADE" | "BOTH";
export type BuffAccountStatus =
  | "NORMAL"
  | "BANNED"
  | "MARKET_RESTRICTED"
  | "TRADE_RESTRICTED"
  | "INVALID"
  | "COOLING_DOWN"
  | "SCANNING";

export interface BuffAccount {
  id?: number;
  userId?: number;
  accountName: string;
  platform?: string; // BUFF, C5
  buffCookie: string;
  role: BuffAccountRole;
  status: BuffAccountStatus;
  weight: number;
  balance: number;
  pendingBalance?: number;
  failCount: number;
  lastCheckTime?: string;
  userAgent?: string;
  remark?: string;
  warningMsg?: string;
  todayScanCount?: number;
  tradeSuccessCount?: number;
  tradeTotalCount?: number;
  tradeSuccessRate?: number;
  checking?: boolean;
  boundTaskId?: number;
  boundTaskName?: string;
  createTime?: string;
  updateTime?: string;
}

export const settingsApi = {
  // 获取配置
  getSettings() {
    return request.get<UserPlatformSettings>("/settings");
  },

  getAppKeyPublicKey() {
    return request.get<AppKeyPublicKeyResult>("/settings/app-key/public-key");
  },

  // 保存配置
  saveSettings(data: UserPlatformSettings) {
    return request.post<unknown>("/settings", data);
  },

  // 发送测试通知
  sendTestNotify() {
    return request.post<unknown>("/settings/test-notify");
  },

  // 获取BUFF账号列表
  getBuffAccounts() {
    return request.get<BuffAccount[]>("/buff/account/list");
  },

  // 保存或更新BUFF账号
  saveBuffAccount(data: BuffAccount) {
    return request.post<unknown>("/buff/account/save", data);
  },

  // 删除BUFF账号
  deleteBuffAccount(id: number) {
    return request.delete<unknown>(`/buff/account/${id}`);
  },

  // 检测单个账号Cookie
  checkBuffAccount(id: number) {
    return request.post<unknown>(`/buff/account/check/${id}`);
  },

  // 一键检测所有账号Cookie
  checkAllBuffAccounts() {
    return request.post<unknown>("/buff/account/check/all");
  },
};
