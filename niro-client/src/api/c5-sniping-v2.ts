import request from "@/utils/request";
import type { PageResult } from "@/types/page";
import type { C5SnipingAccount } from "@/types/c5-sniping-account";
import type {
  C5SnipingBuyAttemptV2Item,
  C5SnipingHitRecordV2Item,
  C5SnipingTaskV2Item,
  C5SnipingTaskV2QueryParam,
  C5SnipingTaskV2SaveParam,
} from "@/types/c5-sniping-v2";

export const c5SnipingV2BaseUrl = "/api/c5/sniping/v2/tasks";

const baseUrl = c5SnipingV2BaseUrl;

export const c5SnipingV2Api = {
  create(data: C5SnipingTaskV2SaveParam) {
    return request.post<void>(baseUrl, data);
  },

  update(id: number, data: C5SnipingTaskV2SaveParam) {
    return request.put<void>(`${baseUrl}/${id}`, data);
  },

  get(id: number) {
    return request.get<C5SnipingTaskV2Item>(`${baseUrl}/${id}`);
  },

  getPage(params: C5SnipingTaskV2QueryParam) {
    return request.get<PageResult<C5SnipingTaskV2Item>>(baseUrl, { params });
  },

  getAvailableAccounts() {
    return request.get<C5SnipingAccount[]>(`${baseUrl}/accounts`);
  },

  enable(id: number) {
    return request.post<void>(`${baseUrl}/${id}/enable`);
  },

  disable(id: number) {
    return request.post<void>(`${baseUrl}/${id}/disable`);
  },

  delete(id: number) {
    return request.delete<void>(`${baseUrl}/${id}`);
  },

  getHits(id: number, params: { page: number; pageSize: number }) {
    return request.get<PageResult<C5SnipingHitRecordV2Item>>(`${baseUrl}/${id}/hits`, { params });
  },

  getBuyAttempts(id: number, params: { page: number; pageSize: number }) {
    return request.get<PageResult<C5SnipingBuyAttemptV2Item>>(`${baseUrl}/${id}/buy-attempts`, {
      params,
    });
  },
};
