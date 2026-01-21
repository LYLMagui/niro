<template>
  <!-- TDesign 布局组件：整体布局容器 -->
  <t-layout class="h-screen w-full overflow-hidden">
    <!-- 侧边栏 -->
    <t-aside>
      <!-- 侧边菜单，绑定当前激活的菜单项 -->
      <t-menu
        theme="light"
        :value="activeValue"
        style="margin-right: 50px"
        height="100%"
        @change="handleMenuChange"
      >
        <!-- 菜单顶部 Logo 区域 -->
        <template #logo>
          <div class="flex items-center justify-center py-4 text-xl font-bold text-blue-600">
            Niro
          </div>
        </template>

        <!-- 菜单项：概览 -->
        <t-menu-item value="Dashboard">
          <template #icon>
            <dashboard-icon />
          </template>
          概览
        </t-menu-item>

        <!-- 菜单项：商品列表 -->
        <t-menu-item value="GoodsList">
          <template #icon>
            <shop-icon />
          </template>
          商品列表
        </t-menu-item>

        <!-- 菜单项：印花价值 -->
        <t-menu-item value="StickerList">
          <template #icon>
            <assignment-icon />
          </template>
          印花价值
        </t-menu-item>

        <!-- 菜单项：任务配置 -->
        <t-menu-item value="TaskConfig">
          <template #icon>
            <server-icon />
          </template>
          任务配置
        </t-menu-item>

        <!-- 菜单项：运行日志 -->
        <t-menu-item value="Logs">
          <template #icon>
            <view-list-icon />
          </template>
          运行日志
        </t-menu-item>

        <!-- 菜单项：个人配置 -->
        <t-menu-item value="Settings">
          <template #icon>
            <setting-icon />
          </template>
          个人配置
        </t-menu-item>
      </t-menu>
    </t-aside>

    <!-- 主体内容区域 -->
    <t-layout class="flex flex-1 flex-col overflow-hidden">
      <!-- 顶部导航栏 -->
      <t-header>
        <t-head-menu theme="light">
          <template #operations>
            <div class="t-menu__operations">
              <t-dropdown :options="dropdownOptions" @click="handleDropdownClick">
                <t-button variant="text" shape="square">
                  <template #icon><user-circle-icon /></template>
                </t-button>
              </t-dropdown>
            </div>
          </template>
        </t-head-menu>
      </t-header>

      <!-- 内容展示区域，使用 Tailwind 控制内边距和背景 -->
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
import { computed, h } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  DashboardIcon,
  ServerIcon,
  ViewListIcon,
  UserCircleIcon,
  PoweroffIcon,
  ShopIcon,
  SettingIcon,
  AssignmentIcon,
} from "tdesign-icons-vue-next";
import { userApi } from "@/api/user";
import { MessagePlugin, type DropdownOption } from "tdesign-vue-next";

const route = useRoute();
const router = useRouter();

// 计算当前激活的菜单项，基于当前路由名称
const activeValue = computed(() => route.name as string);

// 菜单切换处理
const handleMenuChange = (value: string | number) => {
  // 仅在 value 存在且为字符串时跳转
  if (value && typeof value === "string") {
    router.push({ name: value });
  }
};

// 下拉菜单选项
const dropdownOptions = [
  { content: "退出登录", value: "logout", prefixIcon: () => h(PoweroffIcon) },
];

const handleDropdownClick = async (data: DropdownOption) => {
  if (data.value === "logout") {
    await handleLogout();
  }
};

const handleLogout = async () => {
  try {
    await userApi.logout();
  } catch (error) {
    console.error(error);
  } finally {
    localStorage.removeItem("niro-web-token");
    MessagePlugin.success("已退出登录");
    router.push("/login");
  }
};
</script>

<style scoped>
/* 针对布局组件的特定样式覆盖 */
.t-layout {
  background: #f3f4f5;
}
</style>
