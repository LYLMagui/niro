import { computed, type Ref } from "vue";
import { storeToRefs } from "pinia";
import { useUserStore } from "@/store/user";
import { useNewPermissionStore } from "@/store/new-permission";

export type PermissionType = string | string[];
export type RoleType = string | string[];

interface UsePermissionReturn {
  permissions: Readonly<Ref<string[]>>;
  roles: Readonly<Ref<string[]>>;
  hasPermission: (permission: PermissionType) => boolean;
  hasRole: (role: RoleType) => boolean;
  isAdmin: Readonly<Ref<boolean>>;
}

export function usePermission(): UsePermissionReturn {
  const userStore = useUserStore();
  const newPermissionStore = useNewPermissionStore();
  const { buttonPermissions } = storeToRefs(newPermissionStore);

  const permissions = computed(() => buttonPermissions.value || []);
  const roles = computed(() => userStore.userInfo.roles || []);
  const isAdmin = computed(() => roles.value.includes("admin"));

  function hasPermission(permission: PermissionType): boolean {
    if (isAdmin.value) {
      return true;
    }

    const userPermissions = permissions.value;
    if (typeof permission === "string") {
      return userPermissions.includes(permission);
    }

    return permission.some((perm) => userPermissions.includes(perm));
  }

  function hasRole(role: RoleType): boolean {
    const userRoles = roles.value;
    if (userRoles.includes("admin")) {
      return true;
    }

    if (typeof role === "string") {
      return userRoles.includes(role);
    }

    return role.some((item) => userRoles.includes(item));
  }

  return {
    permissions,
    roles,
    hasPermission,
    hasRole,
    isAdmin,
  };
}

export default usePermission;
