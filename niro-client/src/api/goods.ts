import request from '@/utils/request';
import type { Result } from '@/types/http';
import type { Goods, GoodsPageQuery, PageResult } from '@/types/goods';

export const goodsApi = {
  /**
   * 分页获取商品列表
   */
  getPage: (params: GoodsPageQuery) => {
    return request.get<Result<PageResult<Goods>>, PageResult<Goods>>('/goods/page', { params });
  }
};
