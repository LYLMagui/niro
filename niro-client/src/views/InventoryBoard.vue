<template>
  <div class="bg-[#f5f5f5] px-1 pb-2 pt-1">
    <section class="overflow-hidden border border-[#d9d9d9] bg-white">
      <div class="px-4 pt-3">
        <div class="flex flex-wrap items-center gap-x-6 gap-y-3">
          <div class="flex items-center">
            <span class="jsh-label">商品信息</span>
            <t-input
              v-model="queryParams.keyword"
              placeholder="请输入商品关键词"
              clearable
              class="!h-8 w-[320px]"
              @enter="handleSearch"
            />
          </div>
          <div v-permission="PermissionConstant.TASK_INVENTORY_VIEW" class="flex items-center gap-2">
            <t-button theme="primary" class="!h-8 px-4" @click="handleSearch">
              <template #icon><search-icon /></template>
              查询
            </t-button>
            <t-button theme="default" variant="base" class="!h-8 px-4" @click="handleReset">
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
            <span class="jsh-label">购买日期</span>
            <t-date-range-picker
              v-model="queryParams.purchaseDateRange"
              placeholder="请选择日期范围"
              clearable
              class="!h-8 w-[320px]"
              @change="handleSearch"
            />
          </div>
        </div>
      </div>

      <div class="mt-[5px] border-t border-[#f2f2f2] px-4 pt-2">
        <div class="flex flex-wrap items-start justify-between gap-y-2">
          <div
            v-permission="PermissionConstant.TASK_INVENTORY_VIEW"
            class="table-operator flex flex-wrap items-center"
          >
            <t-button
              theme="primary"
              class="!h-8"
              :disabled="selectedRowKeys.length === 0"
              @click="handleCalculateCost"
            >
              <template #icon><calculator-icon /></template>
              计算成本
            </t-button>
            <t-button
              variant="outline"
              theme="default"
              class="!h-8"
              :disabled="selectedRowKeys.length === 0"
              @click="clearSelection"
            >
              清空勾选
            </t-button>
            <t-button variant="text" theme="default" class="!h-8" @click="handleColumnSetting">
              列设置
            </t-button>
          </div>

          <div class="mb-2 flex items-center gap-2 text-xs text-[#909399]">
            <span>提示：批量成本计算仅针对当前页已勾选库存项</span>
            <t-tag theme="primary" variant="light" class="rounded-[2px]">
              已选择 {{ selectedRowKeys.length }} 项
            </t-tag>
            <t-tag theme="warning" variant="light" class="rounded-[2px]">
              总库存 {{ totalQuantity }} 件 / ¥{{ totalAmount.toFixed(2) }}
            </t-tag>
          </div>
        </div>
      </div>

      <div class="px-4 pb-4">
        <t-table
          row-key="id"
          :data="pagedInventoryList"
          :columns="columns"
          :loading="loading"
          :pagination="pagination"
          :selected-row-keys="selectedRowKeys"
          select-on-row-click
          hover
          :header-affixed-top="{ offsetTop: 0, container: '.t-layout__content' }"
          class="jsh-ledger-table"
          @page-change="onPageChange"
          @select-change="handleSelectChange"
        >
          <template #empty>
            <t-empty description="暂无库存数据" />
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
                <span class="truncate text-xs text-gray-400">购买日期: {{ row.purchaseDate }}</span>
              </div>
            </div>
          </template>

          <template #price="{ row }">
            <span class="font-medium text-gray-900">¥{{ row.price.toFixed(2) }}</span>
          </template>

          <template #quantity="{ row }">
            <t-tag theme="primary" variant="light" size="small">{{ row.quantity }} 件</t-tag>
          </template>

          <template #totalAmount="{ row }">
            <span class="font-semibold text-red-600">¥{{ row.totalAmount.toFixed(2) }}</span>
          </template>

          <template #operation="{ row }">
            <t-button
              v-permission="PermissionConstant.TASK_INVENTORY_VIEW"
              variant="text"
              theme="primary"
              size="small"
              @click="openRemarkDialog(row)"
            >
              <template #icon><edit-icon /></template>
              编辑备注
            </t-button>
          </template>
        </t-table>
      </div>
    </section>

    <t-dialog v-model:visible="remarkDialogVisible" header="编辑备注" width="500px" @confirm="saveRemark">
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

        <div v-if="!isMultiRemark">
          <t-textarea
            v-model="remarkList[0]"
            placeholder="请输入备注信息..."
            :autosize="{ minRows: 3, maxRows: 6 }"
          />
        </div>

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

    <t-dialog v-model:visible="costDialogVisible" header="成本计算结果" :footer="false" width="400px">
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
            <div class="text-xl font-bold text-red-600">¥{{ calculatedCost.amount.toFixed(2) }}</div>
          </div>
          <div class="rounded-lg bg-green-50 p-4">
            <div class="mb-1 text-xs text-gray-500">平均单价</div>
            <div class="text-xl font-bold text-green-600">¥{{ calculatedCost.avgPrice.toFixed(2) }}</div>
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
import { ref, reactive, computed, watch } from "vue";
import { MessagePlugin, type PrimaryTableCol } from "tdesign-vue-next";
import { SearchIcon, EditIcon, CalculatorIcon } from "tdesign-icons-vue-next";
import { orderApi } from "@/api/order";
import { usePermission } from "@/hooks/usePermission";
import type { InventoryItem, InventoryQueryParam } from "@/types/order";
import { PermissionConstant } from "@/constant/PermissionConstant";

const loading = ref(false);
const inventoryList = ref<InventoryItem[]>([]);

const queryParams = reactive<InventoryQueryParam>({
  page: 1,
  pageSize: 20,
  keyword: "",
  purchaseDateRange: [],
});

const selectedRowKeys = ref<(string | number)[]>([]);
const showAdvancedFilters = ref(true);

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showJumper: true,
});

const { hasPermission } = usePermission();
const canViewInventory = computed(() => hasPermission(PermissionConstant.TASK_INVENTORY_VIEW));

const columns: PrimaryTableCol[] = [
  { colKey: "row-select", type: "multiple", width: 50, fixed: "left" },
  { colKey: "goods", title: "商品信息", width: 350, cell: "goods" },
  { colKey: "price", title: "单价", width: 120, cell: "price", align: "right" },
  { colKey: "quantity", title: "数量", width: 100, cell: "quantity", align: "center" },
  { colKey: "totalAmount", title: "总价", width: 120, cell: "totalAmount", align: "right" },
  {
    colKey: "operation",
    title: "操作",
    width: 120,
    fixed: "right",
    cell: "operation",
    align: "left",
  },
];

const remarkDialogVisible = ref(false);
const currentItem = ref<InventoryItem | null>(null);
const remarkList = ref<string[]>([]);

const isMultiRemark = computed(() => (currentItem.value?.quantity || 0) > 1);

const costDialogVisible = ref(false);
const calculatedCost = reactive({
  quantity: 0,
  amount: 0,
  avgPrice: 0,
  items: [] as { goodsName: string; quantity: number }[],
});

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

const fetchData = async () => {
  if (!canViewInventory.value) {
    inventoryList.value = [];
    pagination.total = 0;
    return;
  }

  loading.value = true;
  try {
    const params: InventoryQueryParam = {
      keyword: queryParams.keyword,
    };

    if (queryParams.purchaseDateRange && queryParams.purchaseDateRange.length === 2) {
      params.startDate = queryParams.purchaseDateRange[0];
      params.endDate = queryParams.purchaseDateRange[1];
    }

    const res = await orderApi.getInventory(params);
    if (Array.isArray(res)) {
      inventoryList.value = res.map((item, index) => ({
        ...item,
        id: item.id ?? index + 1,
      }));
      pagination.total = res.length;

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
  pagination.current = 1;
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
};

const handleSelectChange = (selectedRowKey: (string | number)[]) => {
  selectedRowKeys.value = selectedRowKey;
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

const openRemarkDialog = (row: InventoryItem) => {
  currentItem.value = row;
  const quantity = row.quantity || 1;
  const initialList = new Array(quantity).fill("");

  try {
    if (row.remark) {
      const parsed = JSON.parse(row.remark);
      if (Array.isArray(parsed)) {
        parsed.forEach((val, idx) => {
          if (idx < quantity) {
            initialList[idx] = val;
          }
        });
      } else {
        initialList[0] = String(parsed);
      }
    }
  } catch {
    if (row.remark) {
      initialList[0] = row.remark;
    }
  }

  remarkList.value = initialList;
  remarkDialogVisible.value = true;
};

const saveRemark = async () => {
  if (!currentItem.value) return;

  let remarkToSave = "";
  if (isMultiRemark.value) {
    remarkToSave = JSON.stringify(remarkList.value);
  } else {
    remarkToSave = remarkList.value[0] || "";
  }

  MessagePlugin.success("备注保存成功");
  remarkDialogVisible.value = false;

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

  const selectedItems = inventoryList.value.filter((item) => selectedRowKeys.value.includes(item.id!));

  calculatedCost.quantity = selectedItems.reduce((sum, item) => sum + item.quantity, 0);
  calculatedCost.amount = selectedItems.reduce((sum, item) => sum + item.totalAmount, 0);
  calculatedCost.items = selectedItems.map((item) => ({
    goodsName: item.goodsName,
    quantity: item.quantity,
  }));
  calculatedCost.avgPrice =
    calculatedCost.quantity > 0 ? calculatedCost.amount / calculatedCost.quantity : 0;

  costDialogVisible.value = true;
};

watch(
  canViewInventory,
  (allowed) => {
    if (allowed) {
      fetchData();
      return;
    }
    inventoryList.value = [];
    pagination.total = 0;
    selectedRowKeys.value = [];
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


