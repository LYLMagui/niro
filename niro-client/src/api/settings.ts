import request from "@/utils/request";

export interface UserPlatformSettings {
  id?: number;
  userId?: number;
  paymentMethod: "BALANCE" | "ALIPAY" | "WECHAT";
  encryptedC5AppKey?: string;
  c5AppKeyMasked?: string;
  hasC5AppKey?: boolean;
  steamTradeUrl?: string;
}

export interface AppKeyPublicKeyResult {
  algorithm: "RSA-OAEP-256";
  publicKey: string;
}

export const settingsApi = {
  getSettings() {
    return request.get<UserPlatformSettings>("/settings");
  },

  getAppKeyPublicKey() {
    return request.get<AppKeyPublicKeyResult>("/settings/app-key/public-key");
  },

  saveSettings(data: UserPlatformSettings) {
    return request.post<unknown>("/settings", data);
  },
};
