/**
 * 商品实体定义
 */
export interface Goods {
  id: number;
  goodsId: number; // Buff 商品 ID
  name: string;
  shortName: string;
  internalName: string;
  iconUrl: string;
  exterior: string; // 外观/磨损
  rarity: string; // 稀有度
  marketHashName: string;
  createTime: string;
}

/**
 * 分页查询参数
 */
export interface GoodsPageQuery {
  page: number;
  pageSize: number;
  name?: string; // 搜索关键词
  exterior?: string; // 磨损筛选
}

/**
 * 分页结果
 */
export interface PageResult<T> {
  records: T[]; // 列表数据
  total: number; // 总数
  current: number; // 当前页
  size: number; // 每页大小
}
