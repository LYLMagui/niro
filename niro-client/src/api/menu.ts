import request from "@/utils/request";
import type { RouterVo } from "@/types/router";

/**
 * 菜单相关接口
 */
export const menuApi = {
  /**
   * 获取用户动态路由
   * @returns 路由列表
   */
  getMenus: () => {
    return request.get<RouterVo[]>("/api/user/menus");
  },
};
