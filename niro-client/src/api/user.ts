import request from "@/utils/request";
import type {
  SendRegisterEmailCodeParam,
  UserDTO,
  UserLoginParam,
  UserRegisterParam,
  ValidateInviteCodeParam,
  ValidateInviteCodeResponse,
} from "@/types/user";

/**
 * 用户信息响应
 */
export interface UserInfoResponse {
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
 * 用户相关接口
 */
export const userApi = {
  /**
   * 用户登录
   * @param params 登录参数（账号、密码）
   * @returns 用户信息
   */
  login: (params: UserLoginParam) => {
    return request.post<UserDTO>("/user/login", params);
  },

  /**
   * 校验邀请码
   * @param params 邀请码参数
   */
  validateInviteCode: (params: ValidateInviteCodeParam) => {
    return request.post<ValidateInviteCodeResponse>("/user/register/invite-code/validate", params);
  },

  /**
   * 发送注册邮箱验证码
   * @param params 邀请码和邮箱参数
   */
  sendRegisterEmailCode: (params: SendRegisterEmailCodeParam) => {
    return request.post<unknown>("/user/register/email-code/send", params);
  },

  /**
   * 用户注册
   * @param params 注册参数
   */
  register: (params: UserRegisterParam) => {
    return request.post<unknown>("/user/register", params);
  },

  /**
   * 退出登录
   */
  logout: () => {
    return request.post<unknown>("/user/logout");
  },

  /**
   * 获取用户信息
   * @returns 用户信息（包含角色和权限）
   */
  getInfo: () => {
    return request.get<UserInfoResponse>("/user/getInfo");
  },
};
