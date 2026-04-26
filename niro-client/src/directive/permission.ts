import type { Directive, DirectiveBinding } from "vue";
import { useNewPermissionStore } from "@/store/new-permission";
import { useUserStore } from "@/store/user";

type PermissionType = string | string[] | undefined | null;

function normalizePermission(requiredPermission: PermissionType): string[] {
  if (typeof requiredPermission === "string") {
    return requiredPermission ? [requiredPermission] : [];
  }
  return Array.isArray(requiredPermission) ? requiredPermission.filter(Boolean) : [];
}

function hasPermission(permissions: string[], requiredPermission: PermissionType): boolean {
  const normalizedPermissions = normalizePermission(requiredPermission);
  if (normalizedPermissions.length === 0) {
    return false;
  }
  return normalizedPermissions.some((permission) => permissions.includes(permission));
}

function setElementVisible(el: HTMLElement, visible: boolean) {
  if (!(el instanceof HTMLElement)) {
    return;
  }
  el.style.display = visible ? "" : "none";
}

async function updateElementPermission(el: HTMLElement, permission: PermissionType) {
  const normalizedPermissions = normalizePermission(permission);
  if (normalizedPermissions.length === 0) {
    setElementVisible(el, false);
    return;
  }

  const userStore = useUserStore();
  if (userStore.userInfo.roles?.includes("admin")) {
    setElementVisible(el, true);
    return;
  }

  const newPermissionStore = useNewPermissionStore();
  const permissions = await newPermissionStore.loadButtonPermissions();
  setElementVisible(el, hasPermission(permissions, normalizedPermissions));
}

const permissionDirective: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding<PermissionType>) {
    setElementVisible(el, false);
    void updateElementPermission(el, binding.value);
  },
  updated(el: HTMLElement, binding: DirectiveBinding<PermissionType>) {
    if (binding.value === binding.oldValue) {
      return;
    }
    setElementVisible(el, false);
    void updateElementPermission(el, binding.value);
  },
};

export function registerPermissionDirective(app: any) {
  app.directive("permission", permissionDirective);
}

export default permissionDirective;
