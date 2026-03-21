<template>
  <div class="p-6">
    <t-result
      v-if="!isAdmin"
      theme="warning"
      title="无权限访问"
      description="仅管理员可访问权限管理页面"
    >
      <template #extra>
        <t-button theme="primary" @click="goDashboard">返回首页</t-button>
      </template>
    </t-result>

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
