import type { Directive, DirectiveBinding } from "vue";
import { useUserStore } from "@/store/user";

/**
 * 权限检查类型
 */
type PermissionType = string | string[];

/**
 * 判断是否有权限
 * @param permissions 权限列表
 * @param requiredPermission 所需权限
 */
function hasPermission(permissions: string[], requiredPermission: PermissionType): boolean {
  // admin 角色拥有所有权限
  if (permissions.includes("*:*:*") || permissions.includes("admin")) {
    return true;
  }

  // 单个权限字符串
  if (typeof requiredPermission === "string") {
    return permissions.includes(requiredPermission);
  }

  // 权限数组：满足一个即可
  return requiredPermission.some((permission) => permissions.includes(permission));
}

function getOriginalDisplay(el: HTMLElement): string {
  const storedDisplay = el.dataset.permissionDisplay;
  if (storedDisplay !== undefined) {
    return storedDisplay;
  }

  const inlineDisplay = el.style.display;
  const computedDisplay = window.getComputedStyle(el).display;
  const originalDisplay =
    inlineDisplay && inlineDisplay !== "none"
      ? inlineDisplay
      : computedDisplay === "none"
        ? ""
        : computedDisplay;

  el.dataset.permissionDisplay = originalDisplay;
  return originalDisplay;
}

function setElementVisible(el: HTMLElement, visible: boolean) {
  const originalDisplay = getOriginalDisplay(el);
  el.style.display = visible ? originalDisplay : "none";
}

/**
 * v-permission 指令
 * 用于控制元素的显示/隐藏
 *
 * 使用方式：
 * - v-permission="'task:create'" 单个权限
 * - v-permission="['task:edit', 'task:delete']" 多权限（满足一个即可）
 */
const permissionDirective: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding<PermissionType>) {
    const userStore = useUserStore();
    const permissions = userStore.userInfo.permissions || [];
    setElementVisible(el, hasPermission(permissions, binding.value));
  },
  updated(el: HTMLElement, binding: DirectiveBinding<PermissionType>) {
    const userStore = useUserStore();
    const permissions = userStore.userInfo.permissions || [];
    setElementVisible(el, hasPermission(permissions, binding.value));
  },
};

/**
 * 注册指令
 */
export function registerPermissionDirective(app: any) {
  app.directive("permission", permissionDirective);
}

export default permissionDirective;
