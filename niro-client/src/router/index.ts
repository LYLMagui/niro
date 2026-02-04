import NProgress from "nprogress";
import "nprogress/nprogress.css";
import { MessagePlugin } from "tdesign-vue-next";
import {
  createRouter,
  createWebHistory,
  type RouteRecordRaw,
  type Router,
} from "vue-router";
import { useUserStore } from "@/store/user";
import { usePermissionStore } from "@/store/permission";

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
    if (
      name &&
      !["Login", "Forbidden", "NotFound", "Root", "Any"].includes(name as string)
    ) {
      router.removeRoute(name as string);
    }
  });
  const permissionStore = usePermissionStore();
  permissionStore.clearRoutes();
}

const whiteList = ["/403", "/404"];

router.beforeEach(async (to, _from, next) => {
  NProgress.start();

  if (to.meta.title) {
    document.title = `${to.meta.title} - Niro Control`;
  }

  const userStore = useUserStore();
  const permissionStore = usePermissionStore();

  const token = userStore.token || localStorage.getItem("niro-web-token");

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

  try {
    // 1. 获取用户信息（角色、权限）
    await userStore.getInfo();

    // 2. 根据角色生成可访问路由
    const accessRoutes = await permissionStore.generateRoutes(userStore.userInfo.roles);

    // 3. 动态挂载路由
    // 注意：这里我们将动态路由挂载到根路由下，或者作为顶级路由
    accessRoutes.forEach((route) => {
      router.addRoute(route as RouteRecordRaw);
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

    // 6. 触发重定向，确保路由生效
    next({ ...to, replace: true });
  } catch (error) {
    console.error("加载路由失败:", error);
    // 失败则清空 token 并跳转登录
    localStorage.removeItem("niro-web-token");
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
