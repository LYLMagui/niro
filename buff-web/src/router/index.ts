import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router';
import Layout from '@/components/Layout.vue';

// 路由配置表
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: Layout, // 使用 Layout 布局作为父级路由
    redirect: '/dashboard', // 默认重定向到仪表盘
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'), // 路由懒加载
        meta: { title: '概览', icon: 'dashboard' }, // 路由元信息，用于菜单渲染
      },
      {
        path: 'tasks',
        name: 'TaskConfig',
        component: () => import('@/views/TaskConfig.vue'),
        meta: { title: '任务配置', icon: 'server' },
      },
      {
        path: 'logs',
        name: 'Logs',
        component: () => import('@/views/Logs.vue'),
        meta: { title: '运行日志', icon: 'bulletin-board' },
      },
    ],
  },
];

// 创建路由实例
const router = createRouter({
  // 使用 HTML5 History 模式
  history: createWebHistory(),
  routes,
});

export default router;
