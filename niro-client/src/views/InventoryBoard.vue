<template>
  <div class="p-6">
    <!-- 库存统计主卡片 -->
    <t-card :bordered="false" class="embedded-card shadow-sm">
      <template #title>
        <div class="flex items-center">
          <dashboard-icon class="mr-2 text-blue-600" />
          <span class="text-lg font-bold text-gray-800">库存统计</span>
        </div>
      </template>
      <template #actions>
        <div class="flex gap-6">
          <div class="text-right">
            <div class="text-xs text-gray-500">总库存数量</div>
            <div class="text-lg font-bold text-blue-600">
              {{ totalQuantity }}
              <span class="text-sm font-normal">件</span>
            </div>
          </div>
          <div class="text-right">
            <div class="text-xs text-gray-500">总库存金额</div>
            <div class="text-lg font-bold text-red-600">¥{{ totalAmount.toFixed(2) }}</div>
          </div>
        </div>
      </template>

      <!-- 筛选栏 -->
      <div class="border-b border-gray-100 p-6">
        <t-row :gutter="16" align="center">
          <t-col :span="3">
            <t-input
              v-model="queryParams.keyword"
              placeholder="搜索商品名称"
              clearable
              @enter="handleSearch"
            />
          </t-col>
          <t-col :span="4">
            <t-date-range-picker
              v-model="queryParams.purchaseDateRange"
              placeholder="选择购买日期范围"
              clearable
              style="width: 100%"
              @change="handleSearch"
            />
          </t-col>
          <t-col :span="5">
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

      <!-- 操作栏 -->
      <div class="px-6 py-4">
        <t-button
          theme="primary"
          :disabled="selectedRowKeys.length === 0"
          @click="handleCalculateCost"
        >
          <template #icon><calculator-icon /></template>
          计算成本
        </t-button>
        <span v-if="selectedRowKeys.length > 0" class="ml-4 text-sm text-gray-500">
          已选择 {{ selectedRowKeys.length }} 项
        </span>
      </div>

      <!-- 库存列表 -->
      <t-table
        row-key="id"
        :data="pagedInventoryList"
        :columns="columns"
        :loading="loading"
        :pagination="pagination"
        select-on-row-click
        :selected-row-keys="selectedRowKeys"
        hover
        :header-affixed-top="{ offsetTop: 0, container: '.t-layout__content' }"
        class="embedded-table w-full"
        @page-change="onPageChange"
        @select-change="handleSelectChange"
      >
        <!-- 空状态 -->
        <template #empty>
          <t-empty description="暂无库存数据" />
        </template>

        <!-- 商品列 -->
        <template #goods="{ row }">
          <div class="flex items-center gap-3">
            <t-image
              :src="row.goodsImg"
              class="h-14 w-14 shrink-0 rounded border border-gray-100 bg-gray-50"
              fit="contain"
            />
            <div class="flex flex-col overflow-hidden">
              <span class="truncate font-medium text-gray-900" :title="row.goodsName">
                {{ row.goodsName }}
              </span>
              <span class="truncate text-xs text-gray-400">购买日期: {{ row.purchaseDate }}</span>
            </div>
          </div>
        </template>

        <!-- 单价列 -->
        <template #price="{ row }">
          <span class="font-medium text-gray-900">¥{{ row.price.toFixed(2) }}</span>
        </template>

        <!-- 数量列 -->
        <template #quantity="{ row }">
          <t-tag theme="primary" variant="light" size="small">{{ row.quantity }} 件</t-tag>
        </template>

        <!-- 总价列 -->
        <template #totalAmount="{ row }">
          <span class="font-bold text-red-600">¥{{ row.totalAmount.toFixed(2) }}</span>
        </template>

        <!-- 操作列 -->
        <template #operation="{ row }">
          <t-button variant="text" theme="primary" size="small" @click="openRemarkDialog(row)">
            <template #icon><edit-icon /></template>
            编辑备注
          </t-button>
        </template>
      </t-table>
    </t-card>

    <!-- 备注编辑弹窗 -->
    <t-dialog
      v-model:visible="remarkDialogVisible"
      header="编辑备注"
      width="500px"
      @confirm="saveRemark"
    >
      <div class="space-y-4">
        <div class="flex items-center gap-3 rounded-lg bg-gray-50 p-4">
          <t-image
            :src="currentItem?.goodsImg"
            class="h-16 w-16 rounded border border-gray-100 bg-white"
            fit="contain"
          />
          <div>
            <p class="font-bold text-gray-900">{{ currentItem?.goodsName }}</p>
            <p class="text-sm text-gray-500">
              ¥{{ currentItem?.price.toFixed(2) }} × {{ currentItem?.quantity }}件 = ¥{{
                currentItem?.totalAmount.toFixed(2)
              }}
            </p>
            <p class="text-xs text-gray-400">购买日期: {{ currentItem?.purchaseDate }}</p>
          </div>
        </div>

        <!-- 单条备注编辑 -->
        <div v-if="!isMultiRemark">
          <t-textarea
            v-model="remarkList[0]"
            placeholder="请输入备注信息..."
            :autosize="{ minRows: 3, maxRows: 6 }"
          />
        </div>

        <!-- 多条备注编辑 -->
        <div v-else class="max-h-60 space-y-3 overflow-y-auto pr-2">
          <div v-for="(_, index) in remarkList" :key="index" class="flex items-start gap-2">
            <span class="mt-2 w-8 text-right text-xs text-gray-400">#{{ index + 1 }}</span>
            <t-textarea
              v-model="remarkList[index]"
              placeholder="请输入单件备注..."
              :autosize="{ minRows: 1, maxRows: 3 }"
              class="flex-1"
            />
          </div>
        </div>
      </div>
    </t-dialog>

    <!-- 成本计算弹窗 -->
    <t-dialog
      v-model:visible="costDialogVisible"
      header="成本计算结果"
      :footer="false"
      width="400px"
    >
      <div class="flex flex-col gap-6 p-4 text-center">
        <div class="grid grid-cols-2 gap-4">
          <div class="rounded-lg bg-blue-50 p-4">
            <div class="mb-1 text-xs text-gray-500">选中商品数量</div>
            <div class="text-xl font-bold text-blue-600">
              {{ calculatedCost.quantity }}
              <span class="text-sm font-normal">件</span>
            </div>
          </div>
          <div class="rounded-lg bg-red-50 p-4">
            <div class="mb-1 text-xs text-gray-500">选中商品总额</div>
            <div class="text-xl font-bold text-red-600">
              ¥{{ calculatedCost.amount.toFixed(2) }}
            </div>
          </div>
        </div>
        <div class="rounded bg-gray-50 p-3 text-left text-sm text-gray-500">
          <p class="mb-1">包含商品：</p>
          <div class="max-h-40 space-y-1 overflow-y-auto">
            <div
              v-for="(item, index) in calculatedCost.items"
              :key="index"
              class="flex justify-between text-xs"
            >
              <span class="w-2/3 truncate" :title="item.goodsName">{{ item.goodsName }}</span>
              <span>x{{ item.quantity }}</span>
            </div>
          </div>
        </div>
      </div>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from "vue";
import { MessagePlugin, type PrimaryTableCol } from "tdesign-vue-next";
import { SearchIcon, DashboardIcon, EditIcon, CalculatorIcon } from "tdesign-icons-vue-next";
import { orderApi } from "@/api/order";
import type { InventoryItem, InventoryQueryParam } from "@/types/order";

// --- 状态定义 ---
const loading = ref(false);
const inventoryList = ref<InventoryItem[]>([]);

const queryParams = reactive<InventoryQueryParam>({
  page: 1,
  pageSize: 20,
  keyword: "",
  purchaseDateRange: [],
});

// 选中的行
const selectedRowKeys = ref<(string | number)[]>([]);

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showJumper: true,
});

const columns: PrimaryTableCol[] = [
  { colKey: "row-select", type: "multiple", width: 50, fixed: "left" },
  { colKey: "goods", title: "商品信息", width: 350, cell: "goods" },
  { colKey: "price", title: "单价", width: 120, cell: "price", align: "center" },
  { colKey: "quantity", title: "数量", width: 100, cell: "quantity", align: "center" },
  { colKey: "totalAmount", title: "总价", width: 120, cell: "totalAmount", align: "center" },
  {
    colKey: "operation",
    title: "操作",
    width: 120,
    fixed: "right",
    cell: "operation",
    align: "center",
  },
];

// 备注编辑相关
const remarkDialogVisible = ref(false);
const currentItem = ref<InventoryItem | null>(null);
const remarkList = ref<string[]>([]);

const isMultiRemark = computed(() => {
  return (currentItem.value?.quantity || 0) > 1;
});

// 成本计算相关
const costDialogVisible = ref(false);
const calculatedCost = reactive({
  quantity: 0,
  amount: 0,
  items: [] as { goodsName: string; quantity: number }[],
});

// --- 计算属性 ---
const pagedInventoryList = computed(() => {
  const start = (pagination.current - 1) * pagination.pageSize;
  const end = start + pagination.pageSize;
  return inventoryList.value.slice(start, end);
});

const totalQuantity = computed(() => {
  return inventoryList.value.reduce((sum, item) => sum + item.quantity, 0);
});

const totalAmount = computed(() => {
  return inventoryList.value.reduce((sum, item) => sum + item.totalAmount, 0);
});

// --- 方法 ---
const fetchData = async () => {
  loading.value = true;
  try {
    // 构建查询参数，将日期范围转换为开始和结束日期
    const params: InventoryQueryParam = {
      keyword: queryParams.keyword,
    };
    
    // 处理日期范围
    if (queryParams.purchaseDateRange && queryParams.purchaseDateRange.length === 2) {
      params.startDate = queryParams.purchaseDateRange[0];
      params.endDate = queryParams.purchaseDateRange[1];
    }
    
    const res = await orderApi.getInventory(params);
    if (Array.isArray(res)) {
      // 为没有ID的数据生成临时ID，确保表格选择功能正常
      inventoryList.value = res.map((item, index) => ({
        ...item,
        id: item.id ?? index + 1,
      }));
      pagination.total = res.length;
      // 如果当前页超过总页数，重置为第一页
      const maxPage = Math.ceil(pagination.total / pagination.pageSize) || 1;
      if (pagination.current > maxPage) {
        pagination.current = 1;
      }
    } else {
      inventoryList.value = [];
      pagination.total = 0;
    }
  } catch (error) {
    console.error("获取库存数据失败:", error);
    MessagePlugin.error("获取库存数据失败");
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  pagination.current = 1; // Reset to first page
  fetchData();
};

const handleReset = () => {
  queryParams.keyword = "";
  queryParams.purchaseDateRange = [];
  pagination.current = 1;
  selectedRowKeys.value = [];
  handleSearch();
};

const onPageChange = (pageInfo: { current: number; pageSize: number }) => {
  pagination.current = pageInfo.current;
  pagination.pageSize = pageInfo.pageSize;
  // No need to fetch data again as we have all data locally
};

// 处理行选择变化
const handleSelectChange = (selectedRowKey: (string | number)[]) => {
  selectedRowKeys.value = selectedRowKey;
};

const openRemarkDialog = (row: InventoryItem) => {
  currentItem.value = row;
  const quantity = row.quantity || 1;
  // Initialize with empty strings
  const initialList = new Array(quantity).fill("");

  try {
    if (row.remark) {
      // 尝试解析JSON
      const parsed = JSON.parse(row.remark);
      if (Array.isArray(parsed)) {
        parsed.forEach((val, idx) => {
          if (idx < quantity) {
            initialList[idx] = val;
          }
        });
      } else {
        // 如果不是数组，当做普通字符串放在第一项
        initialList[0] = String(parsed);
      }
    }
  } catch {
    // 解析失败，当做普通字符串
    if (row.remark) {
      initialList[0] = row.remark;
    }
  }

  remarkList.value = initialList;
  remarkDialogVisible.value = true;
};

const saveRemark = async () => {
  if (!currentItem.value) return;

  // 过滤空值并保存
  // 如果有多条，存 JSON 字符串
  let remarkToSave = "";
  if (isMultiRemark.value) {
    // 即使是空的也要保留占位，保证索引对应
    remarkToSave = JSON.stringify(remarkList.value);
  } else {
    remarkToSave = remarkList.value[0] || "";
  }

  // 备注保存到本地数据（实际项目中应该调用API保存）
  MessagePlugin.success("备注保存成功");
  remarkDialogVisible.value = false;

  // 更新本地数据
  const item = inventoryList.value.find((i) => i.id === currentItem.value?.id);
  if (item) {
    item.remark = remarkToSave;
  }
};

const handleCalculateCost = () => {
  if (selectedRowKeys.value.length === 0) {
    MessagePlugin.warning("请先选择需要计算的商品");
    return;
  }

  const selectedItems = inventoryList.value.filter((item) =>
    selectedRowKeys.value.includes(item.id!)
  );

  calculatedCost.quantity = selectedItems.reduce((sum, item) => sum + item.quantity, 0);
  calculatedCost.amount = selectedItems.reduce((sum, item) => sum + item.totalAmount, 0);
  calculatedCost.items = selectedItems.map((item) => ({
    goodsName: item.goodsName,
    quantity: item.quantity,
  }));

  costDialogVisible.value = true;
};

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
/* 使用 embedded-table 样式与订单记录保持一致 */
:deep(.t-table__body),
:deep(.t-table__body tr),
:deep(.t-table__body td) {
  background-color: #ffffff !important;
}
</style>
