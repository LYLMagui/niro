import request from "../utils/request";

export interface LogItem {
  timestamp: string;
  level: string;
  message: string;
  traceId?: string;
  service?: string;
  class?: string;
  ip?: string;
  accountName?: string;
  _isDiscovery?: boolean;
  _account?: string;
  [key: string]: unknown;
}

/**
 * 全链路日志查询
 * @param traceId 追踪ID
 */
export function searchLogs(traceId: string) {
  return request.get<LogItem[]>(`/log/search`, {
    params: { traceId },
  });
}
