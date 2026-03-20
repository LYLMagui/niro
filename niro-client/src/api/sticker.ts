import request from "@/utils/request";

interface StickerListParams {
  page: number;
  pageSize: number;
  name?: string;
}

export const stickerApi = {
  /**
   * 获取印花列表
   * @param params 查询参数
   */
  getStickerList(params: StickerListParams) {
    return request.get("/buff/sticker/page", {
      params: {
        pageNum: params.page,
        pageSize: params.pageSize,
        keyword: params.name,
      },
    });
  },

  /**
   * 触发印花同步任务
   */
  syncStickers() {
    return request.post("/buff/sticker/sync");
  },
};
