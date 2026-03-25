<template>
  <div class="bg-[#f5f5f5] px-1 pb-2 pt-1">
    <section class="overflow-hidden border border-[#d9d9d9] bg-white">
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
        <div class="flex flex-wrap items-center gap-x-6 gap-y-3">
          <div class="flex items-center">
            <span class="jsh-label">订单关键词</span>
            <t-input
              v-model="queryParams.keyword"
              placeholder="请输入商品名/订单号"
              clearable
              class="!h-8 w-[320px]"
              @enter="handleSearch"
            />
          </div>
          <div class="flex items-center gap-2">
            <t-button
              v-permission="PermissionConstant.TASK_RECORD_LIST"
              theme="primary"
              class="!h-8 px-4"
              @click="handleSearch"
            >
              <template #icon><search-icon /></template>
              查询
            </t-button>
            <t-button
              v-permission="PermissionConstant.TASK_RECORD_LIST"
              theme="default"
              variant="base"
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

      <div v-if="showAdvancedFilters" class="px-4 pt-1">
        <div class="flex flex-wrap items-center gap-x-6 gap-y-3">
          <div class="flex items-center">
            <span class="jsh-label">平台类型</span>
            <t-select
              v-model="queryParams.platform"
              placeholder="请选择平台"
              clearable
              class="!h-8 w-[240px]"
              @change="handleSearch"
            >
              <t-option value="BUFF" label="BUFF" />
              <t-option value="C5" label="C5" />
            </t-select>
          </div>
        </div>
      </div>

      <div class="mt-[5px] border-t border-[#f2f2f2] px-4 pt-2">
        <div class="flex flex-wrap items-start justify-between gap-y-2">
          <div class="table-operator flex flex-wrap items-center">
            <t-button
              v-permission="PermissionConstant.TASK_C5_LIST"
              theme="default"
              variant="outline"
              class="!h-8"
              :loading="c5SyncLoading"
              @click="handleC5Sync"
            >
              同步 C5 订单
            </t-button>

            <t-popconfirm content="确认批量删除勾选订单吗？" @confirm="handleBatchDelete">
              <t-button
                v-permission="PermissionConstant.ORDER_RECORD_DELETE"
                theme="danger"
                variant="outline"
                class="!h-8"
                :disabled="selectedRowKeys.length === 0"
              >
                批量删除
              </t-button>
            </t-popconfirm>

            <t-button variant="text" theme="default" class="!h-8" @click="handleColumnSetting">
              列设置
            </t-button>
          </div>

          <div class="mb-2 flex items-center gap-2 text-xs text-[#909399]">
            <span>提示：批量删除仅处理当前页已勾选数据</span>
            <t-tag theme="primary" variant="light" class="rounded-[2px]">
              已选择 {{ selectedRowKeys.length }} 项
            </t-tag>
            <t-button
              variant="text"
              theme="default"
              class="!h-8"
              :disabled="selectedRowKeys.length === 0"
              @click="clearSelection"
            >
              清空勾选
            </t-button>
          </div>
        </div>
      </div>

      <div class="px-4 pb-4">
        <t-table
          row-key="id"
          :data="dataList"
          :columns="columns"
          :loading="loading"
          :pagination="pagination"
          :selected-row-keys="selectedRowKeys"
          select-on-row-click
          hover
          :header-affixed-top="{ offsetTop: 0, container: '.t-layout__content' }"
          class="jsh-ledger-table"
          @page-change="onPageChange"
          @sort-change="onSortChange"
          @select-change="handleSelectChange"
        >
          <template #empty>
            <t-empty description="暂无订单记录" />
          </template>

          <template #orderId="{ row }">
            <span v-if="row.orderId" v-permission="PermissionConstant.TASK_RECORD_LIST">
              <t-link theme="primary" class="font-mono text-xs" @click="viewC5Detail(row)">
                {{ row.orderId }}
              </t-link>
            </span>
            <span v-else class="text-xs text-gray-400">未生成</span>
          </template>

          <template #goods="{ row }">
            <div class="flex items-center gap-3">
              <t-image
                :src="row.goodsImg"
                class="h-10 w-10 shrink-0 rounded border border-gray-100 bg-gray-50"
                fit="contain"
              />
              <div class="flex flex-col overflow-hidden">
                <span class="truncate font-medium text-gray-900" :title="row.goodsName">
                  {{ row.goodsName }}
                </span>
                <span class="truncate text-xs text-gray-400">
                  磨损: {{ row.paintwear > 0 ? row.paintwear : "无" }}
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
              <span class="text-sm text-gray-600">{{ row.accountName || "-" }}</span>
            </div>
          </template>

          <template #price="{ row }">
            <span class="font-medium text-gray-900">¥{{ row.price.toFixed(2) }}</span>
          </template>

          <template #status="{ row }">
            <div v-if="row.status === 1">
              <t-tag theme="success" variant="light">
                <template #icon><check-circle-icon /></template>
                购买成功
              </t-tag>
            </div>
            <div v-else-if="row.status === 2 || row.status === 11">
              <t-tooltip :content="row.errorMsg || '未知错误'" placement="top">
                <t-tag theme="danger" variant="light" class="cursor-help">
                  <template #icon><close-circle-icon /></template>
                  购买失败
                </t-tag>
              </t-tooltip>
            </div>
            <div v-else-if="row.status === 3">
              <t-tag theme="default" variant="light">已取消</t-tag>
            </div>
            <div v-else>
              <t-tag theme="primary" variant="light">处理中</t-tag>
            </div>
          </template>

          <template #time="{ row }">
            <span class="text-gray-500">{{ formatTime(row.createTime) }}</span>
          </template>

          <template #operation="{ row }">
            <div v-permission="PermissionConstant.TASK_RECORD_LIST" class="flex items-center gap-2">
              <t-button
                v-if="row.platform === 'C5'"
                variant="text"
                theme="primary"
                size="small"
                @click="viewC5Detail(row)"
              >
                <template #icon><info-circle-icon /></template>
                详情
              </t-button>
              <t-popconfirm content="确认删除该订单记录吗？" @confirm="handleDelete(row.id)">
                <t-button
                  v-permission="PermissionConstant.ORDER_RECORD_DELETE"
                  variant="text"
                  theme="danger"
                  size="small"
                >
                  <template #icon><delete-icon /></template>
                  删除
                </t-button>
              </t-popconfirm>
            </div>
          </template>
        </t-table>
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
          <div>
            <div class="font-bold text-gray-900">{{ orderDetail.goodsName }}</div>
            <div class="text-xs text-gray-400">订单号: {{ orderDetail.orderId }}</div>
          </div>
        </div>

        <t-descriptions bordered :column="1">
          <t-descriptions-item label="支付金额">
            <span class="font-bold text-red-600">¥{{ orderDetail.actualPay || "0.00" }}</span>
          </t-descriptions-item>
          <t-descriptions-item label="订单状态">
            <t-tag :theme="getStatusTheme(orderDetail.status)" variant="light">
              {{ getStatusText(orderDetail.status) }}
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

        <div v-if="orderDetail.extra" class="mt-6">
          <div class="mb-2 text-sm font-bold text-gray-700">扩展信息</div>
          <pre class="overflow-auto rounded bg-gray-900 p-4 text-xs text-green-400">{{
            JSON.stringify(orderDetail.extra, null, 2)
          }}</pre>
        </div>
      </div>
      <t-empty v-else description="暂无详情数据" />
    </t-drawer>
  </div>
</template>
<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import { MessagePlugin, type PrimaryTableCol } from "tdesign-vue-next";
import {
  SearchIcon,
  CheckCircleIcon,
  CloseCircleIcon,
  InfoCircleIcon,
  DeleteIcon,
} from "tdesign-icons-vue-next";
import { orderApi } from "@/api/order";
import dayjs from "dayjs";
import type { TradeOrderRecord, OrderQueryParam } from "@/types/order";
import { PermissionConstant } from "@/constant/PermissionConstant";
import { usePermission } from "@/hooks/usePermission";

const { hasPermission } = usePermission();
const canViewOrderRecord = computed(() => hasPermission(PermissionConstant.TASK_RECORD_LIST));
const canTriggerC5Sync = computed(() => hasPermission(PermissionConstant.TASK_C5_LIST));
const canDeleteOrderRecord = computed(() => hasPermission(PermissionConstant.ORDER_RECORD_DELETE));

const loading = ref(false);
const c5SyncLoading = ref(false);
const dataList = ref<TradeOrderRecord[]>([]);
const selectedRowKeys = ref<(string | number)[]>([]);
const showAdvancedFilters = ref(true);
const activeTab = ref(0);

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

const columns: PrimaryTableCol[] = [
  { colKey: "row-select", type: "multiple", width: 56, fixed: "left" as any },
  { colKey: "id", title: "ID", width: 80, align: "left" },
  { colKey: "goods", title: "商品信息", width: 300, cell: "goods" },
  { colKey: "account", title: "平台/账号", width: 150, cell: "account" },
  { colKey: "orderId", title: "平台订单号", width: 160, cell: "orderId" },
  { colKey: "price", title: "价格", width: 120, cell: "price", sorter: true },
  { colKey: "status", title: "状态", width: 150, cell: "status", sorter: true },
  { colKey: "createTime", title: "时间", width: 180, cell: "time", sorter: true },
  { colKey: "operation", title: "操作", width: 160, fixed: "right", cell: "operation" },
];

const selectedRecords = computed(() => {
  const keySet = new Set(selectedRowKeys.value.map((key) => Number(key)));
  return dataList.value.filter((item) => keySet.has(Number(item.id)));
});

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
    if (res && res.records) {
      dataList.value = res.records;
      pagination.total = res.total;
      pagination.current = res.current;
      pagination.pageSize = res.size;
      selectedRowKeys.value = selectedRowKeys.value.filter((key) =>
        dataList.value.some((item) => Number(item.id) === Number(key))
      );
    }
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  queryParams.page = 1;
  selectedRowKeys.value = [];
  fetchData();
};

const handleTabChange = (val: any) => {
  activeTab.value = val;
  if (val === 0) {
    queryParams.status = undefined;
  } else if (val === 1) {
    queryParams.status = 1;
  } else if (val === 2) {
    queryParams.status = 2;
  } else if (val === 3) {
    queryParams.status = 3;
  }
  queryParams.page = 1;
  selectedRowKeys.value = [];
  fetchData();
};

const handleReset = () => {
  queryParams.platform = undefined;
  activeTab.value = 0;
  queryParams.status = undefined;
  queryParams.keyword = "";
  selectedRowKeys.value = [];
  handleSearch();
};

const onPageChange = (pageInfo: { current: number; pageSize: number }) => {
  queryParams.page = pageInfo.current;
  queryParams.pageSize = pageInfo.pageSize;
  selectedRowKeys.value = [];
  fetchData();
};

const onSortChange = (val: any) => {
  queryParams.sortField = val?.sortBy;
  queryParams.sortOrder = val?.descending ? "desc" : "asc";
  selectedRowKeys.value = [];
  fetchData();
};

const handleDelete = async (id: number) => {
  try {
    await orderApi.delete(id);
    MessagePlugin.success("删除成功");
    fetchData();
  } catch (error) {
    console.error(error);
  }
};

const handleSelectChange = (value: (string | number)[]) => {
  selectedRowKeys.value = value;
};

const clearSelection = () => {
  selectedRowKeys.value = [];
};

const toggleAdvancedFilters = () => {
  showAdvancedFilters.value = !showAdvancedFilters.value;
};

const handleColumnSetting = () => {
  MessagePlugin.info("列设置能力将在下一轮迭代接入");
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
    console.error(error);
  } finally {
    c5SyncLoading.value = false;
  }
};

const formatTime = (time: string | number) => {
  if (!time) return "-";
  return dayjs(time).format("YYYY-MM-DD HH:mm:ss");
};

const drawerVisible = ref(false);
const detailLoading = ref(false);
const orderDetail = ref<any>(null);

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
  try {
    orderDetail.value = await orderApi.getC5Detail(row.orderId);
  } catch (error) {
    console.error(error);
  } finally {
    detailLoading.value = false;
  }
};

const getStatusText = (status: number) => {
  const map: Record<number, string> = {
    0: "处理中",
    1: "成功",
    2: "失败",
    3: "已取消",
    10: "购买成功",
    11: "失败",
    200: "成功",
  };
  return map[status] || `未知(${status})`;
};

const getStatusTheme = (status: number) => {
  const map: Record<number, string> = {
    0: "primary",
    1: "success",
    2: "danger",
    3: "default",
    10: "success",
    11: "danger",
    200: "success",
  };
  return (map[status] || "default") as any;
};

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
.jsh-label {
  padding-right: 8px;
  color: #303133;
  font-size: 13px;
  line-height: 32px;
  white-space: nowrap;
}

.jsh-expand-link {
  color: #1890ff;
  line-height: 32px;
  user-select: none;
}

.jsh-expand-link:hover {
  color: #40a9ff;
}

.table-operator :deep(.t-button) {
  margin: 0 8px 8px 0;
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

:deep(.jsh-ledger-table.t-table) {
  border: 1px solid #e8e8e8 !important;
  border-radius: 0 !important;
  box-shadow: none !important;
}

:deep(.jsh-ledger-table::before),
:deep(.jsh-ledger-table::after) {
  display: none !important;
}

:deep(.jsh-ledger-table .t-table__content) {
  border-radius: 0 !important;
  background: #fff !important;
}

:deep(.jsh-ledger-table .t-table__header th) {
  padding: 11px 10px !important;
  border-bottom: 1px solid #e8e8e8 !important;
  background: #fafafa !important;
  color: #606266 !important;
  font-size: 13px !important;
  font-weight: 500 !important;
}

:deep(.jsh-ledger-table .t-table__body td) {
  padding-top: 15px !important;
  padding-bottom: 15px !important;
  padding-left: 10px !important;
  padding-right: 10px !important;
  border-bottom: 1px solid #f0f0f0 !important;
  font-size: 13px;
  color: #303133;
}

:deep(.jsh-ledger-table .t-table__row--hover td) {
  background: #f5f5f5 !important;
}

:deep(.jsh-ledger-table .t-table__empty) {
  min-height: 320px;
  background: #ffffff !important;
}
</style>


