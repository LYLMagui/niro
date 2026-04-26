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
  roleSort?: number;
  status: number;
  remark?: string;
}

export interface RoleMutationParam {
  roleName: string;
  roleKey: string;
  roleSort: number;
  status: number;
  remark?: string;
}

export const rbacApi = {
  listUsers() {
    return request.get<RbacUser[]>("/api/rbac/users");
  },

  listRoles(params?: { keyword?: string; status?: number }) {
    return request.get<RbacRole[]>("/api/rbac/roles", { params });
  },

  createRole(data: RoleMutationParam) {
    return request.post<unknown>("/api/rbac/roles", data);
  },

  updateRole(roleId: number, data: RoleMutationParam) {
    return request.put<unknown>(`/api/rbac/roles/${roleId}`, data);
  },

  deleteRole(roleId: number) {
    return request.delete<unknown>(`/api/rbac/roles/${roleId}`);
  },

  assignUserRoles(userId: number, roleIds: number[]) {
    return request.put<unknown>(`/api/rbac/users/${userId}/roles`, { roleIds });
  },

  batchAppendUserRoles(userIds: number[], roleIds: number[]) {
    return request.post<unknown>("/api/rbac/users/roles/batch-append", { userIds, roleIds });
  },
};
