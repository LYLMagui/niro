import request from '@/utils/request';

export const stickerApi = {
  /**
   * 获取印花列表
   * @param params 查询参数
   */
  getStickerList(params: any) {
    return request.get('/buff/sticker/page', { 
      params: {
        pageNum: params.page,
        pageSize: params.pageSize,
        keyword: params.name
      }
    });
  },

  /**
   * 触发印花同步任务
   * @param userId 用户ID
   */
  syncStickers(userId: number) {
    return request.post('/buff/sticker/sync', null, {
      params: { userId }
    });
  }
};
