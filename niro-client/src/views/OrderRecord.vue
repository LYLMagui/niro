<template>
  <div class="flex h-full min-h-0 flex-col px-1 pt-1 pb-2">
    <section
      :class="[
        'flex min-h-0 flex-1 flex-col rounded-[1px] bg-white',
        isMobile ? 'overflow-visible' : 'overflow-hidden',
      ]"
    >
      <div
        ref="orderRecordBodyRef"
        :class="[
          'order-record-body relative flex min-h-0 flex-1 flex-col overflow-x-hidden',
          isMobile ? 'overflow-y-visible' : 'overflow-hidden',
        ]"
      >
        <t-tabs
          v-model="activeTab"
          class="jsh-tabs border-b border-[#e8e8e8] bg-white px-4"
          @change="handleTabChange"
        >
          <t-tab-panel :value="0" label="全部" />
          <t-tab-panel :value="1" label="成功" />
          <t-tab-panel :value="2" label="失败" />
          <t-tab-panel :value="3" label="取消" />
        </t-tabs>

        <div class="px-4 pt-3">
          <div class="jsh-filter-layout flex flex-wrap items-center gap-x-6 gap-y-3">
            <div class="jsh-filter-item flex items-center">
              <span class="jsh-label">订单关键词：</span>
              <t-input
                v-model="queryParams.keyword"
                placeholder="请输入商品名/订单号"
                clearable
                class="jsh-filter-input !h-8"
                @enter="handleSearch"
              />
            </div>

            <div v-if="showAdvancedFilters" class="jsh-filter-item flex items-center">
              <span class="jsh-label">平台类型：</span>
              <t-select
                v-model="queryParams.platform"
                placeholder="请选择平台"
                clearable
                class="jsh-filter-select !h-8"
                @change="handleSearch"
              >
                <t-option value="BUFF" label="BUFF" />
                <t-option value="C5" label="C5" />
              </t-select>
            </div>

            <div class="jsh-filter-actions flex items-center gap-2">
              <t-button
                v-permission="PermissionConstant.TASK_RECORD_LIST"
                theme="primary"
                class="!h-8 px-4"
                @click="handleSearch"
              >
                查询
              </t-button>
              <t-button
                v-permission="PermissionConstant.TASK_RECORD_LIST"
                variant="outline"
                theme="default"
                class="!h-8 px-4"
                @click="handleReset"
              >
                重置
              </t-button>
              <a class="jsh-expand-link" @click="toggleAdvancedFilters">
                {{ showAdvancedFilters ? "收起" : "展开" }}
              </a>
            </div>
          </div>
        </div>

        <div class="mt-3 px-4 pt-2">
          <div class="jsh-toolbar flex flex-wrap items-start justify-between gap-y-3">
            <div class="order-toolbar-main flex min-w-0 flex-1 flex-wrap items-center gap-2">
              <div
                class="table-operator flex flex-wrap items-center"
                :class="{ 'table-operator--mobile': isMobile }"
              >
                <t-button
                  v-permission="PermissionConstant.TASK_C5_LIST"
                  variant="outline"
                  theme="default"
                  class="jsh-action-btn !h-8"
                  :loading="c5SyncLoading"
                  @click="handleC5Sync"
                >
                  同步 C5 订单
                </t-button>

                <t-popconfirm
                  v-permission="PermissionConstant.ORDER_RECORD_DELETE"
                  content="确认批量删除勾选订单吗？"
                  @confirm="handleBatchDelete"
                >
                  <t-button
                    variant="outline"
                    theme="default"
                    class="jsh-action-btn !h-8"
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
                  class="order-overview-pill inline-flex items-center gap-1.5 rounded-[6px] px-3 py-1.5"
                  :class="item.pillClass"
                >
                  <span class="text-[11px] leading-none">{{ item.label }}</span>
                  <span class="text-[15px] leading-none font-semibold">{{ item.value }}</span>
                </div>
              </div>
            </div>

            <div
              class="text-xs text-[#909399]"
              :class="isMobile ? 'task-selection-summary' : 'flex items-center gap-2'"
            >
              <span class="selection-summary__hint">提示：批量删除仅处理当前页已勾选数据</span>
              <t-tag theme="primary" variant="light" class="selection-summary__count rounded-[2px]">
                已选择 {{ selectedRowKeys.length }} 项
              </t-tag>
              <t-button
                variant="outline"
                theme="default"
                class="jsh-action-btn !h-8"
                :disabled="selectedRowKeys.length === 0"
                @click="clearSelection"
              >
                清空勾选
              </t-button>
            </div>
          </div>
        </div>

        <div
          :class="[
            'order-record-main relative px-4 pt-3 pb-4',
            isMobile ? 'min-h-fit flex-none' : 'min-h-0 flex-1',
          ]"
        >
          <div
            v-if="!isMobile"
            class="order-record-table-wrap relative h-full min-h-0 overflow-hidden"
          >
            <t-table
              row-key="id"
              height="100%"
              :data="dataList"
              :columns="columns"
              :loading="loading"
              :pagination="undefined"
              :selected-row-keys="selectedRowKeys"
              select-on-row-click
              hover
              :class="[
                'jsh-ledger-table',
                { 'jsh-ledger-table--empty': !loading && dataList.length === 0 },
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
                  />
                  <div class="flex min-w-0 flex-col">
                    <span class="truncate font-medium text-[#303133]" :title="row.goodsName">
                      {{ row.goodsName }}
                    </span>
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
                <template v-if="row.orderId">
                  <t-link
                    v-if="row.platform === 'C5'"
                    theme="primary"
                    class="font-mono-value text-xs"
                    @click="viewC5Detail(row)"
                  >
                    {{ row.orderId }}
                  </t-link>
                  <span v-else class="font-mono-value text-xs text-[#606266]">
                    {{ row.orderId }}
                  </span>
                </template>
                <span v-else class="text-xs text-[#b0b4bb]">未生成</span>
              </template>

              <template #price="{ row }">
                <span class="font-mono-value font-medium text-[#303133]">
                  {{ formatPrice(row.price) }}
                </span>
              </template>

              <template #status="{ row }">
                <t-tooltip
                  v-if="isFailureStatus(row.status)"
                  :content="getErrorText(row.errorMsg)"
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
              </template>

              <template #time="{ row }">
                <span class="text-[#606266]">{{ formatTime(row.createTime) }}</span>
              </template>

              <template #operation="{ row }">
                <div
                  v-permission="PermissionConstant.TASK_RECORD_LIST"
                  class="flex items-center gap-2"
                >
                  <t-link
                    v-if="row.platform === 'C5' && row.orderId"
                    theme="primary"
                    @click="viewC5Detail(row)"
                  >
                    详情
                  </t-link>
                  <t-popconfirm content="确认删除该订单记录吗？" @confirm="handleDelete(row.id)">
                    <t-link v-permission="PermissionConstant.ORDER_RECORD_DELETE" theme="danger">
                      删除
                    </t-link>
                  </t-popconfirm>
                </div>
              </template>
            </t-table>

            <div
              v-if="pagination.total > 0"
              class="order-table-pagination border-t border-[#ebeef5] bg-white px-4 py-3"
            >
              <t-pagination
                size="medium"
                :current="pagination.current"
                :page-size="pagination.pageSize"
                :total="pagination.total"
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
              <div v-for="row in dataList" :key="row.id" class="order-mobile-card">
                <div class="order-mobile-card__header">
                  <div class="order-mobile-card__goods">
                    <t-checkbox
                      :checked="selectedRowKeys.includes(row.id)"
                      @change="(checked) => handleMobileSelectChange(row.id, Boolean(checked))"
                    />
                    <t-image :src="row.goodsImg" class="order-mobile-card__thumb" fit="contain" />
                    <div class="min-w-0 flex-1">
                      <div
                        class="truncate text-sm font-medium text-[#303133]"
                        :title="row.goodsName"
                      >
                        {{ row.goodsName }}
                      </div>
                      <div class="mt-1 text-xs text-[#909399]">
                        磨损: {{ formatPaintwear(row.paintwear) }}
                      </div>
                    </div>
                  </div>

                  <t-tooltip
                    v-if="isFailureStatus(row.status)"
                    :content="getErrorText(row.errorMsg)"
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

                <div class="order-mobile-card__meta">
                  <div class="order-mobile-card__meta-item">
                    <span class="order-mobile-card__meta-label">平台/账号：</span>
                    <div class="flex min-w-0 flex-1 items-center gap-2">
                      <t-tag
                        :theme="row.platform === 'BUFF' ? 'warning' : 'primary'"
                        variant="light"
                        size="small"
                      >
                        {{ row.platform }}
                      </t-tag>
                      <span class="truncate text-[#606266]">{{ row.accountName || "-" }}</span>
                    </div>
                  </div>

                  <div class="order-mobile-card__meta-item">
                    <span class="order-mobile-card__meta-label">平台订单号：</span>
                    <t-link
                      v-if="row.platform === 'C5' && row.orderId"
                      theme="primary"
                      class="order-mobile-card__meta-link font-mono-value"
                      @click="viewC5Detail(row)"
                    >
                      {{ row.orderId }}
                    </t-link>
                    <span
                      v-else-if="row.orderId"
                      class="order-mobile-card__meta-text font-mono-value"
                    >
                      {{ row.orderId }}
                    </span>
                    <span v-else class="order-mobile-card__meta-text text-[#b0b4bb]">未生成</span>
                  </div>

                  <div class="order-mobile-card__meta-item">
                    <span class="order-mobile-card__meta-label">价格：</span>
                    <t-tag
                      class="order-mobile-card__meta-value order-mobile-card__meta-value--price"
                      size="small"
                      variant="light"
                    >
                      {{ formatPrice(row.price) }}
                    </t-tag>
                  </div>

                  <div class="order-mobile-card__meta-item">
                    <span class="order-mobile-card__meta-label">时间：</span>
                    <span class="order-mobile-card__meta-text">
                      {{ formatTime(row.createTime) }}
                    </span>
                  </div>
                </div>

                <div
                  class="order-mobile-card__actions"
                  v-permission="PermissionConstant.TASK_RECORD_LIST"
                >
                  <t-button
                    v-if="row.platform === 'C5' && row.orderId"
                    variant="outline"
                    theme="primary"
                    size="small"
                    @click="viewC5Detail(row)"
                  >
                    详情
                  </t-button>
                  <t-popconfirm content="确认删除该订单记录吗？" @confirm="handleDelete(row.id)">
                    <t-button
                      v-permission="PermissionConstant.ORDER_RECORD_DELETE"
                      variant="outline"
                      theme="danger"
                      size="small"
                    >
                      删除
                    </t-button>
                  </t-popconfirm>
                </div>
              </div>
            </div>

            <div v-if="!loading && pagination.total > 0" class="order-mobile__pagination">
              <t-pagination
                size="small"
                theme="simple"
                :current="pagination.current"
                :page-size="pagination.pageSize"
                :total="pagination.total"
                :show-page-size="false"
                :total-content="false"
                @change="onPageChange"
              />
            </div>
          </div>
        </div>
      </div>
    </section>

    <t-drawer
      v-model:visible="drawerVisible"
      header="C5 订单详情"
      size="500px"
      :footer="false"
      destroy-on-close
    >
      <div v-if="detailLoading" class="flex h-64 items-center justify-center">
        <t-loading />
      </div>
      <div v-else-if="orderDetail" class="p-4">
        <div class="mb-6 flex items-center gap-4 rounded-lg bg-gray-50 p-4">
          <t-image
            :src="orderDetail.goodsImg"
            class="h-16 w-16 rounded border border-gray-200 bg-white"
            fit="contain"
          />
          <div class="min-w-0">
            <div class="truncate font-bold text-gray-900">{{ orderDetail.goodsName || "-" }}</div>
            <div class="mt-1 text-xs break-all text-gray-400">
              订单号: {{ orderDetail.orderId || "-" }}
            </div>
          </div>
        </div>

        <t-descriptions bordered :column="1">
          <t-descriptions-item label="支付金额">
            <span class="font-mono-value font-bold text-red-600">
              {{ formatPrice(orderDetail.actualPay) }}
            </span>
          </t-descriptions-item>
          <t-descriptions-item label="订单状态">
            <t-tag :theme="getStatusMeta(orderDetail.status).theme" variant="light">
              {{ getStatusMeta(orderDetail.status).label }}
            </t-tag>
          </t-descriptions-item>
          <t-descriptions-item
            v-if="orderDetail.failedDesc || orderDetail.errorMsg"
            label="失败原因"
          >
            <span class="text-red-600">{{ orderDetail.failedDesc || orderDetail.errorMsg }}</span>
          </t-descriptions-item>
          <t-descriptions-item label="支付状态">
            {{ orderDetail.payStatus === 1 ? "已支付" : "未支付" }}
          </t-descriptions-item>
          <t-descriptions-item label="创建时间">
            {{ orderDetail.createTimeStr || formatTime(orderDetail.createTime) }}
          </t-descriptions-item>
        </t-descriptions>

        <div v-if="orderDetail.extra !== undefined && orderDetail.extra !== null" class="mt-6">
          <div class="mb-2 text-sm font-bold text-gray-700">扩展信息</div>
          <pre class="overflow-auto rounded bg-gray-900 p-4 text-xs text-green-400">{{
            formatExtra(orderDetail.extra)
          }}</pre>
        </div>
      </div>
      <t-empty v-else description="暂无详情数据" />
    </t-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useWindowSize } from "@vueuse/core";
import dayjs from "dayjs";
import {
  MessagePlugin,
  type PrimaryTableCol,
  type SortInfo,
  type TableSort,
  type TagProps,
} from "tdesign-vue-next";
import { CheckCircleIcon, CloseCircleIcon } from "tdesign-icons-vue-next";
import { orderApi } from "@/api/order";
import type { OrderQueryParam, TradeOrderRecord } from "@/types/order";
import { PermissionConstant } from "@/constant/PermissionConstant";
import { usePermission } from "@/hooks/usePermission";

interface PaginationChangeContext {
  current: number;
  pageSize: number;
}

interface OrderDetail {
  goodsImg?: string;
  goodsName?: string;
  orderId?: string;
  actualPay?: number | string;
  status: number;
  failedDesc?: string;
  errorMsg?: string;
  payStatus?: number;
  createTime?: string | number;
  createTimeStr?: string;
  extra?: Record<string, unknown> | string | number | boolean | null;
}

interface SummaryCard {
  key: string;
  label: string;
  value: number;
  pillClass: string;
}

const FAILURE_STATUSES = new Set([2, 11]);
const SUCCESS_STATUSES = new Set([1, 10, 200]);

const { hasPermission } = usePermission();
const { width } = useWindowSize();

const canViewOrderRecord = computed(() => hasPermission(PermissionConstant.TASK_RECORD_LIST));
const canTriggerC5Sync = computed(() => hasPermission(PermissionConstant.TASK_C5_LIST));
const canDeleteOrderRecord = computed(() => hasPermission(PermissionConstant.ORDER_RECORD_DELETE));
const isMobile = computed(() => width.value <= 640);

const loading = ref(false);
const c5SyncLoading = ref(false);
const dataList = ref<TradeOrderRecord[]>([]);
const selectedRowKeys = ref<(string | number)[]>([]);
const showAdvancedFilters = ref(!isMobile.value);
const activeTab = ref(0);
const orderRecordBodyRef = ref<HTMLElement | null>(null);

const queryParams = reactive<OrderQueryParam>({
  page: 1,
  pageSize: 20,
  platform: undefined,
  status: undefined,
  keyword: "",
});

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showJumper: true,
});

const priceFormatter = new Intl.NumberFormat("zh-CN", {
  style: "currency",
  currency: "CNY",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const columns = computed<PrimaryTableCol[]>(() => [
  { colKey: "row-select", type: "multiple", width: 56, fixed: "left" },
  { colKey: "id", title: "ID", width: 80 },
  { colKey: "goods", title: "商品信息", width: 280, cell: "goods" },
  { colKey: "account", title: "平台/账号", width: 150, cell: "account" },
  { colKey: "orderId", title: "平台订单号", width: 180, cell: "orderId" },
  { colKey: "price", title: "价格", width: 120, cell: "price", sorter: true },
  { colKey: "status", title: "状态", width: 140, cell: "status", sorter: true },
  { colKey: "createTime", title: "时间", width: 180, cell: "time", sorter: true },
  { colKey: "operation", title: "操作", width: 120, fixed: "right", cell: "operation" },
]);

const selectedRecords = computed(() => {
  const keySet = new Set(selectedRowKeys.value.map((key) => Number(key)));
  return dataList.value.filter((item) => keySet.has(Number(item.id)));
});

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

const formatTime = (time?: string | number) => {
  if (!time) return "-";
  return dayjs(time).format("YYYY-MM-DD HH:mm:ss");
};

const formatExtra = (extra: OrderDetail["extra"]) => {
  if (extra === undefined || extra === null) {
    return "-";
  }

  if (typeof extra === "string") {
    return extra;
  }

  return JSON.stringify(extra, null, 2);
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

const handleSearch = () => {
  queryParams.page = 1;
  pagination.current = 1;
  selectedRowKeys.value = [];
  fetchData();
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
  queryParams.keyword = "";
  queryParams.platform = undefined;
  queryParams.status = undefined;
  queryParams.sortField = undefined;
  queryParams.sortOrder = undefined;
  queryParams.page = 1;
  activeTab.value = 0;
  selectedRowKeys.value = [];
  fetchData();
};

const onPageChange = (pageInfo: PaginationChangeContext) => {
  queryParams.page = pageInfo.current;
  queryParams.pageSize = pageInfo.pageSize;
  pagination.current = pageInfo.current;
  pagination.pageSize = pageInfo.pageSize;
  selectedRowKeys.value = [];
  fetchData();
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

const handleDelete = async (id: number) => {
  try {
    await orderApi.delete(id);
    MessagePlugin.success("删除成功");
    fetchData();
  } catch (error) {
    console.error("删除订单记录失败", error);
  }
};

const handleSelectChange = (value: (string | number)[]) => {
  selectedRowKeys.value = value;
};

const handleMobileSelectChange = (id: number, checked: boolean) => {
  const key = Number(id);
  if (checked) {
    selectedRowKeys.value = Array.from(new Set([...selectedRowKeys.value, key]));
    return;
  }

  selectedRowKeys.value = selectedRowKeys.value.filter((item) => Number(item) !== key);
};

const clearSelection = () => {
  selectedRowKeys.value = [];
};

const toggleAdvancedFilters = () => {
  showAdvancedFilters.value = !showAdvancedFilters.value;
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
    await Promise.all(deletableIds.map((id) => orderApi.delete(id)));
    MessagePlugin.success(`已批量删除 ${deletableIds.length} 条订单记录`);
    selectedRowKeys.value = [];
    fetchData();
  } catch (error) {
    console.error("批量删除订单失败", error);
  }
};

const handleC5Sync = async () => {
  if (!canTriggerC5Sync.value) {
    MessagePlugin.warning("当前账号没有 C5 同步权限");
    return;
  }

  c5SyncLoading.value = true;
  try {
    const res = await orderApi.triggerC5Sync(1);
    MessagePlugin.success(typeof res === "string" ? res : "C5 订单同步任务已触发");
    if (canViewOrderRecord.value) {
      fetchData();
    }
  } catch (error) {
    console.error("触发 C5 订单同步失败", error);
  } finally {
    c5SyncLoading.value = false;
  }
};

const drawerVisible = ref(false);
const detailLoading = ref(false);
const orderDetail = ref<OrderDetail | null>(null);

const viewC5Detail = async (row: TradeOrderRecord) => {
  if (!canViewOrderRecord.value) {
    MessagePlugin.warning("当前账号没有订单记录权限");
    return;
  }

  if (row.platform !== "C5" || !row.orderId) {
    MessagePlugin.info("暂不支持查询非 C5 平台详情");
    return;
  }

  drawerVisible.value = true;
  detailLoading.value = true;
  orderDetail.value = null;
  try {
    orderDetail.value = (await orderApi.getC5Detail(row.orderId)) as OrderDetail;
  } catch (error) {
    console.error("获取 C5 订单详情失败", error);
  } finally {
    detailLoading.value = false;
  }
};

watch(isMobile, (mobile) => {
  showAdvancedFilters.value = !mobile;
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
    drawerVisible.value = false;
    orderDetail.value = null;
  },
  { immediate: true }
);
</script>

<style scoped>
.jsh-filter-layout {
  row-gap: 12px;
}

.jsh-filter-item {
  flex-shrink: 0;
}

.jsh-label {
  width: 96px;
  padding-right: 10px;
  color: #303133;
  font-size: 13px;
  line-height: 32px;
  text-align: right;
  white-space: nowrap;
}

.jsh-expand-link {
  padding: 0 4px;
  color: rgb(24, 144, 255);
  line-height: 32px;
  user-select: none;
}

.jsh-expand-link:hover {
  color: rgb(64, 169, 255);
}

.table-operator :deep(.t-button) {
  margin: 0 8px 8px 0;
}

.task-selection-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.selection-summary__hint {
  min-width: 0;
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

:deep(.jsh-ledger-table) {
  height: 100%;
}

.order-record-table-wrap {
  display: flex;
  flex-direction: column;
}

.order-record-table-wrap :deep(.t-table) {
  flex: 1;
  min-height: 0;
}

.order-table-pagination {
  flex-shrink: 0;
}

.order-mobile {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 12px;
}

.order-mobile__list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.order-mobile__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 240px;
  color: #909399;
}

.order-mobile-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
  padding: 12px;
}

.order-mobile-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.order-mobile-card__goods {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: flex-start;
  gap: 10px;
}

.order-mobile-card__thumb {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
  background: #f9fafb;
}

.order-mobile-card__meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}

.order-mobile-card__meta-item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-width: 0;
  font-size: 12px;
  color: #303133;
}

.order-mobile-card__meta-label {
  flex: 0 0 72px;
  color: #909399;
  white-space: nowrap;
}

.order-mobile-card__meta-text,
.order-mobile-card__meta-link {
  min-width: 0;
  flex: 1;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.order-mobile-card__meta-value {
  flex: 0 1 auto;
  max-width: calc(100% - 80px);
  font-weight: 600;
  color: #475569;
  background-color: #f8fafc;
  border-color: #e2e8f0;
}

.order-mobile-card__meta-value--price {
  background-color: #fef2f2;
  border-color: #fecdd3;
  color: #e11d48;
}

.order-mobile-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
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

:deep(.jsh-ledger-table--empty .t-table__empty) {
  height: 100%;
}

@media (max-width: 640px) {
  .order-record-body {
    overflow: visible;
    overscroll-behavior: auto;
    -webkit-overflow-scrolling: touch;
  }

  .jsh-toolbar {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .jsh-toolbar > .task-selection-summary,
  .jsh-toolbar > .flex.items-center.gap-2 {
    width: 100%;
  }

  .jsh-filter-layout {
    gap: 12px;
  }

  .jsh-filter-item {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    width: 100%;
  }

  .jsh-filter-item:deep(.t-input),
  .jsh-filter-item:deep(.t-select),
  .jsh-filter-input,
  .jsh-filter-select {
    width: 100%;
  }

  .jsh-label {
    width: 100%;
    margin-bottom: 6px;
    padding-right: 0;
    line-height: 1.5;
    text-align: left;
  }

  .jsh-filter-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
    width: 100%;
  }

  .jsh-filter-actions :deep(.t-button) {
    min-width: 0;
    width: 100%;
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
    width: 100%;
  }

  .table-operator--mobile > * {
    min-width: 0;
    width: 100%;
  }

  .table-operator--mobile :deep(.t-popup__reference) {
    display: block;
    width: 100%;
  }

  .table-operator--mobile :deep(.t-button) {
    min-width: 0;
    width: 100%;
    height: 34px !important;
    margin: 0;
    padding: 0 8px;
    font-size: 13px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .task-selection-summary {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 10px 12px;
    width: 100%;
  }

  .selection-summary__hint {
    min-width: 0;
    line-height: 1.5;
    overflow-wrap: anywhere;
    word-break: break-word;
  }

  .selection-summary__count {
    flex-shrink: 0;
    max-width: 100%;
  }

  .task-selection-summary :deep(.t-button) {
    min-width: 0;
    height: 32px !important;
    padding: 0 10px;
    font-size: 13px;
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

  .order-overview-pill {
    justify-content: center;
    min-width: 0;
    flex: 1 1 0;
    padding: 4px 6px;
  }

  .order-record-main {
    padding-right: 0;
    padding-left: 0;
    padding-bottom: 16px;
  }

  .order-mobile {
    min-height: auto;
  }

  .order-mobile-card {
    padding: 10px;
  }

  .order-mobile-card__header {
    flex-direction: column;
    align-items: stretch;
  }

  .order-mobile-card__meta-item {
    align-items: flex-start;
  }

  .order-mobile-card__actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .order-mobile-card__actions :deep(.t-button) {
    min-width: 0;
    width: 100%;
    height: 40px;
    padding: 0 10px;
    font-size: 12px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}
</style>
