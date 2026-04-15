<template>
  <PageFrame
    :is-mobile="isMobile"
    desktop-body-class="overflow-y-auto"
    desktop-content-class="px-4 pt-3 pb-4"
    mobile-content-class="px-3 pt-3 pb-3"
  >
    <div ref="floatingContainerRef" class="relative flex flex-col gap-3 bg-[#f5f7fa]">
      <section class="rounded-[6px] border border-[#ebeef5] bg-white p-4">
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div class="order-2 flex min-w-0 flex-1 flex-col gap-3 sm:order-none">
            <h1 class="text-[18px] font-semibold text-[#303133]">订单统计看板</h1>

            <section aria-labelledby="inventory-time-filter-title">
              <div class="flex flex-col gap-2 sm:flex-row sm:items-center">
                <h2
                  id="inventory-time-filter-title"
                  class="shrink-0 text-[13px] font-medium text-[#606266] sm:w-[92px]"
                >
                  时间快捷筛选：
                </h2>
                <div class="flex min-w-0 flex-1 flex-col gap-2">
                  <div class="flex flex-wrap items-center gap-1.5">
                    <button
                      v-for="option in timeOptions"
                      :key="option.value"
                      type="button"
                      class="inline-flex items-center px-2 py-1 text-[12px] leading-none transition-colors duration-200 focus-visible:ring-2 focus-visible:ring-[var(--td-brand-color)] focus-visible:ring-offset-2 focus-visible:outline-none"
                      :class="getFilterButtonClass(isTimePresetActive(option.value))"
                      :aria-pressed="isTimePresetActive(option.value)"
                      @click="toggleTimePreset(option.value)"
                    >
                      {{ option.label }}
                    </button>
                    <t-date-picker
                      multiple
                      clearable
                      format="YYYY-MM-DD"
                      value-type="YYYY-MM-DD"
                      placeholder="自定义日期"
                      class="inventory-board-date-picker"
                      :value="customDates"
                      :input-props="{ readonly: true }"
                      @change="handleCustomDatesChange"
                    />
                  </div>
                  <div v-if="hasCustomDates" class="flex flex-wrap items-center gap-1.5">
                    <t-tag
                      v-for="date in customDates"
                      :key="date"
                      variant="light"
                      size="small"
                      class="inventory-date-token"
                      closable
                      @close="removeCustomDate(date)"
                    >
                      {{ dayjs(date).format("MM-DD") }}
                    </t-tag>
                  </div>
                  <span class="text-[12px] leading-6 text-[#909399] sm:whitespace-nowrap">
                    {{ dateFilterHintText }}
                  </span>
                </div>
              </div>
            </section>

            <section aria-labelledby="inventory-goods-filter-title">
              <div class="flex flex-col gap-2 sm:flex-row sm:items-center">
                <h2
                  id="inventory-goods-filter-title"
                  class="shrink-0 text-[13px] font-medium text-[#606266] sm:w-[92px]"
                >
                  商品快捷筛选：
                </h2>
                <div class="flex min-w-0 flex-1 flex-wrap items-center gap-2">
                  <button
                    type="button"
                    class="inline-flex items-center px-2 py-1 text-[12px] leading-none transition-colors duration-200 focus-visible:ring-2 focus-visible:ring-[var(--td-brand-color)] focus-visible:ring-offset-2 focus-visible:outline-none"
                    :class="getFilterButtonClass(selectedGoods === 'all')"
                    :aria-pressed="selectedGoods === 'all'"
                    @click="selectGoods('all')"
                  >
                    全部商品
                  </button>
                  <button
                    v-for="goods in goodsOptions"
                    :key="goods"
                    type="button"
                    class="inline-flex items-center px-2 py-1 text-[12px] leading-none transition-colors duration-200 focus-visible:ring-2 focus-visible:ring-[var(--td-brand-color)] focus-visible:ring-offset-2 focus-visible:outline-none"
                    :class="getFilterButtonClass(selectedGoods === goods)"
                    :aria-pressed="selectedGoods === goods"
                    @click="selectGoods(goods)"
                  >
                    {{ goods }}
                  </button>
                </div>
              </div>
            </section>
          </div>

          <div
            class="order-1 grid w-full grid-cols-2 gap-2 sm:order-none sm:flex sm:w-[220px] sm:max-w-[220px] sm:flex-col"
          >
            <div
              class="flex items-center justify-between rounded-[10px] border border-[#e5e7eb] bg-[#fafbfc] px-3 py-2"
            >
              <span class="text-[11px] text-[#909399]">全部总金额</span>
              <span class="font-numeric text-[13px] font-semibold text-[#111827]">
                {{ formatCurrency(normalizedGlobalSummary.totalAmount) }}
              </span>
            </div>
            <div
              ref="totalQuantityCardRef"
              class="flex items-center justify-between rounded-[10px] border border-[#e5e7eb] bg-[#fafbfc] px-3 py-2"
            >
              <span class="text-[11px] text-[#909399]">全部总数量</span>
              <span class="font-numeric text-[13px] font-semibold text-[#111827]">
                {{ formatInteger(normalizedGlobalSummary.totalQuantity) }}
              </span>
            </div>
          </div>
        </div>
      </section>

      <div
        v-show="selectedGoods !== 'all'"
        ref="floatingAnchorRef"
        :class="isMobile ? 'pointer-events-none fixed top-[156px] right-3 z-30' : 'absolute z-30'"
        :style="isMobile ? undefined : floatingAnchorStyle"
      >
        <div class="flex items-center">
          <button
            type="button"
            class="pointer-events-auto inline-flex h-10 cursor-move touch-manipulation items-center gap-2 rounded-lg border border-[#dcdfe6] bg-white px-3.5 text-[13px] font-medium text-[#111827] shadow-[0_10px_24px_rgba(15,23,42,0.10)] transition-all duration-300 hover:border-[#cfd4dc] hover:shadow focus-visible:ring-2 focus-visible:ring-[var(--td-brand-color)] focus-visible:ring-offset-2 focus-visible:outline-none active:shadow-none"
            :class="isMobile ? 'max-w-[calc(100vw-24px)]' : ''"
            @pointerdown.stop="handleTriggerPointerDown"
          >
            <span
              class="inline-flex h-2.5 w-2.5 rounded-full"
              :class="floatingTriggerToneClass"
            ></span>
            Steam 折扣
          </button>
        </div>

        <section
          v-show="isSteamDialogVisible"
          :class="
            isMobile
              ? 'pointer-events-auto absolute top-[calc(100%+8px)] right-0 w-[306px] max-w-[calc(100vw-24px)] rounded-[16px] border border-[#dcdfe6] bg-white shadow-[0_24px_60px_rgba(15,23,42,0.18)]'
              : 'absolute top-[calc(100%+12px)] right-0 w-[320px] max-w-[calc(100vw-24px)] rounded-[14px] border border-[#dcdfe6] bg-white shadow-[0_24px_60px_rgba(15,23,42,0.18)]'
          "
          aria-labelledby="steam-discount-title"
          @click.stop
        >
          <header
            :class="[
              'flex items-start justify-between gap-3 border-b border-[#eef2f7]',
              isMobile ? 'px-5 py-4' : 'px-4 py-3',
            ]"
          >
            <div>
              <h2
                id="steam-discount-title"
                :class="['font-semibold text-[#111827]', isMobile ? 'text-[14px]' : 'text-[14px]']"
              >
                Steam 折扣测算
              </h2>
              <p :class="['mt-1 text-[#909399]', isMobile ? 'text-[11px]' : 'text-[12px]']">
                按 Steam 到手价与平均买入价计算
              </p>
            </div>
            <button
              type="button"
              :class="[
                'inline-flex items-center justify-center rounded-full leading-none text-[#909399] transition hover:bg-[#f5f7fa] hover:text-[#303133] focus-visible:ring-2 focus-visible:ring-[var(--td-brand-color)] focus-visible:ring-offset-2 focus-visible:outline-none',
                isMobile ? 'h-8 w-8 text-[18px]' : 'h-7 w-7 text-[16px]',
              ]"
              aria-label="关闭 Steam 折扣弹窗"
              @click="closeSteamDialog"
            >
              ×
            </button>
          </header>

          <div :class="isMobile ? 'px-5 py-5' : 'px-4 py-4'">
            <div :class="['text-[#606266]', isMobile ? 'text-[11px]' : 'text-[12px]']">
              当前商品
            </div>
            <div
              :class="['mt-1 font-medium text-[#111827]', isMobile ? 'text-[14px]' : 'text-[14px]']"
            >
              {{ selectedGoods }}
            </div>

            <div class="mt-4">
              <div :class="['mb-1 text-[#606266]', isMobile ? 'text-[11px]' : 'text-[12px]']">
                Steam 市场卖出单价
              </div>
              <t-input
                v-model="steamMarketPrice"
                clearable
                name="steamMarketPrice"
                aria-label="Steam 市场卖出单价"
                autocomplete="off"
                placeholder="输入 Steam 单价…"
              >
                <template #suffix>¥</template>
              </t-input>
            </div>

            <div
              :class="[
                'mt-4 grid grid-cols-2 text-[#606266]',
                isMobile ? 'gap-3 text-[10px]' : 'gap-2 text-[11px]',
              ]"
            >
              <div :class="['rounded-[10px] bg-[#f8fafc]', isMobile ? 'px-4 py-4' : 'px-3 py-3']">
                <div class="text-[#909399]">当前数量</div>
                <div
                  :class="[
                    'font-numeric mt-1 font-semibold text-[#111827]',
                    isMobile ? 'text-[16px]' : 'text-[15px]',
                  ]"
                >
                  {{ formatInteger(normalizedCurrentSummary.totalQuantity) }}
                </div>
              </div>
              <div :class="['rounded-[10px] bg-[#f8fafc]', isMobile ? 'px-4 py-4' : 'px-3 py-3']">
                <div class="text-[#909399]">Steam 到手总额</div>
                <div
                  :class="[
                    'font-numeric mt-1 font-semibold text-[#111827]',
                    isMobile ? 'text-[16px]' : 'text-[15px]',
                  ]"
                >
                  {{ steamTotalAmountText }}
                </div>
              </div>
            </div>

            <div
              :class="[
                'mt-4 rounded-[12px] border border-dashed border-[#d8dee8] bg-[#fcfcfd] text-center',
                isMobile ? 'px-4 py-[14px]' : 'px-4 py-5',
              ]"
            >
              <div :class="['text-[#909399]', isMobile ? 'text-[10px]' : 'text-[11px]']">
                当前折扣
              </div>
              <div
                :class="[
                  'font-numeric mt-1.5 leading-none font-semibold',
                  isMobile ? 'text-[32px]' : 'text-[38px]',
                  discountToneClass,
                ]"
              >
                {{ discountText }}
              </div>
              <div :class="['mt-1.5 text-[#606266]', isMobile ? 'text-[10px]' : 'text-[12px]']">
                {{ discountDescription }}
              </div>
            </div>
          </div>
        </section>
      </div>

      <section class="grid gap-3 md:grid-cols-4">
        <article
          v-for="item in summaryCards"
          :key="item.label"
          class="rounded-[6px] border border-[#ebeef5] bg-white px-3 py-3"
        >
          <div class="text-[11px] text-[#909399]">{{ item.label }}</div>
          <div class="font-numeric mt-1 text-[20px] font-semibold text-[#111827]">
            {{ item.value }}
          </div>
          <div class="mt-1 text-[11px] text-[#b0b4bb]">{{ item.hint }}</div>
        </article>
      </section>

      <section class="rounded-[6px] border border-[#ebeef5] bg-white p-3">
        <div class="flex flex-wrap items-start justify-between gap-3">
          <div>
            <div class="text-[13px] font-medium text-[#303133]">统计汇总</div>
            <div class="mt-1 text-[12px] text-[#606266]">{{ scopeLabel }}</div>
          </div>
          <div class="flex flex-wrap gap-2">
            <t-tag
              v-for="tag in activeTimeLabels"
              :key="tag"
              variant="light"
              size="small"
              class="inventory-scope-tag"
            >
              {{ tag }}
            </t-tag>
            <t-tag
              variant="light"
              size="small"
              color="#f8fafc"
              class="border border-[#dbe5f1] text-[#475569]"
            >
              {{ selectedGoods === "all" ? "全部商品" : selectedGoods }}
            </t-tag>
          </div>
        </div>

        <div class="mt-3 grid gap-2 md:grid-cols-3">
          <div class="rounded-[10px] border border-[#ebeef5] bg-[#f8fafc] px-3 py-3">
            <div class="text-[11px] text-[#909399]">合并购买数量</div>
            <div class="font-numeric mt-1 text-[18px] font-semibold text-[#111827]">
              {{ formatInteger(normalizedCurrentSummary.totalQuantity) }}
            </div>
            <div class="mt-1 text-[11px] text-[#909399]">选中范围内所有订单数量之和</div>
          </div>
          <div class="rounded-[10px] border border-[#ebeef5] bg-[#f8fafc] px-3 py-3">
            <div class="text-[11px] text-[#909399]">合并购买总额</div>
            <div class="font-numeric mt-1 text-[18px] font-semibold text-[#111827]">
              {{ formatCurrency(normalizedCurrentSummary.totalAmount) }}
            </div>
            <div class="mt-1 text-[11px] text-[#909399]">选中范围内所有订单金额总和</div>
          </div>
          <div class="rounded-[10px] border border-[#ebeef5] bg-[#f8fafc] px-3 py-3">
            <div class="text-[11px] text-[#909399]">合并平均买入价</div>
            <div class="font-numeric mt-1 text-[18px] font-semibold text-[#111827]">
              {{ formatCurrency(normalizedCurrentSummary.averagePrice) }}
            </div>
            <div class="mt-1 text-[11px] text-[#909399]">总额 ÷ 总数量</div>
          </div>
        </div>
      </section>

      <section class="rounded-[6px] border border-[#ebeef5] bg-white p-3">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <div class="text-[13px] font-medium text-[#303133]">结果列表</div>
          </div>
          <div class="flex items-center gap-2">
            <button
              type="button"
              class="inline-flex items-center px-2 py-1 text-[12px] leading-none transition-colors duration-200 focus-visible:ring-2 focus-visible:ring-[var(--td-brand-color)] focus-visible:ring-offset-2 focus-visible:outline-none"
              :class="getFilterButtonClass(viewMode === 'aggregate')"
              :aria-pressed="viewMode === 'aggregate'"
              @click="viewMode = 'aggregate'"
            >
              按商品聚合
            </button>
            <button
              type="button"
              class="inline-flex items-center px-2 py-1 text-[12px] leading-none transition-colors duration-200 focus-visible:ring-2 focus-visible:ring-[var(--td-brand-color)] focus-visible:ring-offset-2 focus-visible:outline-none"
              :class="getFilterButtonClass(viewMode === 'split')"
              :aria-pressed="viewMode === 'split'"
              @click="viewMode = 'split'"
            >
              按时间拆分
            </button>
          </div>
        </div>

        <div v-if="!isMobile" class="mt-3 overflow-hidden rounded-md border border-[#ebeef5]">
          <t-table
            row-key="key"
            size="small"
            hover
            table-layout="auto"
            :data="tableData"
            :columns="tableColumns"
            cell-empty-content="-"
            class="niro-unified-table bg-white"
          >
            <template #empty>
              <div class="flex min-h-[180px] items-center justify-center">
                <t-empty description="当前筛选条件下暂无数据" />
              </div>
            </template>

            <template #goodsName="{ row }">
              <div class="min-w-0">
                <div class="truncate font-medium text-[#303133]" :title="row.goodsName">
                  {{ row.goodsName }}
                </div>
              </div>
            </template>

            <template #dateLabel="{ row }">
              <span class="text-[13px] text-[#606266]">
                {{ "dateLabel" in row ? row.dateLabel : "-" }}
              </span>
            </template>

            <template #quantity="{ row }">
              <span class="font-numeric text-[13px] font-medium text-[#111827]">
                {{ formatInteger(row.quantity) }}
              </span>
            </template>

            <template #amount="{ row }">
              <span class="font-numeric text-[13px] font-medium text-[#111827]">
                {{ formatCurrency(row.amount) }}
              </span>
            </template>

            <template #avgPrice="{ row }">
              <span class="font-numeric text-[13px] font-medium text-[#111827]">
                {{ formatCurrency(row.avgPrice) }}
              </span>
            </template>
          </t-table>
        </div>

        <div v-else class="mt-3">
          <div v-if="tableData.length > 0" class="space-y-3">
            <article
              v-for="row in tableData"
              :key="row.key"
              class="rounded-[12px] border border-[#e5e7eb] bg-white px-3 py-3 shadow-[0_1px_2px_rgba(15,23,42,0.04)]"
            >
              <div class="flex items-start gap-3">
                <div
                  class="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-[10px] border border-[#e5e7eb] bg-[#f5f7fa]"
                >
                  <img
                    v-if="row.imageUrl"
                    :src="row.imageUrl"
                    :alt="row.goodsName"
                    class="h-full w-full object-contain"
                  />
                  <div
                    v-else
                    class="h-9 w-9 rounded-[8px] border border-[#d5dbe5] bg-[#eef2f7]"
                  ></div>
                </div>

                <div class="min-w-0 flex-1">
                  <div class="flex items-start justify-between gap-3">
                    <div class="min-w-0 flex-1">
                      <div
                        class="truncate text-[14px] leading-6 font-medium text-[#303133]"
                        :title="row.goodsName"
                      >
                        {{ row.goodsName }}
                      </div>
                    </div>
                    <t-tag
                      v-if="'dateLabel' in row"
                      size="small"
                      variant="light"
                      color="var(--td-brand-color-light)"
                      class="shrink-0 rounded-[6px] border border-[var(--td-brand-color-2)] text-[var(--td-brand-color-7)]"
                    >
                      {{ row.dateLabel }}
                    </t-tag>
                  </div>

                  <div class="mt-3 grid grid-cols-2 gap-x-4 gap-y-3">
                    <div>
                      <div class="text-[11px] text-[#909399]">购买数量</div>
                      <div
                        class="font-numeric mt-1 text-[17px] leading-none font-semibold text-[#111827]"
                      >
                        {{ formatInteger(row.quantity) }}
                      </div>
                    </div>
                    <div>
                      <div class="text-[11px] text-[#909399]">总金额</div>
                      <div
                        class="font-numeric mt-1 text-[17px] leading-none font-semibold text-[#111827]"
                      >
                        {{ formatCurrency(row.amount) }}
                      </div>
                    </div>
                    <div class="col-span-2">
                      <div class="text-[11px] text-[#909399]">平均买入价</div>
                      <div
                        class="font-numeric mt-1 text-[15px] leading-none font-semibold text-[#111827]"
                      >
                        {{ formatCurrency(row.avgPrice) }}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </article>
          </div>

          <div v-else class="overflow-hidden rounded-md border border-[#ebeef5] bg-white">
            <div class="flex min-h-[180px] items-center justify-center">
              <t-empty description="当前筛选条件下暂无数据" />
            </div>
          </div>
        </div>
      </section>
    </div>
  </PageFrame>
</template>

<script setup lang="ts">
import { computed, nextTick, onActivated, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useWindowSize } from "@vueuse/core";
import dayjs from "dayjs";
import { MessagePlugin } from "tdesign-vue-next";
import type { CSSProperties, Ref } from "vue";
import type { DateValue, PrimaryTableCol, TableRowData } from "tdesign-vue-next";
import { orderApi } from "@/api/order";
import PageFrame from "@/components/PageFrame.vue";
import type {
  PurchaseStatsGoodsItem,
  PurchaseStatsSplitItem,
  PurchaseStatsSummary,
} from "@/types/order";

type TimePreset = "today" | "yesterday" | "last3" | "last7" | "all";
type ViewMode = "aggregate" | "split";

interface TimeOption {
  value: TimePreset;
  label: string;
}

interface SummaryCard {
  label: string;
  value: string;
  hint: string;
}

interface AggregateRow {
  key: string;
  goodsName: string;
  quantity: number;
  amount: number;
  avgPrice: number;
  imageUrl?: string;
}

interface SplitRow extends AggregateRow {
  dateLabel: string;
  dateSort: string;
}

type DisplayRow = AggregateRow | SplitRow;

const { width } = useWindowSize();
const isMobile = computed(() => width.value <= 640);

const timeOptions: TimeOption[] = [
  { value: "today", label: "今日" },
  { value: "yesterday", label: "昨日" },
  { value: "last3", label: "近3天" },
  { value: "last7", label: "近7天" },
  { value: "all", label: "全部" },
];

const selectedTimePresets = ref<TimePreset[]>(["today"]);
const customDates = ref<string[]>([]);
const selectedGoods = ref<string>("all");
const steamMarketPrice = ref("");
const viewMode = ref<ViewMode>("aggregate");
const isSteamDialogVisible = ref(false);
const floatingTriggerPosition = ref({ x: 0, y: 0 });
const floatingContainerRef: Ref<HTMLElement | null> = ref(null);
const floatingAnchorRef: Ref<HTMLElement | null> = ref(null);
const totalQuantityCardRef: Ref<HTMLElement | null> = ref(null);
const summaryLoading = ref(false);
const itemsLoading = ref(false);
const globalSummary = ref<PurchaseStatsSummary>({
  totalAmount: 0,
  totalQuantity: 0,
  avgPrice: 0,
  goodsTypeCount: 0,
});
const currentSummary = ref<PurchaseStatsSummary>({
  totalAmount: 0,
  totalQuantity: 0,
  avgPrice: 0,
  goodsTypeCount: 0,
});
const aggregateItems = ref<PurchaseStatsGoodsItem[]>([]);
const splitItems = ref<PurchaseStatsSplitItem[]>([]);

const DRAG_DISTANCE_THRESHOLD = 6;

let dragOffsetX = 0;
let dragOffsetY = 0;
let pointerDownX = 0;
let pointerDownY = 0;
let isDragging = false;

const priceFormatter = new Intl.NumberFormat("zh-CN", {
  style: "currency",
  currency: "CNY",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const integerFormatter = new Intl.NumberFormat("zh-CN", {
  maximumFractionDigits: 0,
});

const goodsOptions = computed(() => aggregateItems.value.map((item) => item.goodsName));

const clampTriggerPosition = (x: number, y: number) => {
  const containerRect = floatingContainerRef.value?.getBoundingClientRect();
  const anchorWidth = floatingAnchorRef.value?.offsetWidth ?? 220;
  const anchorHeight = floatingAnchorRef.value?.offsetHeight ?? 44;
  const containerWidth = containerRect?.width ?? window.innerWidth;
  const containerHeight = containerRect?.height ?? window.innerHeight;
  const minX = 12 - anchorWidth;
  const maxX = Math.max(containerWidth - 12, minX);
  const maxY = Math.max(containerHeight - anchorHeight - 12, 12);

  return {
    x: Math.min(Math.max(x, minX), maxX),
    y: Math.min(Math.max(y, 12), maxY),
  };
};

const floatingAnchorStyle = computed<CSSProperties>(() => ({
  left: `${floatingTriggerPosition.value.x}px`,
  top: `${floatingTriggerPosition.value.y}px`,
}));

const hasCustomDates = computed(() => customDates.value.length > 0);

const activeTimePresets = computed<TimePreset[]>(() => {
  if (hasCustomDates.value) {
    return [];
  }

  if (selectedTimePresets.value.length === 0 || selectedTimePresets.value.includes("all")) {
    return ["all"];
  }
  return selectedTimePresets.value;
});

const aggregateColumns: PrimaryTableCol<TableRowData>[] = [
  { colKey: "goodsName", title: "商品", minWidth: 220, cell: "goodsName" },
  { colKey: "quantity", title: "购买数量", width: 140, align: "left", cell: "quantity" },
  { colKey: "amount", title: "总金额", width: 160, align: "left", cell: "amount" },
  { colKey: "avgPrice", title: "平均买入价", width: 160, align: "left", cell: "avgPrice" },
];

const splitColumns: PrimaryTableCol<TableRowData>[] = [
  { colKey: "goodsName", title: "商品", minWidth: 220, cell: "goodsName" },
  { colKey: "dateLabel", title: "日期", width: 160, align: "left", cell: "dateLabel" },
  { colKey: "quantity", title: "购买数量", width: 140, align: "left", cell: "quantity" },
  { colKey: "amount", title: "总金额", width: 160, align: "left", cell: "amount" },
  { colKey: "avgPrice", title: "平均买入价", width: 160, align: "left", cell: "avgPrice" },
];

const formatCurrency = (value: number) => priceFormatter.format(value);
const formatInteger = (value: number) => integerFormatter.format(value);

const buildQueryParams = (keyword?: string) => {
  const params: { keyword?: string; startDate?: string; endDate?: string } = {};

  if (keyword && keyword !== "all") {
    params.keyword = keyword;
  }

  if (hasCustomDates.value) {
    const sortedDates = [...customDates.value].sort((left, right) => left.localeCompare(right));
    params.startDate = sortedDates[0];
    params.endDate = sortedDates[sortedDates.length - 1];
    return params;
  }

  if (activeTimePresets.value.includes("all")) {
    return params;
  }

  const today = dayjs();
  let startDate = today;
  let endDate = today;

  if (activeTimePresets.value.includes("last7")) {
    startDate = today.subtract(6, "day");
  } else if (activeTimePresets.value.includes("last3")) {
    startDate = today.subtract(2, "day");
  } else {
    if (activeTimePresets.value.includes("yesterday")) {
      startDate = today.subtract(1, "day");
    }
    if (activeTimePresets.value.includes("today")) {
      endDate = today;
    } else if (activeTimePresets.value.includes("yesterday")) {
      endDate = today.subtract(1, "day");
    }
  }

  params.startDate = startDate.format("YYYY-MM-DD");
  params.endDate = endDate.format("YYYY-MM-DD");
  return params;
};

const isTimePresetActive = (preset: TimePreset) => activeTimePresets.value.includes(preset);

const toggleTimePreset = (preset: TimePreset) => {
  customDates.value = [];

  if (preset === "all") {
    selectedTimePresets.value = ["all"];
    return;
  }

  const nextSet = new Set(selectedTimePresets.value.filter((item) => item !== "all"));

  if (nextSet.has(preset)) {
    nextSet.delete(preset);
  } else {
    nextSet.add(preset);
  }

  selectedTimePresets.value = nextSet.size > 0 ? Array.from(nextSet) : ["all"];
};

const handleCustomDatesChange = (value: DateValue | DateValue[]) => {
  const nextDates = Array.isArray(value) ? value : value ? [value] : [];

  customDates.value = nextDates
    .map((item) => dayjs(item).format("YYYY-MM-DD"))
    .filter((item, index, array) => array.indexOf(item) === index)
    .sort((left, right) => left.localeCompare(right));
};

const removeCustomDate = (date: string) => {
  customDates.value = customDates.value.filter((item) => item !== date);
};

const closeSteamDialog = () => {
  isSteamDialogVisible.value = false;
};

const toggleSteamDialog = () => {
  if (selectedGoods.value === "all") {
    closeSteamDialog();
    return;
  }

  isSteamDialogVisible.value = !isSteamDialogVisible.value;
};

const handlePointerMove = (event: PointerEvent) => {
  const anchorElement = floatingAnchorRef.value;
  const containerRect = floatingContainerRef.value?.getBoundingClientRect();
  if (!anchorElement || !containerRect) {
    return;
  }

  if (!isDragging) {
    const distanceX = event.clientX - pointerDownX;
    const distanceY = event.clientY - pointerDownY;
    const distance = Math.hypot(distanceX, distanceY);

    if (distance < DRAG_DISTANCE_THRESHOLD) {
      return;
    }

    const rect = anchorElement.getBoundingClientRect();
    dragOffsetX = pointerDownX - rect.left;
    dragOffsetY = pointerDownY - rect.top;
    isDragging = true;
  }

  const nextX = event.clientX - containerRect.left - dragOffsetX;
  const nextY = event.clientY - containerRect.top - dragOffsetY;

  floatingTriggerPosition.value = clampTriggerPosition(nextX, nextY);
};

const stopDrag = () => {
  const wasDragging = isDragging;

  isDragging = false;
  window.removeEventListener("pointermove", handlePointerMove);
  window.removeEventListener("pointerup", stopDrag);

  if (!wasDragging) {
    toggleSteamDialog();
  }
};

const handleTriggerPointerDown = (event: PointerEvent) => {
  if (isMobile.value) {
    toggleSteamDialog();
    return;
  }

  pointerDownX = event.clientX;
  pointerDownY = event.clientY;
  isDragging = false;

  window.addEventListener("pointermove", handlePointerMove);
  window.addEventListener("pointerup", stopDrag);
};

const selectGoods = (value: string) => {
  if (value === "all") {
    closeSteamDialog();
  }

  selectedGoods.value = value;
};

const normalizedCurrentSummary = computed(() => ({
  totalQuantity: currentSummary.value.totalQuantity ?? 0,
  totalAmount: currentSummary.value.totalAmount ?? 0,
  averagePrice: currentSummary.value.avgPrice ?? 0,
  goodsCount: currentSummary.value.goodsTypeCount ?? 0,
}));

const normalizedGlobalSummary = computed(() => ({
  totalAmount: globalSummary.value.totalAmount ?? 0,
  totalQuantity: globalSummary.value.totalQuantity ?? 0,
}));

const activeTimeLabels = computed(() => {
  if (hasCustomDates.value) {
    return customDates.value.map((item) => dayjs(item).format("MM-DD"));
  }

  if (activeTimePresets.value.includes("all")) {
    return ["全部"];
  }

  return timeOptions
    .filter((item) => activeTimePresets.value.includes(item.value))
    .map((item) => item.label);
});

const dateFilterHintText = computed(() => {
  if (hasCustomDates.value) {
    return "当前按自定义日期统计";
  }
  if (activeTimePresets.value.includes("all")) {
    return "当前统计全部日期";
  }
  return "支持多选，按选中日期汇总";
});

const scopeLabel = computed(() => {
  const goodsLabel = selectedGoods.value === "all" ? "全部商品" : selectedGoods.value;
  return `${goodsLabel} · ${activeTimeLabels.value.join(" + ")}`;
});

const STEAM_NET_RATE = 0.87;

const parsedSteamMarketPrice = computed(() => {
  const normalized = steamMarketPrice.value.trim();
  if (!normalized) {
    return null;
  }

  const value = Number(normalized);
  return Number.isFinite(value) && value > 0 ? value : null;
});

const steamNetUnitPrice = computed(() => {
  const marketPrice = parsedSteamMarketPrice.value;
  if (!marketPrice) {
    return null;
  }

  return marketPrice * STEAM_NET_RATE;
});

const steamTotalAmount = computed(() => {
  const netUnitPrice = steamNetUnitPrice.value;
  const quantity = normalizedCurrentSummary.value.totalQuantity;

  if (!netUnitPrice || quantity <= 0) {
    return null;
  }

  return netUnitPrice * quantity;
});

const steamTotalAmountText = computed(() => {
  if (steamTotalAmount.value === null) {
    return "--";
  }

  return formatCurrency(steamTotalAmount.value);
});

const discountRatio = computed(() => {
  const netUnitPrice = steamNetUnitPrice.value;
  const averagePrice = normalizedCurrentSummary.value.averagePrice;

  if (!netUnitPrice || averagePrice <= 0) {
    return null;
  }

  return averagePrice / netUnitPrice;
});

const discountText = computed(() => {
  if (discountRatio.value === null) {
    return "--";
  }

  return `${(discountRatio.value * 10).toFixed(1)} 折`;
});

const discountToneClass = computed(() => {
  const ratio = discountRatio.value;

  if (ratio === null) {
    return "text-[#909399]";
  }
  if (ratio <= 0.35) {
    return "text-[#16a34a]";
  }
  if (ratio <= 0.55) {
    return "text-[var(--td-brand-color)]";
  }
  if (ratio <= 0.75) {
    return "text-[#d97706]";
  }
  return "text-[#dc2626]";
});

const discountDescription = computed(() => {
  if (parsedSteamMarketPrice.value === null) {
    return "请输入有效的 Steam 单价";
  }
  if (discountRatio.value === null) {
    return "当前范围暂无可计算的平均买入价";
  }
  return "按 Steam 到手价与平均买入价计算";
});

const summaryCards = computed<SummaryCard[]>(() => [
  {
    label: "总购买数量",
    value: formatInteger(normalizedCurrentSummary.value.totalQuantity),
    hint: "当前筛选条件下的累计购买数量",
  },
  {
    label: "总购买金额",
    value: formatCurrency(normalizedCurrentSummary.value.totalAmount),
    hint: "当前筛选条件下的累计成交金额",
  },
  {
    label: "平均买入价",
    value: formatCurrency(normalizedCurrentSummary.value.averagePrice),
    hint: "按总额 ÷ 总数量得到的平均单价",
  },
  {
    label: "商品数",
    value: formatInteger(normalizedCurrentSummary.value.goodsCount),
    hint: "当前结果里覆盖的商品种类数",
  },
]);

const floatingTriggerToneClass = computed(() => {
  if (discountRatio.value === null) {
    return "bg-[var(--td-brand-color-3)]";
  }
  if (discountRatio.value <= 0.35) {
    return "bg-[var(--td-brand-color-4)]";
  }
  if (discountRatio.value <= 0.55) {
    return "bg-[var(--td-brand-color)]";
  }
  if (discountRatio.value <= 0.75) {
    return "bg-[var(--td-brand-color-7)]";
  }
  return "bg-[var(--td-brand-color-8)]";
});

const aggregateRows = computed<AggregateRow[]>(() =>
  aggregateItems.value.map((item) => ({
    key: item.goodsName,
    goodsName: item.goodsName,
    quantity: item.totalQuantity,
    amount: item.totalAmount,
    avgPrice: item.avgPrice,
    imageUrl: item.goodsImg,
  }))
);

const splitRows = computed<SplitRow[]>(() =>
  splitItems.value.map((item) => ({
    key: `${item.goodsName}-${item.date}`,
    goodsName: item.goodsName,
    quantity: item.totalQuantity,
    amount: item.totalAmount,
    avgPrice: item.avgPrice,
    imageUrl: item.goodsImg,
    dateLabel: dayjs(item.date).format("MM-DD"),
    dateSort: item.date,
  }))
);

const tableColumns = computed(() =>
  viewMode.value === "aggregate" ? aggregateColumns : splitColumns
);

const tableData = computed<DisplayRow[]>(() =>
  viewMode.value === "aggregate" ? aggregateRows.value : splitRows.value
);

const fetchGlobalSummary = async () => {
  summaryLoading.value = true;
  try {
    globalSummary.value = await orderApi.getPurchaseStatsSummary();
  } finally {
    summaryLoading.value = false;
  }
};

const fetchCurrentStats = async () => {
  const params = buildQueryParams(selectedGoods.value);
  summaryLoading.value = true;
  itemsLoading.value = true;
  try {
    const [summary, items, splitItemList] = await Promise.all([
      orderApi.getPurchaseStatsSummary(params),
      orderApi.getPurchaseStatsItems(params),
      orderApi.getPurchaseStatsSplitItems(params),
    ]);
    currentSummary.value = summary;
    aggregateItems.value = items;
    splitItems.value = splitItemList;

    if (selectedGoods.value !== "all" && items.length === 0) {
      selectedGoods.value = "all";
    }
  } catch (error) {
    MessagePlugin.error("加载订单统计数据失败");
    throw error;
  } finally {
    summaryLoading.value = false;
    itemsLoading.value = false;
  }
};

const loadStatsData = async () => {
  await Promise.all([fetchGlobalSummary(), fetchCurrentStats()]);
};

const getFilterButtonClass = (active: boolean) => {
  if (active) {
    return "rounded-[4px] bg-[var(--td-brand-color-light)] font-medium text-[var(--td-brand-color-7)]";
  }

  return "rounded-[4px] bg-transparent text-[#5b6473] hover:bg-[var(--td-brand-color-light)] hover:text-[var(--td-brand-color-7)]";
};

const initializeFloatingElements = () => {
  if (isMobile.value || selectedGoods.value === "all") {
    return;
  }

  const containerRect = floatingContainerRef.value?.getBoundingClientRect();
  const quantityCardRect = totalQuantityCardRef.value?.getBoundingClientRect();
  if (!containerRect || !quantityCardRect) {
    floatingTriggerPosition.value = clampTriggerPosition(window.innerWidth - 248, 144);
    return;
  }

  const anchorWidth = floatingAnchorRef.value?.offsetWidth ?? 220;
  const targetX = quantityCardRect.right - containerRect.left - anchorWidth;
  const targetY = quantityCardRect.bottom - containerRect.top + 16;

  floatingTriggerPosition.value = clampTriggerPosition(targetX, targetY);
};

const syncFloatingElements = () => {
  void nextTick(() => {
    initializeFloatingElements();
  });
};

watch(isMobile, (mobile) => {
  if (!mobile) {
    syncFloatingElements();
  }
});

watch(selectedGoods, () => {
  void fetchCurrentStats();
  syncFloatingElements();
});

watch(
  [activeTimePresets, customDates],
  () => {
    void fetchCurrentStats();
  },
  { deep: true }
);

onMounted(() => {
  void loadStatsData();
  syncFloatingElements();
  window.addEventListener("resize", syncFloatingElements);
});

onActivated(() => {
  syncFloatingElements();
});

onBeforeUnmount(() => {
  stopDrag();
  window.removeEventListener("resize", syncFloatingElements);
});
</script>

<style scoped>
:deep(.inventory-board-date-picker .t-input) {
  min-height: 28px;
  border: none;
  border-radius: 4px;
  box-shadow: none;
  background: transparent;
  padding: 4px 8px;
  transition:
    background-color 0.2s ease,
    color 0.2s ease;
}

:deep(.inventory-board-date-picker .t-input:hover) {
  background: rgba(24, 144, 255, 0.06);
}

:deep(.inventory-board-date-picker .t-input__inner),
:deep(.inventory-board-date-picker .t-input__prefix),
:deep(.inventory-board-date-picker .t-input__suffix) {
  color: rgb(91, 100, 115);
}

:deep(.inventory-board-date-picker .t-input--focused) {
  box-shadow: 0 0 0 2px var(--td-brand-color-focus);
}

:deep(.inventory-board-date-picker .t-input--focused .t-input__inner),
:deep(.inventory-board-date-picker .t-input--focused .t-input__prefix),
:deep(.inventory-board-date-picker .t-input--focused .t-input__suffix) {
  color: var(--td-brand-color-7);
}

:deep(.inventory-date-token.t-tag),
:deep(.inventory-scope-tag.t-tag) {
  background-color: var(--td-brand-color-light);
  color: var(--td-brand-color-7);
}

:deep(.inventory-date-token.t-tag .t-tag__suffix-icon) {
  color: var(--td-brand-color-7);
}
</style>
