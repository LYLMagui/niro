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
                  type="button"
                  class="flex w-full items-center px-3 py-2 text-left text-sm text-[#303133] transition-colors hover:bg-[#f5f5f5]"
                  @click="handleLogout"
                >
                  <poweroff-icon class="mr-2 text-[#909399]" />
                  退出登录
                </button>
              </div>
            </template>

            <button
              type="button"
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
        <div class="min-h-0 flex-1 overflow-y-auto py-2 pr-[37px] pl-[27px]">
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
        <div class="erp-tabbar border-b border-[#d9d9d9] bg-white px-1">
          <t-tabs
            v-model="activeTabKey"
            class="erp-page-tabs"
            theme="normal"
            @change="handleTabChange"
          >
            <t-tab-panel
              v-for="tab in displayTabs"
              :key="tab.key"
              :value="tab.key"
              :label="tab.title"
            >
              <template #label>
                <div class="flex items-center gap-1">
                  <span class="max-w-[150px] truncate">{{ tab.title }}</span>
                  <t-button
                    v-if="tab.affix !== true"
                    variant="text"
                    shape="square"
                    size="small"
                    class="erp-tab-close"
                    :aria-label="`关闭 ${tab.title}`"
                    @click.stop="handleCloseTab(tab)"
                  >
                    <template #icon>
                      <close-icon class="text-[12px]" />
                    </template>
                  </t-button>
                </div>
              </template>
            </t-tab-panel>
          </t-tabs>
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
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { CloseIcon, PoweroffIcon, UserCircleIcon, ViewListIcon } from "tdesign-icons-vue-next";
import { usePermissionStore } from "@/store/permission";
import { HOME_TAB, useTabsStore, type PageTab } from "@/store/tabs";
import { useUserStore } from "@/store/user";
import { transformRoutesToMenus, type MenuConfig } from "@/utils/menu";
import SidebarItem from "./SidebarItem.vue";

const route = useRoute();
const router = useRouter();

const userStore = useUserStore();
const permissionStore = usePermissionStore();
const tabsStore = useTabsStore();

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

const sidebarMenus = computed((): MenuConfig[] => {
  const routes = permissionStore.topbarRouters;
  return transformRoutesToMenus(routes as any);
});

const activeValue = computed(() => {
  const matchedValue = findMenuValueByPath(sidebarMenus.value, route.path);
  if (matchedValue) return matchedValue;
  return String(route.name || route.path);
});

const displayName = computed(
  () => userStore.userInfo.nickname || userStore.userInfo.username || "用户"
);

const pageTabs = computed(() => tabsStore.tabs);
const activeTabKey = computed({
  get: () =>
    tabsStore.tabs.find((tab) => tab.fullPath === route.fullPath)?.key ||
    String(route.name || route.path),
  set: (value: string) => {
    const tab = tabsStore.tabs.find((item) => item.key === value);
    if (tab && tab.fullPath !== route.fullPath) {
      router.push(tab.fullPath);
    }
  },
});

const displayTabs = computed<PageTab[]>(() => {
  const tabs = pageTabs.value;
  return tabs.length > 0 ? tabs : [HOME_TAB];
});

watch(
  () => route.fullPath,
  () => {
    tabsStore.syncRoute(route as any);
  },
  { immediate: true }
);

const handleTabChange = (value: string | number) => {
  const nextTab = tabsStore.tabs.find((tab) => tab.key === String(value));
  if (nextTab && nextTab.fullPath !== route.fullPath) {
    router.push(nextTab.fullPath);
  }
};

const handleCloseTab = (tab: PageTab) => {
  const nextTab = tabsStore.closeTab(tab.key, activeTabKey.value);
  if (nextTab) {
    router.replace(nextTab.fullPath);
  }
};

const handleMenuChange = () => {};

const refreshCurrentPage = () => {
  window.location.reload();
};

const handleLogout = async () => {
  tabsStore.clearTabs();
  await userStore.logout();
};
</script>

<style scoped>
.erp-shell {
  --erp-sidebar-width: clamp(210px, 16vw, 276px);
  background: #f5f5f5;
}

.erp-side {
  transition: width 0.2s ease;
}

.erp-top-btn {
  color: #fff !important;
  background: transparent !important;
  border: 0 !important;
  box-shadow: none !important;
}

.erp-top-btn:hover {
  background: rgba(255, 255, 255, 0.2) !important;
}

.erp-tabbar {
  scrollbar-gutter: stable;
}

:deep(.erp-page-tabs .t-tabs__nav) {
  padding-right: 0;
  padding-left: 0;
}

:deep(.erp-page-tabs .t-tabs__nav-item) {
  height: 35px;
  padding: 0 14px;
  font-size: 13px;
  line-height: 35px;
}

:deep(.erp-page-tabs .t-tabs__nav-item:hover) {
  color: #1890ff;
}

:deep(.erp-page-tabs .t-tabs__nav-item.t-is-active) {
  color: #1890ff !important;
}

:deep(.erp-page-tabs .t-tabs__nav-track) {
  background-color: #1890ff !important;
}

:deep(.erp-page-tabs .t-tab-panel) {
  margin-right: 0;
}

:deep(.erp-page-tabs .t-tab-panel__content) {
  display: none;
}

:deep(.erp-page-tabs .t-tabs__nav-wrap) {
  overflow-x: auto;
}

:deep(.erp-page-tabs .t-tabs__nav-wrap::-webkit-scrollbar) {
  height: 0;
}

:deep(.erp-page-tabs .t-button.erp-tab-close) {
  width: 16px;
  min-width: 16px;
  height: 16px;
  padding: 0;
  color: #909399;
  border-radius: 0;
}

:deep(.erp-page-tabs .t-button.erp-tab-close:hover) {
  color: #1890ff;
}

:deep(.erp-side-menu .t-menu__operations) {
  display: none;
}

:deep(.erp-side-menu .t-default-menu__inner) {
  padding: 0 0 8px;
}

:deep(.erp-side-menu .t-menu__item),
:deep(.erp-side-menu .t-submenu__title) {
  min-height: 40px;
  margin: 0;
  color: #303133;
  border-radius: 0;
}

:deep(.erp-side-menu .t-menu__item:hover),
:deep(.erp-side-menu .t-submenu__title:hover) {
  background: #f5f7fa;
}

:deep(.erp-side-menu .t-is-active.t-menu__item),
:deep(.erp-side-menu .t-submenu__title.t-is-active) {
  position: relative;
  color: #1890ff !important;
  background: #e6f7ff !important;
}

:deep(.erp-side-menu .t-is-active.t-menu__item::before),
:deep(.erp-side-menu .t-submenu__title.t-is-active::before) {
  position: absolute;
  top: 7px;
  bottom: 7px;
  left: 0;
  width: 3px;
  content: "";
  background: #1890ff;
}

.erp-main-content {
  scrollbar-gutter: stable;
}
</style>
