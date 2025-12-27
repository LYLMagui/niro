/**
 * 通用响应结构
 */
export interface Result<T = unknown> {
  code: number;
  message: string;
  data: T;
}
