import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { newPermissionApi } from "@/api/new-permission";
import { buildNewPermissionRoutes } from "@/router/newRouteBuilder";
import type { AppRouteRecordRaw, NewPermissionNavigation } from "@/types/router";

const NAVIGATION_CACHE_KEY = "niro-new-permission-navigation";
const VERSION_CACHE_KEY = "niro-new-permission-version";
const BUTTONS_CACHE_KEY = "niro-new-permission-buttons";

function readJsonCache<T>(key: string): T | null {
  if (typeof window === "undefined") {
    return null;
  }

  const raw = sessionStorage.getItem(key);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

function readCachedNavigation(): NewPermissionNavigation | null {
  return readJsonCache<NewPermissionNavigation>(NAVIGATION_CACHE_KEY);
}

function readCachedButtons(): string[] {
  return readJsonCache<string[]>(BUTTONS_CACHE_KEY) || [];
}

function readCachedVersion(): string {
  if (typeof window === "undefined") {
    return "";
  }
  return sessionStorage.getItem(VERSION_CACHE_KEY) || "";
}

function writeCachedNavigation(navigation: NewPermissionNavigation) {
  if (typeof window === "undefined") {
    return;
  }
  sessionStorage.setItem(NAVIGATION_CACHE_KEY, JSON.stringify(navigation));
  sessionStorage.setItem(VERSION_CACHE_KEY, navigation.configVersion || "");
}

function writeCachedButtons(buttons: string[]) {
  if (typeof window === "undefined") {
    return;
  }
  sessionStorage.setItem(BUTTONS_CACHE_KEY, JSON.stringify(buttons));
}

function clearNewPermissionCache() {
  if (typeof window === "undefined") {
    return;
  }
  sessionStorage.removeItem(NAVIGATION_CACHE_KEY);
  sessionStorage.removeItem(VERSION_CACHE_KEY);
  sessionStorage.removeItem(BUTTONS_CACHE_KEY);
}

export const useNewPermissionStore = defineStore("new-permission", () => {
  const navigation = ref<NewPermissionNavigation | null>(readCachedNavigation());
  const buttonPermissions = ref<string[]>(readCachedButtons());
  const routes = ref<AppRouteRecordRaw[]>(buildNewPermissionRoutes(navigation.value));
  const isNavigationLoaded = ref(routes.value.length > 0);
  const isButtonsLoaded = ref(buttonPermissions.value.length > 0);
  const loadFailed = ref(false);

  const configVersion = computed(() => navigation.value?.configVersion || readCachedVersion());

  async function loadNavigation(force = false): Promise<AppRouteRecordRaw[]> {
    if (force) {
      navigation.value = null;
      buttonPermissions.value = [];
      routes.value = [];
      isNavigationLoaded.value = false;
      isButtonsLoaded.value = false;
      clearNewPermissionCache();
    }

    try {
      const remoteNavigation = await newPermissionApi.getPublishedNavigation();
      const cachedVersion = readCachedVersion();
      if (cachedVersion && cachedVersion !== remoteNavigation.configVersion) {
        buttonPermissions.value = [];
        isButtonsLoaded.value = false;
        clearNewPermissionCache();
      }

      navigation.value = remoteNavigation;
      writeCachedNavigation(remoteNavigation);
      routes.value = buildNewPermissionRoutes(remoteNavigation);
      isNavigationLoaded.value = true;
      loadFailed.value = false;
      return routes.value;
    } catch (error) {
      console.warn("[new-permission] 新导航加载失败", error);
      loadFailed.value = true;
      navigation.value = readCachedNavigation();
      routes.value = buildNewPermissionRoutes(navigation.value);
      isNavigationLoaded.value = routes.value.length > 0;
      return routes.value;
    }
  }

  async function loadButtonPermissions(force = false): Promise<string[]> {
    if (!force && isButtonsLoaded.value && buttonPermissions.value.length > 0) {
      return buttonPermissions.value;
    }

    try {
      const permissions = await newPermissionApi.getPublishedButtonPermissions();
      buttonPermissions.value = permissions;
      writeCachedButtons(permissions);
      isButtonsLoaded.value = true;
      loadFailed.value = false;
      return permissions;
    } catch (error) {
      console.warn("[new-permission] 新按钮权限加载失败", error);
      loadFailed.value = true;
      buttonPermissions.value = readCachedButtons();
      isButtonsLoaded.value = buttonPermissions.value.length > 0;
      return buttonPermissions.value;
    }
  }

  function hasButtonPermission(permissionCode?: string): boolean {
    if (!permissionCode) {
      return false;
    }
    return buttonPermissions.value.includes(permissionCode);
  }

  function clear() {
    navigation.value = null;
    buttonPermissions.value = [];
    routes.value = [];
    isNavigationLoaded.value = false;
    isButtonsLoaded.value = false;
    loadFailed.value = false;
    clearNewPermissionCache();
  }

  return {
    navigation,
    buttonPermissions,
    routes,
    configVersion,
    isNavigationLoaded,
    isButtonsLoaded,
    loadFailed,
    loadNavigation,
    loadButtonPermissions,
    hasButtonPermission,
    clear,
  };
});

export { clearNewPermissionCache };
