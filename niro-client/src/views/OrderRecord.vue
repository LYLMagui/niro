<template>
  <div class="p-6">
    <!-- 订单记录主卡片 -->
    <t-card :bordered="false" class="embedded-card shadow-sm">
      <template #title>
        <div class="flex items-center">
          <history-icon class="mr-2 text-blue-600" />
          <span class="text-lg font-bold text-gray-800">订单记录</span>
        </div>
      </template>

      <!-- 顶部分类 Tabs -->
      <t-tabs v-model="activeTab" class="px-6" @change="handleTabChange">
        <t-tab-panel :value="0" label="全部" />
        <t-tab-panel :value="1" label="成功" />
        <t-tab-panel :value="2" label="失败" />
        <t-tab-panel :value="3" label="取消" />
      </t-tabs>

      <!-- 筛选栏 (对齐 TaskConfig 布局) -->
      <div class="border-b border-gray-100 p-6">
        <t-row :gutter="16" align="center">
          <t-col :span="3">
            <t-input
              v-model="queryParams.keyword"
              placeholder="搜索商品名 / 订单号"
              clearable
              @enter="handleSearch"
            />
          </t-col>
          <t-col :span="2">
            <t-select
              v-model="queryParams.platform"
              placeholder="全部平台"
              clearable
              @change="handleSearch"
            >
              <t-option value="BUFF" label="BUFF" />
              <t-option value="C5" label="C5" />
            </t-select>
          </t-col>
          <t-col :span="7">
            <div class="flex gap-2">
              <t-button theme="primary" @click="handleSearch">
                <template #icon><search-icon /></template>
                查询
              </t-button>
              <t-button theme="default" variant="base" @click="handleReset">重置</t-button>
            </div>
          </t-col>
        </t-row>
      </div>

      <!-- 订单表格 -->
      <t-table
        row-key="id"
        :data="dataList"
        :columns="columns"
        :loading="loading"
        :pagination="pagination"
        hover
        :header-affixed-top="{ offsetTop: 0, container: '.t-layout__content' }"
        class="embedded-table w-full"
        @page-change="onPageChange"
        @sort-change="onSortChange"
      >
        <!-- 空状态 -->
        <template #empty>
          <t-empty description="暂无订单记录" />
        </template>

        <!-- 订单号列 -->
        <template #orderId="{ row }">
          <t-link
            v-if="row.orderId"
            theme="primary"
            class="font-mono text-xs"
            @click="viewC5Detail(row)"
          >
            {{ row.orderId }}
          </t-link>
          <span v-else class="text-xs text-gray-400">未生成</span>
        </template>

        <!-- 商品列 -->
        <template #goods="{ row }">
          <div class="flex items-center gap-3">
            <t-image
              :src="row.goodsImg"
              class="h-12 w-12 shrink-0 rounded border border-gray-100 bg-gray-50"
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

        <!-- 平台/账号列 -->
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

        <!-- 价格列 -->
        <template #price="{ row }">
          <span class="font-medium text-gray-900">¥{{ row.price.toFixed(2) }}</span>
        </template>

        <!-- 状态列 -->
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

        <!-- 时间列 -->
        <template #time="{ row }">
          <span class="text-gray-500">{{ formatTime(row.createTime) }}</span>
        </template>

        <!-- 操作列 -->
        <template #operation="{ row }">
          <div class="flex gap-2">
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
              <t-button variant="text" theme="danger" size="small">
                <template #icon><delete-icon /></template>
                删除
              </t-button>
            </t-popconfirm>
          </div>
        </template>
      </t-table>
    </t-card>

    <!-- C5 订单详情侧边栏 -->
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
import { ref, reactive, onMounted } from "vue";
import { MessagePlugin, type PrimaryTableCol } from "tdesign-vue-next";
import {
  SearchIcon,
  CheckCircleIcon,
  CloseCircleIcon,
  HistoryIcon,
  InfoCircleIcon,
  DeleteIcon,
} from "tdesign-icons-vue-next";
import { orderApi } from "@/api/order";
import dayjs from "dayjs";
import type { TradeOrderRecord } from "@/types/order";
import type { OrderQueryParam } from "@/types/order";

// --- 状态定义 ---
const loading = ref(false);
const dataList = ref<TradeOrderRecord[]>([]);
const activeTab = ref(0); // 0: 全部, 1: 成功, 2: 失败

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
  { colKey: "id", title: "ID", width: 80, align: "left" },
  { colKey: "goods", title: "商品信息", width: 300, cell: "goods" },
  { colKey: "account", title: "平台/账号", width: 150, cell: "account" },
  { colKey: "orderId", title: "平台订单号", width: 160, cell: "orderId" },
  {
    colKey: "price",
    title: "价格",
    width: 120,
    cell: "price",
    sorter: true,
  },
  {
    colKey: "status",
    title: "状态",
    width: 150,
    cell: "status",
    sorter: true,
  },
  {
    colKey: "createTime",
    title: "时间",
    width: 180,
    cell: "time",
    sorter: true,
  },
  { colKey: "operation", title: "操作", width: 160, fixed: "right", cell: "operation" },
];

// --- 方法 ---
const fetchData = async () => {
  loading.value = true;
  try {
    const res = await orderApi.getPage(queryParams);
    if (res && res.records) {
      dataList.value = res.records;
      pagination.total = res.total;
      pagination.current = res.current;
      pagination.pageSize = res.size;
    }
  } catch (error) {
    console.error(error);
    // MessagePlugin.error("网络异常"); // 拦截器已处理
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  queryParams.page = 1;
  fetchData();
};

const handleTabChange = (val: any) => {
  activeTab.value = val;
  // Tab 映射: 0=全部, 1=成功, 2=失败, 3=取消
  // status 映射: 1=成功, 2/11=失败, 3=取消
  if (val === 0) {
    queryParams.status = undefined; // 全部
  } else if (val === 1) {
    queryParams.status = 1; // 成功
  } else if (val === 2) {
    queryParams.status = 2; // 失败
  } else if (val === 3) {
    queryParams.status = 3; // 取消
  }
  queryParams.page = 1;
  fetchData();
};

const handleReset = () => {
  queryParams.platform = undefined;
  // 重置时也要同步 Tab
  activeTab.value = 0;
  queryParams.status = undefined;
  queryParams.keyword = "";
  handleSearch();
};

const onPageChange = (pageInfo: { current: number; pageSize: number }) => {
  queryParams.page = pageInfo.current;
  queryParams.pageSize = pageInfo.pageSize;
  fetchData();
};

const onSortChange = (val: any) => {
  queryParams.sortField = val?.sortBy;
  queryParams.sortOrder = val?.descending ? "desc" : "asc";
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

const formatTime = (time: string | number) => {
  if (!time) return "-";
  return dayjs(time).format("YYYY-MM-DD HH:mm:ss");
};

const drawerVisible = ref(false);
const detailLoading = ref(false);
const orderDetail = ref<any>(null);

const viewC5Detail = async (row: TradeOrderRecord) => {
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
    10: "购买成功", // C5 v2 状态码
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
    10: "success", // C5 v2 状态码
    11: "danger",
    200: "success",
  };
  return (map[status] || "default") as any;
};

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
/* 可以在这里添加自定义样式 */
</style>
