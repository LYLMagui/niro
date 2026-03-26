<template>
  <div class="bg-[#f5f5f5] px-1 pb-2 pt-1">
    <section class="overflow-hidden border border-[#d9d9d9] bg-white">
      <t-tabs
        v-model="activeTab"
        class="jsh-tabs border-b border-[#e8e8e8] bg-white px-4"
        @change="handleTabChange"
      >
        <t-tab-panel
          :value="TaskRunModeEnum.SCAN"
          :label="currentPlatform === PlatformEnum.C5 ? '下单任务' : '扫货扫描'"
        />
        <t-tab-panel
          v-if="currentPlatform !== PlatformEnum.C5"
          :value="TaskRunModeEnum.TRADE"
          label="下单任务"
        />
      </t-tabs>

      <div class="px-4 pt-3">
        <div class="flex flex-wrap items-center gap-x-6 gap-y-3">
          <div class="flex items-center">
            <span class="jsh-label">任务关键词</span>
            <t-input
              v-model="queryParams.keyword"
              placeholder="请输入任务/商品关键词"
              clearable
              class="!h-8 w-[320px]"
              @enter="fetchData"
            />
          </div>
          <div v-permission="PermissionConstant.TASK_BUFF_LIST" class="flex items-center gap-2">
            <t-button theme="primary" class="!h-8 px-4" @click="fetchData">查询</t-button>
            <t-button theme="default" variant="base" class="!h-8 px-4" @click="resetQuery">
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
            <span class="jsh-label">任务状态</span>
            <t-select
              v-model="queryParams.status"
              placeholder="请选择任务状态"
              clearable
              class="!h-8 w-[240px]"
              @change="fetchData"
            >
              <t-option
                :label="TaskStatusMap[TaskStatusEnum.STOPPED].label"
                :value="TaskStatusEnum.STOPPED"
              />
              <t-option
                :label="TaskStatusMap[TaskStatusEnum.RUNNING].label"
                :value="TaskStatusEnum.RUNNING"
              />
              <t-option
                :label="TaskStatusMap[TaskStatusEnum.COMPLETED].label"
                :value="TaskStatusEnum.COMPLETED"
              />
              <t-option
                :label="TaskStatusMap[TaskStatusEnum.ERROR].label"
                :value="TaskStatusEnum.ERROR"
              />
            </t-select>
          </div>
          <div class="flex items-center">
            <span class="jsh-label">当前平台</span>
            <t-input :value="currentPlatform" readonly class="!h-8 w-[240px]" />
          </div>
        </div>
      </div>

      <div class="mt-[5px] border-t border-[#f2f2f2] px-4 pt-2">
        <div class="flex flex-wrap items-start justify-between gap-y-2">
          <div class="table-operator flex flex-wrap items-center">
            <t-button
              theme="primary"
              class="!h-8"
              v-permission="PermissionConstant.TASK_BUFF_LIST"
              @click="handleAdd"
            >
              新增任务
            </t-button>

            <t-popconfirm content="确认批量启动选中任务吗？" @confirm="handleBatchStart">
              <t-button
                variant="outline"
                theme="success"
                class="!h-8"
                v-permission="PermissionConstant.TASK_BUFF_LIST"
                :disabled="selectedRowKeys.length === 0"
              >
                批量启动
              </t-button>
            </t-popconfirm>

            <t-popconfirm content="确认批量停止选中任务吗？" @confirm="handleBatchStop">
              <t-button
                variant="outline"
                theme="warning"
                class="!h-8"
                v-permission="PermissionConstant.TASK_BUFF_LIST"
                :disabled="selectedRowKeys.length === 0"
              >
                批量停止
              </t-button>
            </t-popconfirm>

            <t-popconfirm content="确认批量删除选中任务吗？" @confirm="handleBatchDelete">
              <t-button
                variant="outline"
                theme="danger"
                class="!h-8"
                v-permission="PermissionConstant.TASK_BUFF_LIST"
                :disabled="selectedRowKeys.length === 0"
              >
                批量删除
              </t-button>
            </t-popconfirm>
          </div>

          <div class="mb-2 flex items-center gap-2 text-xs text-[#909399]">
            <span>提示：批量操作仅处理当前页勾选数据</span>
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
          class="jsh-ledger-table"
          @page-change="onPageChange"
          @select-change="handleSelectChange"
        >
          <template #goods="{ row }">
            <div class="flex items-center">
              <t-image v-if="row.goodsIconUrl" :src="row.goodsIconUrl" class="mr-2 h-9 w-9 rounded" />
              <div>
                <div class="max-w-xs truncate font-medium" :title="row.name">
                  {{ row.name }}
                </div>
                <div v-if="row.goodsId" class="text-xs text-gray-500">ID: {{ row.goodsId }}</div>
              </div>
            </div>
          </template>

          <template #taskType="{ row }">
            <t-tag v-if="row.taskType === TaskTypeEnum.FLIPPING" theme="warning" variant="light">
              站内倒卖
            </t-tag>
            <t-tag v-else theme="primary" variant="light">炼金扫货</t-tag>
          </template>

          <template #target="{ row }">
            <div v-if="row.taskType === TaskTypeEnum.FLIPPING">
              <div class="text-xs text-gray-500">最小利润</div>
              <div class="font-medium text-orange-600">¥{{ row.minProfit ?? 0 }}</div>
            </div>
            <div v-else>
              <div class="text-xs text-gray-500">最高价格: ¥{{ row.maxPrice ?? 0 }}</div>
              <div class="text-xs text-gray-500">
                磨损: {{ row.minPaintwear ?? 0 }}-{{ row.maxPaintwear ?? 1 }}
              </div>
            </div>
          </template>

          <template #progress="{ row }">
            <template v-if="row.runMode === TaskRunModeEnum.TRADE">
              <span class="text-gray-400">-</span>
            </template>
            <template v-else>
              <span>{{ row.successCount }} / {{ row.buyCount }}</span>
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
            <t-tag v-if="row.status === TaskStatusEnum.STOPPED" theme="default">停止</t-tag>
            <t-tag v-else-if="row.status === TaskStatusEnum.RUNNING" theme="success">运行中</t-tag>
            <t-tag v-else-if="row.status === TaskStatusEnum.COMPLETED" theme="primary">
              已完成
            </t-tag>
            <t-tag v-else-if="row.status === TaskStatusEnum.SYSTEM_RUNNING" theme="warning">
              {{ !row.accountNames || row.accountNames.length === 0 ? "监控中" : "执行中" }}
            </t-tag>
            <t-tag v-else theme="danger">异常</t-tag>
          </template>

          <template #createTime="{ row }">
            {{ row.createTime ? dayjs(row.createTime).format("YYYY-MM-DD HH:mm:ss") : "-" }}
          </template>

          <template #finishTime="{ row }">
            {{ row.finishTime ? dayjs(row.finishTime).format("YYYY-MM-DD HH:mm:ss") : "-" }}
          </template>

          <template #op-header>
            <div class="w-full text-center">操作</div>
          </template>

          <template #op="{ row }">
            <div v-permission="PermissionConstant.TASK_BUFF_LIST" class="flex items-center gap-2">
              <t-link
                v-if="![TaskStatusEnum.RUNNING, TaskStatusEnum.SYSTEM_RUNNING].includes(row.status)"
                theme="primary"
                @click="handleEdit(row)"
              >
                编辑
              </t-link>
              <t-link v-else theme="primary" disabled>编辑</t-link>

              <t-popconfirm
                v-if="[TaskStatusEnum.STOPPED, TaskStatusEnum.ERROR].includes(row.status)"
                :content="
                  !row.accountNames || row.accountNames.length === 0
                    ? '当前任务未配置下单账号，将以“仅监控”模式启动，确认吗？'
                    : '确认要启动任务吗？'
                "
                @confirm="handleStatus(row, TaskStatusEnum.RUNNING)"
              >
                <t-link :theme="!row.accountNames || row.accountNames.length === 0 ? 'warning' : 'success'">
                  启动
                </t-link>
              </t-popconfirm>

              <t-popconfirm
                v-if="[TaskStatusEnum.RUNNING, TaskStatusEnum.SYSTEM_RUNNING].includes(row.status)"
                content="确认要停止任务吗？"
                @confirm="handleStatus(row, TaskStatusEnum.STOPPED)"
              >
                <t-link theme="warning">停止</t-link>
              </t-popconfirm>

              <t-popconfirm content="确认要删除任务吗？" @confirm="handleDelete(row)">
                <t-link theme="danger">删除</t-link>
              </t-popconfirm>
            </div>
          </template>
        </t-table>
      </div>
    </section>

    <TaskConfig ref="configRef" dialog-only @success="fetchData" />
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import dayjs from "dayjs";
import { MessagePlugin, type PrimaryTableCol } from "tdesign-vue-next";
import type { TableRowData } from "tdesign-vue-next";
import { taskApi } from "@/api/task";
import type { BuffScanTask, TaskQueryParam } from "@/types/task";
import TaskConfig from "./TaskConfig.vue";
import { PlatformEnum } from "@/enums/PlatformEnum";
import { TaskStatusEnum, TaskStatusMap } from "@/enums/TaskStatusEnum";
import { TaskTypeEnum } from "@/enums/TaskTypeEnum";
import { TaskRunModeEnum } from "@/enums/TaskRunModeEnum";
import { PermissionConstant } from "@/constant/PermissionConstant";
import { usePermission } from "@/hooks/usePermission";

interface TaskConfigExpose {
  handleAdd: (defaultMode?: "SCAN" | "TRADE" | "BOTH", platform?: string) => void;
  handleEdit: (row: BuffScanTask, platform?: string) => void;
}

interface PageInfo {
  current: number;
  pageSize: number;
}

const route = useRoute();
const { hasPermission } = usePermission();

const canViewTaskList = computed(() => hasPermission(PermissionConstant.TASK_BUFF_LIST));
const currentPlatform = computed(() => {
  if (route.path.includes("/c5")) return PlatformEnum.C5;
  return (route.meta.platform as string) || PlatformEnum.BUFF;
});

const activeTab = ref<"SCAN" | "TRADE">(TaskRunModeEnum.SCAN);
const loading = ref(false);
const dataList = ref<BuffScanTask[]>([]);
const selectedRowKeys = ref<Array<string | number>>([]);
const showAdvancedFilters = ref(true);
const configRef = ref<TaskConfigExpose | null>(null);

const queryParams = reactive<TaskQueryParam>({
  page: 1,
  pageSize: 10,
  keyword: "",
  status: undefined,
  runMode: TaskRunModeEnum.SCAN,
});

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
});

const columns = computed<PrimaryTableCol<TableRowData>[]>(() => {
  const baseColumns: PrimaryTableCol<TableRowData>[] = [
    { colKey: "row-select", type: "multiple", width: 56, fixed: "left" as const },
    { colKey: "id", title: "ID", width: 80, align: "left" },
    { colKey: "goods", title: "商品信息", width: 220, cell: "goods", align: "left" },
  ];

  const taskTypeColumn: PrimaryTableCol<TableRowData>[] =
    currentPlatform.value !== PlatformEnum.C5
      ? [{ colKey: "taskType", title: "模式", width: 100, cell: "taskType", align: "left" }]
      : [];

  const accountColumns: PrimaryTableCol<TableRowData>[] =
    currentPlatform.value !== PlatformEnum.C5
      ? [{ colKey: "accounts", title: "执行账号", width: 150, cell: "accounts", align: "left" }]
      : [];

  return [
    ...baseColumns,
    ...taskTypeColumn,
    { colKey: "target", title: "目标配置", width: 150, cell: "target", align: "left" },
    ...accountColumns,
    { colKey: "progress", title: "进度", width: 100, cell: "progress", align: "left" },
    { colKey: "createTime", title: "创建时间", width: 170, cell: "createTime", align: "left" },
    { colKey: "finishTime", title: "完成时间", width: 170, cell: "finishTime", align: "left" },
    { colKey: "status", title: "状态", width: 120, cell: "status", align: "left" },
    { colKey: "op", title: "操作", width: 180, cell: "op", fixed: "right", align: "left" },
  ];
});

const selectedTasks = computed(() => {
  const keySet = new Set(selectedRowKeys.value.map((key) => Number(key)));
  return dataList.value.filter((item) => keySet.has(Number(item.id)));
});

const fetchData = async () => {
  if (!canViewTaskList.value) {
    dataList.value = [];
    pagination.total = 0;
    selectedRowKeys.value = [];
    return;
  }

  loading.value = true;
  try {
    queryParams.platform = currentPlatform.value;
    queryParams.taskTypes = [TaskTypeEnum.SNIPING, TaskTypeEnum.FLIPPING];
    queryParams.runMode =
      currentPlatform.value === PlatformEnum.C5 ? TaskRunModeEnum.BOTH : activeTab.value;

    const res = await taskApi.getPage(queryParams);
    dataList.value = res.records;
    pagination.total = res.total;
    selectedRowKeys.value = selectedRowKeys.value.filter((key) =>
      dataList.value.some((item) => Number(item.id) === Number(key))
    );
  } catch (error) {
    console.error("获取任务列表失败", error);
  } finally {
    loading.value = false;
  }
};

const handleTabChange = (val: string) => {
  activeTab.value = val as "SCAN" | "TRADE";
  queryParams.page = 1;
  selectedRowKeys.value = [];
  fetchData();
};

const onPageChange = (pageInfo: PageInfo) => {
  queryParams.page = pageInfo.current;
  queryParams.pageSize = pageInfo.pageSize;
  pagination.current = pageInfo.current;
  pagination.pageSize = pageInfo.pageSize;
  selectedRowKeys.value = [];
  fetchData();
};

const resetQuery = () => {
  queryParams.keyword = "";
  queryParams.status = undefined;
  queryParams.page = 1;
  pagination.current = 1;
  selectedRowKeys.value = [];
  fetchData();
};

const handleAdd = () => {
  if (!canViewTaskList.value) {
    MessagePlugin.warning("当前账号没有任务管理权限");
    return;
  }
  configRef.value?.handleAdd(activeTab.value, currentPlatform.value);
};

const handleEdit = (row: BuffScanTask) => {
  configRef.value?.handleEdit(row, currentPlatform.value);
};

const handleStatus = async (row: BuffScanTask, status: number) => {
  try {
    await taskApi.updateStatus(row.id, status, row.platform);
    MessagePlugin.success(status === TaskStatusEnum.RUNNING ? "启动成功" : "停止成功");
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

const handleSelectChange = (value: Array<string | number>) => {
  selectedRowKeys.value = value;
};

const clearSelection = () => {
  selectedRowKeys.value = [];
};

const toggleAdvancedFilters = () => {
  showAdvancedFilters.value = !showAdvancedFilters.value;
};

const handleBatchStart = async () => {
  const pendingTasks = selectedTasks.value.filter((row) =>
    [TaskStatusEnum.STOPPED, TaskStatusEnum.ERROR].includes(row.status)
  );
  if (pendingTasks.length === 0) {
    MessagePlugin.warning("未找到可启动的任务，请检查所选数据状态");
    return;
  }

  try {
    await Promise.all(
      pendingTasks.map((row) => taskApi.updateStatus(row.id, TaskStatusEnum.RUNNING, row.platform))
    );
    MessagePlugin.success(`已批量启动 ${pendingTasks.length} 个任务`);
    selectedRowKeys.value = [];
    fetchData();
  } catch (error) {
    console.error("批量启动任务失败", error);
  }
};

const handleBatchStop = async () => {
  const runningTasks = selectedTasks.value.filter((row) =>
    [TaskStatusEnum.RUNNING, TaskStatusEnum.SYSTEM_RUNNING].includes(row.status)
  );
  if (runningTasks.length === 0) {
    MessagePlugin.warning("未找到可停止的任务，请检查所选数据状态");
    return;
  }

  try {
    await Promise.all(
      runningTasks.map((row) => taskApi.updateStatus(row.id, TaskStatusEnum.STOPPED, row.platform))
    );
    MessagePlugin.success(`已批量停止 ${runningTasks.length} 个任务`);
    selectedRowKeys.value = [];
    fetchData();
  } catch (error) {
    console.error("批量停止任务失败", error);
  }
};

const handleBatchDelete = async () => {
  if (selectedTasks.value.length === 0) {
    MessagePlugin.warning("请先选择需要删除的任务");
    return;
  }

  try {
    await Promise.all(selectedTasks.value.map((row) => taskApi.delete(row.id)));
    MessagePlugin.success(`已批量删除 ${selectedTasks.value.length} 个任务`);
    selectedRowKeys.value = [];
    fetchData();
  } catch (error) {
    console.error("批量删除任务失败", error);
  }
};

watch(
  () => currentPlatform.value,
  (val) => {
    if (val === PlatformEnum.C5) {
      activeTab.value = TaskRunModeEnum.SCAN;
    }
    queryParams.page = 1;
    pagination.current = 1;
    selectedRowKeys.value = [];
    fetchData();
  }
);

watch(
  canViewTaskList,
  (allowed) => {
    if (allowed) {
      fetchData();
      return;
    }
    dataList.value = [];
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
  background: #fafcff !important;
}
</style>
