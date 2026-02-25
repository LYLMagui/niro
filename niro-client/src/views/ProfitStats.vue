<script setup lang="ts">
import { ref, reactive, computed, onMounted } from "vue";
import { MessagePlugin } from "tdesign-vue-next";
import {
  AddIcon,
  CloudDownloadIcon,
  CloudUploadIcon,
  DeleteIcon,
  EditIcon,
  SearchIcon,
} from "tdesign-icons-vue-next";
import type { PrimaryTableCol, TableRowData } from "tdesign-vue-next";
import type { ProfitRecord, ProfitSummary, ProfitRecordForm } from "@/types/profit";
import { calculateBuyCost } from "@/types/profit";
import * as profitApi from "@/api/profit";

// 状态
const loading = ref(false);
const dataList = ref<ProfitRecord[]>([]);
const searchKeyword = ref("");
const dialogVisible = ref(false);
const editingRecord = ref<ProfitRecord | null>(null);

// 表单数据
const formData = reactive<ProfitRecordForm>({
  time: "",
  goodsName: "",
  buyPrice: 0,
  quantity: 1,
  sellTotal: 0,
  cost: 0,
  remark: "",
});

// 表格列定义
const columns: PrimaryTableCol<TableRowData>[] = [
  { colKey: "serial", title: "序号", width: 70, align: "center" },
  { colKey: "time", title: "时间", width: 160, align: "center" },
  { colKey: "goodsName", title: "商品名称", width: 200, ellipsis: true },
  { colKey: "buyPrice", title: "买入单价", width: 100, align: "right" },
  { colKey: "quantity", title: "数量", width: 80, align: "center" },
  { colKey: "buyCost", title: "实际购入价", width: 110, align: "right" },
  { colKey: "sellTotal", title: "卖出总价", width: 100, align: "right" },
  { colKey: "cost", title: "成本/手续费", width: 110, align: "right" },
  { colKey: "profit", title: "预估利润", width: 110, align: "right" },
  { colKey: "remark", title: "备注", width: 120, ellipsis: true },
  { colKey: "operation", title: "操作", width: 100, fixed: "right", align: "center" },
];

// 过滤后的数据
const filteredData = computed(() => {
  if (!searchKeyword.value) return dataList.value;
  return dataList.value.filter((r) =>
    r.goodsName.toLowerCase().includes(searchKeyword.value.toLowerCase())
  );
});

// 统计汇总
const summary = computed<ProfitSummary>(() => {
  const totalBuyCost = filteredData.value.reduce(
    (sum, r) => sum + calculateBuyCost(r.buyPrice, r.quantity),
    0
  );
  const totalProfit = filteredData.value.reduce((sum, r) => sum + r.profit, 0);
  const profitRate = totalBuyCost > 0 ? (totalProfit / totalBuyCost) * 100 : 0;

  return {
    totalProfit: Math.round(totalProfit * 100) / 100,
    totalBuyCost: Math.round(totalBuyCost * 100) / 100,
    profitRate: Math.round(profitRate * 100) / 100,
    recordCount: filteredData.value.length,
  };
});

// 格式化金额
const formatMoney = (value: number): string => {
  return `¥${value.toFixed(2)}`;
};

// 格式化利润（带正负号）
const formatProfit = (value: number): string => {
  return value >= 0 ? `+${value.toFixed(2)}` : value.toFixed(2);
};

// 格式化时间
const formatTime = (time: string): string => {
  if (!time) return "-";
  return time.replace("T", " ").slice(0, 16);
};

// 获取数据
const fetchData = () => {
  dataList.value = profitApi.getRecords();
};

// 重置表单
const resetForm = () => {
  formData.time = new Date().toISOString().slice(0, 16);
  formData.goodsName = "";
  formData.buyPrice = 0;
  formData.quantity = 1;
  formData.sellTotal = 0;
  formData.cost = 0;
  formData.remark = "";
};

// 新增记录
const handleAdd = () => {
  editingRecord.value = null;
  resetForm();
  dialogVisible.value = true;
};

// 编辑记录
const handleEdit = (row: ProfitRecord) => {
  editingRecord.value = row;
  formData.time = row.time.slice(0, 16);
  formData.goodsName = row.goodsName;
  formData.buyPrice = row.buyPrice;
  formData.quantity = row.quantity;
  formData.sellTotal = row.sellTotal;
  formData.cost = row.cost;
  formData.remark = row.remark;
  dialogVisible.value = true;
};

// 提交表单
const handleSubmit = () => {
  if (!formData.goodsName.trim()) {
    MessagePlugin.warning("请输入商品名称");
    return;
  }
  if (formData.buyPrice <= 0) {
    MessagePlugin.warning("请输入有效的买入单价");
    return;
  }
  if (formData.quantity <= 0) {
    MessagePlugin.warning("请输入有效的数量");
    return;
  }

  const submitData: ProfitRecordForm = {
    time: new Date(formData.time).toISOString(),
    goodsName: formData.goodsName.trim(),
    buyPrice: Number(formData.buyPrice),
    quantity: Number(formData.quantity),
    sellTotal: Number(formData.sellTotal),
    cost: Number(formData.cost),
    remark: formData.remark.trim(),
  };

  if (editingRecord.value) {
    profitApi.updateRecord(editingRecord.value.id, submitData);
    MessagePlugin.success("更新成功");
  } else {
    profitApi.addRecord(submitData);
    MessagePlugin.success("添加成功");
  }

  dialogVisible.value = false;
  fetchData();
};

// 删除记录
const handleDelete = (id: string) => {
  profitApi.deleteRecord(id);
  MessagePlugin.success("删除成功");
  fetchData();
};

// 导出数据
const handleExport = () => {
  const data = profitApi.exportData();
  const blob = new Blob([data], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `profit-records-${new Date().toISOString().slice(0, 10)}.json`;
  a.click();
  URL.revokeObjectURL(url);
  MessagePlugin.success("导出成功");
};

// 导入数据
const handleImport = () => {
  const input = document.createElement("input");
  input.type = "file";
  input.accept = ".json";
  input.onchange = (e) => {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (ev) => {
      const jsonString = ev.target?.result as string;
      if (profitApi.importData(jsonString)) {
        MessagePlugin.success("导入成功");
        fetchData();
      } else {
        MessagePlugin.error("导入失败，请检查文件格式");
      }
    };
    reader.readAsText(file);
  };
  input.click();
};

// 计算实际购入价
const getBuyCost = (row: ProfitRecord): number => {
  return calculateBuyCost(row.buyPrice, row.quantity);
};

// 生命周期
onMounted(() => {
  fetchData();
});
</script>

<template>
  <div class="profit-stats-container">
    <t-card :bordered="false" class="embedded-card shadow-sm">
      <!-- 标题 -->
      <template #title>
        <div class="flex items-center">
          <t-icon name="chart-bar" class="mr-2 text-blue-600" />
          <span class="text-lg font-bold text-gray-800">利润统计</span>
        </div>
      </template>

      <!-- 工具栏 -->
      <div class="toolbar">
        <div class="toolbar-left">
          <t-button theme="primary" @click="handleAdd">
            <template #icon><AddIcon /></template>
            新增记录
          </t-button>
          <t-button theme="default" variant="outline" @click="handleExport">
            <template #icon><CloudDownloadIcon /></template>
            导出
          </t-button>
          <t-button theme="default" variant="outline" @click="handleImport">
            <template #icon><CloudUploadIcon /></template>
            导入
          </t-button>
        </div>
        <div class="toolbar-right">
          <t-input
            v-model="searchKeyword"
            placeholder="搜索商品名称..."
            clearable
            class="search-input"
          >
            <template #prefix-icon><SearchIcon /></template>
          </t-input>
        </div>
      </div>

      <!-- 数据表格 -->
      <t-table
        :data="filteredData"
        :columns="columns"
        :loading="loading"
        row-key="id"
        hover
        class="embedded-table w-full"
        :header-affixed-top="{ offsetTop: 0, container: '.t-layout__content' }"
      >
        <!-- 序号列 -->
        <template #serial="{ rowIndex }">
          {{ rowIndex + 1 }}
        </template>

        <!-- 时间列 -->
        <template #time="{ row }">
          {{ formatTime(row.time) }}
        </template>

        <!-- 买入单价列 -->
        <template #buyPrice="{ row }">
          {{ formatMoney(row.buyPrice) }}
        </template>

        <!-- 实际购入价列 -->
        <template #buyCost="{ row }">
          {{ formatMoney(getBuyCost(row)) }}
        </template>

        <!-- 卖出总价列 -->
        <template #sellTotal="{ row }">
          {{ row.sellTotal > 0 ? formatMoney(row.sellTotal) : "-" }}
        </template>

        <!-- 成本/手续费列 -->
        <template #cost="{ row }">
          {{ formatMoney(row.cost) }}
        </template>

        <!-- 预估利润列 -->
        <template #profit="{ row }">
          <span :class="row.profit >= 0 ? 'font-bold text-green-600' : 'font-bold text-red-600'">
            {{ formatProfit(row.profit) }}
          </span>
        </template>

        <!-- 备注列 -->
        <template #remark="{ row }">
          <span v-if="row.remark" class="text-gray-500">{{ row.remark }}</span>
          <span v-else class="text-gray-300">-</span>
        </template>

        <!-- 操作列 -->
        <template #operation="{ row }">
          <div class="operation-btns">
            <t-link theme="primary" hover="underline" @click="handleEdit(row)">
              <template #icon><EditIcon /></template>
              编辑
            </t-link>
            <t-popconfirm content="确定要删除该记录吗？" @confirm="handleDelete(row.id)">
              <t-link theme="danger" hover="underline">
                <template #icon><DeleteIcon /></template>
                删除
              </t-link>
            </t-popconfirm>
          </div>
        </template>
      </t-table>

      <!-- 空状态 -->
      <div v-if="filteredData.length === 0 && !loading" class="empty-state">
        <t-empty description="暂无记录，点击" @click="handleAdd">
          <template #action>
            <t-button theme="primary" variant="dashed">新增记录</t-button>
          </template>
        </t-empty>
      </div>

      <!-- 统计汇总区域 -->
      <div class="summary-section">
        <div class="summary-title">
          <t-icon name="chart-bar" class="mr-1" />
          统计汇总
        </div>
        <div class="summary-cards">
          <div class="summary-card">
            <div class="summary-label">记录数</div>
            <div class="summary-value">{{ summary.recordCount }} 条</div>
          </div>
          <div class="summary-card">
            <div class="summary-label">Steam 购入总价</div>
            <div class="summary-value primary">{{ formatMoney(summary.totalBuyCost) }}</div>
          </div>
          <div class="summary-card">
            <div class="summary-label">总利润</div>
            <div
              :class="[
                'summary-value',
                summary.totalProfit >= 0 ? 'profit-positive' : 'profit-negative',
              ]"
            >
              {{ formatProfit(summary.totalProfit) }}
            </div>
          </div>
          <div class="summary-card">
            <div class="summary-label">利润率</div>
            <div
              :class="[
                'summary-value',
                summary.profitRate >= 0 ? 'profit-positive' : 'profit-negative',
              ]"
            >
              {{ formatProfit(summary.profitRate) }}%
            </div>
          </div>
        </div>
      </div>
    </t-card>

    <!-- 编辑对话框 -->
    <t-dialog
      v-model:visible="dialogVisible"
      :header="editingRecord ? '编辑记录' : '新增记录'"
      :footer="true"
      width="500px"
      @confirm="handleSubmit"
    >
      <t-form :data="formData" label-width="100px" class="record-form">
        <t-form-item label="交易时间" name="time">
          <input v-model="formData.time" type="datetime-local" class="t-input" />
        </t-form-item>
        <t-form-item label="商品名称" name="goodsName" required>
          <t-input v-model="formData.goodsName" placeholder="请输入商品名称" />
        </t-form-item>
        <t-form-item label="买入单价" name="buyPrice" required>
          <t-input-number v-model="formData.buyPrice" :min="0" :decimal-limit="2" :step="0.01" />
        </t-form-item>
        <t-form-item label="数量" name="quantity" required>
          <t-input-number v-model="formData.quantity" :min="1" :step="1" />
        </t-form-item>
        <t-form-item label="卖出总价" name="sellTotal">
          <t-input-number v-model="formData.sellTotal" :min="0" :decimal-limit="2" :step="0.01" />
        </t-form-item>
        <t-form-item label="成本/手续费" name="cost">
          <t-input-number v-model="formData.cost" :min="0" :decimal-limit="2" :step="0.01" />
        </t-form-item>
        <t-form-item label="备注" name="remark">
          <t-input v-model="formData.remark" placeholder="可选备注" />
        </t-form-item>
        <div v-if="formData.buyPrice > 0 && formData.quantity > 0" class="form-preview">
          <span class="preview-label">实际购入价：</span>
          <span class="preview-value">
            {{ formatMoney(calculateBuyCost(formData.buyPrice, formData.quantity)) }}
          </span>
        </div>
      </t-form>
    </t-dialog>
  </div>
</template>

<style scoped>
.profit-stats-container {
  padding: 24px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 0;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--td-border-level-1-color);
}

.toolbar-left {
  display: flex;
  gap: 12px;
}

.toolbar-right {
  width: 280px;
}

.search-input {
  width: 100%;
}

.operation-btns {
  display: flex;
  gap: 8px;
  justify-content: center;
}

.empty-state {
  padding: 48px 0;
}

.summary-section {
  padding-top: 24px;
  margin-top: 24px;
  border-top: 1px solid var(--td-border-level-1-color);
}

.summary-title {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  font-size: 16px;
  font-weight: 600;
  color: var(--td-text-color-primary);
}

.summary-cards {
  display: flex;
  gap: 16px;
}

.summary-card {
  flex: 1;
  padding: 16px 20px;
  text-align: center;
  background: var(--td-bg-color-container-hover);
  border-radius: 8px;
}

.summary-label {
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--td-text-color-secondary);
}

.summary-value {
  font-size: 20px;
  font-weight: 600;
  color: var(--td-text-color-primary);
}

.summary-value.primary {
  color: var(--td-brand-color);
}

.summary-value.profit-positive {
  color: #00a870;
}

.summary-value.profit-negative {
  :#e34d59;
}

.record-form {
  padding: 8px 0;
}

.form-preview {
  padding: 12px 16px;
  margin-top: 8px;
  text-align: right;
  background: var(--td-bg-color-secondary);
  border-radius: 4px;
}

.preview-label {
  font-size: 13px;
  color: var(--td-text-color-secondary);
}

.preview-value {
  margin-left: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--td-brand-color);
}

:deep(.t-table) {
  font-size: 13px;
}

:deep(.t-table th) {
  font-weight: 600;
}

:deep(.profit-positive) {
  color: #00a870;
}

:deep(.profit-negative) {
  color: #e34d59;
}
</style>
