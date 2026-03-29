import { defineStore } from "pinia";
import { ref } from "vue";
import type { RouteLocationNormalizedLoaded } from "vue-router";

const STORAGE_KEY = "niro-page-tabs";
const HOME_TAB_KEY = "/dashboard";

export interface PageTab {
  key: string;
  title: string;
  path: string;
  fullPath: string;
  keepAlive: boolean;
  affix?: boolean;
}

export const HOME_TAB: PageTab = {
  key: HOME_TAB_KEY,
  title: "首页",
  path: "/dashboard",
  fullPath: "/dashboard",
  keepAlive: true,
  affix: true,
};

function isHomeRoute(route: RouteLocationNormalizedLoaded) {
  return route.path === HOME_TAB.path || String(route.name || "") === "Dashboard";
}

export function resolveTabKey(route: RouteLocationNormalizedLoaded) {
  return isHomeRoute(route) ? HOME_TAB_KEY : String(route.name || route.path);
}

export function resolveTabTitle(route: RouteLocationNormalizedLoaded) {
  return isHomeRoute(route) ? HOME_TAB.title : String(route.meta.title || route.name || "页面");
}

export function resolveTabFromRoute(route: RouteLocationNormalizedLoaded): PageTab {
  return {
    key: resolveTabKey(route),
    title: resolveTabTitle(route),
    path: route.path,
    fullPath: route.fullPath,
    keepAlive: route.meta.noCache !== true,
    affix: isHomeRoute(route),
  };
}

function readTabsFromStorage(): PageTab[] {
  if (typeof window === "undefined") {
    return [];
  }

  const raw = sessionStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return [];
  }

  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      return [];
    }

    return parsed.filter((item): item is PageTab => {
      return (
        item &&
        typeof item.key === "string" &&
        typeof item.title === "string" &&
        typeof item.path === "string" &&
        typeof item.fullPath === "string" &&
        typeof item.keepAlive === "boolean"
      );
    });
  } catch {
    return [];
  }
}

function normalizeTabs(source: PageTab[]) {
  const tabs: PageTab[] = [HOME_TAB];
  const seen = new Set<string>([HOME_TAB_KEY]);

  for (const tab of source) {
    if (tab.key === HOME_TAB_KEY || tab.path === HOME_TAB.path) {
      continue;
    }

    if (seen.has(tab.key)) {
      continue;
    }

    seen.add(tab.key);
    tabs.push(tab);
  }

  return tabs;
}

export const useTabsStore = defineStore("tabs", () => {
  const tabs = ref<PageTab[]>(readTabsFromStorage());

  function persistTabs() {
    if (typeof window === "undefined") {
      return;
    }

    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(tabs.value));
  }

  function ensureHomeTab() {
    tabs.value = normalizeTabs(tabs.value);
  }

  function syncRoute(route: RouteLocationNormalizedLoaded) {
    if (route.meta.hidden) {
      return resolveTabFromRoute(route);
    }

    const nextTab = resolveTabFromRoute(route);
    ensureHomeTab();

    const existingIndex = tabs.value.findIndex((tab) => tab.key === nextTab.key);
    if (existingIndex >= 0) {
      tabs.value.splice(existingIndex, 1, nextTab);
    } else {
      tabs.value.push(nextTab);
    }

    persistTabs();
    return nextTab;
  }

  function closeTab(key: string, activeKey?: string): PageTab | undefined {
    const index = tabs.value.findIndex((tab) => tab.key === key);
    if (index < 0) {
      return undefined;
    }

    const target = tabs.value[index];
    if (target.affix) {
      return tabs.value.find((tab) => tab.key === activeKey) ?? tabs.value[0] ?? HOME_TAB;
    }

    tabs.value.splice(index, 1);
    ensureHomeTab();
    persistTabs();

    if (activeKey !== key) {
      return undefined;
    }

    return tabs.value[index] ?? tabs.value[index - 1] ?? tabs.value[0] ?? HOME_TAB;
  }

  function clearTabs() {
    tabs.value = [];
    persistTabs();
  }

  return {
    tabs,
    closeTab,
    syncRoute,
    clearTabs,
  };
});
