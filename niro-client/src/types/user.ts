/**
 * 用户登录参数接口
 */
export interface UserLoginParam {
  /** 账号 */
  username: string;
  /** 密码 */
  password: string;
}

/**
 * 邀请码校验参数
 */
export interface ValidateInviteCodeParam {
  /** 邀请码 */
  inviteCode: string;
}

/**
 * 邀请码校验结果
 */
export interface ValidateInviteCodeResponse {
  /** 是否可用 */
  valid: boolean;
  /** 提示信息 */
  message?: string;
}

/**
 * 发送注册邮箱验证码参数
 */
export interface SendRegisterEmailCodeParam {
  /** 邮箱 */
  email: string;
}

/**
 * 用户注册参数接口
 */
export interface UserRegisterParam {
  /** 邀请码 */
  inviteCode: string;
  /** 邮箱 */
  email: string;
  /** 邮箱验证码 */
  emailCode: string;
  /** 密码 */
  password: string;
}

/**
 * 用户信息数据传输对象
 */
export interface UserDTO {
  /** 用户ID */
  id: number;
  /** 账号 */
  username: string;
  /** 昵称 */
  nickname?: string;
  /** 邮箱 */
  email?: string;
  /** 头像URL */
  avatar?: string;
  /** 状态: 1-正常, 0-禁用 */
  status?: number;
  /** Token令牌 */
  token?: string;
}
