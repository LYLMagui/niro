import request from "@/utils/request";
import type {
  C5InventoryAssetPageResult,
  C5InventoryItemListParam,
  C5InventoryListingCreateParam,
  C5InventoryListingFeeBatchCalculateParam,
  C5InventoryListingFeeCalculateParam,
  C5InventoryListingFeeResult,
  C5InventoryListingResult,
  C5InventoryMarketReferencePageResult,
  C5InventoryMarketReferenceParam,
  C5InventoryPageResult,
  C5InventoryQueryParam,
  C5InventoryStatsResult,
  C5InventoryRefreshParam,
  C5InventoryRefreshResult,
} from "@/types/c5-inventory";

const baseUrl = "/api/c5/inventory";

export const c5InventoryApi = {
  getInventory(params: C5InventoryQueryParam) {
    return request.get<C5InventoryPageResult>(baseUrl, { params });
  },

  getInventoryStats(params: Omit<C5InventoryQueryParam, "page" | "pageSize" | "status">) {
    return request.get<C5InventoryStatsResult>(`${baseUrl}/stats`, { params });
  },

  refreshInventory(data: C5InventoryRefreshParam) {
    return request.post<C5InventoryRefreshResult>(`${baseUrl}/refresh`, data);
  },

  getInventoryItems(params: C5InventoryItemListParam) {
    return request.get<C5InventoryAssetPageResult>(`${baseUrl}/items`, { params });
  },

  createInventoryListings(data: C5InventoryListingCreateParam) {
    return request.post<C5InventoryListingResult>(`${baseUrl}/listings`, data);
  },

  getMarketReferences(params: C5InventoryMarketReferenceParam) {
    return request.get<C5InventoryMarketReferencePageResult>(`${baseUrl}/market-references`, {
      params,
    });
  },

  refreshMarketReferences(data: C5InventoryMarketReferenceParam) {
    return request.post<C5InventoryMarketReferencePageResult>(
      `${baseUrl}/market-references/refresh`,
      data
    );
  },

  calculateListingFee(data: C5InventoryListingFeeCalculateParam) {
    return request.post<C5InventoryListingFeeResult>(`${baseUrl}/listing-fee`, data);
  },

  calculateListingFees(data: C5InventoryListingFeeBatchCalculateParam) {
    return request.post<C5InventoryListingFeeResult[]>(`${baseUrl}/listing-fees`, data);
  },
};
