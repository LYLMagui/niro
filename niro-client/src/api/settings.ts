import request from "@/utils/request";

export interface UserBuffSettings {
  id?: number;
  userId?: number;
  buffCookie: string;
  paymentMethod: "BALANCE" | "ALIPAY" | "WECHAT";
  wecomCorpid?: string;
  wecomCorpsecret?: string;
  wecomAgentid?: string;
  wecomTouser?: string;
}

export const settingsApi = {
  // 获取配置
  getSettings() {
    return request.get<UserBuffSettings>("/settings");
  },

  // 保存配置
  saveSettings(data: UserBuffSettings) {
    return request.post<void>("/settings", data);
  },
};
