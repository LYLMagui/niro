<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from "vue";
import { MessagePlugin } from "tdesign-vue-next";
import {
  AddIcon,
  EditIcon,
  DeleteIcon,
  CheckCircleFilledIcon,
  TimeFilledIcon,
  CloseCircleFilledIcon,
  ImageIcon,
  GiftIcon,
} from "tdesign-icons-vue-next";
import type {
  PrimaryTableCol,
  TableRowData,
  InputNumberValue,
  ChangeContext,
  UploadFile,
  UploadChangeContext,
} from "tdesign-vue-next";
import type {
  UnboxRecord,
  UnboxRecordForm,
  PurchaseStatus,
  WeaponAttribute,
  UnboxSummary,
} from "@/types/unbox";
import {
  calculateActualPrice,
  calculateEstimatedProfit,
  calculateProfitRate,
  calculateActualProfit,
  formatDecimal,
} from "@/types/unbox";

const STORAGE_KEY = "niro-unbox-records";
const DISCOUNT_KEY = "niro-unbox-global-discount";
const DEFAULT_DISCOUNT = 0.72;

const tableData = ref<UnboxRecord[]>([]);
const dialogVisible = ref(false);
const editingId = ref<string | null>(null);
const globalDiscount = ref(DEFAULT_DISCOUNT);
const loading = ref(false);
const uploadFiles = ref<UploadFile[]>([]);

const formData = reactive<UnboxRecordForm>({
  boxName: "",
  purchasePrice: 0,
  screenshot: "",
  weaponName: "",
  wearValue: 0,
  attribute: "普通",
  steamPrice: 0,
  platformPrice: 0,
  discount: DEFAULT_DISCOUNT,
  purchaseStatus: "pending",
  actualSellPrice: 0,
});

const attributeOptions: { label: string; value: WeaponAttribute }[] = [
  { label: "普通", value: "普通" },
  { label: "ST", value: "ST" },
  { label: "纪念", value: "纪念" },
  { label: "其他", value: "其他" },
];

const purchaseStatusOptions: {
  value: PurchaseStatus;
  label: string;
  theme: "success" | "warning" | "danger";
  icon: typeof CheckCircleFilledIcon;
}[] = [
  { value: "purchased", label: "已购买", theme: "success", icon: CheckCircleFilledIcon },
  { value: "pending", label: "未购买", theme: "warning", icon: TimeFilledIcon },
  { value: "abandoned", label: "放弃购买", theme: "danger", icon: CloseCircleFilledIcon },
];

const columns: PrimaryTableCol<TableRowData>[] = [
  { colKey: "index", title: "序号", width: 60, align: "center" },
  { colKey: "boxName", title: "箱子", width: 140, minWidth: 120, ellipsis: true },
  { colKey: "weaponName", title: "枪", width: 160, minWidth: 130, ellipsis: true },
  { colKey: "wearValue", title: "磨损", width: 80, align: "center" },
  { colKey: "attribute", title: "属性", width: 70, align: "center" },
  { colKey: "steamPrice", title: "Steam价", width: 90, align: "right" },
  { colKey: "actualPrice", title: "实际价", width: 90, align: "right" },
  { colKey: "platformPrice", title: "平台价", width: 90, align: "right" },
  { colKey: "discount", title: "折扣", width: 60, align: "center" },
  { colKey: "estimatedProfit", title: "预估利润", width: 100, align: "right" },
  { colKey: "purchaseStatus", title: "状态", width: 90, align: "center" },
  { colKey: "profitRate", title: "利润率", width: 80, align: "center" },
  { colKey: "actualSellPrice", title: "出售价", width: 90, align: "right" },
  { colKey: "actualProfit", title: "实际利润", width: 90, align: "right" },
  { colKey: "operation", title: "操作", width: 120, fixed: "right", align: "center" },
];

const summary = computed<UnboxSummary>(() => {
  const totalSteamCost = tableData.value.reduce((sum, row) => sum + row.steamPrice, 0);
  const totalEstimatedProfit = tableData.value.reduce((sum, row) => sum + row.estimatedProfit, 0);
  return {
    totalSteamCost: formatDecimal(totalSteamCost),
    totalEstimatedProfit: formatDecimal(totalEstimatedProfit),
  };
});

const previewActualPrice = computed(() =>
  calculateActualPrice(formData.steamPrice, formData.discount)
);
const previewEstimatedProfit = computed(() =>
  calculateEstimatedProfit(formData.steamPrice, formData.platformPrice)
);
const previewProfitRate = computed(() =>
  calculateProfitRate(previewEstimatedProfit.value, previewActualPrice.value)
);
const previewActualProfit = computed(() =>
  calculateActualProfit(formData.actualSellPrice, previewActualPrice.value)
);

const clampDiscount = (value: number): number => {
  const safeValue = Number.isFinite(value) ? value : DEFAULT_DISCOUNT;
  return formatDecimal(Math.min(Math.max(safeValue, 0), 1));
};

const persistRecords = (records: UnboxRecord[]) => {
  if (typeof window === "undefined") return;
  localStorage.setItem(STORAGE_KEY, JSON.stringify(records));
};

const hydrateRecord = (record: UnboxRecord): UnboxRecord => {
  const actualPrice = calculateActualPrice(record.steamPrice, record.discount);
  const estimatedProfit = calculateEstimatedProfit(record.steamPrice, record.platformPrice);
  const profitRate = calculateProfitRate(estimatedProfit, actualPrice);
  const actualProfit = calculateActualProfit(record.actualSellPrice, actualPrice);
  return {
    ...record,
    actualPrice,
    estimatedProfit,
    profitRate,
    actualProfit,
  };
};

const loadRecords = () => {
  if (typeof window === "undefined") return;
  const cache = localStorage.getItem(STORAGE_KEY);
  if (cache) {
    try {
      const parsed = JSON.parse(cache) as UnboxRecord[];
      tableData.value = parsed.map(hydrateRecord);
    } catch (error) {
      console.error("加载开箱记录失败", error);
      tableData.value = [];
    }
  }
  const discountCache = localStorage.getItem(DISCOUNT_KEY);
  if (discountCache) {
    globalDiscount.value = clampDiscount(Number(discountCache));
  }
};

const resetForm = () => {
  formData.boxName = "";
  formData.purchasePrice = 0;
  formData.screenshot = "";
  formData.weaponName = "";
  formData.wearValue = 0;
  formData.attribute = "普通";
  formData.steamPrice = 0;
  formData.platformPrice = 0;
  formData.discount = globalDiscount.value;
  formData.purchaseStatus = "pending";
  formData.actualSellPrice = 0;
  uploadFiles.value = [];
};

const composeRecord = (payload: UnboxRecordForm, meta?: { id?: string; createdAt?: string }) => {
  const now = new Date().toISOString();
  const actualPrice = calculateActualPrice(payload.steamPrice, payload.discount);
  const estimatedProfit = calculateEstimatedProfit(payload.steamPrice, payload.platformPrice);
  const profitRate = calculateProfitRate(estimatedProfit, actualPrice);
  const actualProfit = calculateActualProfit(payload.actualSellPrice, actualPrice);
  const record: UnboxRecord = {
    id: meta?.id ?? crypto.randomUUID(),
    ...payload,
    actualPrice,
    estimatedProfit,
    profitRate,
    actualProfit,
    createdAt: meta?.createdAt ?? now,
    updatedAt: now,
  };
  return record;
};

const handleAdd = () => {
  editingId.value = null;
  resetForm();
  dialogVisible.value = true;
};

const handleEdit = (row: UnboxRecord) => {
  editingId.value = row.id;
  formData.boxName = row.boxName;
  formData.purchasePrice = row.purchasePrice;
  formData.screenshot = row.screenshot;
  formData.weaponName = row.weaponName;
  formData.wearValue = row.wearValue;
  formData.attribute = row.attribute;
  formData.steamPrice = row.steamPrice;
  formData.platformPrice = row.platformPrice;
  formData.discount = row.discount;
  formData.purchaseStatus = row.purchaseStatus;
  formData.actualSellPrice = row.actualSellPrice;
  uploadFiles.value = row.screenshot
    ? [
        {
          name: "screenshot",
          url: row.screenshot,
          status: "success",
        },
      ]
    : [];
  dialogVisible.value = true;
};

const validateForm = (): boolean => {
  if (!formData.boxName.trim()) {
    MessagePlugin.warning("请填写箱子名称");
    return false;
  }
  if (!formData.weaponName.trim()) {
    MessagePlugin.warning("请填写枪械名称");
    return false;
  }
  if (formData.steamPrice <= 0) {
    MessagePlugin.warning("Steam 购入价需大于 0");
    return false;
  }
  if (formData.platformPrice < 0) {
    MessagePlugin.warning("平台价不能小于 0");
    return false;
  }
  if (formData.discount <= 0 || formData.discount > 1.2) {
    MessagePlugin.warning("请设置合理的折扣（0-1.2）");
    return false;
  }
  return true;
};

const handleSubmit = () => {
  if (!validateForm()) return;
  const payload = { ...formData };
  if (editingId.value) {
    const index = tableData.value.findIndex((item) => item.id === editingId.value);
    if (index === -1) {
      MessagePlugin.error("记录不存在或已删除");
      dialogVisible.value = false;
      return;
    }
    const updated = composeRecord(payload, {
      id: tableData.value[index].id,
      createdAt: tableData.value[index].createdAt,
    });
    tableData.value.splice(index, 1, updated);
    persistRecords(tableData.value);
    MessagePlugin.success("记录已更新");
  } else {
    const record = composeRecord(payload);
    tableData.value.unshift(record);
    persistRecords(tableData.value);
    MessagePlugin.success("记录已创建");
  }
  dialogVisible.value = false;
};

const handleDelete = (id: string) => {
  tableData.value = tableData.value.filter((item) => item.id !== id);
  persistRecords(tableData.value);
  MessagePlugin.success("记录已删除");
};

const handleApplyGlobalDiscount = () => {
  if (!tableData.value.length) {
    MessagePlugin.info("暂无记录可更新");
    return;
  }
  tableData.value = tableData.value.map((record) =>
    composeRecord(
      {
        boxName: record.boxName,
        purchasePrice: record.purchasePrice,
        screenshot: record.screenshot,
        weaponName: record.weaponName,
        wearValue: record.wearValue,
        attribute: record.attribute,
        steamPrice: record.steamPrice,
        platformPrice: record.platformPrice,
        discount: globalDiscount.value,
        purchaseStatus: record.purchaseStatus,
        actualSellPrice: record.actualSellPrice,
      },
      { id: record.id, createdAt: record.createdAt }
    )
  );
  persistRecords(tableData.value);
  MessagePlugin.success("已批量更新折扣");
};

const handleDiscountInput = (value: InputNumberValue, _context: ChangeContext) => {
  const numericValue = typeof value === "number" ? value : Number(value);
  const safeValue = Number.isNaN(numericValue) ? 0 : numericValue;
  formData.discount = clampDiscount(safeValue ?? 0);
};


const readFileAsDataUrl = (file: File): Promise<string> =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ""));
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });

const syncUploadPreview = async (files: UploadFile[]) => {
  if (!files.length) {
    formData.screenshot = "";
    return;
  }
  const target = files[0];
  if (target.url) {
    formData.screenshot = target.url;
    return;
  }
  if (target.raw) {
    const dataUrl = await readFileAsDataUrl(target.raw);
    target.url = dataUrl;
    formData.screenshot = dataUrl;
  }
  // TODO: ?? OCR ??????
};

const handleUploadChange = async (files: UploadFile[], _context: UploadChangeContext) => {
  const normalized = files.slice(0, 1);
  uploadFiles.value = normalized;
  await syncUploadPreview(normalized);
};

const handleUploadRemove = () => {
  uploadFiles.value = [];
  formData.screenshot = "";
};

const handlePasteUpload = async (event: ClipboardEvent) => {
  const items = event.clipboardData?.items ?? [];
  const imageItem = Array.from(items).find((item) => item.type.startsWith("image/"));
  if (!imageItem) return;
  const file = imageItem.getAsFile();
  if (!file) return;
  event.preventDefault();
  const uploadFile: UploadFile = {
    name: file.name || "pasted-image",
    type: file.type,
    size: file.size,
    raw: file,
    status: "success",
  };
  uploadFiles.value = [uploadFile];
  await syncUploadPreview(uploadFiles.value);
};

const formatCurrency = (value: number): string => `¥${value.toFixed(2)}`;
const formatPercent = (value: number): string => `${value.toFixed(2)}%`;

const getStatusConfig = (value: PurchaseStatus) =>
  purchaseStatusOptions.find((item) => item.value === value) ?? purchaseStatusOptions[1];

watch(
  globalDiscount,
  (value) => {
    const normalized = clampDiscount(value);
    if (normalized !== value) {
      globalDiscount.value = normalized;
      return;
    }
    if (typeof window !== "undefined") {
      localStorage.setItem(DISCOUNT_KEY, normalized.toString());
    }
  },
  { immediate: false }
);

onMounted(() => {
  loadRecords();
});
</script>

<template>
  <div class="p-6">
    <t-card :bordered="false" class="embedded-card shadow-sm">
      <template #title>
        <div class="flex items-center">
          <GiftIcon class="mr-2 text-blue-600" />
          <span class="text-lg font-bold text-gray-800">开箱记录</span>
        </div>
      </template>
      <template #actions>
        <div class="summary-actions">
          <div class="summary-item">
            <span class="summary-label">Steam 购入总价</span>
            <span class="summary-value text-blue-600">
              {{ formatCurrency(summary.totalSteamCost) }}
            </span>
          </div>
          <div class="summary-item">
            <span class="summary-label">预估总利润</span>
            <span
              class="summary-value"
              :class="summary.totalEstimatedProfit >= 0 ? 'text-green-600' : 'text-red-600'"
            >
              {{ formatCurrency(summary.totalEstimatedProfit) }}
            </span>
          </div>
        </div>
      </template>

      <!-- 筛选栏 -->
      <div class="border-b border-gray-100 p-6">
        <t-row :gutter="16" align="middle" class="toolbar-row">
          <t-col :span="3">
            <t-button theme="primary" size="small" @click="handleAdd">
              <template #icon><AddIcon /></template>
              新增记录
            </t-button>
          </t-col>
          <t-col :span="9">
            <div class="toolbar-right">
              <span class="text-sm text-gray-600">全局折扣</span>
              <t-input-number
                v-model="globalDiscount"
                :min="0"
                :max="1.2"
                :step="0.01"
                :decimal-places="2"
                size="small"
                class="discount-input"
              />
              <t-button
                variant="outline"
                theme="default"
                size="small"
                :disabled="!tableData.length"
                @click="handleApplyGlobalDiscount"
              >
                应用至全部
              </t-button>
            </div>
          </t-col>
        </t-row>
      </div>

      <t-table
        :data="tableData"
        :columns="columns"
        :loading="loading"
        row-key="id"
        hover
        size="small"
        class="embedded-table w-full"
        :header-affixed-top="{ offsetTop: 0, container: '.t-layout__content' }"
      >
        <template #index="{ rowIndex }">
          {{ rowIndex + 1 }}
        </template>

        <template #purchasePrice="{ row }">
          {{ formatCurrency(row.purchasePrice) }}
        </template>

        <template #steamPrice="{ row }">
          {{ formatCurrency(row.steamPrice) }}
        </template>

        <template #actualPrice="{ row }">
          {{ formatCurrency(row.actualPrice) }}
        </template>

        <template #platformPrice="{ row }">
          {{ formatCurrency(row.platformPrice) }}
        </template>

        <template #discount="{ row }">
          {{ row.discount.toFixed(2) }}
        </template>

        <template #estimatedProfit="{ row }">
          <span :class="row.estimatedProfit >= 0 ? 'text-positive' : 'text-negative'">
            {{ formatCurrency(row.estimatedProfit) }}
          </span>
        </template>

        <template #profitRate="{ row }">
          <span :class="row.profitRate >= 0 ? 'text-positive' : 'text-negative'">
            {{ formatPercent(row.profitRate) }}
          </span>
        </template>

        <template #actualSellPrice="{ row }">
          {{ row.actualSellPrice > 0 ? formatCurrency(row.actualSellPrice) : "-" }}
        </template>

        <template #actualProfit="{ row }">
          <span :class="row.actualProfit >= 0 ? 'text-positive' : 'text-negative'">
            {{ formatCurrency(row.actualProfit) }}
          </span>
        </template>

        <template #wearValue="{ row }">
          {{ row.wearValue.toFixed(4) }}
        </template>

        <template #screenshot="{ row }">
          <div class="screenshot-cell">
            <t-image
              v-if="row.screenshot"
              :src="row.screenshot"
              :alt="`${row.boxName} 截图`"
              fit="cover"
              shape="round"
              :style="{ width: '40px', height: '40px' }"
            />
            <div v-else class="screenshot-placeholder">
              <ImageIcon />
            </div>
          </div>
        </template>

        <template #attribute="{ row }">
          <t-tag theme="primary" variant="light-outline">{{ row.attribute }}</t-tag>
        </template>

        <template #purchaseStatus="{ row }">
          <t-tag :theme="getStatusConfig(row.purchaseStatus).theme" variant="light">
            {{ getStatusConfig(row.purchaseStatus).label }}
          </t-tag>
        </template>

        <template #operation="{ row }">
          <div class="operation-buttons">
            <t-button size="small" variant="text" class="operation-btn" @click="handleEdit(row)">
              <template #icon><EditIcon /></template>
              编辑
            </t-button>
            <t-popconfirm content="确认删除该记录？" @confirm="handleDelete(row.id)">
              <t-button size="small" variant="text" theme="danger" class="operation-btn">
                <template #icon><DeleteIcon /></template>
                删除
              </t-button>
            </t-popconfirm>
          </div>
        </template>
      </t-table>

      <div v-if="!tableData.length" class="empty-state">
        <t-empty description="暂无开箱记录">
          <template #action>
            <t-button theme="primary" variant="dashed" class="action-button" @click="handleAdd">
              新增记录
            </t-button>
          </template>
        </t-empty>
      </div>
    </t-card>

    <t-dialog
      v-model:visible="dialogVisible"
      :header="editingId ? '编辑记录' : '新增记录'"
      width="640px"
      :footer="true"
      @confirm="handleSubmit"
    >
      <t-form :data="formData" label-width="96px" layout="vertical" class="record-form">
        <t-row :gutter="16">
          <t-col :span="6">
            <t-form-item label="????" name="boxName" required>
              <t-input
                v-model="formData.boxName"
                placeholder="???? 2 ????"
                maxlength="50"
              />
            </t-form-item>
          </t-col>
          <t-col :span="6">
            <t-form-item label="?????" name="purchasePrice">
              <t-input-number
                v-model="formData.purchasePrice"
                :min="0"
                :decimal-places="2"
                :step="0.01"
              />
            </t-form-item>
          </t-col>
          <t-col :span="6">
            <t-form-item label="??" name="screenshot">
              <div class="upload-wrapper" @paste="handlePasteUpload">
                <t-upload
                  v-model="uploadFiles"
                  theme="image"
                  :max="1"
                  accept="image/*"
                  :auto-upload="false"
                  :draggable="true"
                  :show-image-file-name="false"
                  :show-upload-progress="false"
                  :show-thumbnail="true"
                  :multiple="false"
                  @change="handleUploadChange"
                  @remove="handleUploadRemove"
                >
                  <template #trigger>
                    <div class="upload-trigger">
                      <div class="upload-trigger__icon">
                        <ImageIcon />
                      </div>
                      <div class="upload-trigger__text">
                        <div class="upload-title">??/???????</div>
                        <div class="upload-subtitle">?????????????? 1 ??</div>
                      </div>
                    </div>
                  </template>
                </t-upload>
                <div class="upload-tip">OCR ???????????</div>
              </div>
            </t-form-item>
          </t-col>
          <t-col :span="6">
            <t-form-item label="????" name="weaponName" required>
              <t-input
                v-model="formData.weaponName"
                placeholder="??AK-47 | ??"
                maxlength="60"
              />
            </t-form-item>
          </t-col>
        </t-row>
        <t-row :gutter="16">
          <t-col :span="6">
            <t-form-item label="??" name="wearValue">
              <t-input-number
                v-model="formData.wearValue"
                :min="0"
                :max="1"
                :decimal-places="4"
                :step="0.0001"
                :show-controls="false"
              />
            </t-form-item>
          </t-col>
          <t-col :span="6">
            <t-form-item label="??" name="attribute">
              <t-select v-model="formData.attribute" :options="attributeOptions" />
            </t-form-item>
          </t-col>
          <t-col :span="6">
            <t-form-item label="Steam???" name="steamPrice" required>
              <t-input-number
                v-model="formData.steamPrice"
                :min="0"
                :decimal-places="2"
                :step="0.01"
                :show-controls="false"
              />
            </t-form-item>
          </t-col>
          <t-col :span="6">
            <t-form-item label="????" name="platformPrice">
              <t-input-number
                v-model="formData.platformPrice"
                :min="0"
                :decimal-places="2"
                :step="0.01"
                :show-controls="false"
              />
            </t-form-item>
          </t-col>
        </t-row>
        <t-row :gutter="16">
          <t-col :span="6">
            <t-form-item label="??" name="discount" required>
              <t-input-number
                :value="formData.discount"
                :min="0"
                :max="1.2"
                :step="0.01"
                :decimal-places="2"
                :show-controls="false"
                @change="handleDiscountInput"
              />
            </t-form-item>
          </t-col>
          <t-col :span="6">
            <t-form-item label="????" name="purchaseStatus">
              <t-select v-model="formData.purchaseStatus">
                <t-option
                  v-for="item in purchaseStatusOptions"
                  :key="item.value"
                  :value="item.value"
                  :label="item.label"
                >
                  <template #label>
                    <span class="status-option">
                      <component :is="item.icon" />
                      {{ item.label }}
                    </span>
                  </template>
                </t-option>
              </t-select>
            </t-form-item>
          </t-col>
          <t-col :span="6">
            <t-form-item label="?????" name="actualSellPrice">
              <t-input-number
                v-model="formData.actualSellPrice"
                :min="0"
                :decimal-places="2"
                :step="0.01"
                :show-controls="false"
              />
            </t-form-item>
          </t-col>
        </t-row>

        <div class="preview-panel">
          <div class="preview-item">
            <span class="preview-label">?????</span>
            <strong>{{ formatCurrency(previewActualPrice) }}</strong>
          </div>
          <div class="preview-item">
            <span class="preview-label">????</span>
            <strong :class="previewEstimatedProfit >= 0 ? 'text-green-600' : 'text-red-600'">
              {{ formatCurrency(previewEstimatedProfit) }}
            </strong>
          </div>
          <div class="preview-item">
            <span class="preview-label">???</span>
            <strong :class="previewProfitRate >= 0 ? 'text-green-600' : 'text-red-600'">
              {{ formatPercent(previewProfitRate) }}
            </strong>
          </div>
          <div class="preview-item">
            <span class="preview-label">????</span>
            <strong :class="previewActualProfit >= 0 ? 'text-green-600' : 'text-red-600'">
              {{ formatCurrency(previewActualProfit) }}
            </strong>
          </div>
        </div>
      </t-form>
    </t-dialog>
  </div>
</template>

<style scoped>
:deep(.embedded-table .t-table__row) {
  height: 44px;
}

:deep(.embedded-table .t-table__td) {
  padding-top: 8px;
  padding-bottom: 8px;
}

.summary-actions {
  display: flex;
  gap: 16px;
  align-items: center;
}

.summary-item {
  display: flex;
  flex-direction: column;
  min-width: 150px;
  text-align: right;
}

.summary-label {
  font-size: 12px;
  color: var(--td-text-color-secondary);
}

.summary-value {
  font-size: 14px;
  font-weight: 600;
}

.toolbar-row {
  margin: 0;
}

.toolbar-right {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: flex-end;
}

.discount-input {
  width: 120px;
}

.screenshot-cell {
  display: flex;
  align-items: center;
  justify-content: center;
}

.screenshot-placeholder {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  font-size: 10px;
  color: var(--td-text-color-secondary);
  border: 1px dashed var(--td-border-level-2-color);
  border-radius: 6px;
}

.operation-buttons {
  display: flex;
  gap: 8px;
  justify-content: center;
}

.operation-btn {
  min-width: 32px;
  min-height: 32px;
}

.empty-state {
  padding: 48px 0;
}

.record-form {
  max-height: 70vh;
  overflow-y: auto;
}

.preview-panel {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 8px;
  padding: 12px;
  margin-top: 12px;
  background: var(--td-bg-color-container);
  border: 1px solid var(--td-border-level-1-color);
  border-radius: 10px;
  box-shadow: var(--td-shadow-1, 0 1px 2px rgba(0, 0, 0, 0.04));
}

.preview-item {
  display: flex;
  flex-direction: column;
}

.preview-label {
  font-size: 12px;
  color: var(--td-text-color-secondary);
}

.upload-wrapper {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.upload-trigger {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 12px;
  border: 1px dashed var(--td-border-level-2-color);
  border-radius: 10px;
  background: var(--td-bg-color-container);
}

.upload-trigger__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  color: var(--td-text-color-secondary);
  background: var(--td-bg-color-secondarycontainer);
}

.upload-trigger__text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.upload-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--td-text-color-primary);
}

.upload-subtitle {
  font-size: 12px;
  color: var(--td-text-color-secondary);
}

.upload-tip {
  font-size: 12px;
  color: var(--td-text-color-secondary);
}

.record-form :deep(.t-upload) {
  width: 100%;
}

.status-option {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

@media (max-width: 1024px) {
  .summary-actions {
    flex-direction: column;
    align-items: flex-end;
  }

  .toolbar-right {
    justify-content: flex-start;
  }
}
</style>
