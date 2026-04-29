<template>
  <PageFrame
    :is-mobile="isMobile"
    body-class="inventory-management-body"
    desktop-outer-class="!p-0"
    desktop-content-class="px-4 pt-0 pb-0"
    mobile-content-class="px-3 pt-3 pb-0"
  >
    <section class="overflow-hidden bg-white">
      <div class="flex flex-col gap-6 bg-white px-0 py-4">
        <PageHeader title="库存管理">
          <template #icon>
            <svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
            </svg>
          </template>
          <template #extra>
            <div class="flex flex-col items-end">
              <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400">库存总量</span>
              <span class="font-numeric text-base font-bold text-slate-900">{{ totalCount }} <small class="text-[10px] font-medium text-slate-400">件</small></span>
            </div>
            <div class="h-8 w-[1px] bg-slate-100"></div>
            <div class="flex flex-col items-end">
              <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400">库存估值</span>
              <span class="font-numeric text-base font-bold text-rose-500">{{ formatCurrency(totalValuation) }}</span>
            </div>
          </template>
        </PageHeader>


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
        </div>
      </div>
    </section>

    <div class="relative min-h-0 flex-1 overflow-y-auto bg-white -mx-4 px-4 py-4">
      <div v-if="loading" class="flex h-64 items-center justify-center">
        <t-loading size="medium" text="加载库存中..." />
      </div>

      <div v-else-if="inventory.length > 0" class="grid grid-cols-2 gap-x-3 gap-y-5 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 2xl:grid-cols-8">
        <div
          v-for="item in inventory"
          :key="getItemKey(item)"
          class="relative group cursor-pointer"
          @click="handleOpenSellDrawer(item)"
        >
          <!-- 堆叠效果底层 (更明显的叠层感) -->
          <div v-if="getQuantity(item) > 2" class="absolute inset-0 bg-white border border-slate-200/60 rounded-md translate-x-1.5 -translate-y-1.5 z-0 opacity-40 shadow-[0_2px_8px_rgba(0,0,0,0.04)]"></div>
          <div v-if="getQuantity(item) > 1" class="absolute inset-0 bg-white border border-slate-200/60 rounded-md translate-x-0.5 -translate-y-0.5 z-10 opacity-80 shadow-[0_2px_8px_rgba(0,0,0,0.04)]"></div>

          <!-- 主卡片 -->
          <div class="relative z-20 flex h-full flex-col overflow-hidden rounded-md border border-slate-200 bg-white shadow-[0_2px_12px_rgba(0,0,0,0.06)] transition-all duration-300 hover:shadow-[0_8px_24px_rgba(0,0,0,0.12)] hover:-translate-y-1.5 active:scale-[0.98]">
            <!-- 悬浮快捷上架按钮 (仅在桌面端显示) -->
            <div class="absolute inset-0 z-40 hidden lg:flex items-center justify-center bg-slate-900/0 opacity-0 group-hover:bg-slate-900/5 group-hover:opacity-100 transition-all duration-300 pointer-events-none">
              <t-button theme="primary" size="small" class="shadow-xl !rounded-full pointer-events-auto translate-y-2 group-hover:translate-y-0 transition-transform">
                上架饰品
              </t-button>
            </div>
            <!-- 图片区域 -->
            <div class="relative aspect-[4/3] w-full bg-slate-50 flex items-center justify-center overflow-hidden">
              <!-- 聚光灯光晕背景 -->
              <div class="absolute inset-0 bg-[radial-gradient(circle_at_50%_50%,rgba(255,255,255,1)_0%,rgba(241,245,249,1)_100%)]"></div>

              <img
                v-if="item.imageUrl"
                :src="item.imageUrl"
                :alt="getDisplayName(item)"
                referrerpolicy="no-referrer"
                class="relative z-10 h-full w-full object-contain mix-blend-multiply transition-transform group-hover:scale-105 duration-300"
              />
              <div v-else class="relative z-10 flex h-full w-full items-center justify-center text-xs text-slate-400">暂无图片</div>

              <!-- 左上角：磨损等级标签 -->
              <div v-if="shouldShowWear(item)" class="absolute top-0 left-0 z-30">
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
              <div v-if="shouldShowWear(item)" class="absolute bottom-0 left-0 right-0 z-30">
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

      <div v-if="pagination.total > 0" class="mt-4 pb-4 flex justify-center">
        <t-pagination
          v-model="pagination.current"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-size-options="pageSizeOptions"
          :disabled="loading"
          show-jumper
          @change="handlePageChange"
        />
      </div>
    </div>

    <!-- 上架定价抽屉 -->
    <t-drawer
      v-model:visible="sellDrawerVisible"
      :header="getDrawerTitle"
      :size="drawerWidth"
      destroy-on-close
      class="sell-drawer"
    >
      <template v-if="sellingItem">
        <div class="flex flex-col h-full overflow-hidden">
          <!-- 1. 饰品摘要 (极简压缩版) -->
          <div class="mb-4 p-3 bg-slate-50/80 border border-slate-100 flex-shrink-0">
            <div class="flex items-center gap-3">
              <div class="w-12 h-12 rounded-lg bg-white border border-slate-100 p-1 shadow-sm overflow-hidden flex-shrink-0">
                <img :src="sellingItem.imageUrl" class="w-full h-full object-contain mix-blend-multiply" />
              </div>
              <div class="min-w-0 flex-1">
                <div class="flex items-center justify-between gap-2">
                  <h3 class="text-[13px] font-bold text-slate-900 truncate">{{ getDisplayName(sellingItem) }}</h3>
                  <t-tag size="small" variant="light-outline" theme="default" class="flex-shrink-0 scale-90">
                    共 {{ getQuantity(sellingItem) }} 件
                  </t-tag>
                </div>
                <div class="mt-1 flex items-center gap-3 text-[11px]">
                  <div class="flex items-center gap-1">
                    <span class="text-slate-400">平均买入:</span>
                    <span class="font-numeric text-slate-600 font-bold">¥{{ (sellingItem.price || 0).toLocaleString() }}</span>
                  </div>
                  <span class="text-slate-200">|</span>
                  <div class="flex items-center gap-1">
                    <span class="text-slate-400">类别:</span>
                    <span class="text-slate-600">{{ sellingItem.itemTypeName || '普通饰品' }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 2. 定价工作台 -->
          <div class="flex-none max-h-[320px] flex flex-col mb-4 overflow-hidden">
            <div class="flex items-center justify-between mb-2">
              <div class="flex items-center gap-2">
                <span class="text-sm font-bold text-slate-800">{{ getQuantity(sellingItem) > 1 ? '批量定价工作台' : '定价工作台' }}</span>
                <t-tag size="small" variant="light" theme="default" class="font-numeric font-bold !text-slate-500 !bg-slate-100/80">{{ selectedSubItemIds.size }}/{{ mockSubItems.length }} 已选</t-tag>
              </div>
              <div v-if="getQuantity(sellingItem) > 1" class="flex items-center gap-3">
                <div class="flex items-center bg-blue-50/50 rounded px-2.5 py-1 border border-blue-100 transition-all hover:bg-blue-50 hover:border-blue-300 group">
                  <span class="text-[11px] font-bold text-blue-500/70 mr-1.5 whitespace-nowrap">全局价</span>
                  <div class="flex items-center gap-0.5">
                    <span class="text-[10px] font-bold text-blue-400 mt-0.5">¥</span>
                    <input
                      v-model="sellPrice"
                      type="text"
                      class="w-16 bg-transparent border-none text-[13px] font-bold text-blue-700 focus:outline-none tabular-nums placeholder:text-blue-200"
                      placeholder="0.00"
                    />
                  </div>
                </div>
                <t-checkbox :checked="selectedSubItemIds.size === mockSubItems.length" @change="handleSelectAllSubItems">
                  <span class="text-[12px] font-bold text-slate-600">全选</span>
                </t-checkbox>
                <t-button
                  theme="primary"
                  variant="outline"
                  size="small"
                  class="!h-[28px] !px-2.5 !text-[11px] font-bold !border-blue-200 !bg-blue-50/50 hover:!bg-blue-100/50"
                  @click="applyGlobalPriceToSelected"
                >
                  <template #icon><t-icon name="check-double" size="14px" /></template>
                  同步全局价
                </t-button>
              </div>
            </div>

            <div class="flex-1 overflow-hidden border border-slate-200 bg-slate-50/30">
              <t-table
                :data="mockSubItems"
                :columns="subItemColumns"
                size="small"
                row-key="assetId"
                :bordered="false"
                hover
                height="100%"
                class="sub-item-pricing-table"
                :loading="inventoryItemsLoading"
              >
                <template #selection="{ row }">
                  <t-checkbox :checked="selectedSubItemIds.has(row.assetId)" @change="() => toggleSubItem(row)" />
                </template>

                <template #wear="{ row }">
                  <div class="flex flex-col gap-0.5">
                    <span class="font-numeric text-[11px] leading-tight font-bold" :class="getWearColorClass(row.wear || 0)">
                      {{ formatAssetWear(row.wear) }}
                    </span>
                    <span class="text-[10px] font-bold text-slate-400/80">{{ getWearName(row.wear || 0) }}</span>
                  </div>
                </template>

                <template #price="{ row }">
                  <div class="flex items-center gap-1">
                    <span class="text-blue-400 text-[10px] font-bold mt-0.5">¥</span>
                    <t-input
                      v-model="row.sellPrice"
                      size="small"
                      placeholder="价格"
                      class="!w-22 !font-bold !text-blue-700"
                      @update:model-value="() => scheduleSubItemListingFeeCalculate(row)"
                    />
                    <t-tooltip :content="queryTargetItem?.assetId === row.assetId ? '当前正在查看此项参考' : '按该项磨损匹配参考价'">
                      <t-button
                        variant="text"
                        shape="square"
                        size="small"
                        :theme="queryTargetItem?.assetId === row.assetId ? 'primary' : 'default'"
                        :class="queryTargetItem?.assetId === row.assetId ? 'bg-blue-50' : 'text-slate-400 hover:text-blue-600'"
                        @click="matchPriceForItem(row)"
                      >
                        <template #icon><t-icon name="gesture-click" /></template>
                      </t-button>
                    </t-tooltip>
                  </div>
                </template>

                <template #fee="{ row }">
                  <span class="text-[11px] font-bold text-slate-600">
                    {{ getSubItemFeeText(row) }}
                  </span>
                </template>

                <template #netPrice="{ row }">
                  <span class="text-[11px] font-bold text-emerald-600">
                    {{ getSubItemNetPriceText(row) }}
                  </span>
                </template>

                <template #status="{ row }">
                  <t-tag v-if="row.ifTradable" theme="success" variant="light-outline" size="small" class="scale-90">可交易</t-tag>
                  <t-tag v-else theme="warning" variant="light-outline" size="small" class="scale-90">冷却中</t-tag>
                </template>

                <template #op="{ row }">
                  <t-button variant="text" theme="primary" size="small" @click.stop="handleConfirmSingleSell(row)">
                    上架
                  </t-button>
                </template>
              </t-table>
            </div>
          </div>

          <!-- 3. C5 市场参考 (核心参考区) -->
          <div class="flex-1 min-h-0 flex flex-col overflow-hidden">
            <div class="flex items-center justify-between mb-3">
              <div class="flex items-center gap-2">
                <div class="w-1 h-4 bg-blue-600 rounded-full"></div>
                <span class="text-sm font-bold text-slate-800">C5 平台同磨损参考</span>
              </div>
              <div class="flex items-center gap-3">
                <t-button v-if="queryTargetItem" variant="outline" size="small" theme="default" @click="handleBackToGlobalReference">
                  返回全局参考
                </t-button>
                <t-button variant="text" size="small" theme="primary" :loading="marketLoading" @click="refreshMarketReferences">
                  <template #icon><t-icon name="refresh" /></template>
                  刷新数据
                </t-button>
              </div>
            </div>

            <!-- 1:1 复刻开箱记录的筛选区域 -->
            <div class="border border-slate-100 bg-slate-50/40 p-3 mb-4">
              <div class="flex flex-col gap-3">
                <div class="flex items-center justify-between">
                  <t-select
                    v-model="marketRangeKey"
                    placeholder="请选择筛选区间"
                    size="small"
                    class="flex-1 max-w-[220px]"
                    :options="dynamicRangeOptions"
                  />

                  <t-button variant="text" size="small" theme="primary" :loading="marketLoading" @click="refreshMarketReferences">
                    <template #icon><t-icon name="refresh" /></template>
                    刷新
                  </t-button>
                </div>

                <div v-if="marketRangeKey === 'custom'" class="flex items-center gap-2 animate-fade-in bg-white p-2 rounded-lg border border-blue-100">
                  <t-input-number
                    v-model="marketCustomMin"
                    :decimal-places="4"
                    placeholder="Min"
                    size="small"
                    class="flex-1"
                  />
                  <span class="text-slate-300">-</span>
                  <t-input-number
                    v-model="marketCustomMax"
                    :decimal-places="4"
                    placeholder="Max"
                    size="small"
                    class="flex-1"
                  />
                  <t-button theme="primary" size="small" @click="refreshMarketReferences(true)">查询</t-button>
                </div>

                <div class="flex flex-col gap-1 px-1">
                  <div class="flex items-center justify-between text-[11px]">
                    <span class="text-slate-400">
                      {{ queryTargetItem ? '指定项磨损:' : '当前磨损:' }}
                      <span class="font-numeric font-medium" :class="queryTargetItem ? 'text-blue-600' : 'text-slate-600'">
                        {{ (queryTargetItem?.wear || sellingItem?.wear)?.toFixed(15) || '--' }}
                      </span>
                    </span>
                    <span class="text-slate-400">当前区间: <span class="text-blue-600 font-medium">{{ getActiveRangeText() }}</span></span>
                  </div>
                </div>
              </div>
            </div>

            <div class="flex-1 min-h-0 overflow-hidden border border-slate-100 bg-white shadow-sm flex flex-col">
              <t-table
                :data="mockMarketPrices"
                :columns="marketRefColumns"
                size="small"
                row-key="productId"
                :bordered="false"
                hover
                height="320"
                class="market-ref-table flex-1"
                :loading="marketLoading"
                :pagination="null"
              >
                <template #wear="{ row, rowIndex }">
                  <div class="flex items-center gap-2">
                    <span class="font-numeric text-[12px] text-slate-600">
                      {{ formatMarketWear(row.wear) }}
                    </span>
                    <t-tag
                      v-if="rowIndex === closestWearIndex"
                      theme="primary"
                      variant="light"
                      size="small"
                      class="text-[9px] h-4 leading-3 px-1"
                    >
                      最接近
                    </t-tag>
                  </div>
                </template>
                <template #price="{ row }">
                  <span class="font-numeric font-bold text-rose-500 text-[13px]">
                    ¥ {{ row.price.toFixed(2) }}
                  </span>
                </template>
                <template #op="{ row }">
                  <t-button
                    variant="text"
                    theme="primary"
                    size="small"
                    class="!px-0 font-bold"
                    @click="handleFillMarketPrice(row.price)"
                  >
                    填入
                  </t-button>
                </template>
              </t-table>

              <div v-if="marketPagination.current > 1 || mockMarketPrices.length > 0 || marketHasMore" class="border-t border-slate-50 p-2 bg-slate-50/50 flex items-center justify-between">
                <t-button
                  variant="outline"
                  size="small"
                  theme="default"
                  :disabled="marketPagination.current <= 1 || marketLoading"
                  @click="handleMarketPreviousPage"
                >
                  上一页
                </t-button>
                <span class="text-[11px] text-slate-400">
                  第 {{ marketPagination.current }} 页
                  <span v-if="!marketLoading && mockMarketPrices.length === 0" class="ml-2 text-slate-300">当前页无匹配</span>
                </span>
                <t-button
                  v-if="marketHasMore"
                  variant="outline"
                  size="small"
                  theme="primary"
                  :loading="marketLoading"
                  @click="handleMarketNextPage"
                >
                  下一页
                </t-button>
                <span v-else class="w-[52px]"></span>
              </div>
            </div>

            <div class="mt-3 px-1 text-[10px] text-slate-400 italic">
              * 数据来自 C5 同平台在售挂单，仅作为当前上架定价参考
            </div>
          </div>
        </div>
      </template>

      <template #footer>
        <div class="w-full px-4 py-2">
          <t-button
            theme="primary"
            block
            size="large"
            class="shadow-lg shadow-blue-100"
            :loading="listingSubmitting"
            :disabled="listingSubmitting || inventoryItemsLoading"
            @click="handleConfirmSell"
          >
            {{ selectedSubItemIds.size > 1 ? `确认批量上架 ${selectedSubItemIds.size} 件饰品` : '确认上架饰品' }}
          </t-button>
        </div>
      </template>
    </t-drawer>
  </PageFrame>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useWindowSize } from "@vueuse/core";
import { MessagePlugin, type PageInfo, type PrimaryTableCol } from "tdesign-vue-next";
import { c5InventoryApi } from "@/api/c5-inventory";
import { c5SnipingAccountApi } from "@/api/c5-sniping-account";
import { PermissionConstant } from "@/constant/PermissionConstant";
import useNewPermission from "@/hooks/useNewPermission";
import PageFrame from "@/components/PageFrame.vue";
import PageHeader from "@/components/PageHeader.vue";
import type {
  C5InventoryAsset,
  C5InventoryItem,
  C5InventoryListingFeeResult,
  C5InventoryMarketReference,
  C5InventoryStatusFilter,
} from "@/types/c5-inventory";
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
const pagination = ref({ current: 1, pageSize: 40, total: 0 });
const marketPagination = ref({ current: 1, pageSize: 10, total: 0 });
const marketHasMore = ref(false);
const pageSizeOptions = [40, 80, 120];
const inventoryStats = ref<Record<C5InventoryStatusFilter, number>>({
  all: 0,
  tradable: 0,
  cooldown: 0,
  selling: 0,
});
const totalValuation = ref(0);
const sellDrawerVisible = ref(false);
const sellingItem = ref<C5InventoryItem | null>(null);
const sellPrice = ref("");
const mockMarketPrices = ref<C5InventoryMarketReference[]>([]);
const mockSubItems = ref<C5InventoryAsset[]>([]);
const selectedSubItemIds = ref<Set<string>>(new Set());
const queryTargetItem = ref<C5InventoryAsset | null>(null);
const inventoryItemsLoading = ref(false);
const listingSubmitting = ref(false);
const subItemFeeTimers = new Map<number, number>();
const subItemFeeRequestVersions = new Map<number, number>();

// 动态抽屉宽度
const drawerWidth = computed(() => {
  return isMobile.value ? '100%' : '820px';
});

const getDrawerTitle = computed(() => {
  if (getQuantity(sellingItem.value) > 1) return `批量定价上架 - ${getDisplayName(sellingItem.value)}`;
  return '饰品定价上架';
});

const subItemColumns: PrimaryTableCol[] = [
  { colKey: "selection", title: "", width: 42, fixed: "left" },
  { colKey: "wear", title: "磨损度", width: 140 },
  { colKey: "price", title: "上架价格", width: 150 },
  { colKey: "fee", title: "手续费", width: 100, align: "right" },
  { colKey: "netPrice", title: "到手价", width: 100, align: "right" },
  { colKey: "status", title: "状态", width: 80, align: "center" },
  { colKey: "op", title: "操作", width: 60, align: "right", fixed: "right" },
];

const getWearColorClass = (wear: number) => {
  if (wear < 0.07) return "text-blue-600 font-bold";
  if (wear < 0.15) return "text-green-600";
  if (wear < 0.38) return "text-orange-500";
  return "text-slate-500";
};

const getWearName = (wear: number) => {
  if (wear < 0.07) return "崭新出厂";
  if (wear < 0.15) return "略有磨损";
  if (wear < 0.38) return "久经沙场";
  if (wear < 0.45) return "破损不堪";
  return "战痕累累";
};

const formatAssetWear = (wear?: number) => typeof wear === "number" ? wear.toFixed(15) : "-";

const applyGlobalPriceToSelected = () => {
  if (!sellPrice.value) return;
  const selectedItems = mockSubItems.value.filter(item => selectedSubItemIds.value.has(item.assetId));
  selectedItems.forEach(item => {
    item.sellPrice = sellPrice.value;
  });
  calculateListingFeesForItems(selectedItems);
  MessagePlugin.success("已应用全局价格到所选项");
};

const matchPriceForItem = async (row: C5InventoryAsset) => {
  queryTargetItem.value = row;
  marketPagination.value.current = 1;

  const wear = row.wear ?? 0;
  const matched = EXTERIOR_RANGES.find(r => r.value !== 'all' && r.value !== 'custom' && wear >= r.min && wear < r.max);
  if (matched) {
    marketRangeKey.value = matched.value;
  }

  await refreshMarketReferences();
  MessagePlugin.success(`已切换至磨损 ${wear.toFixed(4)} 的市场参考`);
};

const handleFillMarketPrice = (price: number) => {
  const priceStr = price.toString();
  const targetItem = queryTargetItem.value;
  if (targetItem) {
    // 填入指定行
    const target = mockSubItems.value.find(item => item.assetId === targetItem.assetId);
    if (target) {
      target.sellPrice = priceStr;
      scheduleSubItemListingFeeCalculate(target);
      MessagePlugin.success("参考价已填入指定饰品");
    }
  } else if (mockSubItems.value.length === 1) {
    // 只有一个商品时，当作填入指定行
    const target = mockSubItems.value[0];
    target.sellPrice = priceStr;
    scheduleSubItemListingFeeCalculate(target);
    MessagePlugin.success("参考价已填入饰品");
  } else {
    // 填入全局
    sellPrice.value = priceStr;
    MessagePlugin.success("参考价已填入全局定价");
  }
};

// C5 市场参考过滤状态 (对齐开箱记录)
const marketRangeKey = ref("all");
const marketCustomMin = ref<number>();
const marketCustomMax = ref<number>();
const marketLoading = ref(false);

const EXTERIOR_RANGES = [
  { label: "不筛选磨损 (按价格)", value: "all", min: 0, max: 1 },
  { label: "工厂新锐 (0.00-0.07)", value: "fn_all", min: 0, max: 0.07 },
  { label: "0.00 - 0.01", value: "fn_1", min: 0, max: 0.01 },
  { label: "0.01 - 0.02", value: "fn_2", min: 0.01, max: 0.02 },
  { label: "0.02 - 0.03", value: "fn_3", min: 0.02, max: 0.03 },
  { label: "0.03 - 0.04", value: "fn_4", min: 0.03, max: 0.04 },
  { label: "0.04 - 0.07", value: "fn_5", min: 0.04, max: 0.07 },
  { label: "略有磨损 (0.07-0.15)", value: "mw_all", min: 0.07, max: 0.15 },
  { label: "0.07 - 0.09", value: "mw_1", min: 0.07, max: 0.09 },
  { label: "0.09 - 0.11", value: "mw_2", min: 0.09, max: 0.11 },
  { label: "0.11 - 0.13", value: "mw_3", min: 0.11, max: 0.13 },
  { label: "0.13 - 0.15", value: "mw_4", min: 0.13, max: 0.15 },
  { label: "久经沙场 (0.15-0.38)", value: "ft_all", min: 0.15, max: 0.38 },
  { label: "0.15 - 0.20", value: "ft_1", min: 0.15, max: 0.20 },
  { label: "0.20 - 0.25", value: "ft_2", min: 0.20, max: 0.25 },
  { label: "0.25 - 0.30", value: "ft_3", min: 0.25, max: 0.30 },
  { label: "0.30 - 0.38", value: "ft_4", min: 0.30, max: 0.38 },
  { label: "破损不堪 (0.38-0.45)", value: "ww_all", min: 0.38, max: 0.45 },
  { label: "战痕累累 (0.45-1.00)", value: "bs_all", min: 0.45, max: 1.00 },
  { label: "自定义区间", value: "custom", min: 0, max: 1 },
];

const dynamicRangeOptions = computed(() => {
  const currentWear = queryTargetItem.value?.wear ?? sellingItem.value?.wear;
  if (typeof currentWear !== "number") {
    return EXTERIOR_RANGES.filter(range => ["mw_all", "custom"].includes(range.value));
  }

  const currentExteriorRange = EXTERIOR_RANGES.find(range =>
    range.value !== "all" && range.value.endsWith("_all") && currentWear >= range.min && currentWear < range.max
  );
  const currentExteriorPrefix = currentExteriorRange?.value.replace("_all", "");
  return EXTERIOR_RANGES.filter(range => {
    if (range.value === "custom" || range.value === "mw_all") return true;
    return Boolean(currentExteriorPrefix && range.value.startsWith(`${currentExteriorPrefix}_`));
  });
});

const getActiveRangeText = () => {
  const current = EXTERIOR_RANGES.find(r => r.value === marketRangeKey.value);
  if (marketRangeKey.value === 'custom') {
    if (marketCustomMin.value !== undefined && marketCustomMax.value !== undefined) {
      return `${marketCustomMin.value.toFixed(4)} - ${marketCustomMax.value.toFixed(4)}`;
    }
    return '未设置';
  }
  return current ? current.label : '未知';
};

const currencyFormatter = new Intl.NumberFormat("zh-CN", {
  style: "currency",
  currency: "CNY",
  minimumFractionDigits: 2,
});

const formatCurrency = (value: number) => currencyFormatter.format(value);

const formatMarketWear = (wear?: number) => typeof wear === "number" ? wear.toFixed(15) : "-";

const closestWearIndex = computed(() => {
  if (!sellingItem.value || mockMarketPrices.value.length === 0) return -1;
  let minDiff = Infinity;
  let index = -1;
  const targetWear = (queryTargetItem.value?.wear ?? sellingItem.value?.wear) ?? 0;
  mockMarketPrices.value.forEach((item, i) => {
    if (typeof item.wear !== "number") return;
    const diff = Math.abs(item.wear - targetWear);
    if (diff < minDiff) {
      minDiff = diff;
      index = i;
    }
  });
  return index;
});

const marketRefColumns: PrimaryTableCol[] = [
  { colKey: "wear", title: "磨损度 (Wear)", ellipsis: true },
  { colKey: "price", title: "价格", width: 100 },
  { colKey: "op", title: "操作", width: 60, align: "right" },
];

const handleOpenSellDrawer = async (item: C5InventoryItem) => {
  sellingItem.value = item;
  sellPrice.value = (item.price || 0).toString();
  queryTargetItem.value = null;
  selectedSubItemIds.value = new Set();
  mockSubItems.value = [];
  resetSubItemFeeCalculations();

  const wear = item.wear || 0;
  const matched = EXTERIOR_RANGES.find(r => r.value !== 'all' && r.value !== 'custom' && wear >= r.min && wear < r.max);
  marketRangeKey.value = matched ? matched.value : 'all';
  marketPagination.value.current = 1;

  sellDrawerVisible.value = true;
  await loadInventoryItems(item);
  refreshMarketReferences();
};

const loadInventoryItems = async (item: C5InventoryItem) => {
  if (!item.accountId) {
    MessagePlugin.warning("当前库存缺少账号信息，无法上架");
    return;
  }

  inventoryItemsLoading.value = true;
  try {
    const res = await c5InventoryApi.getInventoryItems({
      accountId: item.accountId,
      marketHashName: item.marketHashName,
      name: item.name,
      exteriorName: item.exteriorName,
      ifTradable: item.ifTradable,
      page: 1,
      pageSize: Math.max(getQuantity(item), 200),
    });
    mockSubItems.value = (res.records || []).map(asset => ({
      ...asset,
      sellPrice: (asset.price ?? item.price ?? 0).toString(),
    }));
    mockSubItems.value.forEach(asset => selectedSubItemIds.value.add(asset.assetId));
    if (mockSubItems.value.length === 0) {
      MessagePlugin.warning("未查询到可上架的库存明细");
    } else {
      await calculateListingFeesForItems(mockSubItems.value);
    }
  } catch (error) {
    console.error("加载库存明细失败", error);
    MessagePlugin.error("库存明细加载失败");
  } finally {
    inventoryItemsLoading.value = false;
  }
};

const toggleSubItem = (sub: { assetId: string }) => {
  if (selectedSubItemIds.value.has(sub.assetId)) {
    selectedSubItemIds.value.delete(sub.assetId);
  } else {
    selectedSubItemIds.value.add(sub.assetId);
  }
};

const handleSelectAllSubItems = () => {
  if (selectedSubItemIds.value.size === mockSubItems.value.length) {
    selectedSubItemIds.value.clear();
  } else {
    mockSubItems.value.forEach(sub => selectedSubItemIds.value.add(sub.assetId));
  }
};

const handleBackToGlobalReference = () => {
  queryTargetItem.value = null;
  refreshMarketReferences(true);
};

const buildMarketReferenceParams = () => {
  const item = sellingItem.value;
  if (!item?.accountId) {
    MessagePlugin.warning("当前库存缺少账号信息，无法查询参考价");
    return null;
  }
  const marketHashName = queryTargetItem.value?.marketHashName || item.marketHashName;
  if (!marketHashName) {
    MessagePlugin.warning("当前库存缺少 marketHashName，无法查询参考价");
    return null;
  }

  const params: {
    accountId: number;
    marketHashName: string;
    wear?: number;
    wearMin?: number;
    wearMax?: number;
    pageNum: number;
    pageSize: number;
  } = {
    accountId: item.accountId,
    marketHashName,
    pageNum: marketPagination.value.current,
    pageSize: marketPagination.value.pageSize,
  };

  if (marketRangeKey.value === 'custom') {
    if (marketCustomMin.value === undefined || marketCustomMax.value === undefined) {
      MessagePlugin.warning("请填写完整的自定义磨损区间");
      return null;
    }
    params.wearMin = marketCustomMin.value;
    params.wearMax = marketCustomMax.value;
    return params;
  }

  const currentRange = EXTERIOR_RANGES.find(r => r.value === marketRangeKey.value);
  if (currentRange && currentRange.value !== 'all') {
    params.wearMin = currentRange.min;
    params.wearMax = currentRange.max;
    return params;
  }

  const wear = queryTargetItem.value?.wear ?? item.wear;
  if (typeof wear === "number") {
    params.wear = wear;
  }
  return params;
};

const refreshMarketReferences = async (resetPage = false) => {
  if (resetPage) {
    marketPagination.value.current = 1;
  }
  const params = buildMarketReferenceParams();
  if (!params) return;

  marketHasMore.value = false;
  marketLoading.value = true;
  try {
    const res = await c5InventoryApi.getMarketReferences(params);
    mockMarketPrices.value = (res.records || []).sort((a, b) => {
      const priceDiff = a.price - b.price;
      if (priceDiff !== 0) return priceDiff;
      return (a.wear ?? 1) - (b.wear ?? 1);
    });
    const currentCount = res.records?.length || 0;
    marketPagination.value.current = res.pageNum || marketPagination.value.current;
    marketPagination.value.pageSize = res.pageSize || marketPagination.value.pageSize;
    marketPagination.value.total = (marketPagination.value.current - 1) * marketPagination.value.pageSize + currentCount;
    marketHasMore.value = Boolean(res.hasMore);
  } catch (error) {
    console.error("查询 C5 同平台参考失败", error);
    MessagePlugin.error("C5 同平台参考查询失败");
  } finally {
    marketLoading.value = false;
  }
};

// 监听筛选切换
watch(marketRangeKey, (newVal) => {
  if (newVal !== 'custom') {
    refreshMarketReferences(true);
  }
});

const handleMarketPreviousPage = () => {
  if (marketPagination.value.current <= 1 || marketLoading.value) return;
  marketPagination.value.current -= 1;
  refreshMarketReferences();
};

const handleMarketNextPage = () => {
  if (!marketHasMore.value || marketLoading.value) return;
  marketPagination.value.current += 1;
  refreshMarketReferences();
};

const parseSellPrice = (value?: string) => {
  const price = Number(value);
  return Number.isFinite(price) && price > 0 ? Math.round(price * 100) / 100 : 0;
};

const resetSubItemFeeCalculations = () => {
  subItemFeeTimers.forEach(timer => window.clearTimeout(timer));
  subItemFeeTimers.clear();
  subItemFeeRequestVersions.clear();
};

const getSubItemFeeText = (item: C5InventoryAsset) => {
  if (item.listingFeeLoading) return "计算中...";
  if (item.listingFeeError) return "计算失败";
  return typeof item.listingFee === "number" ? formatCurrency(item.listingFee) : "--";
};

const getSubItemNetPriceText = (item: C5InventoryAsset) => {
  if (item.listingFeeLoading) return "计算中...";
  if (item.listingFeeError) return "--";
  const price = parseSellPrice(item.sellPrice);
  if (typeof item.listingSellerPrice === "number") return formatCurrency(item.listingSellerPrice);
  if (typeof item.listingFee === "number" && price > 0) return formatCurrency(price - item.listingFee);
  return "--";
};

const applyListingFeeResult = (item: C5InventoryAsset, feeResult?: C5InventoryListingFeeResult) => {
  item.listingFee = feeResult?.fee;
  item.listingSellerPrice = feeResult?.sellerPrice ?? feeResult?.income ?? feeResult?.actualAmount;
  item.listingFeeError = false;
};

const clearListingFeeResult = (item: C5InventoryAsset) => {
  item.listingFee = undefined;
  item.listingSellerPrice = undefined;
  item.listingFeeError = false;
  item.listingFeeLoading = false;
};

const calculateListingFeesForItems = async (items: C5InventoryAsset[]) => {
  const item = sellingItem.value;
  if (!item?.accountId) return;

  const validItems = items.filter(asset => parseSellPrice(asset.sellPrice) > 0);
  items.filter(asset => parseSellPrice(asset.sellPrice) <= 0).forEach(clearListingFeeResult);
  if (validItems.length === 0) return;

  const versionMap = new Map<number, number>();
  validItems.forEach(asset => {
    const timer = subItemFeeTimers.get(asset.id);
    if (timer) {
      window.clearTimeout(timer);
      subItemFeeTimers.delete(asset.id);
    }
    const version = (subItemFeeRequestVersions.get(asset.id) || 0) + 1;
    subItemFeeRequestVersions.set(asset.id, version);
    versionMap.set(asset.id, version);
    asset.listingFeeLoading = true;
    asset.listingFeeError = false;
  });
  try {
    const results = await c5InventoryApi.calculateListingFees({
      accountId: item.accountId,
      items: validItems.map(asset => ({
        inventoryItemId: asset.id,
        price: parseSellPrice(asset.sellPrice),
      })),
    });
    const resultMap = new Map(results.map(result => [result.inventoryItemId, result]));
    validItems.forEach(asset => {
      if (subItemFeeRequestVersions.get(asset.id) === versionMap.get(asset.id)) {
        applyListingFeeResult(asset, resultMap.get(asset.id));
      }
    });
  } catch (error) {
    console.error("批量计算 C5 上架手续费失败", error);
    validItems.forEach(asset => {
      if (subItemFeeRequestVersions.get(asset.id) === versionMap.get(asset.id)) {
        asset.listingFeeError = true;
      }
    });
    MessagePlugin.error("批量手续费计算失败，请稍后重试");
  } finally {
    validItems.forEach(asset => {
      if (subItemFeeRequestVersions.get(asset.id) === versionMap.get(asset.id)) {
        asset.listingFeeLoading = false;
      }
    });
  }
};

const calculateListingFeeForItem = async (item: C5InventoryAsset) => {
  const accountId = sellingItem.value?.accountId;
  const price = parseSellPrice(item.sellPrice);
  if (!accountId || price <= 0) {
    clearListingFeeResult(item);
    return;
  }

  const version = (subItemFeeRequestVersions.get(item.id) || 0) + 1;
  subItemFeeRequestVersions.set(item.id, version);
  item.listingFeeLoading = true;
  item.listingFeeError = false;
  try {
    const results = await c5InventoryApi.calculateListingFees({
      accountId,
      items: [{ inventoryItemId: item.id, price }],
    });
    if (subItemFeeRequestVersions.get(item.id) !== version) return;
    applyListingFeeResult(item, results[0]);
  } catch (error) {
    if (subItemFeeRequestVersions.get(item.id) !== version) return;
    console.error("计算 C5 单项上架手续费失败", error);
    item.listingFeeError = true;
  } finally {
    if (subItemFeeRequestVersions.get(item.id) === version) {
      item.listingFeeLoading = false;
    }
  }
};

const scheduleSubItemListingFeeCalculate = (item: C5InventoryAsset) => {
  const timer = subItemFeeTimers.get(item.id);
  if (timer) {
    window.clearTimeout(timer);
  }
  subItemFeeTimers.set(item.id, window.setTimeout(() => {
    calculateListingFeeForItem(item);
  }, 400));
};

const submitListings = async (items: C5InventoryAsset[]) => {
  const item = sellingItem.value;
  if (!item?.accountId) {
    MessagePlugin.warning("当前库存缺少账号信息，无法上架");
    return;
  }
  if (items.length === 0) {
    MessagePlugin.warning("请至少选择一件饰品");
    return;
  }

  const listingItems = items.map(asset => ({
    inventoryItemId: asset.id,
    price: parseSellPrice(asset.sellPrice || sellPrice.value),
  }));
  if (listingItems.some(asset => asset.price <= 0)) {
    MessagePlugin.warning("请填写有效的上架价格");
    return;
  }

  listingSubmitting.value = true;
  try {
    const result = await c5InventoryApi.createInventoryListings({
      accountId: item.accountId,
      description: "",
      acceptBargain: 0,
      items: listingItems,
    });
    MessagePlugin.success(`上架提交完成，成功 ${result.succeed || 0} 件，失败 ${result.failed || 0} 件`);
    sellDrawerVisible.value = false;
    await reloadInventory();
  } catch (error) {
    console.error("提交 C5 库存上架失败", error);
    MessagePlugin.error("上架提交失败，请稍后重试");
  } finally {
    listingSubmitting.value = false;
  }
};

const handleConfirmSingleSell = async (subItem: C5InventoryAsset) => {
  await submitListings([subItem]);
};

const handleConfirmSell = async () => {
  const selectedItems = mockSubItems.value.filter(item => selectedSubItemIds.value.has(item.assetId));
  await submitListings(selectedItems);
};

const accountOptions = computed(() => [
  { label: "所有账号", value: ALL_ACCOUNT_VALUE },
  ...accounts.value.map(account => ({
    label: account.accountName || `账号 ${account.id}`,
    value: account.id || ALL_ACCOUNT_VALUE,
  })),
]);

const refreshableAccounts = computed(() => accounts.value.filter(account => account.id && account.steamId));

const tabMetas: Array<{ label: string; value: C5InventoryStatusFilter }> = [
  { label: "全部", value: "all" },
  { label: "可交易", value: "tradable" },
  { label: "冷却中", value: "cooldown" },
  { label: "上架中", value: "selling" },
];

const tabs = computed(() =>
  tabMetas.map((tab) => ({
    ...tab,
    label: `${tab.label} (${inventoryStats.value[tab.value] || 0})`,
  }))
);

const loadAccounts = async () => {
  accountLoading.value = true;
  try {
    const res = await c5SnipingAccountApi.getAccounts();
    accounts.value = res?.records || [];
  } catch (error) {
    console.error("加载 C5 账号列表失败", error);
    MessagePlugin.error("C5 账号列表加载失败");
  } finally {
    accountLoading.value = false;
  }
};

const getInventoryBaseParams = () => ({
  accountId: selectedAccountId.value === ALL_ACCOUNT_VALUE ? undefined : selectedAccountId.value,
  keyword: searchKeyword.value.trim() || undefined,
});

const loadInventoryStats = async () => {
  const res = await c5InventoryApi.getInventoryStats(getInventoryBaseParams());
  inventoryStats.value = {
    all: res?.all || 0,
    tradable: res?.tradable || 0,
    cooldown: res?.cooldown || 0,
    selling: res?.selling || 0,
  };
  totalValuation.value = res?.totalValue || 0;
};

const loadInventory = async () => {
  loading.value = true;
  try {
    const res = await c5InventoryApi.getInventory({
      ...getInventoryBaseParams(),
      status: activeTab.value,
      page: pagination.value.current,
      pageSize: pagination.value.pageSize,
    });
    inventory.value = res.records || [];
    totalCount.value = res.itemTotal || 0;
    pagination.value.total = res.total || 0;
    pagination.value.current = res.current || pagination.value.current;
    pagination.value.pageSize = res.size || pagination.value.pageSize;
  } catch (error) {
    console.error("加载 C5 库存失败", error);
    inventory.value = [];
    totalCount.value = 0;
    pagination.value.total = 0;
    MessagePlugin.error("库存列表加载失败");
  } finally {
    loading.value = false;
  }
};

const reloadInventory = async () => {
  await Promise.all([loadInventory(), loadInventoryStats()]);
};

const getSelectedRefreshAccounts = () => {
  if (selectedAccountId.value === ALL_ACCOUNT_VALUE) {
    return refreshableAccounts.value;
  }
  return accounts.value.filter(account => account.id === selectedAccountId.value && account.steamId);
};

const handleSearch = () => {
  pagination.value.current = 1;
  reloadInventory();
};

const handleReset = () => {
  const shouldReload = selectedAccountId.value === ALL_ACCOUNT_VALUE && activeTab.value === "all";
  selectedAccountId.value = ALL_ACCOUNT_VALUE;
  searchKeyword.value = "";
  activeTab.value = "all";
  pagination.value.current = 1;
  if (shouldReload) {
    reloadInventory();
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
    pagination.value.current = 1;
    await reloadInventory();
  } catch (error) {
    console.error("刷新 C5 库存失败", error);
    MessagePlugin.error("刷新失败，请稍后重试");
  } finally {
    refreshing.value = false;
  }
};

const getDisplayName = (item?: C5InventoryItem | null) => item?.name || item?.marketHashName || "未命名饰品";

const getItemKey = (item: C5InventoryItem) => `${item.accountId || "all"}-${item.assetId || item.id}`;

const getQuantity = (item?: C5InventoryItem | null) => item?.quantity || 1;

const shouldShowWear = (item: C5InventoryItem | null): item is C5InventoryItem & { wear: number } => {
  if (!item || typeof item.wear !== "number" || item.wear === -1) {
    return false;
  }

  // 排除不需要磨损的 itemType
  const noWearTypes = [
    "csgo_type_weapon_case",
    "csgo_type_sticker",
    "csgo_type_tool",
    "csgo_type_tag",
    "csgo_type_collectible",
    "csgo_type_ticket",
    "csgo_type_music_kit",
    "csgo_type_spray",
    "csgo_type_storage_unit"
  ];
  if (item.itemType && noWearTypes.includes(item.itemType)) {
    return false;
  }

  // 排除不需要磨损的 itemTypeName
  const noWearTypeNames = ["箱子", "印花", "钥匙", "代理商", "工具", "收藏品", "通行证", "音乐盒", "涂鸦", "无磨损"];
  if (item.itemTypeName && noWearTypeNames.some(name => item.itemTypeName!.includes(name))) {
    return false;
  }

  return true;
};

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

const handlePageChange = (pageInfo: PageInfo) => {
  pagination.value.current = pageInfo.current;
  pagination.value.pageSize = pageInfo.pageSize;
  loadInventory();
};

watch([selectedAccountId, activeTab], () => {
  pagination.value.current = 1;
  reloadInventory();
});

onMounted(async () => {
  await loadAccounts();
  await reloadInventory();
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
