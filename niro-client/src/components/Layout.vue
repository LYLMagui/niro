<template>
  <t-layout class="erp-shell h-screen w-full overflow-hidden">
    <t-header class="!h-[48px] !p-0">
      <div class="flex h-[48px] items-center justify-between bg-[#1890ff] px-2 text-white">
        <div class="flex items-center gap-1">
          <t-button
            variant="text"
            shape="square"
            class="erp-top-btn"
            :aria-label="collapsed ? '展开侧边栏' : '收起侧边栏'"
            @click="toggleSidebar"
          >
            <template #icon>
              <t-icon :name="collapsed ? 'chevron-right' : 'chevron-left'" />
            </template>
          </t-button>
          <span class="erp-brand-title pl-1 text-[24px] leading-none font-semibold tracking-[0.5px] text-white">
            Niro Control
          </span>
        </div>

        <div class="flex items-center gap-1">
          <t-button variant="text" shape="square" class="erp-top-btn" @click="refreshCurrentPage">
            <template #icon>
              <t-icon name="refresh" />
            </template>
          </t-button>

          <t-popup trigger="click" placement="bottom-right">
            <template #content>
              <div class="w-44 rounded bg-white py-1">
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
              class="flex items-center gap-1.5 rounded px-2 py-0.5 text-sm text-white transition-colors hover:bg-white/15"
            >
              <t-avatar size="24px" class="bg-white/20 text-white">
                <template #icon><user-circle-icon /></template>
              </t-avatar>
              <span class="max-w-[140px] truncate text-[11px] text-white/90">
                {{ displayName }}
              </span>
            </button>
          </t-popup>
        </div>
      </div>
    </t-header>

    <t-layout class="min-h-0 flex-1 overflow-hidden">
      <div
        v-if="isMobile && !collapsed"
        class="erp-side-mask absolute inset-0 z-20 bg-black/30"
        @click="toggleSidebar"
      ></div>
      <t-aside
        :width="sideWidth"
        class="erp-side relative z-30 flex h-full flex-shrink-0 flex-col border-r border-[#e8e8e8] bg-white transition-all duration-200"
        :class="{ 'erp-side--collapsed': collapsed, 'erp-side--mobile': isMobile }"
      >
        <div class="min-h-0 flex-1 overflow-x-hidden overflow-y-auto px-0 pt-0 pb-3">
          <t-menu
            theme="light"
            :value="activeValue"
            :collapsed="collapsed"
            :width="['150px', '56px']"
            class="erp-side-menu !border-0"
            @change="handleMenuChange"
          >
            <sidebar-item
              v-for="menu in sidebarMenus"
              :key="menu.value"
              :item="menu"
              :collapsed="collapsed"
            />
          </t-menu>
        </div>
      </t-aside>

      <t-layout class="min-w-0 flex-1 overflow-hidden">
        <div class="erp-tabbar bg-white px-1">
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
                <div class="erp-tab-label flex items-center">
                  <span class="erp-tab-label__placeholder" aria-hidden="true"></span>
                  <span class="erp-tab-label__text max-w-[150px] truncate">{{ tab.title }}</span>
                  <span class="erp-tab-label__action" aria-hidden="true">
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
                  </span>
                </div>
              </template>
            </t-tab-panel>
          </t-tabs>
        </div>

        <t-content
          class="erp-main-content min-h-0 flex-1"
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
import { useWindowSize } from "@vueuse/core";
import { useRoute, useRouter } from "vue-router";
import { CloseIcon, PoweroffIcon, UserCircleIcon } from "tdesign-icons-vue-next";
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
const { width } = useWindowSize();
const isMobile = computed(() => width.value <= 768);
const collapsed = ref(isMobile.value);
const sideWidth = computed(() => {
  if (isMobile.value) {
    return collapsed.value ? '0px' : '156px';
  }
  return collapsed.value ? '56px' : '156px';
});

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
    if (isMobile.value) {
      collapsed.value = true;
    }
  },
  { immediate: true }
);

watch(isMobile, (mobile) => {
  collapsed.value = mobile;
});

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

const toggleSidebar = () => {
  collapsed.value = !collapsed.value;
};

const refreshCurrentPage = () => {
  window.location.reload();
};

const handleLogout = async () => {
  tabsStore.clearTabs();
  await userStore.logout();
};
</script>

<style scoped>

:deep(.erp-side-menu .t-default-menu__inner) {
  width: 100%;
  padding: 0;
}

:deep(.erp-side-menu .t-menu__logo) {
  display: none;
}

:deep(.erp-side-menu .t-menu__content) {
  margin-left: 0;
}

.erp-side {
  overflow: hidden;
  transition:
    width 0.2s ease,
    min-width 0.2s ease,
    transform 0.2s ease;
}

.erp-side-mask {
  position: absolute;
}

.erp-side--collapsed :deep(.t-default-menu) {
  width: 56px !important;
}

.erp-side--collapsed :deep(.t-default-menu__inner) {
  width: 56px;
}

.erp-side--collapsed :deep(.t-default-menu__inner .t-menu) {
  padding: 0;
}

.erp-side--collapsed :deep(.t-menu__item),
.erp-side--collapsed :deep(.t-submenu > .t-menu__item),
.erp-side--collapsed :deep(.t-submenu__title) {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  min-width: 56px;
  margin: 4px 0;
  padding: 0 !important;
  line-height: 1;
}

.erp-side--collapsed :deep(.t-menu__item .t-menu__content),
.erp-side--collapsed :deep(.t-submenu > .t-menu__item .t-menu__content),
.erp-side--collapsed :deep(.t-submenu__title .t-menu__content) {
  display: none !important;
}

.erp-side--collapsed :deep(.t-fake-arrow) {
  display: none !important;
}

.erp-side--collapsed :deep(.t-menu__item .t-menu__icon),
.erp-side--collapsed :deep(.t-submenu > .t-menu__item .t-menu__icon),
.erp-side--collapsed :deep(.t-submenu__title .t-menu__icon) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  min-width: 20px;
  margin: 0;
  flex: 0 0 20px;
}

.erp-side--collapsed :deep(.t-menu__item .t-icon),
.erp-side--collapsed :deep(.t-submenu > .t-menu__item .t-icon),
.erp-side--collapsed :deep(.t-submenu__title .t-icon) {
  width: 20px;
  min-width: 20px;
  height: 20px;
  margin: 0;
}

:deep(.erp-side-menu .t-menu__item),
:deep(.erp-side-menu .t-submenu__title) {
  position: relative;
  min-height: 30px;
  margin: 4px 0;
  padding: 0 9px;
  color: rgb(106, 106, 106);
  border-radius: 0;
  font-size: 14px;
  font-weight: 400;
  line-height: 30px;
  transition:
    background-color 0.2s ease,
    color 0.2s ease;
}

:deep(.erp-side-menu .t-menu__item .t-menu__content),
:deep(.erp-side-menu .t-submenu__title .t-menu__content) {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 7px;
}

:deep(.erp-side-menu .t-menu__item .t-icon),
:deep(.erp-side-menu .t-submenu__title .t-icon) {
  display: block;
  width: 16px;
  min-width: 16px;
  height: 16px;
  color: rgb(106, 106, 106);
}

:deep(.erp-side-menu .t-menu__item .t-menu__icon + span),
:deep(.erp-side-menu .t-submenu__title .t-menu__icon + span) {
  min-width: 0;
  flex: 1;
}


:deep(.erp-side-menu .t-menu__item:hover),
:deep(.erp-side-menu .t-submenu__title:hover) {
  background: #f5f7fa;
}

:deep(.erp-side-menu .t-fake-arrow) {
  margin-left: auto;
  color: rgb(106, 106, 106);
}

:deep(.erp-side-menu .t-menu__sub .t-menu__item),
:deep(.erp-side-menu .t-menu__sub .t-submenu__title) {
  min-height: 28px;
  padding-left: 20px !important;
  font-size: 14px;
  font-weight: 400;
  line-height: 28px;
  color: rgb(106, 106, 106);
}

:deep(.erp-side-menu .t-menu__sub .t-menu__sub .t-menu__item),
:deep(.erp-side-menu .t-menu__sub .t-menu__sub .t-submenu__title) {
  padding-left: 28px !important;
}

:deep(.erp-side-menu .t-menu__item.t-is-active:not(.t-is-opened)) {
  color: #1677ff !important;
  font-weight: 400 !important;
  background: #f2f7ff !important;
}

:deep(.erp-side-menu .t-submenu > .t-menu__item.t-is-active.t-is-opened),
:deep(.erp-side-menu .t-submenu.t-is-active > .t-menu__item),
:deep(.erp-side-menu .t-menu__item.t-is-opened) {
  color: #1677ff !important;
  font-weight: 400 !important;
  background: transparent !important;
}

:deep(.erp-side-menu .t-submenu > .t-menu__item.t-is-active.t-is-opened .t-icon),
:deep(.erp-side-menu .t-submenu.t-is-active > .t-menu__item .t-icon),
:deep(.erp-side-menu .t-menu__item.t-is-opened .t-icon) {
  color: #1677ff !important;
}

:deep(.erp-side-menu .t-menu__item.t-is-active:not(.t-is-opened)::before) {
  position: absolute;
  top: 8px;
  bottom: 8px;
  left: 0;
  width: 3px;
  content: "";
  background: #1677ff;
}

:deep(.erp-side-menu .t-submenu > .t-menu__item.t-is-active.t-is-opened::before),
:deep(.erp-side-menu .t-menu__item.t-is-opened::before),
:deep(.erp-side-menu .t-submenu.t-is-active > .t-menu__item::before) {
  content: none !important;
  display: none !important;
}

:deep(.erp-page-tabs .t-tabs__nav) {
  align-items: stretch;
}

:deep(.erp-page-tabs .t-tabs__nav-wrap) {
  min-height: 40px;
  height: 40px;
  margin-bottom: -1px;
}

:deep(.erp-page-tabs .t-tabs__nav-item) {
  min-height: 40px;
  height: 40px;
  line-height: 40px;
  padding-top: 0;
  padding-bottom: 0;
}

:deep(.erp-page-tabs .t-tabs__nav-item.t-is-active) {
  font-weight: 500;
}

:deep(.erp-page-tabs .t-tabs__bar) {
  height: 2px;
}

:deep(.erp-page-tabs .t-tabs__operations) {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  min-width: 32px;
  height: 40px;
  color: #909399;
  background: transparent;
  border-bottom: 1px solid #e5e7eb;
  transition:
    color 0.2s ease,
    background-color 0.2s ease;
}

:deep(.erp-page-tabs .t-tabs__operations:empty) {
  display: none;
}

:deep(.erp-page-tabs .t-tabs__operations:hover) {
  color: #606266;
  background: #f8fafc;
}

:deep(.erp-page-tabs .t-tabs__operations--left) {
  border-right: 1px solid #f0f0f0;
}

:deep(.erp-page-tabs .t-tabs__operations--right) {
  border-left: 1px solid #f0f0f0;
}

.erp-tab-label {
  min-width: 0;
  gap: 3px;
  min-height: 40px;
  align-items: center;
}

.erp-tab-label__placeholder,
.erp-tab-label__action {
  width: 14px;
  min-width: 14px;
  height: 14px;
  flex-shrink: 0;
}

.erp-tab-label__action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.erp-tab-label__text {
  display: block;
  min-width: 0;
  font-size: 12px;
  line-height: 1;
  text-align: center;
}

:deep(.erp-page-tabs .t-tabs__nav-item) .erp-tab-close {
  width: 16px;
  height: 16px;
  min-width: 16px;
  padding: 0;
  color: #909399;
  border-radius: 4px;
  flex-shrink: 0;
  opacity: 0;
  pointer-events: none;
  transition:
    opacity 0.2s ease,
    background-color 0.2s ease,
    color 0.2s ease;
}

:deep(.erp-page-tabs .t-tabs__nav-item:hover) .erp-tab-close,
:deep(.erp-page-tabs .t-tabs__nav-item.t-is-active) .erp-tab-close:focus-within,
:deep(.erp-page-tabs .t-tabs__nav-item) .erp-tab-close:focus-visible {
  opacity: 1;
  pointer-events: auto;
}

:deep(.erp-page-tabs .t-tabs__nav-item) .erp-tab-close:hover {
  color: #606266;
  background: #f2f3f5;
}

:deep(.erp-top-btn) {
  width: 26px;
  height: 26px;
  min-width: 26px;
  color: rgba(255, 255, 255, 0.88);
}

:deep(.erp-top-btn .t-icon),
:deep(.erp-top-btn .t-button__text > .t-icon),
:deep(.erp-top-btn .t-button__icon > .t-icon) {
  font-size: 14px;
}

:deep(.erp-top-btn:hover) {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}

.erp-brand-title {
  font-size: 24px;
  transition: font-size 0.2s ease;
}

@media (max-width: 768px) {
  .erp-brand-title {
    font-size: 20px;
    letter-spacing: 0.2px;
  }

  .erp-shell {
    min-width: 320px;
  }

  .erp-side--mobile {
    position: absolute;
    top: 0;
    left: 0;
    bottom: 0;
    z-index: 30;
    box-shadow: 4px 0 16px rgba(15, 23, 42, 0.12);
  }

  .erp-side--mobile.erp-side--collapsed {
    min-width: 0 !important;
    border-right: 0;
    box-shadow: none;
  }

  :deep(.erp-page-tabs .t-tabs__operations) {
    width: 28px;
    min-width: 28px;
  }

  :deep(.erp-page-tabs .t-tabs__nav-next),
  :deep(.erp-page-tabs .t-tabs__nav-prev) {
    width: 20px;
  }

  :deep(.erp-page-tabs .t-tabs__nav-item) {
    padding-left: 10px;
    padding-right: 10px;
  }

  .erp-tab-label__text {
    max-width: 84px;
  }
}

@media (max-width: 480px) {
  .erp-brand-title {
    font-size: 18px;
    letter-spacing: 0;
  }
}
</style>
