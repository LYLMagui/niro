import { defineStore } from "pinia";
import { ref } from "vue";
import { MessagePlugin } from "tdesign-vue-next";
import { clearNewPermissionCache } from "@/store/new-permission";
import { userApi } from "@/api/user";
import type { UserLoginParam } from "@/types/user";

/**
 * 用户信息接口
 */
export interface UserInfo {
  /** 用户ID */
  id: number;
  /** 用户名 */
  username: string;
  /** 昵称 */
  nickname?: string;
  /** 头像 */
  avatar?: string;
  /** 邮箱 */
  email?: string;
  /** 角色列表 */
  roles: string[];
  /** 权限列表 */
  permissions: string[];
}

/**
 * 定义用户状态 Store
 */
export const useUserStore = defineStore("user", () => {
  // Token
  const token = ref<string>("");
  // 用户信息
  const userInfo = ref<UserInfo>({
    id: 0,
    username: "",
    nickname: "",
    avatar: "",
    email: "",
    roles: [],
    permissions: [],
  });

  /**
   * 从本地存储初始化 Token
   */
  function initToken() {
    const storedToken = localStorage.getItem("niro-web-token");
    if (storedToken) {
      token.value = storedToken;
    }
  }

  /**
   * 登录
   * @param loginParams 登录参数（账号、密码）
   */
  async function login(loginParams: UserLoginParam) {
    try {
      const res = await userApi.login(loginParams);
      if (res && res.token) {
        clearNewPermissionCache();
        token.value = res.token;
        localStorage.setItem("niro-web-token", res.token);
        MessagePlugin.success("登录成功");
        return true;
      }
      MessagePlugin.error("登录失败");
      return false;
    } catch (error) {
      console.error("登录失败:", error);
      return false;
    }
  }

  /**
   * 获取用户信息
   */
  async function getInfo() {
    try {
      const res = await userApi.getInfo();
      if (res) {
        userInfo.value = {
          id: res.id,
          username: res.username,
          nickname: res.nickname,
          avatar: res.avatar,
          email: res.email,
          roles: res.roles || [],
          permissions: res.permissions || [],
        };
      }
    } catch (error) {
      console.error("获取用户信息失败:", error);
      throw error;
    }
  }

  /**
   * 退出登录
   */
  async function logout() {
    try {
      await userApi.logout();
    } catch (error) {
      console.error("退出登录请求失败:", error);
    } finally {
      clearToken();
      // 跳转到登录页
      window.location.href = "/login";
    }
  }

  /**
   * 清除 Token
   */
  function clearToken() {
    token.value = "";
    userInfo.value = {
      id: 0,
      username: "",
      nickname: "",
      avatar: "",
      email: "",
      roles: [],
      permissions: [],
    };
    clearNewPermissionCache();
    localStorage.removeItem("niro-web-token");
  }

  // 初始化时读取本地 Token
  initToken();

  return {
    token,
    userInfo,
    login,
    getInfo,
    logout,
    clearToken,
    initToken,
  };
});
