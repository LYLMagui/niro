import request from "@/utils/request";
import type {
  UnboxRecordC5ListingPageResult,
  UnboxRecordC5ListingQueryParam,
  UnboxRecordDTO,
  UnboxRecordOcrResult,
  UnboxRecordPageResult,
  UnboxRecordSaveParam,
  UnboxRecordSummaryDTO,
} from "@/types/unbox";

export const unboxApi = {
  page(params?: { page?: number; pageSize?: number; startDate?: string; endDate?: string }) {
    return request.get<UnboxRecordPageResult>('/unbox/record/page', { params });
  },

  summary(params?: { startDate?: string; endDate?: string }) {
    return request.get<UnboxRecordSummaryDTO>('/unbox/record/summary', { params });
  },

  list(params?: { startDate?: string; endDate?: string }) {
    return request.get<UnboxRecordDTO[]>('/unbox/record/list', { params });
  },

  getDetail(id: number) {
    return request.get<UnboxRecordDTO>(`/unbox/record/${id}`);
  },

  create(data: UnboxRecordSaveParam) {
    return request.post<number>('/unbox/record', data);
  },

  update(id: number, data: UnboxRecordSaveParam) {
    return request.put<void>(`/unbox/record/${id}`, data);
  },

  ocrImage(file: File) {
    const formData = new FormData();
    formData.append("file", file);
    return request.post<UnboxRecordOcrResult>("/unbox/record/ocr", formData);
  },

  queryC5Listings(data: UnboxRecordC5ListingQueryParam) {
    return request.post<UnboxRecordC5ListingPageResult>("/unbox/record/c5/listings", data);
  },

  delete(id: number) {
    return request.delete<void>(`/unbox/record/${id}`);
  },
};
