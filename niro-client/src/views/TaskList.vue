<template>
  <div class="p-6">
    <t-card :bordered="false" class="embedded-card shadow-sm">
      <template #title>
        <div class="flex items-center">
          <t-icon name="server" class="mr-2 text-blue-600" />
          <span class="text-lg font-bold text-gray-800">任务管理</span>
        </div>
      </template>

      <!-- 顶部分类 Tabs -->
      <t-tabs v-model="activeTab" class="px-6" @change="handleTabChange">
        <t-tab-panel value="SCAN" label="扫货扫描" />
        <t-tab-panel value="TRADE" label="下单任务" />
        <t-tab-panel v-if="isAdmin" value="SYSTEM" label="系统任务" />
      </t-tabs>

      <!-- 搜索与操作栏 -->
      <div class="border-b border-gray-100 p-6">
        <t-row :gutter="16" align="center">
          <t-col :span="3">
            <t-input
              v-model="queryParams.keyword"
              placeholder="搜索任务/商品名称"
              clearable
              @enter="fetchData"
            />
          </t-col>
          <t-col :span="2">
            <t-select
              v-model="queryParams.status"
              placeholder="任务状态"
              clearable
              @change="fetchData"
            >
              <t-option label="停止" :value="0" />
              <t-option label="运行中" :value="1" />
              <t-option label="执行中" :value="4" />
              <t-option label="已完成" :value="2" />
              <t-option label="异常" :value="3" />
            </t-select>
          </t-col>
          <t-col :span="3">
            <div class="flex gap-2">
              <t-button theme="primary" @click="fetchData">查询</t-button>
              <t-button theme="default" variant="base" @click="resetQuery">重置</t-button>
            </div>
          </t-col>
          <t-col :span="4">
            <div class="flex justify-end gap-3">
              <t-button
                v-if="activeTab === 'SYSTEM' && isAdmin"
                theme="default"
                variant="outline"
                @click="handleAddSystem"
              >
                新增系统任务
              </t-button>
              <t-button v-if="activeTab !== 'SYSTEM'" theme="primary" @click="handleAdd">
                新增任务
              </t-button>
            </div>
          </t-col>
        </t-row>
      </div>

      <!-- 数据表格 -->
      <t-table
        row-key="id"
        :data="dataList"
        :columns="columns"
        :loading="loading"
        :pagination="pagination"
        hover
        class="embedded-table w-full"
        @page-change="onPageChange"
      >
        <template #goods="{ row }">
          <div class="flex items-center">
            <t-image v-if="row.goodsIconUrl" :src="row.goodsIconUrl" class="mr-2 h-8 w-8 rounded" />
            <div
              v-else-if="row.taskType >= 2"
              class="mr-2 flex h-8 w-8 items-center justify-center rounded bg-blue-100 text-blue-600"
            >
              <t-icon name="setting" />
            </div>
            <div>
              <div class="max-w-xs truncate font-bold" :title="row.name">
                {{ row.name }}
              </div>
              <div v-if="row.goodsId" class="text-xs text-gray-500">ID: {{ row.goodsId }}</div>
            </div>
          </div>
        </template>

        <template #taskType="{ row }">
          <t-tag v-if="row.taskType === 1" theme="warning" variant="light">站内倒卖</t-tag>
          <t-tag v-else-if="row.taskType === 2" theme="primary" variant="light">分类同步</t-tag>
          <t-tag v-else-if="row.taskType === 3" theme="primary" variant="light">商品同步</t-tag>
          <t-tag v-else-if="row.taskType === 4" theme="primary" variant="light">印花同步</t-tag>
          <t-tag v-else-if="row.taskType === 5" theme="primary" variant="light">分类商品同步</t-tag>
          <t-tag v-else theme="primary" variant="light">炼金扫货</t-tag>
        </template>

        <template #target="{ row }">
          <div v-if="row.taskType === 1">
            <div class="text-xs text-gray-500">最小利润:</div>
            <div class="font-bold text-orange-600">¥{{ row.minProfit }}</div>
          </div>
          <div v-else-if="row.taskType >= 2">
            <div class="text-xs text-gray-500">系统自动执行</div>
          </div>
          <div v-else>
            <div class="text-xs text-gray-500">最高价格: ¥{{ row.maxPrice }}</div>
            <div class="text-xs text-gray-500">
              磨损: {{ row.minPaintwear }}-{{ row.maxPaintwear }}
            </div>
          </div>
        </template>

        <template #progress="{ row }">
          <template v-if="row.runMode === 'TRADE'">
            <span class="text-gray-400">-</span>
          </template>
          <template v-else>
            <span v-if="row.taskType < 2">{{ row.successCount }} / {{ row.buyCount }}</span>
            <span v-else>-</span>
          </template>
        </template>

        <template #accounts="{ row }">
          <div v-if="row.accountNames && row.accountNames.length > 0" class="flex flex-wrap gap-1">
            <t-tag
              v-for="name in row.accountNames"
              :key="name"
              theme="primary"
              variant="light"
              size="small"
              class="rounded"
            >
              {{ name }}
            </t-tag>
          </div>
          <t-tag v-else theme="warning" variant="light" size="small" class="rounded">
            <template #icon><t-icon name="view-module" /></template>
            仅监控
          </t-tag>
        </template>

        <template #status="{ row }">
          <!-- 下单模式语义化展示 -->
          <template v-if="row.runMode === 'TRADE'">
            <t-tag v-if="row.status === 0" theme="default">停止</t-tag>
            <t-tag v-else-if="row.status === 1" theme="success">运行中</t-tag>
            <t-tag v-else-if="row.status === 4" theme="warning" variant="light">
              <template #icon><t-loading size="small" inherit-color /></template>
              监听中
            </t-tag>
            <t-tag v-else-if="row.status === 2" theme="primary">已完成</t-tag>
            <t-tag v-else theme="danger">异常</t-tag>
          </template>
          <!-- 其他模式展示 -->
          <template v-else>
            <t-tag v-if="row.status === 0" theme="default">停止</t-tag>
            <t-tag v-else-if="row.status === 1" theme="success">运行中</t-tag>
            <t-tag v-else-if="row.status === 4" theme="warning">
              {{ !row.accountNames || row.accountNames.length === 0 ? "监控中" : "执行中" }}
            </t-tag>
            <t-tag v-else-if="row.status === 2" theme="primary">已完成</t-tag>
            <t-tag v-else theme="danger">异常</t-tag>
          </template>
        </template>

        <template #op="{ row }">
          <div class="flex items-center justify-center space-x-2">
            <t-link v-if="![1, 4].includes(row.status)" theme="primary" @click="handleEdit(row)">
              编辑
            </t-link>
            <t-link v-else theme="primary" disabled>编辑</t-link>

            <t-popconfirm
              v-if="[0, 2, 3].includes(row.status)"
              :content="
                !row.accountNames || row.accountNames.length === 0
                  ? '当前任务未配置下单账号，将以“仅监控”模式启动，确定吗？'
                  : '确定要启动任务吗？'
              "
              @confirm="handleStatus(row, 1)"
            >
              <t-link
                :theme="!row.accountNames || row.accountNames.length === 0 ? 'warning' : 'success'"
              >
                启动
              </t-link>
            </t-popconfirm>
            <t-popconfirm
              v-if="[1, 4].includes(row.status)"
              content="确定要停止任务吗？"
              @confirm="handleStatus(row, 0)"
            >
              <t-link theme="warning">停止</t-link>
            </t-popconfirm>
            <t-popconfirm
              v-if="![2, 3, 4].includes(row.taskType)"
              content="确定要删除任务吗？"
              @confirm="handleDelete(row)"
            >
              <t-link theme="danger">删除</t-link>
            </t-popconfirm>
          </div>
        </template>
      </t-table>
    </t-card>

    <!-- 配置对话框 -->
    <TaskConfig ref="configRef" dialog-only @success="fetchData" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from "vue";
import { taskApi } from "@/api/task";
import type { BuffScanTask, TaskQueryParam } from "@/types/task";
import { MessagePlugin, type PrimaryTableCol } from "tdesign-vue-next";
import TaskConfig from "./TaskConfig.vue";

// 用户信息
const userInfo = computed(() => {
  const info = localStorage.getItem("niro-user-info");
  return info ? JSON.parse(info) : null;
});
const isAdmin = computed(() => userInfo.value?.id === 1);

// 状态
const activeTab = ref("SCAN");
const loading = ref(false);
const dataList = ref<BuffScanTask[]>([]);
const configRef = ref();

const queryParams = reactive<TaskQueryParam>({
  page: 1,
  pageSize: 10,
  keyword: "",
  status: undefined,
  runMode: "SCAN",
});

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
});

const columns: PrimaryTableCol[] = [
  { colKey: "id", title: "ID", width: 80, align: "left" },
  { colKey: "goods", title: "商品信息", width: 220, cell: "goods", align: "left" },
  { colKey: "taskType", title: "模式", width: 100, cell: "taskType", align: "left" },
  { colKey: "target", title: "目标配置", width: 150, cell: "target", align: "left" },
  { colKey: "accounts", title: "执行账号", width: 150, cell: "accounts", align: "left" },
  { colKey: "progress", title: "进度", width: 100, cell: "progress", align: "left" },
  { colKey: "status", title: "状态", width: 120, cell: "status", align: "left" },
  { colKey: "op", title: "操作", width: 180, cell: "op", fixed: "right", align: "center" },
];

// 方法
const fetchData = async () => {
  loading.value = true;
  try {
    // 根据 Tab 调整 runMode
    if (activeTab.value === "SYSTEM") {
      queryParams.runMode = undefined;
      queryParams.taskTypes = [2, 3, 4, 5];
    } else {
      queryParams.runMode = activeTab.value as any;
      queryParams.taskTypes = [0, 1];
    }

    const res = await taskApi.getPage(queryParams);
    dataList.value = res.records;
    pagination.total = res.total;
  } catch (error) {
    console.error("获取任务列表失败", error);
  } finally {
    loading.value = false;
  }
};

const handleTabChange = (val: any) => {
  activeTab.value = val;
  queryParams.page = 1;
  fetchData();
};

const onPageChange = (pageInfo: any) => {
  queryParams.page = pageInfo.current;
  queryParams.pageSize = pageInfo.pageSize;
  pagination.current = pageInfo.current;
  pagination.pageSize = pageInfo.pageSize;
  fetchData();
};

const resetQuery = () => {
  queryParams.keyword = "";
  queryParams.status = undefined;
  queryParams.page = 1;
  fetchData();
};

const handleAdd = () => {
  configRef.value?.handleAdd(activeTab.value);
};

const handleAddSystem = () => {
  configRef.value?.handleAddSystem();
};

const handleEdit = (row: BuffScanTask) => {
  configRef.value?.handleEdit(row);
};

const handleStatus = async (row: BuffScanTask, status: number) => {
  try {
    await taskApi.updateStatus(row.id, status);
    MessagePlugin.success(status === 1 ? "启动成功" : "停止成功");
    fetchData();
  } catch (error) {
    console.error("更新状态失败", error);
  }
};

const handleDelete = async (row: BuffScanTask) => {
  try {
    await taskApi.delete(row.id);
    MessagePlugin.success("删除成功");
    fetchData();
  } catch (error) {
    console.error("删除任务失败", error);
  }
};

onMounted(() => {
  fetchData();
});
</script>
