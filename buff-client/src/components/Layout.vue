<template>
  <!-- TDesign 布局组件：整体布局容器 -->
  <t-layout class="h-screen w-full">
    <!-- 侧边栏 -->
    <t-aside>
      <!-- 侧边菜单，绑定当前激活的菜单项 -->
      <t-menu theme="light" :value="activeValue" style="margin-right: 50px" height="550px">
        <!-- 菜单顶部 Logo 区域 -->
        <template #logo>
          <div class="flex items-center justify-center py-4 text-xl font-bold text-blue-600">
            Buff Spider
          </div>
        </template>

        <!-- 菜单项：概览 -->
        <t-menu-item value="Dashboard" to="/dashboard" @click="router.push('/dashboard')">
          <template #icon>
            <dashboard-icon />
          </template>
          概览
        </t-menu-item>

        <!-- 菜单项：任务配置 -->
        <t-menu-item value="TaskConfig" to="/tasks" @click="router.push('/tasks')">
          <template #icon>
            <server-icon />
          </template>
          任务配置
        </t-menu-item>

        <!-- 菜单项：运行日志 -->
        <t-menu-item value="Logs" to="/logs" @click="router.push('/logs')">
          <template #icon>
            <view-list-icon />
          </template>
          运行日志
        </t-menu-item>
      </t-menu>
    </t-aside>

    <!-- 主体内容区域 -->
    <t-layout>
      <!-- 顶部导航栏 -->
      <t-header>
        <t-head-menu theme="light">
          <template #operations>
            <div class="t-menu__operations">
              <t-button variant="text" shape="square">
                <template #icon><user-circle-icon /></template>
              </t-button>
            </div>
          </template>
        </t-head-menu>
      </t-header>

      <!-- 内容展示区域，使用 Tailwind 控制内边距和背景 -->
      <t-content class="overflow-auto bg-gray-50 p-6">
        <router-view />
      </t-content>

      <!-- 底部版权信息 -->
      <t-footer class="py-4 text-center text-sm text-gray-400">
        Copyright @ 2024 Buff Spider Control
      </t-footer>
    </t-layout>
  </t-layout>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { DashboardIcon, ServerIcon, ViewListIcon, UserCircleIcon } from "tdesign-icons-vue-next";

const route = useRoute();
const router = useRouter();

// 计算当前激活的菜单项，基于当前路由名称
const activeValue = computed(() => route.name as string);
</script>

<style scoped>
/* 针对布局组件的特定样式覆盖 */
.t-layout {
  background: #f3f4f5;
}
</style>
