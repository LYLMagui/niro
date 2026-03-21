<template>
  <div class="p-6">
    <t-card v-if="!isAdmin" :bordered="false" class="mx-auto max-w-[560px] text-center shadow-sm">
      <div class="py-10">
        <p class="text-xl font-semibold text-gray-800">无权限访问</p>
        <p class="mt-2 text-sm text-gray-500">仅管理员可访问权限管理页面</p>
        <t-button class="mt-6" theme="primary" @click="goDashboard">返回首页</t-button>
      </div>
    </t-card>

    <RbacPermissionPanel v-else />
  </div>
</template>

<script setup lang="ts">
import RbacPermissionPanel from "@/components/RbacPermissionPanel.vue";
import { usePermission } from "@/hooks/usePermission";
import { computed } from "vue";
import { useRouter } from "vue-router";

const { hasRole } = usePermission();
const router = useRouter();

const isAdmin = computed(() => hasRole("admin"));

const goDashboard = () => {
  router.push("/dashboard");
};
</script>
