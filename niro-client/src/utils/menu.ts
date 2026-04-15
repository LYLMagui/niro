import type { Component } from "vue";
import type { RouterVo } from "@/types/router";
import { getIconComponent } from "./icon-map";

/**
 * TDesign Menu 菜单项配置
 */
export interface MenuConfig {
  /** 菜单值（唯一标识） */
  value: string;
  /** 菜单标签 */
  label: string;
  /** 图标组件 */
  icon?: Component;
  /** 子菜单 */
  children?: MenuConfig[];
  /** 是否隐藏 */
  hidden?: boolean;
  /** 路由路径（用于跳转） */
  path?: string;
  /** 重定向地址 */
  redirect?: string;
  /** 路由名称（用于 keep-alive） */
  routeName?: string;
  /** 外链地址 */
  link?: string;
  /** 是否总是显示根菜单 */
  alwaysShow?: boolean;
}

/**
 * 将后端路由树转换为 TDesign Menu 可识别的格式
 * @param routes 后端返回的路由列表
 * @param parentPath 父级路径
 * @returns TDesign Menu 配置数组
 */
export function transformRoutesToMenus(routes: RouterVo[], parentPath = ""): MenuConfig[] {
  return routes
    .filter((route) => !route.meta?.hidden && route.path !== "/" && route.name !== "Root")
    .map((route) => transformRouteToMenu(route, parentPath));
}

/**
 * 将单个后端路由转换为 TDesign Menu 项
 * @param route 后端路由数据
 * @param parentPath 父级路径
 * @returns TDesign Menu 配置
 */
function transformRouteToMenu(route: RouterVo, parentPath: string): MenuConfig {
  // 计算完整路径
  // 如果是绝对路径，直接使用；否则进行拼接
  let fullPath = route.path;
  if (route.path && !route.path.startsWith("http")) {
    if (route.path.startsWith("/")) {
      fullPath = route.path;
    } else {
      fullPath = `${parentPath}/${route.path}`.replace(/\/+/g, "/");
    }
  }

  const menu: MenuConfig = {
    value: route.name || fullPath || "",
    label: route.meta?.title || route.name || "",
    path: fullPath,
    routeName: route.name,
    redirect: route.redirect,
    hidden: route.meta?.hidden ?? false,
    alwaysShow: route.meta?.alwaysShow ?? false,
  };

  // 处理图标映射
  const iconComponent = getIconComponent(route.meta?.icon);
  if (iconComponent) {
    menu.icon = iconComponent;
  }

  // 处理外链
  if (route.meta?.link) {
    menu.link = route.meta?.link;
  }

  // 递归处理子菜单
  if (route.children && route.children.length > 0) {
    const visibleChildren = route.children.filter((child) => !child.meta?.hidden);
    if (visibleChildren.length > 0) {
      menu.children = visibleChildren.map((child) => transformRouteToMenu(child, fullPath || ""));
    }
  }

  return menu;
}

/**
 * 判断菜单是否有子菜单
 * @param menu 菜单配置
 * @returns 是否有子菜单
 */
export function hasChildren(menu: MenuConfig): boolean {
  return !!(menu.children && menu.children.length > 0);
}

/**
 * 判断菜单是否为叶子节点（无子菜单）
 * @param menu 菜单配置
 * @returns 是否为叶子节点
 */
export function isLeafMenu(menu: MenuConfig): boolean {
  return !hasChildren(menu);
}

/**
 * 过滤出顶级菜单（没有父级的菜单）
 * @param menus 菜单配置数组
 * @returns 顶级菜单列表
 */
export function getTopLevelMenus(menus: MenuConfig[]): MenuConfig[] {
  return menus.filter((menu) => !menu.children || menu.children.length === 0);
}

/**
 * 获取面包屑数据
 * @param currentPath 当前路由路径
 * @param routes 路由树
 * @returns 面包屑项列表
 */
export interface BreadcrumbItem {
  /** 面包屑文本 */
  title: string;
  /** 面包屑路径 */
  path?: string;
  /** 是否可点击跳转 */
  clickable?: boolean;
  /** 图标 */
  icon?: Component;
}

/**
 * 根据当前路径生成面包屑
 * @param currentPath 当前路由路径
 * @param routes 路由树
 * @returns 面包屑项列表
 */
export function getBreadcrumbs(currentPath: string, routes: RouterVo[]): BreadcrumbItem[] {
  const breadcrumbs: BreadcrumbItem[] = [];

  function findRouteAndParents(
    routeList: RouterVo[],
    parentPath: string = ""
  ): { route: RouterVo; parentPath: string } | null {
    for (const route of routeList) {
      const fullPath = `${parentPath}/${route.path}`.replace(/\/+/g, "/").replace(/^\//, "");

      if (fullPath === currentPath || route.path === currentPath) {
        return { route, parentPath };
      }

      if (route.children) {
        const found = findRouteAndParents(route.children, fullPath);
        if (found) {
          return found;
        }
      }
    }
    return null;
  }

  const found = findRouteAndParents(routes);
  if (found) {
    const { route } = found;

    // 添加首页面包屑
    breadcrumbs.push({
      title: "首页",
      path: "/",
      clickable: true,
      icon: undefined,
    });

    // 添加当前路由面包屑
    breadcrumbs.push({
      title: route.meta?.title || route.name || "",
      path: route.path,
      clickable: false,
      icon: undefined,
    });
  }

  return breadcrumbs;
}

/**
 * 根据路由名称查找菜单项
 * @param routeName 路由名称
 * @param menus 菜单配置数组
 * @returns 匹配的菜单项或 undefined
 */
export function findMenuByRouteName(
  routeName: string,
  menus: MenuConfig[]
): MenuConfig | undefined {
  for (const menu of menus) {
    if (menu.routeName === routeName) {
      return menu;
    }
    if (menu.children) {
      const found = findMenuByRouteName(routeName, menu.children);
      if (found) {
        return found;
      }
    }
  }
  return undefined;
}

/**
 * 获取当前激活的菜单值
 * @param currentRouteName 当前路由名称
 * @param menus 菜单配置数组
 * @returns 激活的菜单值
 */
export function getActiveMenuValue(currentRouteName: string, menus: MenuConfig[]): string {
  // 精确匹配
  const exactMatch = findMenuByRouteName(currentRouteName, menus);
  if (exactMatch) {
    return exactMatch.value;
  }

  // 默认返回当前路由名称
  return currentRouteName;
}
