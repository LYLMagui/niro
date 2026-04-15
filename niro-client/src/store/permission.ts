import { defineStore } from "pinia";
import { ref } from "vue";
import type { RouterVo, AppRouteRecordRaw } from "@/types/router";
import { menuApi } from "@/api/menu";
import { getComponent } from "@/router/componentMap";

function readStoredRoutes(): RouterVo[] {
  if (typeof window === "undefined") {
    return [];
  }

  const storedRoutes = sessionStorage.getItem("niro-dynamic-routes-raw");
  if (!storedRoutes) {
    return [];
  }

  try {
    const parsedRoutes = JSON.parse(storedRoutes);
    return Array.isArray(parsedRoutes) ? parsedRoutes : [];
  } catch {
    return [];
  }
}

function mapAsyncRoutes(routes: RouterVo[], depth = 0): AppRouteRecordRaw[] {
  if (depth > 10) {
    console.warn("路由嵌套深度超过限制，可能存在循环引用");
    return [];
  }

  const res: AppRouteRecordRaw[] = [];

  routes.forEach((route) => {
    const componentKey = route.component;

    const tmp: AppRouteRecordRaw = {
      path: route.path || "",
      name: route.name || "",
      meta: {
        title: route.meta.title || "",
        icon: route.meta.icon,
        noCache: !(route.meta.keepAlive ?? true),
        hidden: route.meta.hidden ?? false,
        breadcrumb: true,
      },
      redirect: route.redirect,
      component: getComponent(componentKey),
      children: [],
    };

    if (depth === 0 && tmp.path.startsWith("/")) {
      tmp.path = tmp.path.slice(1);
    }

    if (route.children && route.children.length > 0) {
      tmp.children = mapAsyncRoutes(route.children, depth + 1);
    }

    res.push(tmp);
  });

  return res;
}

export function getCachedAccessRoutes(): AppRouteRecordRaw[] {
  return mapAsyncRoutes(readStoredRoutes());
}

/**
 * 定义权限状态 Store
 */
export const usePermissionStore = defineStore("permission", () => {
  const routes = ref<AppRouteRecordRaw[]>([]);
  const addRoutes = ref<AppRouteRecordRaw[]>([]);
  const defaultRoutes = ref<AppRouteRecordRaw[]>([]);
  const topbarRouters = ref<AppRouteRecordRaw[]>([]);
  const isRoutesLoaded = ref(false);

  function filterAsyncRoutes(routes: RouterVo[], depth = 0): AppRouteRecordRaw[] {
    return mapAsyncRoutes(routes, depth);
  }

  /**
   * 生成异步路由
   * @param roles 角色列表
   */
  async function generateRoutes(roles: string[]): Promise<AppRouteRecordRaw[]> {
    const parsedRoutes = readStoredRoutes();
    if (parsedRoutes.length > 0) {
      const accessedRoutes = roles.includes("admin")
        ? filterAsyncRoutes(parsedRoutes)
        : filterAsyncRoutes(parsedRoutes);

      routes.value = [...accessedRoutes];
      addRoutes.value = accessedRoutes;
      topbarRouters.value = accessedRoutes;

      return accessedRoutes;
    }

    const res = await menuApi.getMenus();
    let accessedRoutes: AppRouteRecordRaw[] = [];

    if (res && Array.isArray(res)) {
      sessionStorage.setItem("niro-dynamic-routes-raw", JSON.stringify(res));

      accessedRoutes = roles.includes("admin") ? filterAsyncRoutes(res) : filterAsyncRoutes(res);
    }

    routes.value = [...accessedRoutes];
    addRoutes.value = accessedRoutes;
    topbarRouters.value = accessedRoutes;

    return accessedRoutes;
  }

  /**
   * 设置默认路由
   * @param routes 路由列表
   */
  function setDefaultRoutes(routes: AppRouteRecordRaw[]) {
    defaultRoutes.value = routes;
  }

  /**
   * 设置顶部路由
   * @param routes 路由列表
   */
  function setTopbarRoutes(routes: AppRouteRecordRaw[]) {
    topbarRouters.value = routes;
  }

  /**
   * 判断是否有指定路由
   * @param path 路由路径
   */
  function hasRoute(path: string): boolean {
    return routes.value.some((route) => route.path === path);
  }

  /**
   * 清空动态路由
   */
  function clearRoutes() {
    addRoutes.value = [];
    isRoutesLoaded.value = false;
  }

  /**
   * 设置动态路由
   * @param routes 路由列表
   */
  function setAddRoutes(routes: AppRouteRecordRaw[]) {
    addRoutes.value = routes;
  }

  return {
    routes,
    addRoutes,
    defaultRoutes,
    topbarRouters,
    isRoutesLoaded,
    generateRoutes,
    filterAsyncRoutes,
    setDefaultRoutes,
    setTopbarRoutes,
    setAddRoutes,
    hasRoute,
    clearRoutes,
  };
});
