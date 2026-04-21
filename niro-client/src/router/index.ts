import NProgress from "nprogress";
import "nprogress/nprogress.css";
import { MessagePlugin } from "tdesign-vue-next";
import { createRouter, createWebHistory, type RouteRecordRaw, type Router } from "vue-router";
import { useUserStore } from "@/store/user";
import { getCachedAccessRoutes, usePermissionStore } from "@/store/permission";
import Layout from "@/components/Layout.vue";

NProgress.configure({ showSpinner: false });

const constantRoutes: RouteRecordRaw[] = [
  {
    path: "/login",
    name: "Login",
    component: () => import("@/views/login.vue"),
    meta: { title: "登录", hidden: true },
  },
  {
    path: "/403",
    name: "Forbidden",
    component: () => import("@/views/403.vue"),
    meta: { title: "403", hidden: true },
  },
  {
    path: "/404",
    name: "NotFound",
    component: () => import("@/views/404.vue"),
    meta: { title: "404", hidden: true },
  },
  {
    path: "/invite-code",
    component: Layout,
    meta: { title: "邀请码管理", hidden: true },
    children: [
      {
        path: "",
        name: "InviteCode",
        component: () => import("@/views/InviteCodeManage.vue"),
        meta: { title: "邀请码管理", hidden: true },
      },
    ],
  },
  {
    path: "/:pathMatch(.*)*",
    name: "BootstrapAny",
    component: Layout,
    meta: { hidden: true },
    children: [],
  },
  {
    path: "/",
    name: "Root",
    component: Layout,
    meta: { title: "首页", hidden: true },
    children: [],
  },
];

const bootstrapRoutes = getCachedAccessRoutes();
const rootRoute = constantRoutes.find((route) => route.name === "Root");
if (bootstrapRoutes.length > 0 && rootRoute) {
  rootRoute.children = bootstrapRoutes;
}

const router: Router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes,
  scrollBehavior: () => ({ left: 0, top: 0 }),
});

function joinRoutePath(path: string, parentPath = ""): string {
  if (!path) {
    return parentPath || "/";
  }

  if (path.startsWith("/")) {
    return path;
  }

  return `${parentPath}/${path}`.replace(/\/+/g, "/");
}

function resolveHomePath(routes: RouteRecordRaw[], parentPath = ""): string {
  for (const route of routes) {
    const currentPath = joinRoutePath(String(route.path || ""), parentPath);

    if (route.children?.length) {
      const childPath = resolveHomePath(route.children as RouteRecordRaw[], currentPath);
      if (childPath !== "/") {
        return childPath;
      }
    }

    if (currentPath !== "/") {
      return currentPath;
    }
  }

  return "/";
}

function hasMatchingRoute(routes: RouteRecordRaw[], targetPath: string, parentPath = ""): boolean {
  return routes.some((route) => {
    const currentPath = joinRoutePath(String(route.path || ""), parentPath);
    if (currentPath === targetPath) {
      return true;
    }

    if (!route.children?.length) {
      return false;
    }

    return hasMatchingRoute(route.children as RouteRecordRaw[], targetPath, currentPath);
  });
}

function resetRouter() {
  router.getRoutes().forEach((route) => {
    const name = route.name;
    if (
      name &&
      !["Login", "Forbidden", "NotFound", "InviteCode", "BootstrapAny", "Root", "Any"].includes(name as string)
    ) {
      router.removeRoute(name as string);
    }
  });
  const permissionStore = usePermissionStore();
  permissionStore.clearRoutes();
}

const whiteList = ["/403", "/404"];

// 路由加载失败防护机制
let routeLoadFailCount = 0;
let lastRouteLoadFailTime = 0;
const MAX_RETRY_COUNT = 3;
const RETRY_COOLDOWN_MS = 5000; // 5秒冷却期

router.beforeEach(async (to, _from, next) => {
  NProgress.start();

  if (to.meta.title) {
    document.title = `${to.meta.title} - Niro Control`;
  }

  const userStore = useUserStore();
  const permissionStore = usePermissionStore();

  // 优先使用 localStorage 中的 token，避免内存与存储不一致
  const token = localStorage.getItem("niro-web-token") || userStore.token;

  if (to.path === "/login") {
    if (token) {
      next({ path: "/" });
      NProgress.done();
      return;
    }
    next();
    NProgress.done();
    return;
  }

  if (!token) {
    MessagePlugin.warning("请先登录");
    next(`/login?redirect=${to.path}`);
    NProgress.done();
    return;
  }

  if (whiteList.includes(to.path)) {
    next();
    NProgress.done();
    return;
  }

  if (permissionStore.isRoutesLoaded) {
    next();
  } else {
    await loadRoutes(to, next);
  }
});

async function loadRoutes(to: any, next: (to?: any) => void) {
  const userStore = useUserStore();
  const permissionStore = usePermissionStore();

  // 检查重试限制
  const now = Date.now();
  if (now - lastRouteLoadFailTime > RETRY_COOLDOWN_MS) {
    routeLoadFailCount = 0; // 冷却期过后重置计数
  }

  if (routeLoadFailCount >= MAX_RETRY_COUNT) {
    console.error("路由加载失败次数过多，停止重试");
    MessagePlugin.error("网络连接异常，请检查后端服务");
    // 清理所有状态
    userStore.clearToken();
    resetRouter();
    next("/login");
    return;
  }

  try {
    // 1. 获取用户信息（角色、权限）
    await userStore.getInfo();

    // 2. 根据角色生成可访问路由
    const accessRoutes = await permissionStore.generateRoutes(userStore.userInfo.roles);

    // 3. 动态挂载路由
    // 将动态路由作为 Root 路由的子路由，确保在 Layout 中渲染
    accessRoutes.forEach((route) => {
      router.addRoute("Root", route as RouteRecordRaw);
    });

    // 4. 添加 404 兜底路由（必须在动态路由之后添加）
    router.addRoute({
      path: "/:pathMatch(.*)*",
      redirect: "/404",
      name: "Any",
      meta: { hidden: true },
    });

    // 5. 标记已加载
    permissionStore.isRoutesLoaded = true;

    // 6. 重置失败计数
    routeLoadFailCount = 0;

    // 7. 确保目标路由存在后跳转
    const isShellRoute = ["Root", "BootstrapAny"].includes(String(to.name || ""));
    const hasTargetRoute =
      (!isShellRoute && router.hasRoute(String(to.name || ""))) ||
      hasMatchingRoute(accessRoutes as RouteRecordRaw[], to.path) ||
      hasMatchingRoute(accessRoutes as RouteRecordRaw[], to.fullPath);

    if (to.path === "/") {
      next({ path: resolveHomePath(accessRoutes as RouteRecordRaw[]), replace: true });
    } else if (hasTargetRoute) {
      next({ ...to, replace: true });
    } else {
      next({ path: resolveHomePath(accessRoutes as RouteRecordRaw[]), replace: true });
    }
  } catch (error) {
    console.error("加载路由失败:", error);
    // 增加失败计数
    routeLoadFailCount++;
    lastRouteLoadFailTime = Date.now();

    // 失败则清空所有 token 状态（内存 + localStorage）并跳转登录
    userStore.clearToken();
    resetRouter();
    next(`/login?redirect=${to.path}`);
  }
}

router.afterEach(() => {
  NProgress.done();
});

export function clearRouteCache() {
  sessionStorage.removeItem("niro-dynamic-routes-raw");
  resetRouter();
}

export { router, resetRouter, constantRoutes };
export default router;
