import request from "@/utils/request";
import type {
  UnboxRecordDTO,
  UnboxRecordOcrResult,
  UnboxRecordSaveParam,
} from "@/types/unbox";

export const unboxApi = {
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

  delete(id: number) {
    return request.delete<void>(`/unbox/record/${id}`);
  },
};
