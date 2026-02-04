import { defineStore } from "pinia";
import { ref } from "vue";
import type { RouterVo, AppRouteRecordRaw } from "@/types/router";
import { menuApi } from "@/api/menu";
import { getComponent } from "@/router/componentMap";

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
    if (depth > 10) {
      console.warn("路由嵌套深度超过限制，可能存在循环引用");
      return [];
    }
    const res: AppRouteRecordRaw[] = [];

    routes.forEach((route) => {
      // 检查是否为 Layout 容器且只有一个空路径子节点（用于处理一级菜单显示）
      const isLayout = route.component === "Layout";

      const tmp: AppRouteRecordRaw = {
        path: route.path || "",
        name: route.name || "",
        meta: {
          title: route.meta?.title || "",
          icon: route.meta?.icon,
          noCache: route.meta?.noCache ?? false,
          hidden: route.hidden ?? false,
          link: route.meta?.link,
          breadcrumb: route.meta?.breadcrumb ?? true,
        },
        redirect: route.redirect,
        component: getComponent(route.component, route.path),
        children: [],
      };

      if (route.children && route.children.length > 0) {
        // 如果当前是 Layout 且子节点路径为空且无组件，则让子节点继承父级路径用于推导业务组件
        const children = route.children.map((child) => {
          if (isLayout && child.path === "" && !child.component) {
            return { ...child, component: route.path };
          }
          return child;
        });
        tmp.children = filterAsyncRoutes(children, depth + 1);
      }

      res.push(tmp);
    });

    return res;
  }

  /**
   * 生成异步路由
   * @param roles 角色列表
   */
  async function generateRoutes(roles: string[]): Promise<AppRouteRecordRaw[]> {
    const storedRoutes = sessionStorage.getItem("niro-dynamic-routes-raw");
    if (storedRoutes) {
      const parsedRoutes = JSON.parse(storedRoutes);
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

      accessedRoutes = roles.includes("admin")
        ? filterAsyncRoutes(res)
        : filterAsyncRoutes(res);
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
