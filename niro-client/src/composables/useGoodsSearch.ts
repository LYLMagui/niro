import { computed, ref, type Ref } from "vue";
import { goodsApi } from "@/api/goods";
import type { GoodsSimple } from "@/types/goods";

export const NON_WEARABLE_CATEGORIES = [
  "印花",
  "Sticker",
  "容器",
  "Container",
  "涂鸦",
  "Spray",
  "布章",
  "Patch",
  "音乐盒",
  "Music Kit",
  "钥匙",
  "Key",
  "工具",
  "Tool",
  "探员",
  "Agent",
  "其他",
  "Other",
];

/**
 * 商品远程搜索 + 磨损类型判断
 */
export function useGoodsSearch(
  goodsId: Ref<number | undefined>,
  options?: { canViewGoods?: Ref<boolean> }
) {
  const goodsLoading = ref(false);
  const goodsOptions = ref<GoodsSimple[]>([]);

  const remoteSearchGoods = async (keyword: string) => {
    if (!keyword) return;
    if (options?.canViewGoods && !options.canViewGoods.value) {
      goodsOptions.value = [];
      return;
    }
    goodsLoading.value = true;
    try {
      goodsOptions.value = await goodsApi.getSimpleList(keyword);
    } finally {
      goodsLoading.value = false;
    }
  };

  /** 判断当前选中商品是否有磨损属性 */
  const isWearable = computed(() => {
    const selected = goodsOptions.value.find((item) => item.goodsId === goodsId.value);
    if (!selected || !selected.parentCategoryName) return true;
    return !NON_WEARABLE_CATEGORIES.some((c) => selected.parentCategoryName!.includes(c));
  });

  return { goodsLoading, goodsOptions, remoteSearchGoods, isWearable };
}
