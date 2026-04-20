import request from "@/utils/request";
import type { Cs2GoodsOption } from "@/types/cs2-goods";

export const cs2GoodsApi = {
  getUnboxCaseOptions(keyword?: string) {
    return request.get<Cs2GoodsOption[]>("/cs2-goods/unbox-case-options", {
      params: keyword ? { keyword } : undefined,
    });
  },

  getC5TaskOptions(keyword?: string) {
    return request.get<Cs2GoodsOption[]>("/cs2-goods/c5-task-options", {
      params: keyword ? { keyword } : undefined,
    });
  },
};
