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
              <t-button theme="default" variant="base" @click="handleReset">
                重置
              </t-button>
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
        :max-height="600"
        class="embedded-table w-full"
        @page-change="onPageChange"
      >
        <!-- 空状态 -->
        <template #empty>
          <t-empty description="暂无订单记录" />
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
                磨损: {{ row.paintwear > 0 ? row.paintwear : '无' }}
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
            <span class="text-sm text-gray-600">{{ row.accountName || '-' }}</span>
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
          <div v-else-if="row.status === 2">
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

      </t-table>
    </t-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from "vue";
import { MessagePlugin, type PrimaryTableCol } from "tdesign-vue-next";
import { SearchIcon, CheckCircleIcon, CloseCircleIcon, HistoryIcon } from "tdesign-icons-vue-next";
import { orderApi } from "@/api/order";
import type { TradeOrderRecord, OrderQueryParam } from "@/types/order";
import dayjs from "dayjs";

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
  { colKey: "price", title: "价格", width: 120, cell: "price" },
  { colKey: "status", title: "状态", width: 150, cell: "status" },
  { colKey: "createTime", title: "时间", width: 180, cell: "time" },
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
  // 0 表示全部 (对应后端 status=null/undefined), 1=成功, 2=失败
  queryParams.status = val === 0 ? undefined : val;
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

const formatTime = (time: string) => {
  return dayjs(time).format("YYYY-MM-DD HH:mm:ss");
};


</script>

<style scoped>
/* 可以在这里添加自定义样式 */
</style>
