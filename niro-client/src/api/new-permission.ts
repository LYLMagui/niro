import request from "@/utils/request";
import type { NewPermissionNavigation, NewPermissionResource } from "@/types/router";

export interface NewPermissionRolePagePreview {
  resourceId: number;
  resourceKey: string;
  title: string;
  pageKey: string;
}

export interface NewPermissionRoleButtonPreview {
  resourceId: number;
  resourceKey: string;
  title: string;
  permissionCode: string;
  buttonGroup: string;
}

export interface NewPermissionRolePageButtonPreview {
  resourceId: number;
  resourceKey: string;
  title: string;
  pageKey: string;
  buttons: NewPermissionRoleButtonPreview[];
}

export interface NewPermissionRolePreview {
  roleId: number;
  roleName: string;
  visiblePages: string[];
  visibleMenus: string[];
  enabledButtons: string[];
  homePageKey: string;
  homePageTitle: string;
  navigationTree: NewPermissionResource[];
  accessiblePages: NewPermissionRolePagePreview[];
  pageButtons: NewPermissionRolePageButtonPreview[];
}

export interface NewPermissionPublishResult {
  success: boolean;
  message: string;
  configVersion: string;
  publishedAt: string;
}

export interface NewPermissionResourceSaveParam {
  id?: number;
  resourceKey: string;
  resourceType: NewPermissionResource["resourceType"];
  parentResourceId?: number;
  pageKey?: string;
  title: string;
  icon?: string;
  sortOrder?: number;
  hidden?: boolean;
  permissionCode?: string;
  buttonGroup?: string;
  remark?: string;
  status?: number;
}

export interface NewPermissionPublishParam {
  remark?: string;
}

export const newPermissionApi = {
  listDraftResources() {
    return request.get<NewPermissionResource[]>("/api/v2/permission/resources");
  },

  getDraftResource(id: number) {
    return request.get<NewPermissionResource>(`/api/v2/permission/resources/${id}`);
  },

  saveDraftResource(data: NewPermissionResourceSaveParam) {
    return request.post<NewPermissionResource>("/api/v2/permission/resources", data);
  },

  listDraftRoleResourceIds(roleId: number) {
    return request.get<number[]>(`/api/v2/permission/roles/${roleId}/resources`);
  },

  saveDraftRoleResources(roleId: number, resourceIds: number[]) {
    return request.put<void>(`/api/v2/permission/roles/${roleId}/resources`, { resourceIds });
  },

  previewRole(roleId: number) {
    return request.get<NewPermissionRolePreview>(`/api/v2/permission/roles/${roleId}/preview`);
  },

  validatePublish(data: NewPermissionPublishParam = {}) {
    return request.post<NewPermissionPublishResult>("/api/v2/permission/publish/validate", data);
  },

  publish(data: NewPermissionPublishParam = {}) {
    return request.post<NewPermissionPublishResult>("/api/v2/permission/publish", data);
  },

  getPublishedNavigation() {
    return request.get<NewPermissionNavigation>("/api/v2/permission/navigation");
  },

  getPublishedButtonPermissions() {
    return request.get<string[]>("/api/v2/permission/buttons");
  },
};
