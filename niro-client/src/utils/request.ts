import type { Result } from "@/types/http";
import axios from "axios";
import type {
  AxiosError,
  InternalAxiosRequestConfig,
  AxiosResponse,
  AxiosRequestConfig,
} from "axios";
import { MessagePlugin } from "tdesign-vue-next";

// 环境变量
const BASE_URL = import.meta.env.VITE_BASE_API || "";

// 创建 axios 实例
const service = axios.create({
  baseURL: BASE_URL, // API 基础路径
  timeout: 10000, // 请求超时时间
  headers: {
    "Content-Type": "application/json;charset=utf-8",
  },
});

// 重新定义请求方法以匹配拦截器的返回类型
export interface RequestInstance {
  get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>;
  post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>;
  put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>;
  delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>;
  request<T = unknown>(config: AxiosRequestConfig): Promise<T>;
}

const request: RequestInstance = {
  get: <T = unknown>(url: string, config?: AxiosRequestConfig) =>
    service.get<Result<T>, T>(url, config),
  post: <T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    service.post<Result<T>, T>(url, data, config),
  put: <T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    service.put<Result<T>, T>(url, data, config),
  delete: <T = unknown>(url: string, config?: AxiosRequestConfig) =>
    service.delete<Result<T>, T>(url, config),
  request: <T = unknown>(config: AxiosRequestConfig) => service.request<Result<T>, T>(config),
};

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 从 localStorage 获取 token
    const token = localStorage.getItem("niro-web-token");
    // 如果 token 存在，则添加到请求头
    if (token) {
      // 这里的 key 必须和后端 sa-token.token-name 一致
      config.headers["niro-web-token"] = "Bearer " + token;
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
      headers["authorization"] || headers["Authorization"] || headers["niro-web-token-update"];
    if (newToken) {
      localStorage.setItem("niro-web-token", newToken);
    }

    if (res.code !== 0) {
      MessagePlugin.error(res.message || "系统未知错误");

      // 401: 未登录或 Token 过期
      if (res.code === 401) {
        console.log("Response Interceptor (200 OK -> Code 401): Redirecting to login...");
        localStorage.removeItem("niro-web-token");
        // 可以重定向到登录页
        window.location.href = "/login";
      }
      return Promise.reject(new Error(res.message || "Error"));
    } else {
      return res.data;
    }
  },
  (error: AxiosError) => {
    console.log("Response Interceptor (Error):", error.response?.status, error.message);
    const { response } = error;
    if (response) {
      const data = response.data as Result;
      MessagePlugin.error(data.message || "系统异常，请联系管理员");
      if (response.status === 401) {
        console.log("Response Interceptor (Status 401): Redirecting to login...");
        localStorage.removeItem("niro-web-token");
        window.location.href = "/login";
      }
    } else {
      // 网络错误
      MessagePlugin.error("网络连接异常，请检查网络");
    }
    return Promise.reject(error);
  }
);

export default request;
