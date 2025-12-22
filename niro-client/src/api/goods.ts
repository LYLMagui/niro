import type { Goods, GoodsPageQuery, PageResult, GoodsSimple } from "@/types/goods";
import request from "@/utils/request";

export const goodsApi = {
  /**
   * 分页获取商品列表
   */
  getPage(params: GoodsPageQuery) {
    return request.get<PageResult<Goods>>("/goods/page", { params });
  },

  /**
   * 获取所有商品简单列表 (ID+名称)
   */
  getSimpleList(keyword?: string) {
    const params: any = {};
    if (keyword) {
      params.keyword = keyword;
    }
    return request.get<GoodsSimple[]>("/goods/simple-list", {
      params,
    });
  },
};
