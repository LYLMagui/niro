import NProgress from "nprogress";
import "nprogress/nprogress.css";
import { MessagePlugin } from "tdesign-vue-next";
import { createRouter, createWebHistory, type RouteRecordRaw, type Router } from "vue-router";
import { useUserStore } from "@/store/user";
import { useNewPermissionStore } from "@/store/new-permission";
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
    path: "/",
    name: "Root",
    component: Layout,
    meta: { title: "首页", hidden: true },
    children: [],
  },
];

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
    if (name && !["Login", "Forbidden", "NotFound", "Root"].includes(name as string)) {
      router.removeRoute(name as string);
    }
  });
  const newPermissionStore = useNewPermissionStore();
  newPermissionStore.clear();
}

const whiteList = ["/403", "/404"];
let routeLoadFailCount = 0;
let lastRouteLoadFailTime = 0;
const MAX_RETRY_COUNT = 3;
const RETRY_COOLDOWN_MS = 5000;

router.beforeEach(async (to, _from, next) => {
  NProgress.start();

  if (to.meta.title) {
    document.title = `${to.meta.title} - Niro Control`;
  }

  const userStore = useUserStore();
  const newPermissionStore = useNewPermissionStore();
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

  const areNewRoutesMounted = newPermissionStore.routes.some((route) =>
    router.hasRoute(String(route.name))
  );
  if (
    newPermissionStore.isNavigationLoaded &&
    newPermissionStore.routes.length > 0 &&
    areNewRoutesMounted
  ) {
    const hasTargetRoute =
      hasMatchingRoute(newPermissionStore.routes as RouteRecordRaw[], to.path) ||
      hasMatchingRoute(newPermissionStore.routes as RouteRecordRaw[], to.fullPath);

    if (to.path === "/") {
      next({ path: resolveHomePath(newPermissionStore.routes as RouteRecordRaw[]), replace: true });
      NProgress.done();
      return;
    }

    if (!hasTargetRoute) {
      next({ path: resolveHomePath(newPermissionStore.routes as RouteRecordRaw[]), replace: true });
      NProgress.done();
      return;
    }

    next();
    NProgress.done();
    return;
  }

  await loadRoutes(to, next);
});

async function mountNewPermissionRoutes(force = false) {
  const newPermissionStore = useNewPermissionStore();
  const previousRouteNames = new Set(newPermissionStore.routes.map((route) => String(route.name)));
  const previousVersion = newPermissionStore.configVersion;
  const newRoutes = await newPermissionStore.loadNavigation(force);
  const latestVersion = newPermissionStore.configVersion;
  const latestRouteNames = new Set(newRoutes.map((route) => String(route.name)));
  const routeNamesUnchanged =
    previousRouteNames.size === latestRouteNames.size &&
    Array.from(previousRouteNames).every((routeName) => latestRouteNames.has(routeName));
  const latestRoutesMounted =
    latestRouteNames.size > 0 &&
    Array.from(latestRouteNames).every((routeName) => router.hasRoute(routeName));

  if (latestRoutesMounted && routeNamesUnchanged && previousVersion === latestVersion && !force) {
    return newRoutes;
  }

  const staleRouteNames = new Set([...previousRouteNames, ...latestRouteNames]);
  staleRouteNames.forEach((routeName) => {
    if (router.hasRoute(routeName)) {
      router.removeRoute(routeName);
    }
  });

  newRoutes.forEach((route) => {
    router.addRoute("Root", route as RouteRecordRaw);
  });
  return newRoutes;
}

async function loadRoutes(to: any, next: (to?: any) => void) {
  const userStore = useUserStore();

  const now = Date.now();
  if (now - lastRouteLoadFailTime > RETRY_COOLDOWN_MS) {
    routeLoadFailCount = 0;
  }

  if (routeLoadFailCount >= MAX_RETRY_COUNT) {
    console.error("路由加载失败次数过多，停止重试");
    MessagePlugin.error("网络连接异常，请检查后端服务");
    userStore.clearToken();
    resetRouter();
    next("/login");
    return;
  }

  try {
    await userStore.getInfo();
    const newRoutes = await mountNewPermissionRoutes();
    const newPermissionStore = useNewPermissionStore();
    await newPermissionStore.loadButtonPermissions();

    if (!router.hasRoute("Any")) {
      router.addRoute({
        path: "/:pathMatch(.*)*",
        redirect: "/404",
        name: "Any",
        meta: { hidden: true },
      });
    }

    routeLoadFailCount = 0;

    const isShellRoute = ["Root", "BootstrapAny"].includes(String(to.name || ""));
    const hasTargetRoute =
      (!isShellRoute && router.hasRoute(String(to.name || ""))) ||
      hasMatchingRoute(newRoutes as RouteRecordRaw[], to.path) ||
      hasMatchingRoute(newRoutes as RouteRecordRaw[], to.fullPath);

    const homePath = resolveHomePath(newRoutes as RouteRecordRaw[]);
    if (to.path === "/" && homePath === "/") {
      next({ path: "/403", replace: true });
    } else if (to.path === "/") {
      next({ path: homePath, replace: true });
    } else if (hasTargetRoute) {
      next({ path: to.fullPath, replace: true });
    } else {
      next({ path: homePath === "/" ? "/403" : homePath, replace: true });
    }
  } catch (error) {
    console.error("加载路由失败:", error);
    routeLoadFailCount++;
    lastRouteLoadFailTime = Date.now();
    userStore.clearToken();
    resetRouter();
    next(`/login?redirect=${to.path}`);
  }
}

router.afterEach(() => {
  NProgress.done();
});

export function clearRouteCache() {
  resetRouter();
}

export { router, resetRouter, constantRoutes };
export default router;
