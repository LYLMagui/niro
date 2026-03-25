<template>
  <t-layout class="erp-shell h-screen w-full overflow-hidden">
    <t-header class="!h-[49px] !p-0">
      <div class="flex h-[49px] items-center justify-between bg-[#1890ff] px-3 text-white">
        <div class="flex items-center gap-2">
          <t-button
            variant="text"
            shape="square"
            class="erp-top-btn"
            @click="collapsed = !collapsed"
          >
            <template #icon>
              <view-list-icon class="text-base" />
            </template>
          </t-button>
          <span class="text-[15px] font-medium tracking-[0.2px]">Niro Control</span>
        </div>

        <div class="flex items-center gap-1">
          <t-button variant="text" shape="square" class="erp-top-btn" @click="refreshCurrentPage">
            <template #icon>
              <t-icon name="refresh" />
            </template>
          </t-button>

          <t-popup trigger="click" placement="bottom-right">
            <template #content>
              <div class="w-44 rounded border border-[#e8e8e8] bg-white py-1 shadow-lg">
                <button
                  class="flex w-full items-center px-3 py-2 text-left text-sm text-[#303133] transition-colors hover:bg-[#f5f5f5]"
                  @click="handleLogout"
                >
                  <poweroff-icon class="mr-2 text-[#909399]" />
                  退出登录
                </button>
              </div>
            </template>

            <button
              class="flex items-center gap-2 rounded px-2 py-1 text-sm text-white transition-colors hover:bg-white/15"
            >
              <t-avatar size="28px" class="bg-white/20 text-white">
                <template #icon><user-circle-icon /></template>
              </t-avatar>
              <span class="max-w-[140px] truncate text-[13px]">{{ displayName }}</span>
            </button>
          </t-popup>
        </div>
      </div>
    </t-header>

    <t-layout class="min-h-0 flex-1 overflow-hidden">
      <t-aside
        :width="collapsed ? '64px' : '150px'"
        :style="{ width: collapsed ? '64px' : '150px' }"
        class="erp-side relative z-10 flex h-full flex-shrink-0 flex-col border-r border-[#e8e8e8] bg-white"
      >
        <div class="flex h-[41px] items-center border-b border-[#e8e8e8] px-3 text-xs text-[#909399]">
          {{ collapsed ? '菜单' : '导航菜单' }}
        </div>

        <div class="min-h-0 flex-1 overflow-y-auto py-2">
          <t-menu
            theme="light"
            :value="activeValue"
            :collapsed="collapsed"
            width="100%"
            class="erp-side-menu !border-0"
            @change="handleMenuChange"
          >
            <sidebar-item v-for="menu in sidebarMenus" :key="menu.value" :item="menu" />
          </t-menu>
        </div>
      </t-aside>

      <t-layout class="min-w-0 flex-1 overflow-hidden">
        <div class="h-[35px] border-b border-[#d9d9d9] bg-white px-1">
          <div class="flex h-full items-end overflow-x-auto">
            <button
              class="h-[34px] min-w-[112px] border border-b-0 border-[#d9d9d9] bg-white px-4 text-[13px] text-[#303133]"
            >
              {{ currentPageTitle }}
            </button>
          </div>
        </div>

        <t-content
          class="erp-main-content min-h-0 flex-1 bg-[#f5f5f5] p-1"
          :class="[activeValue === 'Logs' ? 'overflow-hidden' : 'overflow-y-auto']"
        >
          <router-view v-slot="{ Component }">
            <keep-alive include="Logs">
              <component :is="Component" />
            </keep-alive>
          </router-view>
        </t-content>
      </t-layout>
    </t-layout>
  </t-layout>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
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

const collapsed = ref(false);

const findMenuValueByPath = (menus: MenuConfig[], path: string): string | undefined => {
  for (const menu of menus) {
    if (menu.path === path) return menu.value;
    if (menu.children?.length) {
      const childValue = findMenuValueByPath(menu.children, path);
      if (childValue) return childValue;
    }
  }
  return undefined;
};

const activeValue = computed(() => {
  const matchedValue = findMenuValueByPath(sidebarMenus.value, route.path);
  if (matchedValue) return matchedValue;
  return String(route.name || route.path);
});

const sidebarMenus = computed((): MenuConfig[] => {
  const routes = permissionStore.topbarRouters;
  return transformRoutesToMenus(routes as any);
});

const breadcrumbs = computed(() => {
  const routes = permissionStore.topbarRouters;
  return getBreadcrumbs(route.path, routes as any);
});

const currentPageTitle = computed(() => {
  const fallbackTitle = (route.meta?.title as string) || "首页";
  const lastCrumb = breadcrumbs.value[breadcrumbs.value.length - 1];
  return lastCrumb?.title || fallbackTitle;
});

const displayName = computed(
  () => userStore.userInfo.nickname || userStore.userInfo.username || "用户"
);

const handleMenuChange = (_value: string | number) => {
  // 菜单跳转在 SidebarItem 中处理
};

const refreshCurrentPage = () => {
  window.location.reload();
};

const handleLogout = async () => {
  await userStore.logout();
  router.push(`/login?redirect=${route.fullPath}`);
};
</script>

<style scoped>
.erp-shell {
  background: #f5f5f5;
}

.erp-side {
  transition: width 0.2s ease;
}

.erp-top-btn {
  color: #fff !important;
}

.erp-top-btn:hover {
  background: rgba(255, 255, 255, 0.2) !important;
}

:deep(.erp-side-menu .t-menu__operations) {
  display: none;
}

:deep(.erp-side-menu .t-default-menu__inner) {
  padding: 0 0 8px;
}

:deep(.erp-side-menu .t-menu__item),
:deep(.erp-side-menu .t-submenu__title) {
  margin: 0 8px 4px;
  height: 34px;
  line-height: 34px;
  border-radius: 0;
  color: #303133;
}

:deep(.erp-side-menu .t-menu__item:hover),
:deep(.erp-side-menu .t-submenu__title:hover) {
  background: #f5f7fa;
}

:deep(.erp-side-menu .t-is-active.t-menu__item),
:deep(.erp-side-menu .t-submenu__title.t-is-active) {
  position: relative;
  background: #e6f7ff !important;
  color: #1890ff !important;
}

:deep(.erp-side-menu .t-is-active.t-menu__item::before),
:deep(.erp-side-menu .t-submenu__title.t-is-active::before) {
  content: "";
  position: absolute;
  top: 7px;
  bottom: 7px;
  left: 0;
  width: 3px;
  background: #1890ff;
}

:deep(.erp-side-menu .t-submenu__content .t-menu__item) {
  margin-left: 16px;
}

.erp-main-content {
  scrollbar-gutter: stable;
}
</style>

