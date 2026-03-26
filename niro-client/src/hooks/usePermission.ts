import { computed } from "vue";
import type { Ref } from "vue";
import { useUserStore } from "@/store/user";

/**
 * 权限类型定义
 */
export type PermissionType = string | string[];

/**
 * 角色类型定义
 */
export type RoleType = string | string[];

/**
 * 返回结构
 */
interface UsePermissionReturn {
  permissions: Readonly<Ref<string[]>>;
  roles: Readonly<Ref<string[]>>;
  hasPermission: (permission: PermissionType) => boolean;
  hasRole: (role: RoleType) => boolean;
  isAdmin: Readonly<Ref<boolean>>;
}

/**
 * 权限 Hook
 *
 * 提供权限和角色的检查方法
 *
 * 使用示例：
 * ```ts
 * const { hasPermission, hasRole } = usePermission()
 *
 * // 单个权限检查
 * if (hasPermission('task:create')) { ... }
 *
 * // 多权限检查（满足一个即可）
 * if (hasPermission(['task:edit', 'task:delete'])) { ... }
 *
 * // 角色检查
 * if (hasRole('admin')) { ... }
 * ```
 */
export function usePermission(): UsePermissionReturn {
  const userStore = useUserStore();

  /**
   * 用户权限列表
   */
  const permissions = computed(() => userStore.userInfo.permissions || []);

  /**
   * 用户角色列表
   */
  const roles = computed(() => userStore.userInfo.roles || []);

  /**
   * 是否是管理员
   */
  const isAdmin = computed(() => {
    const perms = permissions.value;
    return perms.includes("*:*:*") || perms.includes("admin") || roles.value.includes("admin");
  });

  /**
   * 检查是否有指定权限
   * @param permission 权限字符串或权限数组
   */
  function hasPermission(permission: PermissionType): boolean {
    const userPermissions = permissions.value;

    // admin 拥有所有权限
    if (userPermissions.includes("*:*:*") || userPermissions.includes("admin")) {
      return true;
    }

    // 单个权限字符串
    if (typeof permission === "string") {
      return userPermissions.includes(permission);
    }

    // 权限数组：满足一个即可
    return permission.some((perm) => userPermissions.includes(perm));
  }

  /**
   * 检查是否有指定角色
   * @param role 角色字符串或角色数组
   */
  function hasRole(role: RoleType): boolean {
    const userRoles = roles.value;

    // admin 角色拥有所有角色权限
    if (userRoles.includes("admin")) {
      return true;
    }

    // 单个角色
    if (typeof role === "string") {
      return userRoles.includes(role);
    }

    // 角色数组：满足一个即可
    return role.some((r) => userRoles.includes(r));
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
