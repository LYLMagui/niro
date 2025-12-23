<template>
  <div class="p-4">
    <t-card :bordered="false" title="扫货任务管理">
      <!-- 搜索栏 -->
      <t-row :gutter="16" class="mb-4">
        <t-col :span="3">
          <t-input v-model="queryParams.name" placeholder="搜索商品名称" clearable @enter="fetchData" />
        </t-col>
        <t-col :span="2">
          <t-select v-model="queryParams.status" placeholder="任务状态" clearable @change="fetchData">
            <t-option label="停止" :value="0" />
            <t-option label="运行中" :value="1" />
            <t-option label="已完成" :value="2" />
            <t-option label="异常" :value="3" />
          </t-select>
        </t-col>
        <t-col :span="2">
          <t-button theme="primary" @click="fetchData">查询</t-button>
          <t-button theme="default" variant="base" @click="resetQuery" class="ml-2">重置</t-button>
        </t-col>
        <t-col :span="5" class="text-right">
          <t-button theme="primary" @click="handleAdd">新增任务</t-button>
        </t-col>
      </t-row>

      <!-- 数据表格 -->
      <t-table
        row-key="id"
        :data="dataList"
        :columns="columns"
        :loading="loading"
        :pagination="pagination"
        @page-change="onPageChange"
      >
        <template #goods="{ row }">
          <div class="flex items-center">
            <t-image v-if="row.goodsIconUrl" :src="row.goodsIconUrl" class="w-8 h-8 mr-2 rounded" />
            <div>
              <div class="font-bold truncate max-w-xs" :title="row.name">{{ row.name }}</div>
              <div class="text-xs text-gray-500">ID: {{ row.goodsId }}</div>
            </div>
          </div>
        </template>
        
        <template #price="{ row }">
          <div class="text-orange-600 font-bold">¥{{ row.maxPrice }}</div>
        </template>

        <template #paintwear="{ row }">
          {{ row.minPaintwear }} - {{ row.maxPaintwear }}
        </template>

        <template #progress="{ row }">
          {{ row.successCount }} / {{ row.buyCount }}
        </template>

        <template #status="{ row }">
          <t-tag v-if="row.status === 0" theme="default">停止</t-tag>
          <t-tag v-else-if="row.status === 1" theme="success">运行中</t-tag>
          <t-tag v-else-if="row.status === 2" theme="primary">已完成</t-tag>
          <t-tag v-else theme="danger">异常</t-tag>
        </template>

        <template #op="{ row }">
          <t-link theme="primary" class="mr-2" @click="handleEdit(row)">编辑</t-link>
          <t-popconfirm v-if="row.status === 0" content="确定要启动任务吗？" @confirm="handleStatus(row, 1)">
             <t-link theme="success" class="mr-2">启动</t-link>
          </t-popconfirm>
          <t-popconfirm v-if="row.status === 1" content="确定要停止任务吗？" @confirm="handleStatus(row, 0)">
             <t-link theme="warning" class="mr-2">停止</t-link>
          </t-popconfirm>
          <t-popconfirm content="确定要删除任务吗？" @confirm="handleDelete(row)">
            <t-link theme="danger">删除</t-link>
          </t-popconfirm>
        </template>
      </t-table>
    </t-card>

    <!-- 新增/编辑对话框 -->
    <t-dialog
      v-model:visible="dialogVisible"
      :header="dialogTitle"
      :confirm-btn="{ content: '提交', loading: submitLoading }"
      @confirm="handleSubmit"
      width="600px"
    >
      <t-form ref="formRef" :data="formData" :rules="rules" :label-width="100">
        <t-form-item label="选择商品" name="goodsId">
           <t-select
            v-model="formData.goodsId"
            filterable
            placeholder="输入商品名称搜索"
            :loading="goodsLoading"
            :on-search="remoteSearchGoods"
            :disabled="!!formData.id"
          >
            <t-option v-for="item in goodsOptions" :key="item.goodsId" :value="item.goodsId" :label="item.name">
               {{ item.name }}
            </t-option>
          </t-select>
        </t-form-item>
        <t-form-item label="最高价格" name="maxPrice">
          <t-input-number v-model="formData.maxPrice" :min="0.01" :step="0.1" suffix="元" theme="column" />
        </t-form-item>
        <t-form-item label="磨损范围" name="minPaintwear">
          <div class="flex items-center w-full">
            <t-input-number v-model="formData.minPaintwear" :min="0" :max="1" :step="0.01" theme="column" placeholder="最小" />
            <span class="mx-2">-</span>
            <t-input-number v-model="formData.maxPaintwear" :min="0" :max="1" :step="0.01" theme="column" placeholder="最大" />
          </div>
        </t-form-item>
        <t-form-item label="购买数量" name="buyCount">
          <t-input-number v-model="formData.buyCount" :min="1" :step="1" theme="column" />
        </t-form-item>
      </t-form>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from "vue";
import { MessagePlugin, DialogPlugin } from "tdesign-vue-next";
import { taskApi } from "@/api/task";
import { goodsApi } from "@/api/goods";
import type { BuffScanTask, TaskQueryParam, TaskSaveParam } from "@/types/task";
import type { GoodsSimple } from "@/types/goods";

// --- 表格数据 ---
const loading = ref(false);
const dataList = ref<BuffScanTask[]>([]);
const queryParams = reactive<TaskQueryParam>({
  pageNo: 1,
  pageSize: 10,
  name: "",
  status: undefined,
});
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
});

const columns = [
  { colKey: "id", title: "ID", width: 80 },
  { colKey: "goods", title: "商品信息", width: 300, cell: "goods" },
  { colKey: "price", title: "目标价格", width: 120, cell: "price" },
  { colKey: "paintwear", title: "磨损范围", width: 150, cell: "paintwear" },
  { colKey: "progress", title: "进度", width: 100, cell: "progress" },
  { colKey: "status", title: "状态", width: 100, cell: "status" },
  { colKey: "op", title: "操作", width: 200, cell: "op", fixed: "right" },
];

// --- 表单数据 ---
const dialogVisible = ref(false);
const dialogTitle = ref("新增任务");
const submitLoading = ref(false);
const formRef = ref();
const formData = reactive<TaskSaveParam>({
  goodsId: undefined as any,
  maxPrice: 0,
  minPaintwear: 0,
  maxPaintwear: 1,
  buyCount: 1,
});

const rules = {
  goodsId: [{ required: true, message: "请选择商品", type: "error" }],
  maxPrice: [{ required: true, message: "请输入最高价格", type: "error" }],
  buyCount: [{ required: true, message: "请输入购买数量", type: "error" }],
};

// --- 商品搜索 ---
const goodsLoading = ref(false);
const goodsOptions = ref<GoodsSimple[]>([]);

const remoteSearchGoods = async (keyword: string) => {
  if (!keyword) return;
  goodsLoading.value = true;
  try {
    const res = await goodsApi.getSimpleList(keyword);
    goodsOptions.value = res;
  } finally {
    goodsLoading.value = false;
  }
};

// --- 方法 ---

const fetchData = async () => {
  loading.value = true;
  try {
    const res = await taskApi.getPage({
      pageNo: pagination.current,
      pageSize: pagination.pageSize,
      name: queryParams.name,
      status: queryParams.status,
    });
    dataList.value = res.records;
    pagination.total = res.total;
  } catch (e) {
    // error handled by interceptor
  } finally {
    loading.value = false;
  }
};

const onPageChange = (pageInfo: any) => {
  pagination.current = pageInfo.current;
  pagination.pageSize = pageInfo.pageSize;
  fetchData();
};

const resetQuery = () => {
  queryParams.name = "";
  queryParams.status = undefined;
  fetchData();
};

const handleAdd = () => {
  dialogTitle.value = "新增任务";
  formData.id = undefined;
  formData.goodsId = undefined as any;
  formData.maxPrice = 0;
  formData.minPaintwear = 0;
  formData.maxPaintwear = 1;
  formData.buyCount = 1;
  dialogVisible.value = true;
  goodsOptions.value = []; // reset options
};

const handleEdit = (row: BuffScanTask) => {
  dialogTitle.value = "编辑任务";
  formData.id = row.id;
  formData.goodsId = row.goodsId;
  formData.maxPrice = row.maxPrice;
  formData.minPaintwear = row.minPaintwear;
  formData.maxPaintwear = row.maxPaintwear;
  formData.buyCount = row.buyCount;
  
  // 预填充当前商品到选项中，否则显示ID
  goodsOptions.value = [{ goodsId: row.goodsId, name: row.name }];
  
  dialogVisible.value = true;
};

const handleSubmit = async () => {
  const result = await formRef.value.validate();
  if (result !== true) return;

  submitLoading.value = true;
  try {
    if (formData.id) {
      await taskApi.update(formData);
      MessagePlugin.success("更新成功");
    } else {
      await taskApi.add(formData);
      MessagePlugin.success("创建成功");
    }
    dialogVisible.value = false;
    fetchData();
  } finally {
    submitLoading.value = false;
  }
};

const handleDelete = async (row: BuffScanTask) => {
  await taskApi.delete(row.id);
  MessagePlugin.success("删除成功");
  fetchData();
};

const handleStatus = async (row: BuffScanTask, status: number) => {
  await taskApi.updateStatus(row.id, status);
  MessagePlugin.success(status === 1 ? "任务已启动" : "任务已停止");
  fetchData();
};

onMounted(() => {
  fetchData();
});
</script>
