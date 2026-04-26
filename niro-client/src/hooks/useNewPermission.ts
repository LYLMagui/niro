import { computed } from "vue";
import { storeToRefs } from "pinia";
import { useNewPermissionStore } from "@/store/new-permission";
import { useUserStore } from "@/store/user";

export function useNewPermission() {
  const userStore = useUserStore();
  const newPermissionStore = useNewPermissionStore();
  const { buttonPermissions } = storeToRefs(newPermissionStore);

  const isAdmin = computed(() => userStore.userInfo.roles?.includes("admin") || false);

  const hasButtonPermission = (permissionCode: string | string[]) => {
    if (isAdmin.value) {
      return true;
    }
    if (typeof permissionCode === "string") {
      return newPermissionStore.hasButtonPermission(permissionCode);
    }
    return permissionCode.some((item) => newPermissionStore.hasButtonPermission(item));
  };

  return {
    buttonPermissions: computed(() => buttonPermissions.value),
    hasButtonPermission,
  };
}

export default useNewPermission;
