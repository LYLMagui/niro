<template>
  <div class="flex h-full min-h-0 flex-col px-1 pt-1 pb-2">
    <section class="flex min-h-0 flex-1 flex-col overflow-hidden rounded-[1px] bg-white">
      <div
        ref="taskListBodyRef"
        :class="[
          'task-list-body relative flex min-h-0 flex-1 flex-col overflow-x-hidden',
          isMobile ? 'overflow-y-auto' : 'overflow-hidden',
        ]"
      >
        <div class="px-4 pt-3">
          <div class="jsh-filter-layout flex flex-wrap items-center gap-x-6 gap-y-3">
            <div class="jsh-filter-item flex items-center">
              <span class="jsh-label">任务关键词：</span>
              <t-input
                v-model="queryParams.keyword"
                placeholder="请输入任务/商品关键词"
                clearable
                class="jsh-filter-input !h-8"
                @enter="fetchData"
              />
            </div>
            <div v-if="showAdvancedFilters" class="jsh-filter-item flex items-center">
              <span class="jsh-label">任务状态：</span>
              <t-select
                v-model="queryParams.status"
                placeholder="请选择任务状态"
                clearable
                class="jsh-filter-select !h-8"
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
                  :label="TaskStatusMap[TaskStatusEnum.SYSTEM_RUNNING].label"
                  :value="TaskStatusEnum.SYSTEM_RUNNING"
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
            <div v-if="canViewTaskList" class="jsh-filter-actions flex items-center gap-2">
              <t-button theme="primary" class="!h-8 px-4" @click="fetchData">查询</t-button>
              <t-button variant="outline" theme="default" class="!h-8 px-4" @click="resetQuery">
                重置
              </t-button>
              <a class="jsh-expand-link" @click="toggleAdvancedFilters">
                {{ showAdvancedFilters ? "收起" : "展开" }}
              </a>
            </div>
          </div>
        </div>

        <div class="mt-3 px-4 pt-2">
          <div class="jsh-toolbar flex flex-wrap items-start justify-between gap-y-3">
            <div class="table-operator flex flex-wrap items-center" :class="{ 'table-operator--mobile': isMobile }">
              <template v-if="canViewTaskList">
                <t-button
                  theme="primary"
                  class="jsh-action-btn jsh-action-btn--primary !h-8"
                  @click="handleAdd"
                >
                  新增任务
                </t-button>

                <t-popconfirm content="确认批量启动选中任务吗？" @confirm="handleBatchStart">
                  <t-button
                    variant="outline"
                    theme="default"
                    class="jsh-action-btn !h-8"
                    :disabled="selectedRowKeys.length === 0"
                  >
                    批量启动
                  </t-button>
                </t-popconfirm>

                <t-popconfirm content="确认批量停止选中任务吗？" @confirm="handleBatchStop">
                  <t-button
                    variant="outline"
                    theme="default"
                    class="jsh-action-btn !h-8"
                    :disabled="selectedRowKeys.length === 0"
                  >
                    批量停止
                  </t-button>
                </t-popconfirm>

                <t-popconfirm content="确认批量删除选中任务吗？" @confirm="handleBatchDelete">
                  <t-button
                    variant="outline"
                    theme="default"
                    class="jsh-action-btn !h-8"
                    :disabled="selectedRowKeys.length === 0"
                  >
                    批量删除
                  </t-button>
                </t-popconfirm>
              </template>
            </div>

            <div
              class="text-xs text-[#909399]"
              :class="isMobile ? 'task-selection-summary' : 'flex items-center gap-2'"
            >
              <span>提示：批量操作仅处理当前页勾选数据</span>
              <t-tag theme="primary" variant="light" class="rounded-[2px]">
                已选择 {{ selectedRowKeys.length }} 项
              </t-tag>
              <t-button
                variant="outline"
                theme="default"
                class="jsh-action-btn !h-8"
                :disabled="selectedRowKeys.length === 0"
                @click="clearSelection"
              >
                清空勾选
              </t-button>
            </div>
          </div>
        </div>

        <div class="task-list-main relative min-h-0 flex-1 px-4 pt-3 pb-4">
          <div v-if="!isMobile" class="relative h-full min-h-0 overflow-hidden">
            <t-table
              row-key="id"
              :data="dataList"
              :columns="columns"
              :loading="loading"
              :pagination="pagination"
              :selected-row-keys="selectedRowKeys"
              select-on-row-click
              hover
              :class="[
                'jsh-ledger-table',
                { 'jsh-ledger-table--empty': !loading && dataList.length === 0 },
              ]"
              @page-change="onPageChange"
              @select-change="handleSelectChange"
            >
              <template #empty>
                <div class="jsh-ledger-empty">
                  <t-empty description="暂无任务数据" />
                </div>
              </template>
              <template #goods="{ row }">
                <div class="flex items-center">
                  <t-image
                    v-if="row.goodsIconUrl"
                    :src="row.goodsIconUrl"
                    class="mr-2 h-9 w-9 rounded"
                  />
                  <div
                    v-else-if="row.taskType >= TaskTypeEnum.SYNC_CATEGORY"
                    class="mr-2 flex h-9 w-9 items-center justify-center rounded bg-blue-100 text-blue-600"
                  >
                    <t-icon name="setting" />
                  </div>
                  <div>
                    <div class="max-w-xs truncate font-medium" :title="row.name">
                      {{ row.name }}
                    </div>
                    <div v-if="row.goodsId" class="text-xs text-gray-500">
                      ID: {{ row.goodsId }}
                    </div>
                  </div>
                </div>
              </template>

              <template #target="{ row }">
                <div>
                  <div class="text-xs text-gray-500">最高价格: ¥{{ row.maxPrice }}</div>
                  <div class="text-xs text-gray-500">
                    磨损: {{ row.minPaintwear }}-{{ row.maxPaintwear }}
                  </div>
                </div>
              </template>

              <template #progress="{ row }">
                <span>{{ row.successCount }} / {{ row.buyCount }}</span>
              </template>

              <template #status="{ row }">
                <t-tag :theme="getStatusMeta(row.status).theme">{{ getStatusMeta(row.status).label }}</t-tag>
              </template>

              <template #createTime="{ row }">
                {{ formatDateTime(row.createTime) }}
              </template>

              <template #finishTime="{ row }">
                {{ formatDateTime(row.finishTime) }}
              </template>

              <template #op-header>
                <div class="w-full text-center">操作</div>
              </template>

              <template #op="{ row }">
                <div
                  v-permission="PermissionConstant.TASK_C5_LIST"
                  class="flex items-center gap-2"
                >
                  <t-link
                    v-if="![1, 4].includes(row.status)"
                    theme="primary"
                    @click="handleEdit(row)"
                  >
                    编辑
                  </t-link>
                  <t-link v-else theme="primary" disabled>编辑</t-link>

                  <t-popconfirm
                    v-if="[0, 3].includes(row.status)"
                    content="确认要启动任务吗？"
                    @confirm="handleStatus(row, 1)"
                  >
                    <t-link theme="success">启动</t-link>
                  </t-popconfirm>

                  <t-popconfirm
                    v-if="[1, 4].includes(row.status)"
                    content="确认要停止任务吗？"
                    @confirm="handleStatus(row, 0)"
                  >
                    <t-link theme="warning">停止</t-link>
                  </t-popconfirm>

                  <t-popconfirm
                    v-if="![2, 3, 4].includes(row.taskType)"
                    content="确认要删除任务吗？"
                    @confirm="handleDelete(row)"
                  >
                    <t-link theme="danger">删除</t-link>
                  </t-popconfirm>
                </div>
              </template>
            </t-table>
          </div>

          <div v-else class="task-mobile min-h-0">
            <div v-if="loading" class="task-mobile__empty text-sm text-[#909399]">加载中...</div>
            <div v-else-if="dataList.length === 0" class="task-mobile__empty">
              <t-empty description="暂无任务数据" />
            </div>
            <div v-else class="task-mobile__list">
              <div v-for="row in dataList" :key="row.id" class="task-mobile-card">
                <div class="task-mobile-card__header">
                  <div class="task-mobile-card__goods">
                    <t-checkbox
                      :checked="selectedRowKeys.includes(row.id)"
                      @change="(checked) => handleMobileSelectChange(row.id, checked)"
                    />
                    <t-image
                      v-if="row.goodsIconUrl"
                      :src="row.goodsIconUrl"
                      class="task-mobile-card__thumb"
                    />
                    <div
                      v-else-if="row.taskType >= TaskTypeEnum.SYNC_CATEGORY"
                      class="task-mobile-card__thumb task-mobile-card__thumb--fallback"
                    >
                      <t-icon name="setting" />
                    </div>
                    <div class="min-w-0 flex-1">
                      <div class="truncate text-sm font-medium text-[#303133]" :title="row.name">
                        {{ row.name }}
                      </div>
                      <div v-if="row.goodsId" class="mt-1 text-xs text-[#909399]">ID: {{ row.goodsId }}</div>
                    </div>
                  </div>
                  <t-tag :theme="getStatusMeta(row.status).theme" variant="light">
                    {{ getStatusMeta(row.status).label }}
                  </t-tag>
                </div>

                <div class="task-mobile-card__meta">
                  <div class="task-mobile-card__meta-item">
                    <span class="task-mobile-card__meta-label">最高价格</span>
                    <span>¥{{ row.maxPrice ?? "-" }}</span>
                  </div>
                  <div class="task-mobile-card__meta-item">
                    <span class="task-mobile-card__meta-label">进度</span>
                    <span>{{ row.successCount }} / {{ row.buyCount }}</span>
                  </div>
                  <div class="task-mobile-card__meta-item task-mobile-card__meta-item--full">
                    <span class="task-mobile-card__meta-label">磨损范围</span>
                    <span>{{ row.minPaintwear ?? 0 }} - {{ row.maxPaintwear ?? 1 }}</span>
                  </div>
                  <div class="task-mobile-card__meta-item task-mobile-card__meta-item--full">
                    <span class="task-mobile-card__meta-label">创建时间</span>
                    <span>{{ formatDateTime(row.createTime) }}</span>
                  </div>
                </div>

                <div class="task-mobile-card__actions" v-permission="PermissionConstant.TASK_C5_LIST">
                  <t-button
                    variant="outline"
                    theme="primary"
                    size="small"
                    :disabled="[1, 4].includes(row.status)"
                    @click="handleEdit(row)"
                  >
                    编辑
                  </t-button>
                  <t-popconfirm
                    v-if="[0, 3].includes(row.status)"
                    content="确认要启动任务吗？"
                    @confirm="handleStatus(row, 1)"
                  >
                    <t-button variant="outline" theme="success" size="small">启动</t-button>
                  </t-popconfirm>
                  <t-popconfirm
                    v-if="[1, 4].includes(row.status)"
                    content="确认要停止任务吗？"
                    @confirm="handleStatus(row, 0)"
                  >
                    <t-button variant="outline" theme="warning" size="small">停止</t-button>
                  </t-popconfirm>
                  <t-popconfirm
                    v-if="![2, 3, 4].includes(row.taskType)"
                    content="确认要删除任务吗？"
                    @confirm="handleDelete(row)"
                  >
                    <t-button variant="outline" theme="danger" size="small">删除</t-button>
                  </t-popconfirm>
                </div>
              </div>
            </div>

            <div v-if="!loading && pagination.total > 0" class="task-mobile__pagination">
              <t-pagination
                size="small"
                theme="simple"
                :current="pagination.current"
                :page-size="pagination.pageSize"
                :total="pagination.total"
                :show-page-size="false"
                :total-content="false"
                @change="onPageChange"
              />
            </div>
          </div>
        </div>

        <TaskConfig
          ref="configRef"
          :overlay-attach="taskListBodyRef"
          dialog-only
          :dialog-compact="true"
          @success="fetchData"
        />
      </div>
    </section>
  </div>
</template>
<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import { useWindowSize } from "@vueuse/core";
import dayjs from "dayjs";
import { taskApi } from "@/api/task";
import type { TaskItem, TaskQueryParam } from "@/types/task";
import { MessagePlugin, type PrimaryTableCol, type TagProps } from "tdesign-vue-next";
import TaskConfig from "./TaskConfig.vue";
import { PlatformEnum } from "@/enums/PlatformEnum";
import { TaskStatusEnum, TaskStatusMap } from "@/enums/TaskStatusEnum";
import { TaskTypeEnum } from "@/enums/TaskTypeEnum";
import { PermissionConstant } from "@/constant/PermissionConstant";
import { usePermission } from "@/hooks/usePermission";

const { hasPermission } = usePermission();

const canViewTaskList = computed(() => hasPermission(PermissionConstant.TASK_C5_LIST));
const currentPlatform = computed(() => PlatformEnum.C5);
const { width } = useWindowSize();
const isMobile = computed(() => width.value <= 768);

const loading = ref(false);
const dataList = ref<TaskItem[]>([]);
const selectedRowKeys = ref<(string | number)[]>([]);
const showAdvancedFilters = ref(!isMobile.value);
const configRef = ref();
const taskListBodyRef = ref<HTMLElement | null>(null);

const queryParams = reactive<TaskQueryParam>({
  page: 1,
  pageSize: 10,
  keyword: "",
  status: undefined,
  runMode: "BOTH" as any,
  platform: PlatformEnum.C5,
});

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
});

const getStatusMeta = (
  status: number
): { label: string; theme: NonNullable<TagProps["theme"]> } => {
  if (status === TaskStatusEnum.STOPPED) return { label: "停止", theme: "default" };
  if (status === TaskStatusEnum.RUNNING) return { label: "运行中", theme: "success" };
  if (status === TaskStatusEnum.SYSTEM_RUNNING) return { label: "执行中", theme: "warning" };
  if (status === TaskStatusEnum.COMPLETED) return { label: "已完成", theme: "primary" };
  return { label: "异常", theme: "danger" };
};

const formatDateTime = (value?: string) =>
  value ? dayjs(value).format("YYYY-MM-DD HH:mm:ss") : "-";

const patchTaskInList = (id: number, patch: Partial<TaskItem>) => {
  const index = dataList.value.findIndex((item) => Number(item.id) === Number(id));
  if (index === -1) {
    return;
  }
  dataList.value[index] = {
    ...dataList.value[index],
    ...patch,
  };
};

const patchTaskStatusInList = (id: number, status: number) => {
  const current = dataList.value.find((item) => Number(item.id) === Number(id));
  if (!current) {
    return;
  }

  patchTaskInList(id, {
    status,
    updateTime: dayjs().format("YYYY-MM-DD HH:mm:ss"),
    finishTime: status === TaskStatusEnum.RUNNING ? undefined : current.finishTime,
  });
};

const columns = computed<PrimaryTableCol[]>(() => [
  { colKey: "row-select", type: "multiple", width: 56, fixed: "left" as any },
  { colKey: "id", title: "ID", width: 80, align: "left" },
  { colKey: "goods", title: "商品信息", width: 220, cell: "goods", align: "left" as any },
  { colKey: "target", title: "目标配置", width: 150, cell: "target", align: "left" as any },
  { colKey: "progress", title: "进度", width: 100, cell: "progress", align: "left" as any },
  {
    colKey: "createTime",
    title: "创建时间",
    width: 170,
    cell: "createTime",
    align: "left" as any,
  },
  {
    colKey: "finishTime",
    title: "完成时间",
    width: 170,
    cell: "finishTime",
    align: "left" as any,
  },
  { colKey: "status", title: "状态", width: 120, cell: "status", align: "left" as any },
  { colKey: "op", title: "操作", width: 180, cell: "op", fixed: "right", align: "left" as any },
]);

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
    queryParams.runMode = "BOTH";
    queryParams.taskTypes = [TaskTypeEnum.SNIPING];
    queryParams.platform = PlatformEnum.C5;

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

const onPageChange = (pageInfo: any) => {
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
  selectedRowKeys.value = [];
  fetchData();
};

const handleAdd = () => {
  if (!canViewTaskList.value) {
    MessagePlugin.warning("当前账号没有任务管理权限");
    return;
  }
  configRef.value?.handleAdd("BOTH", PlatformEnum.C5);
};

const handleEdit = (row: TaskItem) => {
  configRef.value?.handleEdit(row, currentPlatform.value);
};

const handleStatus = async (row: TaskItem, status: number) => {
  try {
    await taskApi.updateStatus(row.id, status, row.platform);
    patchTaskStatusInList(row.id, status);
    MessagePlugin.success(status === TaskStatusEnum.RUNNING ? "启动成功" : "停止成功");
  } catch (error) {
    console.error("更新状态失败", error);
  }
};

const handleDelete = async (row: TaskItem) => {
  try {
    await taskApi.delete(row.id);
    MessagePlugin.success("删除成功");
    fetchData();
  } catch (error) {
    console.error("删除任务失败", error);
  }
};

const handleSelectChange = (value: (string | number)[]) => {
  selectedRowKeys.value = value;
};

const handleMobileSelectChange = (id: number, checked: boolean) => {
  const key = Number(id);
  if (checked) {
    selectedRowKeys.value = Array.from(new Set([...selectedRowKeys.value, key]));
    return;
  }
  selectedRowKeys.value = selectedRowKeys.value.filter((item) => Number(item) !== key);
};

const clearSelection = () => {
  selectedRowKeys.value = [];
};

const toggleAdvancedFilters = () => {
  showAdvancedFilters.value = !showAdvancedFilters.value;
};

const handleBatchStart = async () => {
  const pendingTasks = selectedTasks.value.filter((row) => [0, 3].includes(row.status));
  if (pendingTasks.length === 0) {
    MessagePlugin.warning("未找到可启动的任务，请检查所选数据状态");
    return;
  }

  try {
    await Promise.all(pendingTasks.map((row) => taskApi.updateStatus(row.id, 1, row.platform)));
    pendingTasks.forEach((row) => patchTaskStatusInList(row.id, TaskStatusEnum.RUNNING));
    MessagePlugin.success(`已批量启动 ${pendingTasks.length} 个任务`);
    selectedRowKeys.value = [];
  } catch (error) {
    console.error("批量启动任务失败", error);
  }
};

const handleBatchStop = async () => {
  const runningTasks = selectedTasks.value.filter((row) => [1, 4].includes(row.status));
  if (runningTasks.length === 0) {
    MessagePlugin.warning("未找到可停止的任务，请检查所选数据状态");
    return;
  }

  try {
    await Promise.all(runningTasks.map((row) => taskApi.updateStatus(row.id, 0, row.platform)));
    runningTasks.forEach((row) => patchTaskStatusInList(row.id, TaskStatusEnum.STOPPED));
    MessagePlugin.success(`已批量停止 ${runningTasks.length} 个任务`);
    selectedRowKeys.value = [];
  } catch (error) {
    console.error("批量停止任务失败", error);
  }
};

const handleBatchDelete = async () => {
  const deletableTasks = selectedTasks.value.filter((row) => ![2, 3, 4].includes(row.taskType));
  if (deletableTasks.length === 0) {
    MessagePlugin.warning("未找到可删除的任务，请检查所选任务类型");
    return;
  }

  try {
    await Promise.all(deletableTasks.map((row) => taskApi.delete(row.id)));
    MessagePlugin.success(`已批量删除 ${deletableTasks.length} 个任务`);
    selectedRowKeys.value = [];
    fetchData();
  } catch (error) {
    console.error("批量删除任务失败", error);
  }
};

watch(isMobile, (mobile) => {
  showAdvancedFilters.value = !mobile;
});

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
.jsh-filter-layout {
  row-gap: 12px;
}

.jsh-filter-item {
  flex-shrink: 0;
}

.jsh-label {
  width: 96px;
  padding-right: 10px;
  color: #303133;
  font-size: 13px;
  line-height: 32px;
  text-align: right;
  white-space: nowrap;
}

.jsh-expand-link {
  padding: 0 4px;
  color: rgb(24, 144, 255);
  line-height: 32px;
  user-select: none;
}

.table-operator :deep(.t-button) {
  margin: 0 8px 8px 0;
}

.task-selection-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.jsh-ledger-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100%;
}

.task-mobile {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 12px;
}

.task-mobile__list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.task-mobile__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 240px;
  color: #909399;
}

.task-mobile-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
  padding: 12px;
}

.task-mobile-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.task-mobile-card__goods {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: flex-start;
  gap: 10px;
}

.task-mobile-card__thumb {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: 6px;
}

.task-mobile-card__thumb--fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #dbeafe;
  background: #eff6ff;
  color: #2563eb;
}

.task-mobile-card__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 12px;
  margin-top: 12px;
}

.task-mobile-card__meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  font-size: 12px;
  color: #303133;
}

.task-mobile-card__meta-item--full {
  grid-column: 1 / -1;
}

.task-mobile-card__meta-label {
  color: #909399;
}

.task-mobile-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.task-mobile__pagination {
  display: flex;
  justify-content: center;
  padding: 4px 0 8px;
}

:deep(.jsh-ledger-table--empty .t-table__empty) {
  height: 100%;
}

@media (max-width: 768px) {
  .task-list-body {
    overscroll-behavior: contain;
    -webkit-overflow-scrolling: touch;
  }

  .jsh-filter-layout {
    gap: 12px;
  }

  .jsh-filter-item {
    display: flex;
    flex-direction: column;
    width: 100%;
    align-items: stretch;
  }

  .jsh-filter-item:deep(.t-input),
  .jsh-filter-item:deep(.t-select) {
    width: 100%;
  }

  .jsh-label {
    width: 100%;
    padding-right: 0;
    margin-bottom: 6px;
    line-height: 1.5;
    text-align: left;
  }

  .jsh-filter-actions {
    width: 100%;
  }

  .table-operator--mobile {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
    width: 100%;
  }

  .table-operator--mobile :deep(.t-button) {
    min-width: 0;
    width: 100%;
    height: 34px !important;
    margin: 0;
    padding: 0 8px;
    font-size: 13px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .task-selection-summary {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 10px 12px;
    width: 100%;
  }

  .task-selection-summary :deep(.t-tag) {
    margin-right: 2px;
  }

  .jsh-filter-input,
  .jsh-filter-select {
    width: 100%;
  }

  .task-selection-summary :deep(.t-button) {
    min-width: 0;
    height: 32px !important;
    padding: 0 10px;
    font-size: 13px;
    white-space: nowrap;
  }

  .task-list-main {
    flex: none;
    padding-right: 0;
    padding-left: 0;
    padding-bottom: 16px;
  }
}

@media (max-width: 640px) {
  .task-mobile-card {
    padding: 10px;
  }

  .task-mobile-card__header {
    flex-direction: column;
    align-items: stretch;
  }

  .task-mobile-card__meta {
    grid-template-columns: 1fr;
  }

  .task-mobile-card__actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .task-mobile-card__actions :deep(.t-button) {
    min-width: 0;
    width: 100%;
    height: 32px;
    padding: 0 8px;
    font-size: 12px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}
</style>
