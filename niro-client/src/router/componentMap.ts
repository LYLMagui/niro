import { h, type Component } from "vue";
import Layout from "@/components/Layout.vue";

const componentMap: Record<string, () => Promise<Component>> = {
  Layout: async () => Layout,
  ParentView: async () => ({ render: () => h("router-view") }),
  InnerLink: () => import("@/views/404.vue"),
  dashboard: () => import("@/views/Dashboard.vue"),
  list: () => import("@/views/TaskList.vue"),
  manager: () => import("@/views/TaskList.vue"),
  buff: () => import("@/views/TaskList.vue"),
  c5: () => import("@/views/TaskList.vue"),
  record: () => import("@/views/OrderRecord.vue"),
  account: () => import("@/views/Settings.vue"),
  goods: () => import("@/views/GoodsList.vue"),
  sticker: () => import("@/views/StickerList.vue"),
  logs: () => import("@/views/Logs.vue"),
  taskconfig: () => import("@/views/TaskConfig.vue"),
  system: () => import("@/views/Settings.vue"),
  403: () => import("@/views/403.vue"),
  404: () => import("@/views/404.vue"),
};

export const getComponent = (componentKey?: string, path?: string): (() => Promise<Component>) => {
  if (!componentKey) {
    if (path) {
      const key = path.replace(/^\/+|\/+$/g, "").split("/").pop()?.toLowerCase() || "";
      return componentMap[key] || componentMap["404"];
    }
    // 严禁返回 Layout，作为容器使用 ParentView
    return componentMap["ParentView"];
  }

  if (componentMap[componentKey]) {
    return componentMap[componentKey];
  }

  const cleanKey = componentKey.replace(/^\/+|\/+$/g, "").split("/").pop()?.toLowerCase() || "";
  return componentMap[cleanKey] || componentMap["404"];
};
