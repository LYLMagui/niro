import request from "@/utils/request";
import type {
  C5InventoryPageResult,
  C5InventoryQueryParam,
  C5InventoryRefreshParam,
  C5InventoryRefreshResult,
} from "@/types/c5-inventory";

const baseUrl = "/api/c5/inventory";

export const c5InventoryApi = {
  getInventory(params: C5InventoryQueryParam) {
    return request.get<C5InventoryPageResult>(baseUrl, { params });
  },

  refreshInventory(data: C5InventoryRefreshParam) {
    return request.post<C5InventoryRefreshResult>(`${baseUrl}/refresh`, data);
  },
};
