import type { RouteRecordRaw } from "vue-router";

/**
 * 后端返回的路由元信息
 */
export interface RouterMeta {
  /** 路由标题，用于菜单和面包屑显示 */
  title: string;
  /** 路由图标 */
  icon?: string;
  /** 是否不缓存 */
  noCache?: boolean;
  /** 内链地址 */
  link?: string;
  /** Breadcrumb 面包屑中是否显示 */
  breadcrumb?: boolean;
}

/**
 * 后端返回的路由结构
 */
export interface RouterVo {
  /** 路由名称 */
  name: string;
  /** 路由地址 */
  path: string;
  /** 是否隐藏路由 */
  hidden?: boolean;
  /** 重定向地址 */
  redirect?: string;
  /** 组件标识 */
  component?: string;
  /** 路由参数 */
  query?: string;
  /** 是否总是显示 */
  alwaysShow?: boolean;
  /** 路由元信息 */
  meta?: RouterMeta;
  /** 子路由 */
  children?: RouterVo[];
}

/**
 * 前端路由记录（扩展 RouteRecordRaw）
 */
export type AppRouteRecordRaw = RouteRecordRaw & {
  /** 路由名称，用于 keep-alive */
  name: string;
  /** 路由元信息 */
  meta: {
    /** 页面标题 */
    title?: string;
    /** 图标 */
    icon?: string;
    /** 是否隐藏 */
    hidden?: boolean;
    /** 是否缓存 */
    noCache?: boolean;
    /** 内链地址 */
    link?: string;
    /** Breadcrumb 面包屑中是否显示 */
    breadcrumb?: boolean;
    /** 激活菜单高亮 */
    activeMenu?: string;
    /** 权限标识 */
    roles?: string[];
    /** 权限校验 */
    permissions?: string[];
  };
  /** 子路由 */
  children?: AppRouteRecordRaw[];
};

/**
 * 菜单树节点结构（用于侧边栏渲染）
 */
export interface MenuItem {
  /** 菜单ID */
  id: number;
  /** 父菜单ID */
  parentId: number;
  /** 菜单名称 */
  name: string;
  /** 路由名称 */
  path: string;
  /** 组件路径 */
  component?: string;
  /** 重定向地址 */
  redirect?: string;
  /** 菜单图标 */
  icon?: string;
  /** 菜单标题 */
  title: string;
  /** 是否隐藏 */
  hidden: boolean;
  /** 是否缓存 */
  noCache: boolean;
  /** 权限标识 */
  permission?: string;
  /** 子菜单 */
  children?: MenuItem[];
}

/**
 * 路由模块配置
 */
export interface RouteModule {
  /** 路由路径 */
  path: string;
  /** 路由记录 */
  route: AppRouteRecordRaw;
}
