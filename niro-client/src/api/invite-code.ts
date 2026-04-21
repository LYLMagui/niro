import request from "@/utils/request";
import type {
  InviteCodeBatchCreateParam,
  InviteCodeBatchCreateResult,
  InviteCodeCreateParam,
  InviteCodeDetail,
  InviteCodePageQuery,
  InviteCodePageResult,
  InviteCodeUpdateParam,
} from "@/types/invite-code";

export const inviteCodeApi = {
  getPage(params: InviteCodePageQuery) {
    return request.get<InviteCodePageResult>("/invite-code/page", { params });
  },

  getDetail(id: number) {
    return request.get<InviteCodeDetail>(`/invite-code/${id}`);
  },

  create(data: InviteCodeCreateParam) {
    return request.post<InviteCodeDetail>("/invite-code/create", data);
  },

  batchCreate(data: InviteCodeBatchCreateParam) {
    return request.post<InviteCodeBatchCreateResult>("/invite-code/batch-create", data);
  },

  update(data: InviteCodeUpdateParam) {
    return request.put<InviteCodeDetail>("/invite-code/update", data);
  },

  updateStatus(id: number, status: number) {
    return request.post<void>(`/invite-code/status/${id}/${status}`);
  },

  batchDisable(ids: number[]) {
    return request.post<void>("/invite-code/batch-disable", ids);
  },
};
