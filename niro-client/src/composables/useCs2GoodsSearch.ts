import { computed, ref, type Ref } from "vue";
import { cs2GoodsApi } from "@/api/cs2-goods";
import type { Cs2GoodsOption } from "@/types/cs2-goods";

export function useCs2GoodsSearch(
  selectedGoodsId: Ref<number | undefined>,
  options?: {
    canViewGoods?: Ref<boolean>;
    scene?: "unbox" | "task";
  }
) {
  const goodsLoading = ref(false);
  const goodsOptions = ref<Cs2GoodsOption[]>([]);
  let currentSearchToken = 0;
  let searchTimer: ReturnType<typeof setTimeout> | undefined;

  const remoteSearchGoods = (keyword: string) => {
    const normalizedKeyword = keyword.trim();
    const searchToken = ++currentSearchToken;
    if (searchTimer) {
      clearTimeout(searchTimer);
      searchTimer = undefined;
    }
    if (!normalizedKeyword) {
      goodsLoading.value = false;
      goodsOptions.value = [];
      return;
    }
    if (options?.canViewGoods && !options.canViewGoods.value) {
      goodsLoading.value = false;
      goodsOptions.value = [];
      return;
    }

    goodsLoading.value = true;
    searchTimer = setTimeout(async () => {
      try {
        const api = options?.scene === "unbox" ? cs2GoodsApi.getUnboxCaseOptions : cs2GoodsApi.getC5TaskOptions;
        const result = await api(normalizedKeyword);
        if (searchToken === currentSearchToken) {
          goodsOptions.value = result;
        }
      } finally {
        if (searchToken === currentSearchToken) {
          goodsLoading.value = false;
        }
      }
    }, 200);
  };

  const selectedGoods = computed(() =>
    goodsOptions.value.find((item) => item.id === selectedGoodsId.value)
  );

  const isWearable = computed(() => selectedGoods.value?.hasExterior ?? true);

  return {
    goodsLoading,
    goodsOptions,
    remoteSearchGoods,
    selectedGoods,
    isWearable,
  };
}
