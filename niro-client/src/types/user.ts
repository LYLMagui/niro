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
