import request from "@/utils/request";

export interface CategoryNode {
  id: number;
  name: string;
  internalName: string;
  parentId: number;
  children?: CategoryNode[];
  // TDesign Cascader compatibility
  value?: number;
  label?: string;
}

export const categoryApi = {
  /**
   * 获取分类树
   */
  getTree() {
    return request.get<CategoryNode[]>("/category/tree");
  },
};
