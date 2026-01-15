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
  timeout: 30000, // 请求超时时间增加到 30s
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
    service.get<any, T>(url, config),
  post: <T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    service.post<any, T>(url, data, config),
  put: <T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    service.put<any, T>(url, data, config),
  delete: <T = unknown>(url: string, config?: AxiosRequestConfig) =>
    service.delete<any, T>(url, config),
  request: <T = unknown>(config: AxiosRequestConfig) => service.request<any, T>(config),
};

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 记录请求开始时间
    (config as any).metadata = { startTime: new Date() };
    console.log(`[Request Start] ${config.method?.toUpperCase()} ${config.url}`);
    
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
    console.error("[Request Error]", error);
    return Promise.reject(error);
  }
);

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<Result<any>>) => {
    // 计算耗时
    const metadata = (response.config as any).metadata;
    const duration = new Date().getTime() - metadata.startTime.getTime();
    console.log(`[Response End] ${response.config.method?.toUpperCase()} ${response.config.url} - ${duration}ms`);

    const res = response.data;
    const headers = response.headers;

    // 检查响应头中是否有新的 token，如果有则更新
    const newToken =
      headers["authorization"] || headers["Authorization"] || headers["niro-web-token-update"];
    if (newToken) {
      localStorage.setItem("niro-web-token", newToken);
    }

    if (res.code !== 0) {
      console.error("[Business Error]", res);
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
      return res.data as any;
    }
  },
  (error: AxiosError) => {
    // 计算耗时（即使失败）
    const metadata = (error.config as any).metadata;
    const duration = metadata ? new Date().getTime() - metadata.startTime.getTime() : 'unknown';
    console.error(`[Response Error] ${error.config?.method?.toUpperCase()} ${error.config?.url} - ${duration}ms`, error);

    const { response } = error;
    if (response) {
      const data = response.data as Result;
      MessagePlugin.error(data.message || "系统异常，请联系管理员");
      if (response.status === 401) {
        console.log("Response Interceptor (Status 401): Redirecting to login...");
        localStorage.removeItem("niro-web-token");
        window.location.href = "/login";
      }
    } else if (error.code === 'ECONNABORTED' && error.message.indexOf('timeout') !== -1) {
      // 请求超时
      MessagePlugin.error("请求超时，请检查后端服务性能或网络状况");
    } else {
      // 网络错误或跨域问题
      console.error("Network Error or CORS:", error.message);
      if (error.message === "Network Error") {
        MessagePlugin.error("网络连接异常，请检查后端服务是否启动或跨域配置");
      } else {
        MessagePlugin.error(error.message || "网络连接异常，请检查网络");
      }
    }
    return Promise.reject(error);
  }
);

export default request;
