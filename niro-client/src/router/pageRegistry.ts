import type { Component } from "vue";

export interface NewPageRegistryMeta {
  title: string;
  icon?: string;
  hidden?: boolean;
  noCache?: boolean;
  breadcrumb?: boolean;
}

export interface NewPageRegistryItem {
  pageKey: string;
  path: string;
  routeName: string;
  component: () => Promise<Component>;
  meta: NewPageRegistryMeta;
}

const pageRegistry: Record<string, NewPageRegistryItem> = {
  Dashboard: {
    pageKey: "Dashboard",
    path: "dashboard",
    routeName: "Dashboard",
    component: () => import("@/views/Dashboard.vue"),
    meta: {
      title: "仪表盘",
      icon: "dashboard",
      noCache: false,
      breadcrumb: true,
    },
  },
  C5SnipingTaskV2: {
    pageKey: "C5SnipingTaskV2",
    path: "c5-sniping-v2",
    routeName: "C5SnipingTaskV2",
    component: () => import("@/views/C5SnipingTaskV2.vue"),
    meta: {
      title: "扫货管理",
      icon: "search",
      noCache: false,
      breadcrumb: true,
    },
  },
  C5SnipingAccountConfig: {
    pageKey: "C5SnipingAccountConfig",
    path: "c5-sniping-account-config",
    routeName: "C5SnipingAccountConfig",
    component: () => import("@/views/C5SnipingAccountConfig.vue"),
    meta: {
      title: "账号配置",
      icon: "setting",
      noCache: false,
      breadcrumb: true,
    },
  },
  OrderRecord: {
    pageKey: "OrderRecord",
    path: "order-record",
    routeName: "OrderRecord",
    component: () => import("@/views/OrderRecord.vue"),
    meta: {
      title: "订单记录",
      icon: "order",
      noCache: false,
      breadcrumb: true,
    },
  },
  GoodsList: {
    pageKey: "GoodsList",
    path: "goods-list",
    routeName: "GoodsList",
    component: () => import("@/views/GoodsList.vue"),
    meta: {
      title: "商品列表",
      icon: "shop",
      noCache: false,
      breadcrumb: true,
    },
  },
  InventoryBoard: {
    pageKey: "InventoryBoard",
    path: "inventory-board",
    routeName: "InventoryBoard",
    component: () => import("@/views/InventoryBoard.vue"),
    meta: {
      title: "订单统计",
      icon: "view-module",
      noCache: false,
      breadcrumb: true,
    },
  },
  InventoryManagement: {
    pageKey: "InventoryManagement",
    path: "inventory-management",
    routeName: "InventoryManagement",
    component: () => import("@/views/InventoryManagement.vue"),
    meta: {
      title: "库存管理",
      icon: "view-module",
      noCache: false,
      breadcrumb: true,
    },
  },
  UnboxRecord: {
    pageKey: "UnboxRecord",
    path: "unbox-record",
    routeName: "UnboxRecord",
    component: () => import("@/views/UnboxRecord.vue"),
    meta: {
      title: "开箱记录",
      icon: "gift",
      noCache: false,
      breadcrumb: true,
    },
  },
  Settings: {
    pageKey: "Settings",
    path: "settings",
    routeName: "Settings",
    component: () => import("@/views/Settings.vue"),
    meta: {
      title: "系统设置",
      icon: "setting",
      noCache: false,
      breadcrumb: true,
    },
  },
  InviteCodeManageNew: {
    pageKey: "InviteCodeManageNew",
    path: "invite-code",
    routeName: "InviteCodeManageNew",
    component: () => import("@/views/InviteCodeManageNew.vue"),
    meta: {
      title: "邀请码管理",
      icon: "qrcode",
      noCache: false,
      breadcrumb: true,
    },
  },
  LogsNew: {
    pageKey: "LogsNew",
    path: "logs",
    routeName: "LogsNew",
    component: () => import("@/views/LogsNew.vue"),
    meta: {
      title: "全链路日志",
      icon: "file-search",
      noCache: false,
      breadcrumb: true,
    },
  },
  PermissionManageNew: {
    pageKey: "PermissionManageNew",
    path: "permission-manage",
    routeName: "PermissionManageNew",
    component: () => import("@/views/PermissionManageNew.vue"),
    meta: {
      title: "权限管理",
      icon: "control",
      noCache: false,
      breadcrumb: true,
    },
  },
};

export function getNewPageRegistryItem(pageKey?: string): NewPageRegistryItem | undefined {
  if (!pageKey) {
    return undefined;
  }
  return pageRegistry[pageKey];
}

export function listNewPageRegistry(): NewPageRegistryItem[] {
  return Object.values(pageRegistry);
}
