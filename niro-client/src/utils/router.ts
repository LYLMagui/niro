import type { Component } from "vue";
import type { RouterVo, AppRouteRecordRaw } from "@/types/router";
import { getComponent } from "@/router/componentMap";

export function transformComponent(componentKey: string): () => Promise<Component> {
  return getComponent(componentKey);
}

export function transformRouteToRecord(route: RouterVo): AppRouteRecordRaw {
  const record: AppRouteRecordRaw = {
    path: route.path || "",
    name: route.name || "",
    meta: {
      title: route.meta?.title || "",
      icon: route.meta?.icon,
      noCache: route.meta?.noCache,
      link: route.meta?.link,
      breadcrumb: route.meta?.breadcrumb,
    },
    redirect: route.redirect,
    component: getComponent(route.component || route.path),
    children: [],
  };

  if (route.children && route.children.length > 0) {
    record.children = route.children.map((child) => transformRouteToRecord(child));
  }

  return record;
}

export function isExternalLink(path: string): boolean {
  return path.startsWith("http://") || path.startsWith("https://");
}

export function getMenuValue(route: RouterVo): string {
  return route.name || route.path || "";
}

export function getShowInSidebarRoutes(routes: any[]): any[] {
  return routes.filter((route) => !route.meta?.hidden);
}

export function flattenRoutePaths(routes: any[]): string[] {
  const paths: string[] = [];

  function traverse(route: any) {
    if (route.path) {
      paths.push(route.path);
    }
    if (route.children) {
      route.children.forEach(traverse);
    }
  }

  routes.forEach(traverse);
  return paths;
}
