<template>
  <PageFrame
    :is-mobile="isMobile"
    :on-body-ref-change="handleOrderRecordBodyRefChange"
    body-class="order-record-body"
    mobile-body-class="overflow-y-visible"
    desktop-outer-class="!p-0"
    desktop-content-class="px-4 pt-0 pb-0"
    mobile-content-class="px-3 pt-3 pb-0"
  >
    <PageHeader title="订单记录">
      <template #icon>
        <svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
        </svg>
      </template>
      <template #extra>
        <div v-if="isMobile" class="flex items-center gap-2 mr-1">
          <t-button
            v-if="canTriggerC5Sync"
            variant="outline"
            size="small"
            theme="primary"
            :loading="c5SyncLoading"
            @click="handleC5Sync"
          >
            <template #icon><t-icon name="refresh" :class="{ 'animate-spin': c5SyncLoading }" /></template>
            同步
          </t-button>
          <t-button
            variant="outline"
            size="small"
            theme="default"
            @click="showAdvancedFilters = !showAdvancedFilters"
          >
            <template #icon><t-icon :name="showAdvancedFilters ? 'chevron-up' : 'filter'" /></template>
            {{ showAdvancedFilters ? '收起' : '筛选' }}
          </t-button>
        </div>
        <div v-if="!isMobile" class="flex flex-col items-end">
          <span class="text-[10px] font-bold tracking-wider text-slate-400 uppercase">
            订单总数
          </span>
          <span class="font-numeric text-base font-bold text-slate-900">
            {{ pagination.total }}
            <small class="text-[10px] font-medium text-slate-400">单</small>
          </span>
        </div>
      </template>
    </PageHeader>

    <div :class="['flex flex-col bg-white px-0 py-4', isMobile ? 'gap-3' : 'gap-6']">
      <!-- 移动端统计数据条 -->
      <div
        v-if="isMobile"
        class="mx-0 flex items-center justify-between rounded-lg bg-slate-50/80 px-3 py-2 text-xs"
      >
        <div class="flex items-center gap-1.5">
          <span class="text-slate-400">订单总数:</span>
          <span class="font-bold text-slate-700">{{ pagination.total }} 单</span>
        </div>
      </div>
      <section class="overflow-hidden bg-white">
      <t-tabs
        v-model="activeTab"
        class="jsh-tabs border-b border-slate-200 bg-white px-4"
        @change="handleTabChange"
      >
        <t-tab-panel :value="0" label="全部" />
        <t-tab-panel :value="1" label="成功" />
        <t-tab-panel :value="2" label="失败" />
        <t-tab-panel :value="3" label="取消" />
      </t-tabs>

      <div class="flex flex-col gap-3 bg-white px-0 py-4">
        <div
          v-if="!isMobile || showAdvancedFilters"
          :class="[
            'jsh-filter-layout grid grid-cols-1 gap-3 xl:items-end',
            showAdvancedFilters
              ? 'xl:grid-cols-[minmax(0,280px)_minmax(0,220px)_minmax(0,320px)_auto]'
              : 'xl:grid-cols-[minmax(0,280px)_auto]',
          ]"
        >
          <label class="jsh-filter-item flex min-w-0 flex-col gap-1.5">
            <span class="jsh-label text-sm font-medium text-slate-700">订单关键词</span>
            <t-input
              v-model="queryParams.keyword"
              placeholder="请输入商品名/C5订单号"
              clearable
              class="jsh-filter-input"
              :class="toolbarFieldClass"
              @enter="handleSearch"
              @clear="handleKeywordClear"
            />
          </label>

          <label v-if="showAdvancedFilters" class="jsh-filter-item flex min-w-0 flex-col gap-1.5">
            <span class="jsh-label text-sm font-medium text-slate-700">账号</span>
            <t-select
              v-model="queryParams.accountId"
              clearable
              filterable
              :loading="accountsLoading"
              :options="accountSelectOptions"
              placeholder="请选择 C5 账号"
              class="jsh-filter-select"
              :class="toolbarFieldClass"
              @change="handleSearch"
            />
          </label>

          <label v-if="showAdvancedFilters" class="jsh-filter-item flex min-w-0 flex-col gap-1.5">
            <span class="jsh-label text-sm font-medium text-slate-700">订单日期</span>
            <t-date-range-picker
              v-model="dateRange"
              clearable
              value-type="YYYY-MM-DD"
              format="YYYY-MM-DD"
              class="jsh-filter-select"
              :class="toolbarFieldClass"
              :placeholder="['开始日期', '结束日期']"
              @change="handleDateRangeChange"
            />
          </label>

          <div
            class="jsh-filter-actions flex flex-wrap items-center gap-2"
          >
            <t-button
              v-permission="PermissionConstant.TASK_RECORD_LIST"
              theme="primary"
              class="jsh-action-btn jsh-action-btn--primary"
              @click="handleSearch"
            >
              查询
            </t-button>
            <t-button
              v-permission="PermissionConstant.TASK_RECORD_LIST"
              variant="outline"
              theme="default"
              class="jsh-action-btn"
              @click="handleReset"
            >
              重置
            </t-button>
            <button type="button" class="jsh-expand-link" @click="toggleAdvancedFilters">
              {{ showAdvancedFilters ? "收起" : "展开" }}
            </button>
          </div>
        </div>

        <div v-if="isMobile" class="order-status-filter">
          <span class="order-status-filter__label">订单状态：</span>
          <div class="order-status-filter__options">
            <button
              v-for="item in mobileStatusOptions"
              :key="item.value"
              type="button"
              class="order-status-filter__option"
              :class="{ 'order-status-filter__option--active': activeTab === item.value }"
              @click="handleTabChange(item.value)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>

        <div class="jsh-toolbar flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div class="order-toolbar-main flex min-w-0 flex-1 flex-wrap items-center gap-2">
            <div
              class="table-operator flex flex-wrap items-center gap-2"
              :class="{ 'table-operator--mobile': isMobile }"
            >
              <div
                v-if="canTriggerC5Sync"
                class="order-sync-control flex items-center gap-2"
                :class="{ 'order-sync-control--mobile': isMobile }"
              >
                <span class="order-sync-control__label">同步账号</span>
                <t-select
                  v-model="selectedSyncAccountId"
                  clearable
                  filterable
                  class="order-sync-control__account-select"
                  :class="toolbarCompactFieldClass"
                  :disabled="c5SyncLoading"
                  :loading="accountsLoading"
                  :options="accountSelectOptions"
                  placeholder="请选择账号"
                  aria-label="同步账号"
                />
                <span class="order-sync-control__label">同步范围</span>
                <t-select
                  v-model="selectedSyncRange"
                  class="order-sync-control__select"
                  :class="toolbarCompactFieldClass"
                  :disabled="c5SyncLoading"
                  :options="syncRangeOptions"
                  aria-label="同步范围"
                />
                <t-popconfirm
                  :content="syncConfirmContent"
                  :disabled="!shouldConfirmFullHistorySync"
                  placement="top"
                  theme="warning"
                  cancel-btn="取消"
                  :confirm-btn="{ content: '确认同步', theme: 'warning' }"
                  :popup-props="syncConfirmPopupProps"
                  @confirm="handleConfirmFullHistorySync"
                >
                  <t-button
                    variant="outline"
                    theme="default"
                    class="jsh-action-btn"
                    :loading="c5SyncLoading"
                    :disabled="c5SyncLoading"
                    @click="handleC5Sync"
                  >
                    {{ isMobile ? "同步订单" : "同步 C5 订单" }}
                  </t-button>
                </t-popconfirm>
              </div>

              <t-popconfirm
                v-if="canDeleteOrderRecord"
                content="确认批量删除勾选订单吗？"
                @confirm="handleBatchDelete"
              >
                <t-button
                  variant="outline"
                  theme="default"
                  class="jsh-action-btn"
                  :disabled="selectedRowKeys.length === 0"
                >
                  批量删除
                </t-button>
              </t-popconfirm>
            </div>

            <div
              class="order-overview-inline flex min-w-0 flex-1 flex-wrap items-center gap-2"
              :class="{ 'order-overview-inline--mobile': isMobile }"
            >
              <div
                v-for="item in orderSummaryCards"
                :key="item.key"
                class="order-overview-pill inline-flex h-7 items-center gap-1 rounded-[6px] px-2.5"
                :class="item.pillClass"
              >
                <span class="text-[12px] leading-none">{{ item.label }}</span>
                <span class="text-[14px] leading-none font-semibold">{{ item.value }}</span>
              </div>
            </div>
          </div>

          <div
            class="text-xs text-slate-500"
            :class="isMobile ? 'task-selection-summary' : 'flex items-center gap-2.5'"
          >
            <t-tag theme="primary" variant="light" class="selection-summary__count rounded-[2px]">
              已选择 {{ selectedRowKeys.length }} 项
            </t-tag>
            <t-button
              variant="outline"
              theme="default"
              class="jsh-action-btn"
              :disabled="selectedRowKeys.length === 0"
              @click="clearSelection"
            >
              清空勾选
            </t-button>
          </div>
        </div>
      </div>
    </section>

    <div
      :class="['order-record-main relative', isMobile ? 'min-h-fit flex-none' : 'min-h-0 flex-1']"
    >
      <div
        v-if="!isMobile"
        class="order-record-table-wrap relative flex h-full min-h-0 flex-col overflow-hidden bg-white"
      >
        <div ref="orderRecordTableViewportRef" class="min-h-0 flex-1 overflow-hidden">
          <t-table
            ref="orderRecordTableRef"
            row-key="id"
            :data="dataList"
            :columns="columns"
            :loading="loading"
            :pagination="undefined"
            :selected-row-keys="selectedRowKeys"
            select-on-row-click
            hover
            :max-height="orderTableMaxHeight"
            :class="[
              'order-c5-table w-full bg-white',
              { 'niro-unified-table--empty': !loading && dataList.length === 0 },
            ]"
            @sort-change="onSortChange"
            @select-change="handleSelectChange"
          >
            <template #empty>
              <div class="jsh-ledger-empty">
                <t-empty description="暂无订单记录" />
              </div>
            </template>

            <template #goods="{ row }">
              <div class="flex items-center gap-3">
                <t-image
                  :src="row.goodsImg"
                  class="h-10 w-10 shrink-0 rounded border border-gray-100 bg-gray-50"
                  fit="contain"
                  referrerpolicy="no-referrer"
                />
                <div class="flex min-w-0 flex-col">
                  <t-tooltip :content="row.goodsName" placement="top-left">
                    <span class="truncate font-medium text-[#303133]">
                      {{ row.goodsName }}
                    </span>
                  </t-tooltip>
                  <span class="truncate text-xs text-[#909399]">
                    磨损: {{ formatPaintwear(row.paintwear) }}
                  </span>
                </div>
              </div>
            </template>

            <template #account="{ row }">
              <div class="flex flex-col items-start gap-1">
                <t-tag
                  :theme="row.platform === 'BUFF' ? 'warning' : 'primary'"
                  variant="light"
                  size="small"
                >
                  {{ row.platform }}
                </t-tag>
                <span class="text-sm text-[#606266]">{{ row.accountName || "-" }}</span>
              </div>
            </template>

            <template #orderId="{ row }">
              <button
                v-if="row.orderId"
                type="button"
                class="font-mono-value cursor-pointer border-0 bg-transparent p-0 text-xs text-[#0052d9] transition-colors hover:text-[#366ef4]"
                :aria-label="`复制 C5 订单号 ${row.orderId}`"
                @click="handleCopyOrderId(row.orderId)"
              >
                {{ row.orderId }}
              </button>
              <span v-else class="text-xs text-[#b0b4bb]">未生成</span>
            </template>

            <template #price="{ row }">
              <span class="font-mono-value font-medium text-[#303133]">
                {{ formatPrice(row.price) }}
              </span>
            </template>

            <template #status="{ row }">
              <div class="order-status-cell">
                <t-tooltip
                  v-if="shouldShowErrorDetail(row.status)"
                  :content="getOrderErrorDetail(row)"
                  placement="top"
                >
                  <t-tag theme="danger" variant="light" class="cursor-help">
                    <template #icon><close-circle-icon /></template>
                    {{ getStatusMeta(row.status).label }}
                  </t-tag>
                </t-tooltip>
                <t-tag v-else :theme="getStatusMeta(row.status).theme" variant="light">
                  <template v-if="isSuccessStatus(row.status)" #icon>
                    <check-circle-icon />
                  </template>
                  {{ getStatusMeta(row.status).label }}
                </t-tag>
                <div v-if="shouldShowErrorDetail(row.status)" class="order-status-cell__error">
                  <span v-if="row.errorCode" class="order-status-cell__error-code">
                    {{ row.errorCode }}
                  </span>
                  <t-tooltip :content="getErrorText(row.errorMsg)" placement="top">
                    <span class="order-status-cell__error-text">
                      {{ getErrorText(row.errorMsg) }}
                    </span>
                  </t-tooltip>
                </div>
              </div>
            </template>

            <template #time="{ row }">
              <span class="text-[#606266]">{{ formatTime(row.createTime) }}</span>
            </template>

            <template #operation="{ row }">
              <div v-permission="PermissionConstant.TASK_RECORD_LIST" class="niro-table-actions">
                <t-popconfirm v-if="canDeleteOrderRecord" content="确认删除该订单记录吗？" @confirm="handleDelete(row.id)">
                  <t-button
                    variant="outline"
                    size="small"
                    theme="danger"
                    class="niro-table-action-btn"
                  >
                    删除
                  </t-button>
                </t-popconfirm>
              </div>
            </template>
          </t-table>
        </div>

        <div v-if="pagination.total > 0" class="border-t border-slate-200 bg-white px-4 py-3">
          <t-pagination
            v-model="pagination.current"
            v-model:page-size="pagination.pageSize"
            :total="pagination.total"
            show-jumper
            @change="onPageChange"
          />
        </div>
      </div>

      <div v-else class="order-mobile min-h-0">
        <div v-if="loading" class="order-mobile__empty text-sm text-[#909399]">加载中...</div>
        <div v-else-if="dataList.length === 0" class="order-mobile__empty">
          <t-empty description="暂无订单记录" />
        </div>
        <div v-else class="order-mobile__list">
          <div
            v-for="row in dataList"
            :key="row.id"
            class="order-mobile-card"
            :class="{ 'order-mobile-card--selected': isMobileRowSelected(row.id) }"
            role="button"
            tabindex="0"
            :aria-pressed="isMobileRowSelected(row.id)"
            @click="toggleMobileRowSelection(row.id)"
            @keydown.enter.prevent="toggleMobileRowSelection(row.id)"
            @keydown.space.prevent="toggleMobileRowSelection(row.id)"
          >
            <t-image
              :src="row.goodsImg"
              :alt="row.goodsName"
              class="order-mobile-card__thumb"
              fit="contain"
              referrerpolicy="no-referrer"
            />

            <div class="order-mobile-card__content">
              <div class="order-mobile-card__price">{{ formatPrice(row.price) }}</div>
              <div class="order-mobile-card__name" :title="row.goodsName">
                {{ row.goodsName }}
              </div>

              <div class="order-mobile-card__details">
                <div class="order-mobile-card__detail-row">
                  <span class="order-mobile-card__detail-label">平台：</span>
                  <div
                    class="order-mobile-card__detail-value order-mobile-card__detail-value--inline"
                  >
                    <t-tag
                      :theme="row.platform === 'BUFF' ? 'warning' : 'primary'"
                      variant="light"
                      size="small"
                    >
                      {{ row.platform }}
                    </t-tag>
                    <span class="order-mobile-card__account">{{ row.accountName || "-" }}</span>
                  </div>
                </div>

                <div class="order-mobile-card__detail-row">
                  <span class="order-mobile-card__detail-label">状态：</span>
                  <div class="order-mobile-card__detail-value">
                    <t-tooltip
                      v-if="shouldShowErrorDetail(row.status)"
                      :content="getOrderErrorDetail(row)"
                      placement="top"
                    >
                      <t-tag theme="danger" variant="light" class="cursor-help">
                        {{ getStatusMeta(row.status).label }}
                      </t-tag>
                    </t-tooltip>
                    <t-tag v-else :theme="getStatusMeta(row.status).theme" variant="light">
                      {{ getStatusMeta(row.status).label }}
                    </t-tag>
                  </div>
                </div>

                <div class="order-mobile-card__detail-row">
                  <span class="order-mobile-card__detail-label">购买时间：</span>
                  <span class="order-mobile-card__detail-value order-mobile-card__time">
                    {{ formatTime(row.createTime) }}
                  </span>
                </div>

                <div class="order-mobile-card__detail-row">
                  <span class="order-mobile-card__detail-label">订单号：</span>
                  <div class="order-mobile-card__detail-value">
                    <button
                      v-if="row.orderId"
                      type="button"
                      class="order-mobile-card__order-id font-mono-value"
                      :aria-label="`复制 C5 订单号 ${row.orderId}`"
                      @click.stop="handleCopyOrderId(row.orderId)"
                    >
                      {{ row.orderId }}
                    </button>
                    <span
                      v-else
                      class="order-mobile-card__order-id order-mobile-card__order-id--muted"
                    >
                      未生成订单号
                    </span>
                  </div>
                </div>

                <div v-if="shouldShowErrorDetail(row.status)" class="order-mobile-card__detail-row">
                  <span class="order-mobile-card__detail-label">错误信息：</span>
                  <div class="order-mobile-card__detail-value order-mobile-card__error">
                    <span v-if="row.errorCode" class="order-mobile-card__error-code">
                      {{ row.errorCode }}
                    </span>
                    <span class="order-mobile-card__error-text" :title="getErrorText(row.errorMsg)">
                      {{ getErrorText(row.errorMsg) }}
                    </span>
                  </div>
                </div>
              </div>
            </div>

            <div
              v-permission="PermissionConstant.TASK_RECORD_LIST"
              class="order-mobile-card__actions"
              @click.stop
            >
              <t-popconfirm v-if="canDeleteOrderRecord" content="确认删除该订单记录吗？" @confirm="handleDelete(row.id)">
                <t-button
                  variant="outline"
                  theme="danger"
                  class="order-mobile-card__delete-btn"
                  @click.stop
                >
                  删除
                </t-button>
              </t-popconfirm>
            </div>
          </div>

          <div v-if="!loading && pagination.total > 0" class="order-mobile__pagination">
            <t-pagination
              theme="simple"
              v-model="pagination.current"
              v-model:page-size="pagination.pageSize"
              :total="pagination.total"
              :show-page-size="false"
              :total-content="false"
              show-jumper
              @change="onPageChange"
            />
          </div>
        </div>
      </div>
    </div>
  </PageFrame>
</template>

<script setup lang="ts">
defineOptions({ name: "OrderRecord" });
import { computed, nextTick, onMounted, reactive, ref, watch } from "vue";
import { useElementSize, useWindowSize } from "@vueuse/core";
import dayjs from "dayjs";
import {
  MessagePlugin,
  type DateRangeValue,
  type PrimaryTableCol,
  type SortInfo,
  type TableInstanceFunctions,
  type TableSort,
  type TagProps,
} from "tdesign-vue-next";
import { CheckCircleIcon, CloseCircleIcon } from "tdesign-icons-vue-next";
import PageFrame from "@/components/PageFrame.vue";
import PageHeader from "@/components/PageHeader.vue";
import { c5SnipingAccountApi } from "@/api/c5-sniping-account";
import { orderApi } from "@/api/order";
import type { C5SnipingAccount } from "@/types/c5-sniping-account";
import type { OrderQueryParam, TradeOrderRecord } from "@/types/order";
import { PermissionConstant } from "@/constant/PermissionConstant";
import { usePermission } from "@/hooks/usePermission";
import useNewPermission from "@/hooks/useNewPermission";

interface PaginationChangeContext {
  current: number;
  pageSize: number;
}

interface SummaryCard {
  key: string;
  label: string;
  value: number;
  pillClass: string;
}

interface SyncRangeOption {
  label: string;
  value: number;
  successText: string;
  longRunning?: boolean;
}

interface AccountSelectOption {
  label: string;
  value: number;
}

interface MobileStatusOption {
  label: string;
  value: number;
}

const FAILURE_STATUSES = new Set([2, 11]);
const ERROR_DETAIL_STATUSES = new Set([2, 3, 11]);
const SUCCESS_STATUSES = new Set([1, 10, 200]);

const { hasPermission } = usePermission();
const { hasButtonPermission } = useNewPermission();
const { width } = useWindowSize();

const canViewOrderRecord = computed(() => hasPermission(PermissionConstant.TASK_RECORD_LIST));
const canTriggerC5Sync = computed(() => hasButtonPermission(PermissionConstant.ORDER_C5_SYNC));
const canDeleteOrderRecord = computed(() => hasButtonPermission(PermissionConstant.ORDER_RECORD_DELETE));
const isMobile = computed(() => width.value <= 768);

const loading = ref(false);
const c5SyncLoading = ref(false);
const accountsLoading = ref(false);
const c5Accounts = ref<C5SnipingAccount[]>([]);
const syncRangeOptions: SyncRangeOption[] = [
  { label: "今天", value: 0, successText: "C5 订单同步任务已提交（今天）" },
  { label: "昨天", value: 1, successText: "C5 订单同步任务已提交（昨天）" },
  { label: "最近 3 天", value: 3, successText: "C5 订单同步任务已提交（最近 3 天）" },
  { label: "最近 7 天", value: 7, successText: "C5 订单同步任务已提交（最近 7 天）" },
  { label: "全部", value: -1, successText: "C5 订单同步任务已提交（全部历史）", longRunning: true },
];
const selectedSyncRange = ref<number>(1);
const selectedSyncAccountId = ref<number>();
const mobileStatusOptions: MobileStatusOption[] = [
  { label: "全部", value: 0 },
  { label: "成功", value: 1 },
  { label: "失败", value: 2 },
  { label: "取消", value: 3 },
];
const dataList = ref<TradeOrderRecord[]>([]);
const selectedRowKeys = ref<(string | number)[]>([]);
const showAdvancedFilters = ref(!isMobile.value);
const activeTab = ref(0);
const orderRecordBodyRef = ref<HTMLElement | null>(null);
const handleOrderRecordBodyRefChange = (element: HTMLElement | null) => {
  orderRecordBodyRef.value = element;
};
const orderRecordTableViewportRef = ref<HTMLElement | null>(null);
const orderRecordTableRef = ref<TableInstanceFunctions<TradeOrderRecord> | null>(null);
const { height: orderRecordTableViewportHeight } = useElementSize(orderRecordTableViewportRef);

const queryParams = reactive<OrderQueryParam>({
  page: 1,
  pageSize: 20,
  status: undefined,
  accountId: undefined,
  keyword: "",
  startDate: undefined,
  endDate: undefined,
});

const dateRange = ref<DateRangeValue>([]);

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showJumper: true,
  size: "small",
});

const orderTableHeaderClass =
  "!bg-slate-50 !text-slate-500 !text-xs !font-semibold !tracking-[0.04em] whitespace-nowrap";
const orderTableBodyClass = "!py-2 text-sm text-slate-700 align-middle";
const toolbarFieldClass =
  "w-full [&_.t-input__wrap]:min-h-10 [&_.t-input__wrap]:border-slate-200 [&_.t-input__wrap]:bg-white [&_.t-input__wrap]:shadow-none [&_.t-input__wrap:hover]:border-slate-300 [&_.t-is-focused]:border-sky-500 [&_.t-is-focused]:shadow-[0_0_0_3px_rgb(14_165_233_/_0.12)]";
const toolbarCompactFieldClass =
  "[&_.t-input__wrap]:min-h-9 [&_.t-input__wrap]:border-slate-200 [&_.t-input__wrap]:bg-white [&_.t-input__wrap]:shadow-none [&_.t-input__wrap:hover]:border-slate-300 [&_.t-is-focused]:border-sky-500 [&_.t-is-focused]:shadow-[0_0_0_3px_rgb(14_165_233_/_0.12)]";
const ORDER_TABLE_MIN_HEIGHT = 320;
const ORDER_TABLE_MAX_HEIGHT_OFFSET = 1;
const orderTableMaxHeight = computed(() => {
  const viewportHeight = orderRecordTableViewportHeight.value;
  if (!viewportHeight) {
    return ORDER_TABLE_MIN_HEIGHT;
  }

  return Math.max(
    Math.floor(viewportHeight - ORDER_TABLE_MAX_HEIGHT_OFFSET),
    ORDER_TABLE_MIN_HEIGHT
  );
});

const priceFormatter = new Intl.NumberFormat("zh-CN", {
  style: "currency",
  currency: "CNY",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const columns = computed<PrimaryTableCol[]>(() => [
  {
    colKey: "row-select",
    type: "multiple",
    width: 56,
    fixed: "left",
    className: `${orderTableBodyClass} !bg-white`,
    thClassName: orderTableHeaderClass,
  },
  {
    colKey: "goods",
    title: "商品信息",
    width: 280,
    cell: "goods",
    align: "left",
    className: orderTableBodyClass,
    thClassName: orderTableHeaderClass,
  },
  {
    colKey: "account",
    title: "平台/账号",
    width: 150,
    cell: "account",
    align: "left",
    className: orderTableBodyClass,
    thClassName: orderTableHeaderClass,
  },
  {
    colKey: "orderId",
    title: "C5订单号",
    width: 180,
    cell: "orderId",
    className: orderTableBodyClass,
    thClassName: orderTableHeaderClass,
  },
  {
    colKey: "price",
    title: "价格",
    width: 120,
    cell: "price",
    sorter: true,
    className: orderTableBodyClass,
    thClassName: orderTableHeaderClass,
  },
  {
    colKey: "status",
    title: "状态",
    width: 220,
    cell: "status",
    sorter: true,
    className: orderTableBodyClass,
    thClassName: orderTableHeaderClass,
  },
  {
    colKey: "createTime",
    title: "时间",
    width: 180,
    cell: "time",
    sorter: true,
    className: orderTableBodyClass,
    thClassName: orderTableHeaderClass,
  },
  {
    colKey: "operation",
    title: "操作",
    width: 120,
    fixed: "right",
    cell: "operation",
    className: `${orderTableBodyClass} !bg-white`,
    thClassName: orderTableHeaderClass,
  },
]);

const selectedRecords = computed(() => {
  const keySet = new Set(selectedRowKeys.value.map((key) => Number(key)));
  return dataList.value.filter((item) => keySet.has(Number(item.id)));
});

const currentSyncRange = computed(
  () =>
    syncRangeOptions.find((item) => item.value === selectedSyncRange.value) ?? syncRangeOptions[1]
);

const getAccountOptionLabel = (account: C5SnipingAccount) =>
  `${account.accountName}${account.id ? `（${account.id}）` : ""}`;

const accountSelectOptions = computed<AccountSelectOption[]>(() =>
  c5Accounts.value
    .filter((item): item is C5SnipingAccount & { id: number } => typeof item.id === "number")
    .map((item) => ({
      label: getAccountOptionLabel(item),
      value: item.id,
    }))
);

const shouldConfirmFullHistorySync = computed(
  () => currentSyncRange.value.longRunning && !c5SyncLoading.value && !!selectedSyncAccountId.value
);
const syncConfirmContent = "确认同步全部历史订单？会拉取完整历史订单，耗时可能较长。";
const syncConfirmPopupProps = {
  overlayInnerStyle: {
    width: "320px",
    maxWidth: "calc(100vw - 32px)",
  },
};

const orderSummaryCards = computed<SummaryCard[]>(() => {
  const summary = dataList.value.reduce(
    (acc, item) => {
      acc.total += 1;
      if (SUCCESS_STATUSES.has(item.status)) {
        acc.success += 1;
      }
      if (FAILURE_STATUSES.has(item.status)) {
        acc.failure += 1;
      }
      if (item.status === 3) {
        acc.canceled += 1;
      }
      return acc;
    },
    { total: 0, success: 0, failure: 0, canceled: 0 }
  );

  return [
    {
      key: "all",
      label: "全部",
      value: summary.total,
      pillClass: "bg-[#f5f7fa] text-[#606266] border border-[#e4e7ed]",
    },
    {
      key: "success",
      label: "成功",
      value: summary.success,
      pillClass: "bg-[#f0fdf4] text-[#16a34a] border border-[#bbf7d0]",
    },
    {
      key: "failure",
      label: "失败",
      value: summary.failure,
      pillClass: "bg-[#fef2f2] text-[#ef4444] border border-[#fecaca]",
    },
    {
      key: "cancel",
      label: "取消",
      value: summary.canceled,
      pillClass: "bg-[#f5f3ff] text-[#7c3aed] border border-[#ddd6fe]",
    },
  ];
});

const formatPrice = (value?: number | string | null) => {
  if (value === undefined || value === null || value === "") {
    return "-";
  }

  const numericValue = typeof value === "string" ? Number(value) : value;
  return Number.isNaN(numericValue) ? "-" : priceFormatter.format(numericValue);
};

const formatPaintwear = (value?: number) => {
  if (value === undefined || value === null || value <= 0) {
    return "无";
  }
  return String(value);
};

const isFailureStatus = (status: number) => FAILURE_STATUSES.has(status);
const shouldShowErrorDetail = (status: number) => ERROR_DETAIL_STATUSES.has(status);
const isSuccessStatus = (status: number) => SUCCESS_STATUSES.has(status);

const getStatusMeta = (
  status: number
): { label: string; theme: NonNullable<TagProps["theme"]> } => {
  if (isSuccessStatus(status)) {
    return { label: "购买成功", theme: "success" };
  }

  if (isFailureStatus(status)) {
    return { label: "购买失败", theme: "danger" };
  }

  if (status === 3) {
    return { label: "已取消", theme: "default" };
  }

  return { label: "处理中", theme: "primary" };
};

const getErrorText = (errorMsg?: string) => errorMsg || "未知错误";

const getOrderErrorDetail = (row: TradeOrderRecord) => {
  if (row.errorCode && row.errorMsg) {
    return `${row.errorCode} · ${row.errorMsg}`;
  }
  return row.errorCode || getErrorText(row.errorMsg);
};

const formatTime = (time?: string | number) => {
  if (!time) return "-";
  return dayjs(time).format("YYYY-MM-DD HH:mm:ss");
};

const fetchData = async () => {
  if (!canViewOrderRecord.value) {
    dataList.value = [];
    pagination.total = 0;
    selectedRowKeys.value = [];
    return;
  }

  loading.value = true;
  try {
    const res = await orderApi.getPage(queryParams);
    dataList.value = res.records ?? [];
    pagination.total = res.total;
    pagination.current = res.current;
    pagination.pageSize = res.size;
    selectedRowKeys.value = selectedRowKeys.value.filter((key) =>
      dataList.value.some((item) => Number(item.id) === Number(key))
    );
  } catch (error) {
    console.error("获取订单记录失败", error);
  } finally {
    loading.value = false;
  }
};

const syncDateRangeToQuery = (value: DateRangeValue) => {
  const [startDate, endDate] = Array.isArray(value) ? value : [];
  queryParams.startDate = typeof startDate === "string" ? startDate : undefined;
  queryParams.endDate = typeof endDate === "string" ? endDate : undefined;
};

const handleSearch = () => {
  syncDateRangeToQuery(dateRange.value);
  queryParams.page = 1;
  pagination.current = 1;
  selectedRowKeys.value = [];
  fetchData();
};

const handleKeywordClear = () => {
  queryParams.keyword = "";
  handleSearch();
};

const handleDateRangeChange = (value: DateRangeValue) => {
  dateRange.value = Array.isArray(value) ? value : [];
  handleSearch();
};

const handleTabChange = (value: string | number) => {
  const nextTab = Number(value);
  activeTab.value = nextTab;

  if (nextTab === 0) {
    queryParams.status = undefined;
  } else if (nextTab === 1) {
    queryParams.status = 1;
  } else if (nextTab === 2) {
    queryParams.status = 2;
  } else if (nextTab === 3) {
    queryParams.status = 3;
  }

  queryParams.page = 1;
  pagination.current = 1;
  selectedRowKeys.value = [];
  fetchData();
};

const handleReset = () => {
  dateRange.value = [];
  queryParams.keyword = "";
  queryParams.status = undefined;
  queryParams.accountId = undefined;
  queryParams.startDate = undefined;
  queryParams.endDate = undefined;
  queryParams.sortField = undefined;
  queryParams.sortOrder = undefined;
  queryParams.page = 1;
  activeTab.value = 0;
  selectedRowKeys.value = [];
  fetchData();
};

const onPageChange = async (pageInfo: PaginationChangeContext) => {
  queryParams.page = pageInfo.current;
  queryParams.pageSize = pageInfo.pageSize;
  pagination.current = pageInfo.current;
  pagination.pageSize = pageInfo.pageSize;
  selectedRowKeys.value = [];
  await fetchData();
  await nextTick();
  orderRecordTableRef.value?.scrollToElement({ index: 0, top: 0, behavior: "auto" });
};

const onSortChange = (sort: TableSort) => {
  const currentSort = Array.isArray(sort) ? sort[0] : (sort as SortInfo | undefined);
  queryParams.sortField = currentSort?.sortBy;
  queryParams.sortOrder = currentSort?.sortBy
    ? currentSort.descending
      ? "desc"
      : "asc"
    : undefined;
  selectedRowKeys.value = [];
  fetchData();
};

const handleDeleteSuccess = async (deletedIds: number[], message: string) => {
  const deletedKeySet = new Set(deletedIds.map((id) => Number(id)));
  selectedRowKeys.value = selectedRowKeys.value.filter((item) => !deletedKeySet.has(Number(item)));
  MessagePlugin.success(message);
  await fetchData();
};

const logDeleteError = (action: string, error: unknown) => {
  console.error(`${action}失败`, error);
};

const handleDelete = async (id: number) => {
  try {
    await orderApi.delete(id);
    await handleDeleteSuccess([id], "删除成功");
  } catch (error) {
    logDeleteError("删除订单记录", error);
  }
};

const copyText = async (text: string) => {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }

  const textarea = document.createElement("textarea");
  textarea.value = text;
  textarea.setAttribute("readonly", "true");
  textarea.style.position = "fixed";
  textarea.style.top = "-9999px";
  document.body.appendChild(textarea);
  textarea.select();
  document.execCommand("copy");
  document.body.removeChild(textarea);
};

const handleCopyOrderId = async (orderId?: string) => {
  if (!orderId) {
    return;
  }

  try {
    await copyText(orderId);
    MessagePlugin.success("C5订单号已复制");
  } catch (error) {
    console.error("复制订单号失败", error);
    MessagePlugin.error("复制失败，请手动复制");
  }
};

const handleSelectChange = (value: (string | number)[]) => {
  selectedRowKeys.value = value;
};

const isMobileRowSelected = (id: number) =>
  selectedRowKeys.value.some((item) => Number(item) === Number(id));

const toggleMobileRowSelection = (id: number) => {
  const key = Number(id);
  if (isMobileRowSelected(key)) {
    selectedRowKeys.value = selectedRowKeys.value.filter((item) => Number(item) !== key);
    return;
  }

  selectedRowKeys.value = Array.from(new Set([...selectedRowKeys.value, key]));
};

const clearSelection = () => {
  selectedRowKeys.value = [];
};

const toggleAdvancedFilters = () => {
  showAdvancedFilters.value = !showAdvancedFilters.value;
};

const fetchAccounts = async () => {
  accountsLoading.value = true;
  try {
    const res = await c5SnipingAccountApi.getAccounts();
    c5Accounts.value = res?.records || [];
  } catch (error) {
    console.error("C5 账号列表加载失败", error);
    MessagePlugin.error("C5 账号列表加载失败");
  } finally {
    accountsLoading.value = false;
  }
};

const handleBatchDelete = async () => {
  if (!canDeleteOrderRecord.value) {
    MessagePlugin.warning("当前账号没有订单删除权限");
    return;
  }

  const deletableIds = selectedRecords.value
    .map((item) => item.id)
    .filter((id): id is number => typeof id === "number");

  if (deletableIds.length === 0) {
    MessagePlugin.warning("请选择可删除的订单记录");
    return;
  }

  try {
    await orderApi.batchDelete(deletableIds);
    await handleDeleteSuccess(deletableIds, `已批量删除 ${deletableIds.length} 条订单记录`);
  } catch (error) {
    logDeleteError("批量删除订单", error);
  }
};

const triggerC5SyncRequest = async (currentRange: SyncRangeOption, accountId: number) => {
  c5SyncLoading.value = true;
  try {
    const res = await orderApi.triggerC5Sync(accountId, currentRange.value);
    const message = typeof res === "string" ? res : currentRange.successText;
    const isDuplicateTrigger =
      message.includes("请勿重复触发") ||
      message.includes("正在执行") ||
      message.includes("重复提交") ||
      message.includes("60 秒内");
    MessagePlugin[isDuplicateTrigger ? "warning" : "success"](message);
    if (!isDuplicateTrigger && canViewOrderRecord.value) {
      fetchData();
    }
  } catch (error) {
    console.error("触发 C5 订单同步失败", error);
    MessagePlugin.error("触发 C5 订单同步失败，请稍后重试");
  } finally {
    c5SyncLoading.value = false;
  }
};

const getSelectedSyncAccountId = () => {
  const accountId = selectedSyncAccountId.value;
  return typeof accountId === "number" ? accountId : undefined;
};

const warnMissingSyncAccount = () => {
  MessagePlugin.warning(accountSelectOptions.value.length > 0 ? "请选择同步账号" : "请先配置/选择 C5 账号");
};

const handleConfirmFullHistorySync = async () => {
  const accountId = getSelectedSyncAccountId();
  if (!accountId) {
    warnMissingSyncAccount();
    return;
  }

  await triggerC5SyncRequest(currentSyncRange.value, accountId);
};

const handleC5Sync = async () => {
  if (!canTriggerC5Sync.value) {
    MessagePlugin.warning("当前账号没有 C5 同步权限");
    return;
  }

  if (c5SyncLoading.value) {
    MessagePlugin.warning("同步任务正在执行，请勿重复触发");
    return;
  }

  const accountId = getSelectedSyncAccountId();
  if (!accountId) {
    if (isMobile.value) {
      showAdvancedFilters.value = true;
    }
    warnMissingSyncAccount();
    return;
  }

  const currentRange = currentSyncRange.value;
  if (currentRange.longRunning) {
    return;
  }

  await triggerC5SyncRequest(currentRange, accountId);
};

watch(isMobile, (mobile) => {
  showAdvancedFilters.value = !mobile;
});

onMounted(() => {
  fetchAccounts();
});

watch(
  canViewOrderRecord,
  (allowed) => {
    if (allowed) {
      fetchData();
      return;
    }

    dataList.value = [];
    pagination.total = 0;
    selectedRowKeys.value = [];
  },
  { immediate: true }
);
</script>

<style scoped>
.jsh-expand-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
  padding: 0 4px;
  border: 0;
  background: transparent;
  color: rgb(71 85 105);
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  user-select: none;
}

.jsh-expand-link:hover {
  color: rgb(15 23 42);
}

.table-operator :deep(.t-popup__reference) {
  display: inline-flex;
}

:deep(.jsh-action-btn.t-button) {
  min-width: 88px;
  border-radius: 4px;
  box-shadow: none;
}

.jsh-filter-item {
  min-width: 0;
}

.order-sync-control {
  min-width: 0;
  flex-wrap: wrap;
}

.order-sync-control__label {
  flex-shrink: 0;
  color: rgb(71 85 105);
  font-size: 13px;
  line-height: 1;
}

.order-sync-control__account-select {
  width: 180px;
  min-width: 180px;
}

.order-sync-control__select {
  width: 108px;
  min-width: 108px;
}

.task-selection-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.selection-summary__count {
  flex-shrink: 0;
}

.order-toolbar-main {
  gap: 12px;
}

.order-overview-inline {
  align-self: center;
}

.order-overview-pill {
  min-height: 28px;
  white-space: nowrap;
}

@media (max-width: 1200px) and (min-width: 641px) {
  .order-toolbar-main {
    align-items: flex-start;
  }

  .order-overview-inline {
    flex: 0 0 100%;
    width: 100%;
    align-self: flex-start;
  }
}

.jsh-ledger-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100%;
}

:deep(.order-c5-table .t-table__header th) {
  padding-top: 10px;
  padding-bottom: 10px;
}

:deep(.order-c5-table .t-table__body td) {
  padding-top: 8px;
  padding-bottom: 8px;
}

:deep(.order-c5-table .t-table) {
  border: none;
  border-radius: 0;
  box-shadow: none;
}

:deep(.order-c5-table .t-table__content) {
  border: none;
  border-radius: 0;
}

:deep(.order-c5-table .t-table__header) {
  overflow: visible;
  border-top-left-radius: 0;
  border-top-right-radius: 0;
}

:deep(.order-c5-table .t-table__row--hover td) {
  background: #fcfcfc !important;
}

.order-record-table-wrap {
  display: flex;
  flex-direction: column;
}

.order-status-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.order-status-cell__error {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: #909399;
}

.order-status-cell__error-code {
  flex-shrink: 0;
  color: #e34d59;
  font-weight: 600;
}

.order-status-cell__error-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-record-table-wrap :deep(.t-table) {
  flex: 1;
  min-height: 0;
}

.order-mobile {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-bottom: 8px;
}

.order-mobile__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: #fff;
}

.order-mobile__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 240px;
  color: #909399;
}

.order-mobile-card {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
  padding: 8px 10px;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background-color 0.2s ease;
}

.order-mobile-card--selected {
  border-color: #ffb27a;
  background: #fff7ed;
  box-shadow: 0 0 0 1px rgba(255, 122, 36, 0.12);
}

.order-mobile-card:focus-visible {
  outline: 2px solid #ff7a24;
  outline-offset: 2px;
}

.order-mobile-card__thumb {
  width: 52px;
  height: 56px;
  flex-shrink: 0;
  border-radius: 7px;
  border: 1px solid #e5e7eb;
  background: #f9fafb;
}

.order-mobile-card__content {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 4px;
}

.order-mobile-card__price {
  font-size: 18px;
  line-height: 1;
  font-weight: 600;
  color: #ff7a24;
}

.order-mobile-card__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  line-height: 1.3;
  font-weight: 500;
  color: #303133;
}

.order-mobile-card__details {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
}

.order-mobile-card__detail-row {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 8px;
}

.order-mobile-card__detail-label {
  width: 52px;
  flex-shrink: 0;
  color: #909399;
  font-size: 11px;
  line-height: 1.5;
  white-space: nowrap;
}

.order-mobile-card__detail-value {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: #606266;
}

.order-mobile-card__detail-value--inline {
  flex-wrap: wrap;
}

.order-mobile-card__account {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  line-height: 1.5;
  color: #606266;
}

.order-mobile-card__time {
  color: #909399;
}

.order-mobile-card__order-id {
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  border: 0;
  background: transparent;
  padding: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #0052d9;
  cursor: pointer;
  text-align: left;
}

.order-mobile-card__order-id--muted {
  color: #b0b4bb;
}

.order-mobile-card__actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.order-mobile-card__error {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 4px;
  font-size: 11px;
  line-height: 1.4;
  color: #909399;
}

.order-mobile-card__error-code {
  flex-shrink: 0;
  color: #e34d59;
  font-weight: 600;
}

.order-mobile-card__error-text {
  min-width: 0;
  flex: 1 1 auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-mobile-card__delete-btn {
  min-width: 46px;
}

.order-mobile__pagination {
  display: flex;
  justify-content: center;
  padding: 4px 0 8px;
}

:deep(.jsh-tabs .t-tabs__nav-item) {
  height: 35px;
  line-height: 35px;
  padding: 0 14px;
  font-size: 13px;
}

:deep(.jsh-tabs .t-is-active) {
  color: #1890ff !important;
}

:deep(.jsh-tabs .t-tabs__nav-track) {
  background-color: #1890ff !important;
}

:deep(.niro-unified-table--empty .t-table__empty) {
  height: 100%;
}

@media (max-width: 640px) {
  .order-record-body {
    overflow: visible;
    overscroll-behavior: auto;
    -webkit-overflow-scrolling: touch;
  }

  .order-status-filter {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-bottom: 10px;
  }

  .order-status-filter__label {
    color: #606266;
    font-size: 13px;
    line-height: 1.5;
  }

  .order-status-filter__options {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 8px;
  }

  .order-status-filter__option {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 0;
    height: 32px;
    padding: 0 6px;
    border: 1px solid #dcdfe6;
    border-radius: 8px;
    background: #f5f7fa;
    color: #606266;
    font-size: 13px;
    line-height: 1;
    font-weight: 600;
    white-space: nowrap;
    transition:
      border-color 0.2s ease,
      background-color 0.2s ease,
      color 0.2s ease,
      box-shadow 0.2s ease;
  }

  .order-status-filter__option--active {
    border-color: #bfdbfe;
    background: #eff6ff;
    color: #2563eb;
    box-shadow: 0 0 0 1px rgba(59, 130, 246, 0.08);
  }

  .jsh-tabs {
    display: none;
  }

  .jsh-filter-layout {
    gap: 12px;
  }

  .jsh-filter-item {
    display: flex;
    flex-direction: column;
    width: 100%;
    align-items: stretch;
  }

  .jsh-filter-item:deep(.t-input),
  .jsh-filter-item:deep(.t-select),
  .jsh-filter-input,
  .jsh-filter-select {
    width: 100%;
  }

  .jsh-label {
    width: 100%;
    padding-right: 0;
    margin-bottom: 6px;
    line-height: 1.5;
    text-align: left;
  }

  .jsh-filter-actions {
    width: 100%;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .jsh-filter-actions :deep(.t-button) {
    min-width: 0;
    width: 100%;
    white-space: nowrap;
  }

  .jsh-expand-link {
    grid-column: 1 / -1;
    padding: 0;
    line-height: 1.5;
  }

  .table-operator--mobile {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
    align-items: center;
    width: 100%;
  }

  .table-operator--mobile > * {
    min-width: 0;
    width: 100%;
  }

  .order-sync-control--mobile {
    display: contents;
  }

  .order-sync-control--mobile .order-sync-control__label {
    grid-column: 1 / -1;
  }

  .order-sync-control--mobile .order-sync-control__account-select {
    grid-column: 1 / -1;
    width: 100%;
    min-width: 0;
  }

  .order-sync-control--mobile .order-sync-control__select {
    grid-column: 1 / 2;
    width: 100%;
    min-width: 0;
  }

  .order-sync-control--mobile .order-sync-control__account-select :deep(.t-input),
  .order-sync-control--mobile .order-sync-control__account-select :deep(.t-input__wrap),
  .order-sync-control--mobile .order-sync-control__select :deep(.t-input),
  .order-sync-control--mobile .order-sync-control__select :deep(.t-input__wrap) {
    min-height: 0;
  }

  .table-operator--mobile :deep(.t-popup__reference) {
    display: block;
    width: 100%;
  }

  .table-operator--mobile :deep(.t-button) {
    min-width: 0;
    width: 100%;
    margin: 0;
    white-space: nowrap;
  }

  .task-selection-summary {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 10px 12px;
    width: 100%;
  }

  .selection-summary__count {
    flex-shrink: 0;
    max-width: 100%;
  }

  .task-selection-summary :deep(.t-button) {
    min-width: 0;
    white-space: nowrap;
  }

  .order-toolbar-main {
    width: 100%;
    flex-direction: column;
    align-items: stretch;
    flex: none;
  }

  .order-overview-inline--mobile {
    display: flex;
    flex-wrap: nowrap;
    align-items: center;
    gap: 4px;
    width: 100%;
  }

  .order-overview-inline--mobile .order-overview-pill {
    min-width: 0;
    flex: 1 1 0;
  }

  .order-overview-pill {
    justify-content: center;
    min-width: 0;
    flex: 1 1 0;
    padding: 0 6px;
  }

  .order-record-main {
    padding-right: 0;
    padding-left: 0;
  }

  .order-mobile {
    min-height: auto;
    gap: 6px;
    padding-top: 0;
    padding-bottom: 0;
  }

  .order-mobile__list {
    gap: 6px;
    padding: 0;
  }

  .order-mobile-card {
    gap: 6px;
    padding: 7px 8px;
  }

  .order-mobile-card__thumb {
    width: 52px;
    height: 52px;
  }

  .order-mobile-card__content {
    gap: 5px;
  }

  .order-mobile-card__price {
    font-size: 17px;
  }

  .order-mobile-card__detail-row {
    gap: 6px;
  }

  .order-mobile-card__detail-label {
    width: 52px;
  }

  .order-mobile-card__detail-value {
    font-size: 11px;
  }

  .order-mobile-card__account {
    max-width: 100%;
  }

  .order-mobile-card__error {
    gap: 3px;
  }

  .order-mobile-card__actions :deep(.t-button) {
    min-width: 0;
    white-space: nowrap;
  }
}
</style>
