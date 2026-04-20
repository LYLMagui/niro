<template>
  <PageFrame
    :is-mobile="isMobile"
    :on-body-ref-change="handleTaskListBodyRefChange"
    body-class="task-list-body"
    desktop-content-class="px-4 pt-3 pb-4"
    mobile-content-class="px-3 pt-3 pb-0"
  >
    <section class="overflow-hidden rounded-md border border-slate-200 bg-white shadow-sm">
      <div class="flex flex-col gap-3 bg-slate-50/70 px-4 py-3">
        <div
          :class="[
            'jsh-filter-layout grid grid-cols-1 gap-3 xl:items-end',
            showAdvancedFilters
              ? 'xl:grid-cols-[minmax(0,280px)_minmax(0,220px)_auto]'
              : 'xl:grid-cols-[minmax(0,280px)_auto]',
          ]"
        >
          <label class="jsh-filter-item flex min-w-0 flex-col gap-1.5">
            <span class="jsh-label text-sm font-medium text-slate-700">任务关键词</span>
            <t-input
              v-model="queryParams.keyword"
              placeholder="请输入任务/商品关键词"
              clearable
              class="jsh-filter-input"
              :class="taskToolbarFieldClass"
              @enter="fetchData"
            />
          </label>
          <label v-if="showAdvancedFilters" class="jsh-filter-item flex min-w-0 flex-col gap-1.5">
            <span class="jsh-label text-sm font-medium text-slate-700">任务状态</span>
            <t-select
              v-model="queryParams.status"
              placeholder="请选择任务状态"
              clearable
              class="jsh-filter-select"
              :class="taskToolbarFieldClass"
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
                :label="TaskStatusMap[TaskStatusEnum.SCHEDULED].label"
                :value="TaskStatusEnum.SCHEDULED"
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
          </label>
          <div
            v-if="canViewTaskList"
            :class="[
              'jsh-filter-actions flex flex-wrap items-center gap-2',
              showAdvancedFilters ? 'xl:justify-end' : 'xl:justify-start',
            ]"
          >
            <t-button
              theme="primary"
              class="jsh-action-btn"
              @click="fetchData"
            >
              查询
            </t-button>
            <t-button
              variant="outline"
              theme="default"
              class="jsh-action-btn"
              @click="resetQuery"
            >
              重置
            </t-button>
            <button type="button" class="jsh-expand-link" @click="toggleAdvancedFilters">
              {{ showAdvancedFilters ? "收起" : "展开" }}
            </button>
          </div>
        </div>

        <div
          class="jsh-toolbar flex flex-col gap-3 border-t border-slate-200 pt-3 lg:flex-row lg:items-center lg:justify-between"
        >
          <div
            class="table-operator flex flex-wrap items-center gap-2"
            :class="{ 'table-operator--mobile': isMobile }"
          >
            <template v-if="canViewTaskList">
              <t-button
                theme="primary"
                class="jsh-action-btn jsh-action-btn--primary"
                @click="handleAdd"
              >
                新增任务
              </t-button>

              <t-popconfirm content="确认批量启动选中任务吗？" @confirm="handleBatchStart">
                <t-button
                  variant="outline"
                  theme="default"
                  class="jsh-action-btn"
                  :disabled="selectedRowKeys.length === 0"
                >
                  批量启动
                </t-button>
              </t-popconfirm>

              <t-popconfirm content="确认批量停止选中任务吗？" @confirm="handleBatchStop">
                <t-button
                  variant="outline"
                  theme="default"
                  class="jsh-action-btn"
                  :disabled="selectedRowKeys.length === 0"
                >
                  批量停止
                </t-button>
              </t-popconfirm>

              <t-popconfirm content="确认批量删除选中任务吗？" @confirm="handleBatchDelete">
                <t-button
                  variant="outline"
                  theme="default"
                  class="jsh-action-btn"
                  :disabled="selectedRowKeys.length === 0"
                >
                  批量删除
                </t-button>
              </t-popconfirm>
            </template>
          </div>

          <div
            class="text-xs text-slate-500"
            :class="isMobile ? 'task-selection-summary' : 'flex items-center gap-2.5'"
          >
            <t-tag theme="primary" variant="light" class="rounded-[2px]">
              已选择 {{ selectedRowKeys.length }} 项
            </t-tag>
            <t-button
              variant="outline"
              theme="default"
              class="jsh-action-btn"
              :disabled="selectedRowKeys.length === 0"
              @click="clearSelection"
            >
              清空勾选
            </t-button>
          </div>
        </div>
      </div>
    </section>

    <div :class="['task-list-main relative min-h-0 flex-1 pt-3', isMobile ? 'pb-0' : 'pb-4']">
      <div
        v-if="!isMobile"
        class="relative flex h-full min-h-0 flex-col overflow-hidden rounded-md border border-slate-200 bg-white shadow-sm"
      >
        <div class="min-h-0 flex-1 overflow-hidden">
          <t-table
            row-key="id"
            :data="dataList"
            :columns="columns"
            :loading="loading"
            :pagination="undefined"
            :selected-row-keys="selectedRowKeys"
            select-on-row-click
            hover
            class="task-c5-table w-full bg-white"
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
                v-else-if="isSystemTask(row.taskType)"
                class="mr-2 flex h-9 w-9 items-center justify-center rounded bg-blue-100 text-blue-600"
              >
                <t-icon name="setting" />
              </div>
              <div>
                <div class="max-w-xs truncate font-medium" :title="row.goodsDisplayName || row.name">
                  {{ row.goodsDisplayName || row.name }}
                </div>
                <div v-if="row.cs2GoodsId" class="text-xs text-gray-500">
                  ID: {{ row.cs2GoodsId }}
                </div>
              </div>
            </div>
          </template>

          <template #target="{ row }">
            <div class="task-target">
              <div class="task-target__primary">
                <div class="task-target__metric">
                  <span class="task-target__label">最高价格：</span>
                  <t-tag
                    class="task-target__value task-target__value--price"
                    size="small"
                    variant="light"
                  >
                    {{ formatPrice(row.maxPrice) }}
                  </t-tag>
                </div>
                <div class="task-target__metric">
                  <span class="task-target__label">扫描频率：</span>
                  <t-tag class="task-target__value task-target__value--scan" size="small" variant="light">
                    {{ formatScanFrequency(row) }}
                  </t-tag>
                </div>
                <div class="task-target__metric">
                  <span class="task-target__label">磨损范围：</span>
                  <t-tag class="task-target__value task-target__value--wear" size="small" variant="light">
                    {{ formatPaintwear(row) }}
                  </t-tag>
                </div>
              </div>
            </div>
          </template>

          <template #progress="{ row }">
            <t-tag
              class="task-target__value task-target__value--progress"
              size="small"
              variant="light"
            >
              {{ row.successCount }} / {{ row.buyCount }}
            </t-tag>
          </template>

          <template #status="{ row }">
            <t-tag :theme="getStatusMeta(row.status).theme">
              {{ getStatusMeta(row.status).label }}
            </t-tag>
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
              class="task-c5-table__actions flex flex-wrap gap-1.5"
            >
              <t-button
                v-if="!isActiveTaskStatus(row.status)"
                variant="outline"
                class="task-c5-table__action-btn"
                @click="handleEdit(row)"
              >
                编辑
              </t-button>
              <t-button
                v-else
                variant="outline"
                disabled
                class="task-c5-table__action-btn"
              >
                编辑
              </t-button>

              <t-button
                variant="outline"
                class="task-c5-table__action-btn"
                @click="handleCopy(row)"
              >
                复制
              </t-button>

              <t-popconfirm
                v-if="isStartableTaskStatus(row.status)"
                content="确认要启动任务吗？"
                @confirm="handleStatus(row, 1)"
              >
                <t-button
                  variant="outline"
                  theme="success"
                  class="task-c5-table__action-btn"
                >
                  启动
                </t-button>
              </t-popconfirm>

              <t-popconfirm
                v-if="isActiveTaskStatus(row.status)"
                content="确认要停止任务吗？"
                @confirm="handleStatus(row, 0)"
              >
                <t-button
                  variant="outline"
                  theme="warning"
                  class="task-c5-table__action-btn"
                >
                  停止
                </t-button>
              </t-popconfirm>

              <t-popconfirm
                v-if="isDeletableTaskType(row.taskType)"
                content="确认要删除任务吗？"
                @confirm="handleDelete(row)"
              >
                <t-button
                  variant="outline"
                  theme="danger"
                  class="task-c5-table__action-btn"
                >
                  删除
                </t-button>
              </t-popconfirm>
            </div>
          </template>
        </t-table>
        </div>

        <div
          v-if="pagination.total > 0"
          class="border-t border-slate-200 bg-white px-4 py-3"
        >
          <t-pagination
            :current="pagination.current"
            :page-size="pagination.pageSize"
            :total="pagination.total"
            @change="onPageChange"
          />
        </div>
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
                  v-else-if="isSystemTask(row.taskType)"
                  class="task-mobile-card__thumb task-mobile-card__thumb--fallback"
                >
                  <t-icon name="setting" />
                </div>
                <div class="min-w-0 flex-1">
                  <div class="truncate text-sm font-medium text-[#303133]" :title="row.goodsDisplayName || row.name">
                    {{ row.goodsDisplayName || row.name }}
                  </div>
                  <div v-if="row.cs2GoodsId" class="mt-1 text-xs text-[#909399]">
                    ID: {{ row.cs2GoodsId }}
                  </div>
                </div>
              </div>
              <t-tag :theme="getStatusMeta(row.status).theme" variant="light">
                {{ getStatusMeta(row.status).label }}
              </t-tag>
            </div>

            <div class="task-mobile-card__meta">
              <div class="task-mobile-card__meta-item">
                <span class="task-mobile-card__meta-label">最高价格：</span>
                <t-tag
                  class="task-mobile-card__meta-value task-mobile-card__meta-value--price"
                  size="small"
                  variant="light"
                >
                  {{ formatPrice(row.maxPrice) }}
                </t-tag>
              </div>
              <div class="task-mobile-card__meta-item">
                <span class="task-mobile-card__meta-label">扫描频率：</span>
                <t-tag class="task-mobile-card__meta-value" size="small" variant="light">
                  {{ formatScanFrequency(row) }}
                </t-tag>
              </div>
              <div class="task-mobile-card__meta-item">
                <span class="task-mobile-card__meta-label">进度：</span>
                <t-tag
                  class="task-mobile-card__meta-value task-mobile-card__meta-value--progress"
                  size="small"
                  variant="light"
                >
                  {{ row.successCount }} / {{ row.buyCount }}
                </t-tag>
              </div>
              <div class="task-mobile-card__meta-item">
                <span class="task-mobile-card__meta-label">磨损范围：</span>
                <t-tag class="task-mobile-card__meta-value" size="small" variant="light">
                  {{ formatPaintwear(row) }}
                </t-tag>
              </div>
              <div class="task-mobile-card__meta-item">
                <span class="task-mobile-card__meta-label">创建时间：</span>
                <t-tag class="task-mobile-card__meta-value" size="small" variant="light">
                  {{ formatDateTime(row.createTime) }}
                </t-tag>
              </div>
            </div>

            <div v-permission="PermissionConstant.TASK_C5_LIST" class="task-mobile-card__actions">
              <t-button
                variant="outline"
                theme="primary"
                :disabled="isActiveTaskStatus(row.status)"
                @click="handleEdit(row)"
              >
                编辑
              </t-button>
              <t-button variant="outline" theme="default" @click="handleCopy(row)">复制</t-button>
              <t-popconfirm
                v-if="isStartableTaskStatus(row.status)"
                content="确认要启动任务吗？"
                @confirm="handleStatus(row, 1)"
              >
                <t-button variant="outline" theme="success">启动</t-button>
              </t-popconfirm>
              <t-popconfirm
                v-if="isActiveTaskStatus(row.status)"
                content="确认要停止任务吗？"
                @confirm="handleStatus(row, 0)"
              >
                <t-button variant="outline" theme="warning">停止</t-button>
              </t-popconfirm>
              <t-popconfirm
                v-if="isDeletableTaskType(row.taskType)"
                content="确认要删除任务吗？"
                @confirm="handleDelete(row)"
              >
                <t-button variant="outline" theme="danger">删除</t-button>
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
  </PageFrame>
</template>
<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import { useWindowSize } from "@vueuse/core";
import dayjs from "dayjs";
import { taskApi } from "@/api/task";
import type { TaskItem, TaskQueryParam } from "@/types/task";
import { MessagePlugin, type PrimaryTableCol, type TagProps } from "tdesign-vue-next";
import PageFrame from "@/components/PageFrame.vue";
import TaskConfig from "./TaskConfig.vue";
import { PlatformEnum } from "@/enums/PlatformEnum";
import {
  isActiveTaskStatus,
  isStartableTaskStatus,
  TaskStatusEnum,
  TaskStatusMap,
} from "@/enums/TaskStatusEnum";
import { isSystemTask, TaskTypeEnum } from "@/enums/TaskTypeEnum";
import { PermissionConstant } from "@/constant/PermissionConstant";
import { usePermission } from "@/hooks/usePermission";

const { hasPermission } = usePermission();

const canViewTaskList = computed(() => hasPermission(PermissionConstant.TASK_C5_LIST));
const currentPlatform = PlatformEnum.C5;
const { width } = useWindowSize();
const isMobile = computed(() => width.value <= 768);

const loading = ref(false);
const dataList = ref<TaskItem[]>([]);
const selectedRowKeys = ref<(string | number)[]>([]);
const showAdvancedFilters = ref(!isMobile.value);
const configRef = ref<{
  handleAdd: (runMode?: "SCAN" | "TRADE" | "BOTH", platform?: string) => void;
  handleEdit: (row: TaskItem, platform?: string) => void;
  handleCopy: (row: TaskItem, platform?: string) => void;
} | null>(null);
const taskListBodyRef = ref<HTMLElement | null>(null);
const handleTaskListBodyRefChange = (element: HTMLElement | null) => {
  taskListBodyRef.value = element;
};

const queryParams = reactive<TaskQueryParam>({
  page: 1,
  pageSize: 10,
  keyword: "",
  status: undefined,
  runMode: "BOTH",
  platform: PlatformEnum.C5,
});

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  size: "small" as const,
});

const getStatusMeta = (
  status: number
): { label: string; theme: NonNullable<TagProps["theme"]> } => ({
  label: TaskStatusMap[status as keyof typeof TaskStatusMap]?.label ?? "异常",
  theme: (TaskStatusMap[status as keyof typeof TaskStatusMap]?.color ?? "danger") as NonNullable<
    TagProps["theme"]
  >,
});

const formatDateTime = (value?: string) =>
  value ? dayjs(value).format("YYYY-MM-DD HH:mm:ss") : "-";

const priceFormatter = new Intl.NumberFormat("zh-CN", {
  style: "currency",
  currency: "CNY",
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

const formatPrice = (value?: number) =>
  value === undefined || value === null ? "-" : priceFormatter.format(value);

const formatPaintwear = ({ minPaintwear, maxPaintwear }: TaskItem) =>
  `${minPaintwear ?? 0} - ${maxPaintwear ?? 1}`;

const formatScanFrequency = ({ scanInterval, scanIntervalMin, scanIntervalMax }: TaskItem) => {
  if (scanInterval !== undefined && scanInterval !== null) {
    return `${scanInterval}s`;
  }

  if (
    scanIntervalMin !== undefined &&
    scanIntervalMin !== null &&
    scanIntervalMax !== undefined &&
    scanIntervalMax !== null
  ) {
    return `${scanIntervalMin} - ${scanIntervalMax}s`;
  }

  return "-";
};

const DELETABLE_TASK_TYPES = [TaskTypeEnum.SNIPING, TaskTypeEnum.FLIPPING] as const;

const isDeletableTaskType = (taskType: number) => DELETABLE_TASK_TYPES.includes(taskType as any);

const taskTableHeaderClass =
  "!bg-slate-50 !text-slate-500 !text-sm !font-semibold !tracking-[0.06em] uppercase whitespace-nowrap";
const taskTableBodyClass = "!py-2 text-sm text-slate-700 align-middle";
const taskToolbarFieldClass =
  "w-full [&_.t-input__wrap]:min-h-10 [&_.t-input__wrap]:rounded-md [&_.t-input__wrap]:border-slate-200 [&_.t-input__wrap]:bg-white [&_.t-input__wrap]:shadow-none [&_.t-input__wrap:hover]:border-slate-300 [&_.t-is-focused]:border-sky-500 [&_.t-is-focused]:shadow-[0_0_0_3px_rgb(14_165_233_/_0.12)]";

const columns = computed<PrimaryTableCol[]>(() => [
  {
    colKey: "row-select",
    type: "multiple",
    width: 56,
    fixed: "left" as any,
    className: `${taskTableBodyClass} !bg-white`,
    thClassName: taskTableHeaderClass,
  },
  {
    colKey: "goods",
    title: "商品信息",
    width: 220,
    cell: "goods",
    align: "left" as any,
    className: taskTableBodyClass,
    thClassName: taskTableHeaderClass,
  },
  {
    colKey: "target",
    title: "目标配置",
    width: 190,
    cell: "target",
    align: "left" as any,
    className: taskTableBodyClass,
    thClassName: taskTableHeaderClass,
  },
  {
    colKey: "progress",
    title: "进度",
    width: 100,
    cell: "progress",
    align: "left" as any,
    className: taskTableBodyClass,
    thClassName: taskTableHeaderClass,
  },
  {
    colKey: "createTime",
    title: "创建时间",
    width: 170,
    cell: "createTime",
    align: "left" as any,
    className: taskTableBodyClass,
    thClassName: taskTableHeaderClass,
  },
  {
    colKey: "finishTime",
    title: "完成时间",
    width: 170,
    cell: "finishTime",
    align: "left" as any,
    className: taskTableBodyClass,
    thClassName: taskTableHeaderClass,
  },
  {
    colKey: "status",
    title: "状态",
    width: 120,
    cell: "status",
    align: "left" as any,
    className: taskTableBodyClass,
    thClassName: taskTableHeaderClass,
  },
  {
    colKey: "op",
    title: "操作",
    width: 220,
    cell: "op",
    fixed: "right",
    align: "left" as any,
    className: `${taskTableBodyClass} !bg-white`,
    thClassName: taskTableHeaderClass,
  },
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
  configRef.value?.handleEdit(row, currentPlatform);
};

const handleCopy = (row: TaskItem) => {
  configRef.value?.handleCopy(row, currentPlatform);
};

const handleStatus = async (row: TaskItem, status: number) => {
  try {
    await taskApi.updateStatus(row.id, status);
    await fetchData();

    const latestRow = dataList.value.find((item) => Number(item.id) === Number(row.id));
    const statusChanged = latestRow
      ? status === TaskStatusEnum.RUNNING
        ? isActiveTaskStatus(latestRow.status)
        : latestRow.status === TaskStatusEnum.STOPPED
      : false;

    if (!statusChanged) {
      MessagePlugin.error(
        status === TaskStatusEnum.RUNNING ? "启动失败，请刷新后重试" : "停止失败，请刷新后重试"
      );
      return;
    }

    MessagePlugin.success(status === TaskStatusEnum.RUNNING ? "启动成功" : "任务已停止");
  } catch (error) {
    console.error("更新状态失败", error);
    MessagePlugin.error(
      status === TaskStatusEnum.RUNNING ? "启动失败，请稍后重试" : "停止失败，请稍后重试"
    );
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
  const pendingTasks = selectedTasks.value.filter((row) => isStartableTaskStatus(row.status));
  if (pendingTasks.length === 0) {
    MessagePlugin.warning("未找到可启动的任务，请检查所选数据状态");
    return;
  }

  try {
    await Promise.all(pendingTasks.map((row) => taskApi.updateStatus(row.id, 1)));
    await fetchData();
    MessagePlugin.success(`已批量启动 ${pendingTasks.length} 个任务`);
    selectedRowKeys.value = [];
  } catch (error) {
    console.error("批量启动任务失败", error);
  }
};

const handleBatchStop = async () => {
  const runningTasks = selectedTasks.value.filter((row) => isActiveTaskStatus(row.status));
  if (runningTasks.length === 0) {
    MessagePlugin.warning("未找到可停止的任务，请检查所选数据状态");
    return;
  }

  try {
    await Promise.all(runningTasks.map((row) => taskApi.updateStatus(row.id, 0)));
    await fetchData();
    MessagePlugin.success(`已批量停止 ${runningTasks.length} 个任务`);
    selectedRowKeys.value = [];
  } catch (error) {
    console.error("批量停止任务失败", error);
  }
};

const handleBatchDelete = async () => {
  const deletableTasks = selectedTasks.value.filter((row) => isDeletableTaskType(row.taskType));
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
.jsh-expand-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
  padding: 0 4px;
  border: 0;
  background: transparent;
  color: rgb(71 85 105);
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
}

.jsh-expand-link:hover {
  color: rgb(15 23 42);
}

.table-operator :deep(.t-popup__reference) {
  display: inline-flex;
}

:deep(.jsh-action-btn.t-button) {
  min-width: 88px;
  border-radius: 4px;
  box-shadow: none;
}

.task-target__label {
  color: rgb(71 85 105);
}

.task-target__value {
  color: rgb(51 65 85);
}

.task-target__value--price {
  color: rgb(220 38 38);
}

.task-target__value--scan {
  color: rgb(37 99 235);
}

.task-target__value--wear {
  color: rgb(249 115 22);
}

.task-target__value--progress {
  color: rgb(30 64 175);
}

:deep(.task-c5-table .t-table__header th) {
  padding-top: 10px;
  padding-bottom: 10px;
}

:deep(.task-c5-table .t-table__body td) {
  padding-top: 8px;
  padding-bottom: 8px;
}

:deep(.task-c5-table .t-table) {
  border: none;
  border-radius: 0;
  box-shadow: none;
}

:deep(.task-c5-table .t-table__content) {
  border: none;
  border-radius: 0;
}

:deep(.task-c5-table .t-table__header) {
  overflow: visible;
  border-top-left-radius: 0;
  border-top-right-radius: 0;
}

:deep(.task-c5-table__actions .t-popup__reference) {
  display: inline-flex;
}

:deep(.task-c5-table__action-btn.t-button) {
  min-width: 64px;
  padding-right: 12px;
  padding-left: 12px;
  border-radius: 4px;
  box-shadow: none;
}

@media (max-width: 768px) {
  .task-list-body {
    overscroll-behavior: contain;
    -webkit-overflow-scrolling: touch;
  }

  .task-mobile__list {
    padding-right: 0;
    padding-left: 0;
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

  .table-operator--mobile > * {
    min-width: 0;
    width: 100%;
  }

  .table-operator--mobile :deep(.t-popup__reference) {
    display: block;
    width: 100%;
  }

  .table-operator--mobile :deep(.t-button) {
    min-width: 0;
    width: 100%;
    margin: 0;
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
    white-space: nowrap;
  }

  .task-list-main {
    flex: none;
    padding-right: 0;
    padding-left: 0;
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

  .task-mobile-card__meta-item {
    align-items: flex-start;
  }

  .task-mobile-card__meta-label {
    flex-basis: 52px;
  }

  .task-mobile-card__meta-value {
    max-width: calc(100% - 80px);
  }

  .task-mobile-card__actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .task-mobile-card__actions :deep(.t-button) {
    min-width: 0;
    width: 100%;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}
</style>
