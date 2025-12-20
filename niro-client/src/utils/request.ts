import axios from "axios";
import type {
  AxiosInstance,
  AxiosError,
  InternalAxiosRequestConfig,
  AxiosResponse,
} from "axios";
import { MessagePlugin } from "tdesign-vue-next";
import type { Result } from "@/types/http";

// 环境变量
const BASE_URL = import.meta.env.VITE_BASE_API || "";

// 创建 axios 实例
const service: AxiosInstance = axios.create({
  baseURL: BASE_URL, // API 基础路径
  timeout: 10000, // 请求超时时间
  headers: {
    "Content-Type": "application/json;charset=utf-8",
  },
});

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 从 localStorage 获取 token
    const token = localStorage.getItem("niro-token");
    // 如果 token 存在，则添加到请求头
    if (token) {
      config.headers["Authorization"] = "Bearer " + token;
    }
    return config;
  },
  (error: AxiosError) => {
    return Promise.reject(error);
  }
);

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<Result>) => {
    const res = response.data;
    const headers = response.headers;

    // 检查响应头中是否有新的 token，如果有则更新
    const newToken =
      headers["authorization"] ||
      headers["Authorization"] ||
      headers["niro-token-update"];
    if (newToken) {
      localStorage.setItem("niro-token", newToken);
    }

    if (res.code !== 0) {
      MessagePlugin.error(res.message || "系统未知错误");

      // 401: 未登录或 Token 过期
      if (res.code === 401) {
        localStorage.removeItem("niro-token");
        // 可以重定向到登录页
        window.location.href = "/login";
      }
      return Promise.reject(new Error(res.message || "Error"));
    } else {
      return res.data;
    }
  },
  (error: AxiosError) => {
    const { response } = error;
    if (response) {
      MessagePlugin.error(
        (response.data as any)?.message || "系统异常，请联系管理员"
      );
      if (response.status === 401) {
        localStorage.removeItem("niro-token");
        window.location.href = "/login";
      }
    } else {
      // 网络错误
      MessagePlugin.error("网络连接异常，请检查网络");
    }
    return Promise.reject(error);
  }
);

export default service;
