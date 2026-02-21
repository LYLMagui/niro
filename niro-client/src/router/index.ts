import NProgress from "nprogress";
import "nprogress/nprogress.css";
import { MessagePlugin } from "tdesign-vue-next";
import { createRouter, createWebHistory, type RouteRecordRaw, type Router } from "vue-router";
import { useUserStore } from "@/store/user";
import { usePermissionStore } from "@/store/permission";
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
    redirect: "/dashboard",
    meta: { title: "首页", hidden: true },
    children: [],
  },
];

const router: Router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes,
  scrollBehavior: () => ({ left: 0, top: 0 }),
});

function resetRouter() {
  router.getRoutes().forEach((route) => {
    const name = route.name;
    if (name && !["Login", "Forbidden", "NotFound", "Root", "Any"].includes(name as string)) {
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
    if (
      router.hasRoute(to.name) ||
      accessRoutes.some((r) => r.path === to.path || r.path === to.fullPath)
    ) {
      next({ ...to, replace: true });
    } else {
      next({ path: "/dashboard", replace: true });
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
