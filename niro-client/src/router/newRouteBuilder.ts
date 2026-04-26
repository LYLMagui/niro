import { getComponent } from "@/router/componentMap";
import { getNewPageRegistryItem } from "@/router/pageRegistry";
import type {
  AppRouteRecordRaw,
  NewPermissionNavigation,
  NewPermissionResource,
} from "@/types/router";

export const NEW_PERMISSION_GROUP_ROUTE_NAME = "NewPermissionGroup";

function sortResources(resources: NewPermissionResource[]): NewPermissionResource[] {
  return [...resources].sort((left, right) => {
    const sortDiff = (left.sortOrder || 0) - (right.sortOrder || 0);
    if (sortDiff !== 0) {
      return sortDiff;
    }
    return left.id - right.id;
  });
}

function sanitizeRouteName(value: string): string {
  const normalized = value.replace(/[^a-zA-Z0-9_]/g, "_");
  return normalized || "NewPermissionRoute";
}

function sanitizeRoutePath(value: string): string {
  const normalized = value
    .trim()
    .replace(/^\/+|\/+$/g, "")
    .replace(/[^a-zA-Z0-9/_-]/g, "-")
    .replace(/\/+/g, "/");
  return normalized || "untitled";
}

function buildPageRoute(resource: NewPermissionResource): AppRouteRecordRaw | null {
  const page = getNewPageRegistryItem(resource.pageKey);
  if (!page) {
    console.warn(`[new-permission] 未注册的 pageKey: ${resource.pageKey || "(empty)"}`, resource);
    return null;
  }

  return {
    path: sanitizeRoutePath(page.path),
    name: page.routeName,
    component: page.component,
    meta: {
      title: resource.title || page.meta.title,
      icon: resource.icon || page.meta.icon,
      hidden: resource.hidden ?? page.meta.hidden ?? false,
      noCache: page.meta.noCache ?? false,
      breadcrumb: page.meta.breadcrumb ?? true,
      isNewPermission: true,
    },
    children: [],
  };
}

function buildMenuRoute(
  resource: NewPermissionResource,
  usedNames: Set<string>
): AppRouteRecordRaw | null {
  const children = buildResourceRoutes(resource.children || [], usedNames);
  const pageRoute = resource.pageKey ? buildPageRoute(resource) : null;
  if (!pageRoute && children.length === 0) {
    return null;
  }

  const name = pageRoute?.name || `NewMenu_${sanitizeRouteName(resource.resourceKey)}`;
  if (usedNames.has(name)) {
    console.warn(`[new-permission] 重复的菜单路由名: ${name}`, resource);
    return null;
  }
  usedNames.add(name);

  if (pageRoute) {
    pageRoute.children = children;
    pageRoute.meta.alwaysShow = children.length > 0;
    return pageRoute;
  }

  return {
    path: sanitizeRoutePath(resource.resourceKey),
    name,
    component: getComponent("ParentView"),
    meta: {
      title: resource.title,
      icon: resource.icon,
      hidden: resource.hidden ?? false,
      noCache: true,
      alwaysShow: true,
      breadcrumb: true,
      isNewPermission: true,
    },
    children,
  };
}

function buildResourceRoutes(
  resources: NewPermissionResource[],
  usedNames: Set<string>
): AppRouteRecordRaw[] {
  return sortResources(resources)
    .filter((resource) => resource.status === 1)
    .flatMap((resource) => {
      if (resource.resourceType === "BUTTON") {
        return [];
      }

      if (resource.resourceType === "PAGE") {
        const pageRoute = buildPageRoute(resource);
        if (!pageRoute) {
          return [];
        }
        if (usedNames.has(pageRoute.name)) {
          console.warn(`[new-permission] 重复的页面路由名: ${pageRoute.name}`, resource);
          return [];
        }
        usedNames.add(pageRoute.name);
        return [pageRoute];
      }

      const menuRoute = buildMenuRoute(resource, usedNames);
      return menuRoute ? [menuRoute] : [];
    });
}

export function buildNewPermissionRoutes(
  navigation?: NewPermissionNavigation | null
): AppRouteRecordRaw[] {
  const resources = navigation?.menus || [];
  if (resources.length === 0) {
    return [];
  }

  const usedNames = new Set<string>([NEW_PERMISSION_GROUP_ROUTE_NAME]);
  return buildResourceRoutes(resources, usedNames);
}
