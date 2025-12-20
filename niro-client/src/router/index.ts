import Layout from "@/components/Layout.vue";
import NProgress from "nprogress"; // 引入进度条
import "nprogress/nprogress.css"; // 引入进度条样式
import { MessagePlugin } from "tdesign-vue-next";
import { createRouter, createWebHistory, RouteRecordRaw } from "vue-router";

// 配置 NProgress
NProgress.configure({ showSpinner: false });

// 路由配置表
const routes: RouteRecordRaw[] = [
  {
    path: "/login",
    name: "Login",
    component: () => import("@/views/login.vue"),
    meta: { title: "登录", hidden: true },
  },
  {
    path: "/",
    component: Layout, // 使用 Layout 布局作为父级路由
    redirect: "/dashboard", // 默认重定向到仪表盘
    children: [
      {
        path: "dashboard",
        name: "Dashboard",
        component: () => import("@/views/Dashboard.vue"), // 路由懒加载
        meta: { title: "概览", icon: "dashboard" }, // 路由元信息，用于菜单渲染
      },
      {
        path: "tasks",
        name: "TaskConfig",
        component: () => import("@/views/TaskConfig.vue"),
        meta: { title: "任务配置", icon: "server" },
      },
      {
        path: "logs",
        name: "Logs",
        component: () => import("@/views/Logs.vue"),
        meta: { title: "运行日志", icon: "bulletin-board" },
      },
    ],
  },
  // 404 页面
  {
    path: "/:pathMatch(.*)*",
    name: "NotFound",
    redirect: "/dashboard",
    meta: { hidden: true },
  },
];

// 创建路由实例
const router = createRouter({
  // 使用 HTML5 History 模式
  history: createWebHistory(),
  routes,
});

// 白名单路由
const whiteList = ["/login"];

// 全局前置守卫
router.beforeEach((to, from, next) => {
  // 开启进度条
  NProgress.start();

  // 获取 Token
  const token = localStorage.getItem("niro-token");

  // 设置页面标题
  if (to.meta.title) {
    document.title = `${to.meta.title} - Niro Control`;
  }

  if (token) {
    if (to.path === "/login") {
      // 已登录且访问登录页，重定向到首页
      next({ path: "/" });
      NProgress.done();
    } else {
      // 已登录且访问非登录页，放行
      next();
    }
  } else {
    // 未登录
    if (whiteList.indexOf(to.path) !== -1) {
      // 在白名单中，放行
      next();
    } else {
      // 不在白名单中，重定向到登录页
      MessagePlugin.warning("请先登录");
      next(`/login?redirect=${to.path}`);
      NProgress.done();
    }
  }
});

// 全局后置钩子
router.afterEach(() => {
  // 关闭进度条
  NProgress.done();
});

export default router;
