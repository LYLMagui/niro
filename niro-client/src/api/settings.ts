import request from "@/utils/request";

export interface UserBuffSettings {
  id?: number;
  userId?: number;
  buffCookie: string;
  paymentMethod: "BALANCE" | "ALIPAY" | "WECHAT";
  // 如果需要其他配置项，可以在这里扩展
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
