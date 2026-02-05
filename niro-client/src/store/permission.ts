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

  function filterAsyncRoutes(routes: RouterVo[], depth = 0, parentPath = ""): AppRouteRecordRaw[] {
    if (depth > 10) {
      console.warn("路由嵌套深度超过限制，可能存在循环引用");
      return [];
    }
    const res: AppRouteRecordRaw[] = [];

    routes.forEach((route) => {
      // 计算当前路由的完整路径
      const fullPath = route.path.startsWith("/") ? route.path : `${parentPath}/${route.path}`.replace(/\/+/g, "/");

      // 根据路径推导组件 key
      const pathParts = (route.path || "").split("/").filter(Boolean);
      let componentKey = route.component;

      // 如果没有指定 component，根据路径最后一个部分推导
      if (!componentKey || componentKey === "ParentView") {
        if (depth === 0) {
          // 顶级菜单使用 ParentView（因为 Layout 在 Root 中）
          componentKey = "ParentView";
        } else if (route.children && route.children.length > 0) {
          // 有子菜单的使用 ParentView
          componentKey = "ParentView";
        } else {
          // 叶子节点根据 path 推导，如 /task/manager/buff -> buff
          componentKey = pathParts[pathParts.length - 1] || "dashboard";
        }
      }

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
        component: getComponent(componentKey, route.path),
        children: [],
      };

      // 顶级路由去除开头的 /（因为是 Root 的子路由）
      if (depth === 0 && tmp.path.startsWith("/")) {
        tmp.path = tmp.path.slice(1);
      }

      if (route.children && route.children.length > 0) {
        tmp.children = filterAsyncRoutes(route.children, depth + 1, fullPath);
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
