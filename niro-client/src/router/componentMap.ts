import { type Component } from "vue";
import Layout from "@/components/Layout.vue";

/**
 * 组件映射表
 * 这里的 key 对应后端 sys_menu 表中的 component_path 字段
 */
const componentMap: Record<string, () => Promise<Component>> = {
  Layout: async () => Layout,
  ParentView: () => import("@/components/ParentView.vue"),
  InnerLink: () => import("@/views/404.vue"),
  dashboard: () => import("@/views/Dashboard.vue"),
  c5snipingv2: () => import("@/views/C5SnipingTaskV2.vue"),
  "c5-sniping-v2": () => import("@/views/C5SnipingTaskV2.vue"),
  c5snipingaccountconfig: () => import("@/views/C5SnipingAccountConfig.vue"),
  "c5-sniping-account-config": () => import("@/views/C5SnipingAccountConfig.vue"),
  record: () => import("@/views/OrderRecord.vue"),
  account: () => import("@/views/Settings.vue"),
  logs: () => import("@/views/Logs.vue"),
  system: () => import("@/views/Settings.vue"),
  permission: () => import("@/views/PermissionManageNew.vue"),
  rbac: () => import("@/views/PermissionManageNew.vue"),
  rbacmanage: () => import("@/views/PermissionManageNew.vue"),
  inventory: () => import("@/views/InventoryBoard.vue"),
  inventorymanagement: () => import("@/views/InventoryManagement.vue"),
  "inventory-management": () => import("@/views/InventoryManagement.vue"),
  unboxrecord: () => import("@/views/UnboxRecord.vue"),
  invitecode: () => import("@/views/InviteCodeManage.vue"),
  "invite-code": () => import("@/views/InviteCodeManage.vue"),
  403: () => import("@/views/403.vue"),
  404: () => import("@/views/404.vue"),
};

export const getComponent = (componentKey?: string, path?: string): (() => Promise<Component>) => {
  if (!componentKey) {
    if (path) {
      const key =
        path
          .replace(/^\/+|\/+$/g, "")
          .split("/")
          .pop()
          ?.toLowerCase() || "";
      return componentMap[key] || componentMap["404"];
    }
    // 严禁返回 Layout，作为容器使用 ParentView
    return componentMap["ParentView"];
  }

  if (componentMap[componentKey]) {
    return componentMap[componentKey];
  }

  const cleanKey =
    componentKey
      .replace(/^\/+|\/+$/g, "")
      .split("/")
      .pop()
      ?.toLowerCase() || "";
  return componentMap[cleanKey] || componentMap["404"];
};
