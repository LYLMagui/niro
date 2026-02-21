<template>
  <t-layout class="h-screen w-full overflow-hidden">
    <!-- 侧边栏 -->
    <t-aside
      :width="collapsed ? '64px' : '240px'"
      :style="{ width: collapsed ? '64px' : '240px' }"
      class="relative z-20 flex flex-shrink-0 flex-col border-r border-gray-100 bg-white transition-all duration-300"
    >
      <!-- Logo & 收缩按钮 -->
      <div class="flex h-16 items-center justify-between px-4">
        <div v-show="!collapsed" class="truncate text-xl font-bold text-blue-600">Niro</div>
        <t-button
          variant="text"
          shape="square"
          class="text-gray-500 hover:bg-gray-100"
          @click="collapsed = !collapsed"
        >
          <template #icon>
            <view-list-icon class="text-lg" />
          </template>
        </t-button>
      </div>

      <!-- 菜单区域 -->
      <div class="flex-1 overflow-x-hidden overflow-y-auto">
        <t-menu
          theme="light"
          :value="activeValue"
          :collapsed="collapsed"
          width="100%"
          class="!bg-transparent"
          @change="handleMenuChange"
        >
          <sidebar-item v-for="menu in sidebarMenus" :key="menu.value" :item="menu" />
        </t-menu>
      </div>

      <!-- 底部用户信息 -->
      <div class="relative border-t border-gray-100 p-2">
        <!-- 自定义弹出菜单 -->
        <div
          v-if="showUserMenu"
          class="animate-fade-in absolute bottom-full z-50 mb-2 rounded-lg border border-gray-100 bg-white py-1 shadow-xl"
          :class="[collapsed ? 'left-1 w-48' : 'right-2 left-2']"
        >
          <div
            class="flex cursor-pointer items-center px-4 py-2 text-sm text-gray-700 transition-colors hover:bg-gray-50"
            @click="handleLogout"
          >
            <poweroff-icon class="mr-2 text-gray-500" />
            <span v-if="!collapsed">退出登录</span>
            <span v-else>退出登录</span>
          </div>
        </div>

        <!-- 用户信息卡片 -->
        <div
          class="flex cursor-pointer items-center rounded-lg p-2 transition-colors hover:bg-gray-100"
          :class="{ 'justify-center': collapsed, 'bg-gray-100': showUserMenu }"
          @click="showUserMenu = !showUserMenu"
        >
          <t-avatar size="small" class="shrink-0 bg-blue-100 text-blue-600">
            <template #icon><user-circle-icon /></template>
          </t-avatar>
          <div v-show="!collapsed" class="ml-3 flex flex-1 flex-col overflow-hidden">
            <span class="truncate text-sm font-medium text-gray-900">
              {{ userStore.userInfo.nickname || userStore.userInfo.username || "用户" }}
            </span>
            <span class="truncate text-xs text-gray-500">
              {{ userStore.userInfo.roles?.[0] === "admin" ? "管理员" : "普通用户" }}
            </span>
          </div>
        </div>
      </div>
    </t-aside>

    <!-- 主体内容区域 -->
    <t-layout class="flex flex-1 flex-col overflow-hidden">
      <!-- 顶部导航栏 -->
      <t-header class="border-b border-gray-100 bg-white">
        <div class="flex h-16 items-center px-6">
          <!-- 面包屑导航 -->
          <t-breadcrumb>
            <t-breadcrumb-item
              v-for="(item, index) in breadcrumbs"
              :key="index"
              :to="item.path"
              :clickable="item.clickable"
            >
              <span>{{ item.title }}</span>
            </t-breadcrumb-item>
          </t-breadcrumb>
        </div>
      </t-header>

      <!-- 内容展示区域 -->
      <t-content
        class="flex flex-1 flex-col bg-gray-50"
        :class="[activeValue === 'Logs' ? 'overflow-hidden' : 'overflow-y-auto']"
      >
        <div :class="[activeValue === 'Logs' ? 'flex-1 overflow-hidden' : 'flex-1 p-3']">
          <router-view v-slot="{ Component }">
            <keep-alive include="Logs">
              <component :is="Component" />
            </keep-alive>
          </router-view>
        </div>

        <!-- 底部版权信息：日志页隐藏，其他页显示 -->
        <t-footer
          v-if="activeValue !== 'Logs'"
          class="border-t border-gray-100 bg-white p-6 text-center text-xs text-gray-400"
        >
          Copyright @ 2024 Niro Control
        </t-footer>
      </t-content>
    </t-layout>
  </t-layout>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { UserCircleIcon, PoweroffIcon, ViewListIcon } from "tdesign-icons-vue-next";
import { useUserStore } from "@/store/user";
import { usePermissionStore } from "@/store/permission";
import { transformRoutesToMenus, getBreadcrumbs, type MenuConfig } from "@/utils/menu";
import SidebarItem from "./SidebarItem.vue";

const route = useRoute();
const router = useRouter();

const userStore = useUserStore();
const permissionStore = usePermissionStore();

// 侧边栏收缩状态
const collapsed = ref(false);
// 用户菜单显示状态
const showUserMenu = ref(false);

// 当前激活的菜单值
const activeValue = computed(() => route.path);

// 侧边栏菜单配置
const sidebarMenus = computed((): MenuConfig[] => {
  const routes = permissionStore.topbarRouters;
  return transformRoutesToMenus(routes as any);
});

// 面包屑数据
const breadcrumbs = computed(() => {
  const routes = permissionStore.topbarRouters;
  return getBreadcrumbs(route.path, routes as any);
});

// 处理菜单切换
const handleMenuChange = (_value: string | number) => {
  // 已经在 SidebarItem 中处理跳转，这里仅作为占位或处理额外逻辑
};

// 退出登录
const handleLogout = async () => {
  await userStore.logout();
  router.push(`/login?redirect=${route.fullPath}`);
};

// 点击外部关闭用户菜单
const closeUserMenu = (e: MouseEvent) => {
  const target = e.target as HTMLElement;
  if (!target.closest(".relative")) {
    showUserMenu.value = false;
  }
};

onMounted(() => {
  document.addEventListener("click", closeUserMenu);
});

onUnmounted(() => {
  document.removeEventListener("click", closeUserMenu);
});
</script>

<style scoped>
.t-layout {
  background: #f3f4f5;
}
</style>
