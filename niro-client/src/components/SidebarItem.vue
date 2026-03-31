<template>
  <template v-if="!item.hidden">
    <t-submenu v-if="showSubMenu" :value="item.value" :title="item.label" class="erp-menu-node">
      <template v-if="item.icon" #icon>
        <component :is="item.icon" />
      </template>
      <sidebar-item v-for="child in item.children" :key="child.value" :item="child" />
    </t-submenu>

    <t-menu-item
      v-else
      :value="theItem.value"
      class="erp-menu-node"
      @click="handleMenuClick(theItem)"
    >
      <template v-if="theItem.icon" #icon>
        <component :is="theItem.icon" />
      </template>
      {{ theItem.label }}
    </t-menu-item>
  </template>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import type { MenuConfig } from "@/utils/menu";

const props = defineProps<{
  item: MenuConfig;
}>();

const router = useRouter();

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

const theItem = computed(() => {
  if (!showSubMenu.value && props.item.children) {
    const visibleChildren = props.item.children.filter((child) => !child.hidden);
    if (visibleChildren.length === 1) {
      return visibleChildren[0];
    }
  }
  return props.item;
});

const handleMenuClick = (menu: MenuConfig) => {
  if (menu.link) {
    window.open(menu.link, "_blank");
    return;
  }

  if (menu.path) {
    router.push(menu.path);
  } else if (menu.routeName) {
    router.push({ name: menu.routeName });
  }
};
</script>
