import request from "@/utils/request";

export interface RbacUser {
  id: number;
  username: string;
  nickname?: string;
  status?: number;
  roleIds: number[];
}

export interface RbacRole {
  roleId: number;
  roleName: string;
  roleKey: string;
  status: number;
}

export interface RbacMenu {
  id: number;
  parentId: number;
  title: string;
  type: number;
  permission?: string;
  status?: number;
}

export const rbacApi = {
  listUsers() {
    return request.get<RbacUser[]>("/api/rbac/users");
  },

  listRoles() {
    return request.get<RbacRole[]>("/api/rbac/roles");
  },

  listMenus() {
    return request.get<RbacMenu[]>("/api/rbac/menus");
  },

  getRoleMenuIds(roleId: number) {
    return request.get<number[]>(`/api/rbac/roles/${roleId}/menus`);
  },

  assignUserRoles(userId: number, roleIds: number[]) {
    return request.put<unknown>(`/api/rbac/users/${userId}/roles`, { roleIds });
  },

  batchAppendUserRoles(userIds: number[], roleIds: number[]) {
    return request.post<unknown>("/api/rbac/users/roles/batch-append", { userIds, roleIds });
  },

  assignRoleMenus(roleId: number, menuIds: number[]) {
    return request.put<unknown>(`/api/rbac/roles/${roleId}/menus`, { menuIds });
  },
};
