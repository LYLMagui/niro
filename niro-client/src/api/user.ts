import request from '@/utils/request';
import type { UserDTO, UserLoginParam } from '@/types/user';
import type { Result } from '@/types/http';

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
    return request.post<Result<UserDTO>, UserDTO>('/user/login', params);
  },

  /**
   * 退出登录
   */
  logout: () => {
    return request.post<Result<void>, void>('/user/logout');
  },
};
