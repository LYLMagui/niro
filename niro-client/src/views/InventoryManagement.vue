<template>
  <PageFrame
    :is-mobile="isMobile"
    body-class="inventory-management-body"
    desktop-outer-class="!p-0"
    desktop-content-class="px-4 pt-0 pb-0"
    mobile-content-class="px-3 pt-3 pb-0"
  >
    <section class="overflow-hidden bg-white">
      <div class="flex flex-col gap-4 bg-white px-0 py-4">
        <div class="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,220px)_minmax(0,280px)_auto] xl:items-end">
          <label class="flex min-w-0 flex-col gap-1.5">
            <span class="text-sm font-medium text-slate-700">账号选择</span>
            <t-select
              v-model="selectedAccountId"
              :options="accountOptions"
              :loading="accountLoading"
              placeholder="请选择账号"
              class="w-full"
            />
          </label>
          <label class="flex min-w-0 flex-col gap-1.5">
            <span class="text-sm font-medium text-slate-700">名称搜索</span>
            <t-input
              v-model="searchKeyword"
              placeholder="输入饰品名称搜索"
              clearable
              class="w-full"
              @enter="handleSearch"
            />
          </label>
          <div class="flex flex-wrap items-center gap-2">
            <t-button theme="primary" class="action-btn" :loading="loading" @click="handleSearch">查询</t-button>
            <t-button variant="outline" theme="default" class="action-btn" :disabled="loading || refreshing" @click="handleReset">
              重置
            </t-button>
            <div class="flex-1"></div>
            <t-button
              v-if="canRefreshInventory"
              variant="outline"
              theme="primary"
              class="action-btn"
              :loading="refreshing"
              :disabled="loading || refreshableAccounts.length === 0"
              @click="handleRefresh"
            >
              <template #icon><t-icon name="refresh" :class="{ 'animate-spin': refreshing }" /></template>
              刷新库存
            </t-button>
          </div>
        </div>

        <div class="flex items-center justify-between border-b border-slate-100">
          <t-tabs v-model="activeTab" :list="tabs" class="inventory-tabs" />
          <div class="px-2 text-xs text-slate-400">共计 {{ totalCount }} 件饰品</div>
        </div>
      </div>
    </section>

    <div class="relative min-h-0 flex-1 overflow-y-auto bg-[#f3f3f3] -mx-4 px-4 py-4">
      <div v-if="loading" class="flex h-64 items-center justify-center">
        <t-loading size="medium" text="加载库存中..." />
      </div>

      <div v-else-if="inventory.length > 0" class="grid grid-cols-2 gap-x-3 gap-y-5 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 2xl:grid-cols-8">
        <div 
          v-for="item in inventory" 
          :key="getItemKey(item)" 
          class="relative group"
        >
          <!-- 堆叠效果底层 (更明显的叠层感) -->
          <div v-if="getQuantity(item) > 2" class="absolute inset-0 bg-white border border-slate-200 rounded-md translate-x-1.5 -translate-y-1.5 z-0 opacity-40 shadow-sm"></div>
          <div v-if="getQuantity(item) > 1" class="absolute inset-0 bg-white border border-slate-200 rounded-md translate-x-0.5 -translate-y-0.5 z-10 opacity-80 shadow-sm"></div>
          
          <!-- 主卡片 -->
          <div class="relative z-20 flex h-full flex-col overflow-hidden rounded-md border border-slate-200 bg-white transition-all hover:shadow-md hover:-translate-y-0.5 active:scale-[0.98]">
            <!-- 图片区域 -->
            <div class="relative aspect-[4/3] w-full bg-[#f2f2f2] flex items-center justify-center overflow-hidden">
              <img
                v-if="item.imageUrl"
                :src="item.imageUrl"
                :alt="getDisplayName(item)"
                referrerpolicy="no-referrer"
                class="h-full w-full object-contain mix-blend-multiply transition-transform group-hover:scale-105 duration-300"
              />
              <div v-else class="flex h-full w-full items-center justify-center text-xs text-slate-400">暂无图片</div>
              
              <!-- 左上角：磨损等级标签 -->
              <div v-if="typeof item.wear === 'number'" class="absolute top-0 left-0 z-30">
                <div 
                  class="px-1.5 py-0.5 text-[10px] text-white font-bold rounded-br-sm shadow-sm"
                  :style="{ backgroundColor: getFloatColor(item.wear) }"
                >
                  {{ getFloatName(item.wear) }}
                </div>
              </div>

              <!-- 右上角：冷却状态 -->
              <div class="absolute top-1.5 right-1.5 z-30 flex flex-col gap-1 items-end">
                <div v-if="!item.ifTradable" class="flex items-center gap-0.5 px-1.5 py-0.5 bg-black/30 text-white text-[10px] rounded-full backdrop-blur-[2px]">
                  <t-icon name="time" size="12px" />
                  <span>{{ formatTradableTime(item.tradableTime) }}</span>
                </div>
              </div>
              
              <!-- 底部：磨损展示区域 -->
              <div v-if="typeof item.wear === 'number'" class="absolute bottom-0 left-0 right-0 z-30">
                <!-- 上层：灰色数值背景 -->
                <div class="bg-[#808080] text-white text-[10px] px-1.5 py-0.5 tabular-nums leading-none font-medium flex items-center h-[18px]">
                  {{ formatWear(item.wear) }}
                </div>
                <!-- 下层：彩色磨损条 -->
                <div class="relative h-[6px] w-full flex">
                  <div class="h-full" style="width: 7%; background-color: #5b82bb;"></div>
                  <div class="h-full" style="width: 8%; background-color: #5bb35b;"></div>
                  <div class="h-full" style="width: 23%; background-color: #f4b254;"></div>
                  <div class="h-full" style="width: 7%; background-color: #cf665b;"></div>
                  <div class="h-full" style="width: 55%; background-color: #8d433d;"></div>
                  
                  <!-- 指示器 -->
                  <div 
                    class="absolute -top-[4px] z-10 -translate-x-1/2"
                    :style="{ left: `${Math.min(Math.max(item.wear, 0), 1) * 100}%` }"
                  >
                    <div class="w-0 h-0 border-l-[3px] border-l-transparent border-r-[3px] border-r-transparent border-t-[4px] border-t-white"></div>
                  </div>
                </div>
              </div>

              <!-- 数量角标 (右下角) -->
              <div v-if="getQuantity(item) > 1" class="absolute bottom-7 right-1 z-30 flex items-center gap-0.5 px-1 bg-black/50 text-white text-[10px] font-bold rounded shadow-sm backdrop-blur-[2px]">
                <t-icon name="layers" size="12px" />
                <span>{{ getQuantity(item) }}</span>
              </div>
            </div>

            <!-- 信息区域 -->
            <div class="flex flex-1 flex-col p-1.5">
              <t-tooltip :content="getDisplayName(item)" placement="top">
                <h3 class="line-clamp-2 h-8 text-[12px] font-normal leading-tight text-slate-700 mb-1 group-hover:text-blue-600 transition-colors">
                  {{ getDisplayName(item) }}
                </h3>
              </t-tooltip>

              <div class="mt-auto flex flex-col gap-1">
                <div class="flex items-end justify-between">
                  <span class="text-[15px] font-bold text-rose-600 leading-none">¥ {{ (item.price || 0).toLocaleString() }}</span>
                  <span :class="['shrink-0 text-[10px]', item.ifTradable ? 'text-emerald-600' : 'text-slate-500']">
                    {{ item.ifTradable ? "可交易" : "冷却中" }}
                  </span>
                </div>
                
                <div v-if="selectedAccountId === ALL_ACCOUNT_VALUE" class="flex items-center gap-1 text-[9px] text-blue-500/70 bg-blue-50/30 px-1 py-0.5 rounded border border-blue-100/20 w-fit max-w-full">
                  <span class="truncate">{{ item.accountName || "未命名账号" }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="flex flex-col items-center justify-center py-32">
        <t-empty description="暂无符合条件的饰品数据" />
      </div>

      <div class="mt-12 pb-8 text-center text-[10px] tracking-tighter text-slate-300">闽ICP备2025101529号-2</div>
    </div>
  </PageFrame>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useWindowSize } from "@vueuse/core";
import { MessagePlugin } from "tdesign-vue-next";
import { c5InventoryApi } from "@/api/c5-inventory";
import { c5SnipingAccountApi } from "@/api/c5-sniping-account";
import { PermissionConstant } from "@/constant/PermissionConstant";
import useNewPermission from "@/hooks/useNewPermission";
import PageFrame from "@/components/PageFrame.vue";
import type { C5InventoryItem, C5InventoryStatusFilter } from "@/types/c5-inventory";
import type { C5SnipingAccount } from "@/types/c5-sniping-account";

const ALL_ACCOUNT_VALUE = 0;

const { hasButtonPermission } = useNewPermission();
const { width } = useWindowSize();
const isMobile = computed(() => width.value <= 768);
const canRefreshInventory = computed(() => hasButtonPermission(PermissionConstant.C5_INVENTORY_REFRESH));

const loading = ref(false);
const accountLoading = ref(false);
const refreshing = ref(false);
const selectedAccountId = ref<number>(ALL_ACCOUNT_VALUE);
const searchKeyword = ref("");
const activeTab = ref<C5InventoryStatusFilter>("all");
const accounts = ref<C5SnipingAccount[]>([]);
const inventory = ref<C5InventoryItem[]>([]);
const totalCount = ref(0);

const accountOptions = computed(() => [
  { label: "所有账号", value: ALL_ACCOUNT_VALUE },
  ...accounts.value.map(account => ({
    label: account.accountName || `账号 ${account.id}`,
    value: account.id || ALL_ACCOUNT_VALUE,
  })),
]);

const refreshableAccounts = computed(() => accounts.value.filter(account => account.id && account.steamId));

const tabs: Array<{ label: string; value: C5InventoryStatusFilter }> = [
  { label: "全部", value: "all" },
  { label: "可交易", value: "tradable" },
  { label: "冷却中", value: "cooldown" },
];

const loadAccounts = async () => {
  accountLoading.value = true;
  try {
    accounts.value = await c5SnipingAccountApi.getAccounts();
  } catch (error) {
    console.error("加载 C5 账号列表失败", error);
    MessagePlugin.error("C5 账号列表加载失败");
  } finally {
    accountLoading.value = false;
  }
};

const loadInventory = async () => {
  loading.value = true;
  try {
    const res = await c5InventoryApi.getInventory({
      accountId: selectedAccountId.value === ALL_ACCOUNT_VALUE ? undefined : selectedAccountId.value,
      keyword: searchKeyword.value.trim() || undefined,
      status: activeTab.value,
      page: 1,
      pageSize: 200,
    });
    inventory.value = res.records || [];
    totalCount.value = res.itemTotal || 0;
  } catch (error) {
    console.error("加载 C5 库存失败", error);
    inventory.value = [];
    totalCount.value = 0;
    MessagePlugin.error("库存列表加载失败");
  } finally {
    loading.value = false;
  }
};

const getSelectedRefreshAccounts = () => {
  if (selectedAccountId.value === ALL_ACCOUNT_VALUE) {
    return refreshableAccounts.value;
  }
  return accounts.value.filter(account => account.id === selectedAccountId.value && account.steamId);
};

const handleSearch = () => {
  loadInventory();
};

const handleReset = () => {
  const accountChanged = selectedAccountId.value !== ALL_ACCOUNT_VALUE;
  selectedAccountId.value = ALL_ACCOUNT_VALUE;
  searchKeyword.value = "";
  activeTab.value = "all";
  if (!accountChanged) {
    loadInventory();
  }
};

const handleRefresh = async () => {
  const targets = getSelectedRefreshAccounts();
  if (targets.length === 0) {
    MessagePlugin.warning(selectedAccountId.value === ALL_ACCOUNT_VALUE ? "暂无已配置 Steam ID 的账号" : "当前账号未配置 Steam ID");
    return;
  }

  refreshing.value = true;
  try {
    const results = await Promise.all(targets.map(account => c5InventoryApi.refreshInventory({ accountId: account.id as number })));
    const total = results.reduce((sum, item) => sum + (item.total || 0), 0);
    MessagePlugin.success(targets.length === 1 ? "库存刷新成功" : `库存刷新完成，刷新 ${targets.length} 个账号，共 ${total} 件饰品`);
    await loadInventory();
  } catch (error) {
    console.error("刷新 C5 库存失败", error);
    MessagePlugin.error("刷新失败，请稍后重试");
  } finally {
    refreshing.value = false;
  }
};

const getDisplayName = (item: C5InventoryItem) => item.name || item.marketHashName || "未命名饰品";

const getItemKey = (item: C5InventoryItem) => `${item.accountId || "all"}-${item.assetId || item.id}`;

const getQuantity = (item: C5InventoryItem) => item.quantity || 1;

const formatWear = (value: number) => value.toFixed(15);

const formatTradableTime = (value?: string) => value || "不可交易";

const getFloatName = (value: number) => {
  if (value <= 0.07) return "崭新出厂";
  if (value <= 0.15) return "略有磨损";
  if (value <= 0.38) return "久经沙场";
  if (value <= 0.45) return "破损不堪";
  return "战痕累累";
};

const getFloatColor = (value: number) => {
  if (value <= 0.07) return "#5b82bb";
  if (value <= 0.15) return "#5bb35b";
  if (value <= 0.38) return "#f4b254";
  if (value <= 0.45) return "#cf665b";
  return "#8d433d";
};

watch([selectedAccountId, activeTab], () => {
  loadInventory();
});

onMounted(async () => {
  await loadAccounts();
  await loadInventory();
});
</script>

<style scoped>
:deep(.inventory-tabs.t-tabs) {
  background: transparent;
}

:deep(.inventory-tabs .t-tabs__nav-container) {
  margin-bottom: -1px;
}

:deep(.inventory-tabs .t-tabs__item) {
  padding: 12px 16px;
  font-size: 14px;
  color: #64748b;
}

:deep(.inventory-tabs .t-tabs__item.t-is-active) {
  color: var(--td-brand-color);
  font-weight: 600;
}

:deep(.inventory-card .t-card__body) {
  height: 100%;
}

.action-btn {
  min-width: 88px;
}

.line-clamp-2 {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}
</style>
