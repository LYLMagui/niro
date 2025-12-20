/**
 * 通用响应结构
 */
export interface Result<T = any> {
  code: number;
  message: string;
  data: T;
}
