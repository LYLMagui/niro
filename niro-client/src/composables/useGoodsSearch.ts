import { computed, ref, type Ref } from "vue";
import { cs2GoodsApi } from "@/api/cs2-goods";

interface GoodsSearchOption {
  goodsId: number;
  name: string;
  parentCategoryName?: string;
}

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

export function useGoodsSearch(
  goodsId: Ref<number | undefined>,
  options?: { canViewGoods?: Ref<boolean> }
) {
  const goodsLoading = ref(false);
  const goodsOptions = ref<GoodsSearchOption[]>([]);

  const remoteSearchGoods = async (keyword: string) => {
    if (!keyword) return;
    if (options?.canViewGoods && !options.canViewGoods.value) {
      goodsOptions.value = [];
      return;
    }
    goodsLoading.value = true;
    try {
      const list = await cs2GoodsApi.getC5TaskOptions(keyword);
      goodsOptions.value = list.map((item) => ({
        goodsId: item.id,
        name: item.displayName,
        parentCategoryName: item.itemType,
      }));
    } finally {
      goodsLoading.value = false;
    }
  };

  const isWearable = computed(() => {
    const selected = goodsOptions.value.find((item) => item.goodsId === goodsId.value);
    if (!selected || !selected.parentCategoryName) return true;
    return !NON_WEARABLE_CATEGORIES.some((c) => selected.parentCategoryName!.includes(c));
  });

  return { goodsLoading, goodsOptions, remoteSearchGoods, isWearable };
}
