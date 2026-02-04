<template>
  <template v-if="!item.hidden">
    <!-- 目录/子菜单：当 alwaysShow 为 true 或有多个子菜单时显示 -->
    <t-submenu
      v-if="showSubMenu"
      :value="item.value"
      :title="item.label"
    >
      <template #icon v-if="item.icon">
        <component :is="item.icon" />
      </template>
      <!-- 递归渲染子项 -->
      <sidebar-item
        v-for="child in item.children"
        :key="child.value"
        :item="child"
      />
    </t-submenu>

    <!-- 菜单项：叶子节点或被提升的单子节点 -->
    <t-menu-item
      v-else
      :value="theItem.value"
      @click="handleMenuClick(theItem)"
    >
      <template #icon v-if="theItem.icon">
        <component :is="theItem.icon" />
      </template>
      {{ theItem.label }}
    </t-menu-item>
  </template>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import type { MenuConfig } from '@/utils/menu';

const props = defineProps<{
  item: MenuConfig;
}>();

const router = useRouter();

/**
 * 判断是否显示为子菜单（目录）
 * 1. 如果 alwaysShow 为 true，则显示为子菜单
 * 2. 如果有多个可见子节点，则显示为子菜单
 * 3. 如果只有一个子节点但 alwaysShow 为 false，则显示为菜单项（提升该子节点）
 */
const showSubMenu = computed(() => {
  if (props.item.alwaysShow) {
    return true;
  }
  if (props.item.children) {
    const visibleChildren = props.item.children.filter((child) => !child.hidden);
    return visibleChildren.length > 1;
  }
  return false;
});

/**
 * 获取实际渲染的菜单项配置
 * 如果是单子节点提升的情况，返回该子节点；否则返回自身
 */
const theItem = computed(() => {
  if (!showSubMenu.value && props.item.children) {
    const visibleChildren = props.item.children.filter((child) => !child.hidden);
    if (visibleChildren.length === 1) {
      return visibleChildren[0];
    }
  }
  return props.item;
});

// 处理菜单点击
const handleMenuClick = (menu: MenuConfig) => {
  // 外链处理
  if (menu.link) {
    window.open(menu.link, "_blank");
    return;
  }

  // 路由跳转
  if (menu.path) {
    router.push(menu.path);
  } else if (menu.routeName) {
    router.push({ name: menu.routeName });
  }
};
</script>
