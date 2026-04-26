<template>
  <PageFrame
    :is-mobile="isMobile"
    body-class="c5-sniping-account-body"
    desktop-outer-class="!p-0"
    desktop-content-class="px-4 pt-0 pb-0"
    mobile-content-class="px-3 pt-3 pb-0"
  >
    <section class="overflow-hidden bg-white">
      <div class="flex flex-col gap-3 bg-white px-0 py-4">
        <div
          class="grid grid-cols-1 gap-3 xl:grid-cols-[minmax(0,280px)_minmax(0,200px)_auto] xl:items-end"
        >
          <label class="flex min-w-0 flex-col gap-1.5">
            <span class="text-sm font-medium text-slate-700">账号名称</span>
            <t-input
              v-model="queryParams.keyword"
              placeholder="搜索账号名称"
              clearable
              class="w-full"
              @enter="fetchData"
            />
          </label>
          <label class="flex min-w-0 flex-col gap-1.5">
            <span class="text-sm font-medium text-slate-700">状态</span>
            <t-select
              v-model="queryParams.status"
              :options="statusOptions"
              clearable
              placeholder="请选择状态"
              class="w-full"
            />
          </label>
          <div class="flex flex-wrap items-center gap-2 xl:justify-end">
            <t-button theme="primary" class="c5-sniping-account-action-btn" @click="fetchData">
              查询
            </t-button>
            <t-button
              variant="outline"
              theme="default"
              class="c5-sniping-account-action-btn"
              @click="resetQuery"
            >
              重置
            </t-button>
          </div>
        </div>

        <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div class="flex flex-wrap items-center gap-2">
            <t-button
              v-if="canCreateAccount"
              theme="primary"
              class="c5-sniping-account-action-btn"
              @click="openCreateDialog"
            >
              新增扫货账号
            </t-button>
          </div>
        </div>
      </div>
    </section>

    <div class="relative min-h-0 flex-1">
      <div class="relative flex h-full min-h-0 flex-col overflow-hidden bg-white">
        <div class="min-h-0 flex-1 overflow-hidden">
          <t-table
            row-key="id"
            :data="pagedAccounts"
            :columns="columns"
            :loading="listLoading"
            :pagination="undefined"
            hover
            class="c5-sniping-account-table w-full bg-white"
          >
            <template #empty>
              <div class="py-8">
                <t-empty description="暂无扫货账号" />
              </div>
            </template>

            <template #accountInfo="{ row }">
              <div class="min-w-0">
                <t-tooltip :content="row.accountName" placement="top-left">
                  <div class="truncate font-medium text-slate-800">
                    {{ row.accountName }}
                  </div>
                </t-tooltip>
                <t-tooltip v-if="row.remark" :content="row.remark" placement="top-left">
                  <div class="mt-1 truncate text-xs text-slate-500">
                    {{ row.remark }}
                  </div>
                </t-tooltip>
              </div>
            </template>

            <template #c5AppKey="{ row }">
              <t-tooltip :content="row.c5AppKey || '未配置'" placement="top-left">
                <div class="max-w-[180px] truncate font-mono text-xs text-slate-600">
                  {{ row.c5AppKey || "-" }}
                </div>
              </t-tooltip>
            </template>

            <template #steamTradeUrl="{ row }">
              <t-tooltip :content="row.steamTradeUrl || '未配置'" placement="top-left">
                <div class="max-w-[220px] truncate font-mono text-xs text-slate-600">
                  {{ row.steamTradeUrl || "-" }}
                </div>
              </t-tooltip>
            </template>

            <template #metrics="{ row }">
              <div class="flex flex-col gap-1 py-1">
                <div class="flex items-center justify-between text-[13px]">
                  <span class="text-slate-500">今日扫描</span>
                  <span class="font-medium text-slate-700">{{ row.todayScanCount ?? 0 }}</span>
                </div>
                <div class="flex items-center justify-between text-[13px]">
                  <span class="text-slate-500">成功次数</span>
                  <span class="font-medium text-slate-700 text-emerald-600">{{ row.tradeSuccessCount ?? 0 }}</span>
                </div>
                <div class="flex items-center justify-between text-[13px]">
                  <span class="text-slate-500">成功率</span>
                  <span class="font-medium text-slate-700">{{ formatPercent(row.tradeSuccessRate) }}</span>
                </div>
              </div>
            </template>

            <template #balance="{ row }">
              <div class="flex flex-col gap-1 py-1">
                <div class="flex items-center justify-between text-[13px]">
                  <span class="text-slate-500">可用余额</span>
                  <span class="font-bold text-emerald-600">{{ formatCurrency(row.balance) }}</span>
                </div>
                <div class="flex items-center justify-between text-[13px]">
                  <span class="text-slate-500">待结算</span>
                  <span class="font-medium text-slate-700">{{ formatCurrency(row.pendingBalance) }}</span>
                </div>
              </div>
            </template>

            <template #op="{ row }">
              <div class="flex flex-wrap gap-1.5">
                <t-button
                  v-if="canUpdateAccount"
                  variant="outline"
                  theme="default"
                  class="c5-sniping-account-table__action-btn"
                  @click="openEditDialog(row)"
                >
                  编辑
                </t-button>
                <t-button
                  v-if="canReadAccountDetail"
                  variant="outline"
                  theme="primary"
                  class="c5-sniping-account-table__action-btn"
                  @click="openDetailDialog(row)"
                >
                  详情
                </t-button>
                <t-button
                  v-if="canDeleteAccount && row.boundTaskId"
                  variant="outline"
                  theme="danger"
                  class="c5-sniping-account-table__action-btn"
                  disabled
                >
                  删除
                </t-button>
                <t-popconfirm v-else-if="canDeleteAccount" content="确定删除该账号吗？" @confirm="onDeleteAccount(row)">
                  <t-button
                    variant="outline"
                    theme="danger"
                    class="c5-sniping-account-table__action-btn"
                  >
                    删除
                  </t-button>
                </t-popconfirm>
              </div>
            </template>
          </t-table>
        </div>

        <div class="border-t border-slate-200 px-4 py-3">
          <t-pagination
            v-model:current="pagination.current"
            v-model:pageSize="pagination.pageSize"
            :total="filteredAccounts.length"
            :page-size-options="pageSizeOptions"
            @change="handlePageChange"
          />
        </div>
      </div>
    </div>

    <t-drawer
      v-model:visible="detailDialogVisible"
      :header="detailDialogTitle"
      :footer="false"
      :close-btn="true"
      size="min(760px, 100vw)"
      placement="right"
      @close="resetDetailDialog"
    >
      <div class="account-detail-drawer__body">
        <div class="mb-4 rounded border border-slate-100 bg-slate-50 px-4 py-3">
          <div class="text-sm font-medium text-slate-800">
            {{ selectedAccount?.accountName || "-" }}
          </div>
          <div class="mt-1 text-xs text-slate-500">绑定任务：{{ detailPagination.total }} 个</div>
        </div>

        <t-table
          row-key="id"
          :data="detailTasks"
          :columns="detailTaskColumns"
          :loading="detailLoading"
          :pagination="undefined"
          hover
          class="w-full bg-white"
        >
          <template #empty>
            <div class="py-8">
              <t-empty description="暂无绑定任务" />
            </div>
          </template>

          <template #detailTaskInfo="{ row }">
            <div class="min-w-0">
              <t-tooltip :content="getDetailTaskTitle(row)" placement="top-left">
                <div class="truncate font-medium text-slate-800">
                  {{ getDetailTaskTitle(row) }}
                </div>
              </t-tooltip>
              <t-tooltip v-if="row.marketHashName" :content="row.marketHashName" placement="top-left">
                <div class="mt-1 truncate text-xs text-slate-400">
                  {{ row.marketHashName }}
                </div>
              </t-tooltip>
            </div>
          </template>

          <template #detailTaskStatus="{ row }">
            <div class="flex items-center justify-center">
              <span
                :class="[
                  'mr-2 h-2 w-2 rounded-full ring-2 ring-white',
                  getTaskStatusMeta(row.taskStatus).dotClass,
                ]"
              ></span>
              <t-tag
                :theme="getTaskStatusMeta(row.taskStatus).theme"
                variant="light-outline"
                size="small"
                class="rounded-[4px] px-2 font-medium"
              >
                {{ getTaskStatusMeta(row.taskStatus).label }}
              </t-tag>
            </div>
          </template>

          <template #detailTaskProgress="{ row }">
            <div class="text-sm text-slate-600">
              {{ formatTaskProgress(row) }}
            </div>
          </template>
        </t-table>

        <div
          v-if="detailPagination.total > detailPagination.pageSize"
          class="mt-3 flex justify-end"
        >
          <t-pagination
            v-model:current="detailPagination.current"
            v-model:pageSize="detailPagination.pageSize"
            :total="detailPagination.total"
            :page-size-options="detailPageSizeOptions"
            @change="handleDetailPageChange"
          />
        </div>
      </div>
    </t-drawer>

    <AppDialog
      v-model:visible="accountDialogVisible"
      :title="accountDialogTitle"
      width="520px"
      @close="resetAccountForm"
    >
      <t-form
        ref="accountFormRef"
        :data="accountFormData"
        :rules="accountRules"
        label-width="100px"
        label-align="right"
        class="compact-form"
        @submit="onAccountSubmit"
      >
        <div class="form-container">
          <t-form-item label="账号名称" name="accountName">
            <t-input
              v-model="accountFormData.accountName"
              placeholder="如：扫货主账号"
              @blur="() => trimStringField(accountFormData, 'accountName')"
            />
          </t-form-item>

          <t-form-item label="C5 AppKey" name="c5AppKey">
            <t-input
              v-model="accountFormData.c5AppKey"
              placeholder="请输入该账号使用的 C5 AppKey"
              @blur="() => trimStringField(accountFormData, 'c5AppKey')"
            />
          </t-form-item>

          <t-form-item label="Steam 链接" name="steamTradeUrl">
            <t-input
              v-model="accountFormData.steamTradeUrl"
              placeholder="请输入该账号使用的 Steam 交易链接"
              @blur="() => trimStringField(accountFormData, 'steamTradeUrl')"
            />
          </t-form-item>

          <t-form-item label="备注" name="remark">
            <t-input
              v-model="accountFormData.remark"
              placeholder="可选备注信息"
              @blur="() => trimStringField(accountFormData, 'remark')"
            />
          </t-form-item>
        </div>

        <div class="form-footer">
          <t-button variant="outline" theme="default" @click="accountDialogVisible = false">
            取消
          </t-button>
          <t-button theme="primary" type="submit" :loading="accountSubmitLoading" :disabled="!canSubmitAccountForm">提交</t-button>
        </div>
      </t-form>
    </AppDialog>
  </PageFrame>
</template>

<script setup lang="ts">
import { useWindowSize } from "@vueuse/core";
import { computed, onMounted, reactive, ref, watch } from "vue";
import {
  MessagePlugin,
  type FormRule,
  type PageInfo,
  type PrimaryTableCol,
  type SubmitContext,
  type TagProps,
} from "tdesign-vue-next";
import { c5SnipingAccountApi } from "@/api/c5-sniping-account";
import { c5SnipingV2Api } from "@/api/c5-sniping-v2";
import PageFrame from "@/components/PageFrame.vue";
import AppDialog from "@/components/AppDialog.vue";
import { BuffAccountStatusMap } from "@/enums/BuffAccountStatusEnum";
import { PermissionConstant } from "@/constant/PermissionConstant";
import useNewPermission from "@/hooks/useNewPermission";
import type { C5SnipingAccount, C5SnipingAccountStatus } from "@/types/c5-sniping-account";
import type { C5SnipingTaskV2Item } from "@/types/c5-sniping-v2";

const { hasButtonPermission } = useNewPermission();
const { width } = useWindowSize();
const isMobile = computed(() => width.value <= 768);
const canCreateAccount = computed(() => hasButtonPermission(PermissionConstant.C5_SNIPING_ACCOUNT_CREATE));
const canUpdateAccount = computed(() => hasButtonPermission(PermissionConstant.C5_SNIPING_ACCOUNT_UPDATE));
const canDeleteAccount = computed(() => hasButtonPermission(PermissionConstant.C5_SNIPING_ACCOUNT_DELETE));
const canReadAccountDetail = computed(() => hasButtonPermission(PermissionConstant.C5_SNIPING_ACCOUNT_DETAIL));

const listLoading = ref(false);
const accounts = ref<C5SnipingAccount[]>([]);
const queryParams = reactive<{ keyword: string; status?: C5SnipingAccountStatus }>({
  keyword: "",
  status: undefined,
});
const pagination = reactive({ current: 1, pageSize: 10 });
const pageSizeOptions = [10, 20, 50];
const accountDialogVisible = ref(false);
const accountDialogTitle = ref("新增扫货账号");
const accountSubmitLoading = ref(false);
const detailDialogVisible = ref(false);
const detailLoading = ref(false);
const selectedAccount = ref<C5SnipingAccount | null>(null);
const detailTasks = ref<C5SnipingTaskV2Item[]>([]);
const detailPagination = reactive({ current: 1, pageSize: 5, total: 0 });
const detailPageSizeOptions = [5, 10, 20];
const detailDialogTitle = computed(() => `${selectedAccount.value?.accountName || "账号"}详情`);
const canSubmitAccountForm = computed(() =>
  accountFormData.id ? canUpdateAccount.value : canCreateAccount.value
);

const priceFormatter = new Intl.NumberFormat("zh-CN", {
  style: "currency",
  currency: "CNY",
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

const statusOptions = Object.entries(BuffAccountStatusMap).map(([value, meta]) => ({
  label: meta.label,
  value,
}));

const accountFormData = reactive<C5SnipingAccount>({
  accountName: "",
  c5AppKey: "",
  steamTradeUrl: "",
  status: "NORMAL",
  balance: 0,
  pendingBalance: 0,
  remark: "",
});

const accountRules: Record<string, FormRule[]> = {
  accountName: [{ required: true, message: "账号名称不能为空", type: "error" }],
  c5AppKey: [{ required: true, message: "C5 AppKey 不能为空", type: "error" }],
  steamTradeUrl: [{ required: true, message: "Steam 交易链接不能为空", type: "error" }],
};

const tableHeaderClass =
  "!bg-slate-50 !text-slate-500 !text-sm !font-semibold !tracking-[0.06em] uppercase whitespace-nowrap";
const tableBodyClass = "!py-2 text-sm text-slate-700 align-middle";

const detailTaskColumns = computed<PrimaryTableCol<C5SnipingTaskV2Item>[]>(() => [
  {
    colKey: "detailTaskInfo",
    title: "任务信息",
    width: 280,
    cell: "detailTaskInfo",
    className: tableBodyClass,
    thClassName: tableHeaderClass,
  },
  {
    colKey: "detailTaskStatus",
    title: "任务状态",
    width: 140,
    align: "center",
    cell: "detailTaskStatus",
    className: tableBodyClass,
    thClassName: tableHeaderClass,
  },
  {
    colKey: "detailTaskProgress",
    title: "执行进度",
    width: 140,
    cell: "detailTaskProgress",
    className: tableBodyClass,
    thClassName: tableHeaderClass,
  },
]);

const columns = computed(() => [
  {
    colKey: "accountInfo",
    title: "账号信息",
    width: 220,
    cell: "accountInfo",
    fixed: "left" as const,
    className: tableBodyClass,
    thClassName: tableHeaderClass,
  },
  {
    colKey: "c5AppKey",
    title: "AppKey",
    width: 220,
    cell: "c5AppKey",
    className: tableBodyClass,
    thClassName: tableHeaderClass,
  },
  {
    colKey: "steamTradeUrl",
    title: "Steam 交易链接",
    width: 260,
    cell: "steamTradeUrl",
    className: tableBodyClass,
    thClassName: tableHeaderClass,
  },
  {
    colKey: "metrics",
    title: "实时统计",
    width: 170,
    cell: "metrics",
    className: tableBodyClass,
    thClassName: tableHeaderClass,
  },
  {
    colKey: "balance",
    title: "余额",
    width: 180,
    cell: "balance",
    className: tableBodyClass,
    thClassName: tableHeaderClass,
  },
  {
    colKey: "op",
    title: "操作",
    width: 220,
    cell: "op",
    fixed: "right" as const,
    className: `${tableBodyClass} !bg-white`,
    thClassName: tableHeaderClass,
  },
]);

const filteredAccounts = computed(() => {
  const keyword = queryParams.keyword.trim().toLowerCase();
  return accounts.value.filter((account) => {
    const matchesKeyword =
      !keyword ||
      account.accountName.toLowerCase().includes(keyword) ||
      account.remark?.toLowerCase().includes(keyword) ||
      account.boundTaskName?.toLowerCase().includes(keyword);
    const matchesStatus = !queryParams.status || account.status === queryParams.status;
    return matchesKeyword && matchesStatus;
  });
});

const pagedAccounts = computed(() => {
  const start = (pagination.current - 1) * pagination.pageSize;
  return filteredAccounts.value.slice(start, start + pagination.pageSize);
});

watch(
  () => [queryParams.keyword, queryParams.status],
  () => {
    pagination.current = 1;
  }
);

watch(filteredAccounts, (list) => {
  const maxPage = Math.max(1, Math.ceil(list.length / pagination.pageSize));
  if (pagination.current > maxPage) {
    pagination.current = maxPage;
  }
});

const trimStringField = <T extends Record<string, unknown>, K extends keyof T>(
  target: T,
  key: K
) => {
  const value = target[key];
  if (typeof value === "string") {
    target[key] = value.replace(/[\r\n]/g, "").trim() as T[K];
  }
};

const formatCurrency = (value?: number) =>
  value === undefined || value === null ? "-" : priceFormatter.format(value);
const formatPercent = (value?: number) => `${((value || 0) * 100).toFixed(1)}%`;
const getDetailTaskTitle = (row: C5SnipingTaskV2Item) =>
  row.name || row.goodsDisplayName || row.marketHashName || `任务 #${row.id}`;
const getTaskStatusMeta = (status?: string) => {
  const map: Record<string, { label: string; theme: TagProps["theme"]; dotClass: string }> = {
    DRAFT: { label: "待开启", theme: "default", dotClass: "bg-slate-400" },
    READY: { label: "待运行", theme: "primary", dotClass: "bg-blue-400" },
    RUNNING: {
      label: "扫描中",
      theme: "success",
      dotClass: "bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.3)]",
    },
    STOPPED: { label: "已停止", theme: "warning", dotClass: "bg-amber-400" },
    COMPLETED: { label: "已完成", theme: "success", dotClass: "bg-emerald-600" },
    ERROR: { label: "运行异常", theme: "danger", dotClass: "bg-rose-500 animate-pulse" },
  };
  return status
    ? map[status] || { label: status, theme: "default", dotClass: "bg-slate-300" }
    : { label: "未知", theme: "default", dotClass: "bg-slate-300" };
};
const formatTaskProgress = (row: C5SnipingTaskV2Item) => {
  if (row.stopMode === "BUY_COUNT" && row.targetBuyCount) {
    return `${row.successBuyCount ?? 0}/${row.targetBuyCount}`;
  }
  return `已买 ${row.successBuyCount ?? 0}`;
};

const fetchAccounts = async () => {
  accounts.value = (await c5SnipingAccountApi.getAccounts()) || [];
};

const loadData = async () => {
  listLoading.value = true;
  try {
    await fetchAccounts();
  } finally {
    listLoading.value = false;
  }
};

const fetchData = async () => {
  pagination.current = 1;
  await loadData();
};

const resetQuery = async () => {
  queryParams.keyword = "";
  queryParams.status = undefined;
  pagination.current = 1;
  await loadData();
};

const resetAccountForm = () => {
  Object.assign(accountFormData, {
    id: undefined,
    userId: undefined,
    accountName: "",
    c5AppKey: "",
    steamTradeUrl: "",
    status: "NORMAL",
    balance: 0,
    pendingBalance: 0,
    lastCheckTime: undefined,
    remark: "",
    warningMsg: undefined,
    todayScanCount: undefined,
    tradeSuccessCount: undefined,
    tradeTotalCount: undefined,
    tradeSuccessRate: undefined,
    boundTaskId: undefined,
    boundTaskName: undefined,
    createTime: undefined,
    updateTime: undefined,
  });
};

const openCreateDialog = () => {
  if (!canCreateAccount.value) {
    return;
  }
  resetAccountForm();
  accountDialogTitle.value = "新增扫货账号";
  accountDialogVisible.value = true;
};

const resetDetailDialog = () => {
  selectedAccount.value = null;
  detailTasks.value = [];
  detailPagination.current = 1;
  detailPagination.total = 0;
};

const fetchDetailTasks = async () => {
  if (!selectedAccount.value?.id) {
    return;
  }
  detailLoading.value = true;
  try {
    const res = await c5SnipingV2Api.getPage({
      page: detailPagination.current,
      pageSize: detailPagination.pageSize,
      accountId: selectedAccount.value.id,
    });
    detailTasks.value = res.records || [];
    detailPagination.total = res.total || 0;
    detailPagination.current = res.current || detailPagination.current;
    detailPagination.pageSize = res.size || detailPagination.pageSize;
  } finally {
    detailLoading.value = false;
  }
};

const openDetailDialog = async (row: C5SnipingAccount) => {
  if (!canReadAccountDetail.value) {
    return;
  }
  selectedAccount.value = row;
  detailPagination.current = 1;
  detailPagination.total = 0;
  detailTasks.value = [];
  detailDialogVisible.value = true;
  await fetchDetailTasks();
};

const openEditDialog = (row: C5SnipingAccount) => {
  if (!canUpdateAccount.value) {
    return;
  }
  resetAccountForm();
  Object.assign(accountFormData, row);
  accountDialogTitle.value = "编辑扫货账号";
  accountDialogVisible.value = true;
};

const onAccountSubmit = async (context: SubmitContext) => {
  if (!canSubmitAccountForm.value) {
    return;
  }
  if (context.validateResult !== true) {
    return;
  }
  accountSubmitLoading.value = true;
  try {
    trimStringField(accountFormData, "accountName");
    trimStringField(accountFormData, "c5AppKey");
    trimStringField(accountFormData, "steamTradeUrl");
    trimStringField(accountFormData, "remark");
    await c5SnipingAccountApi.saveAccount({
      id: accountFormData.id,
      accountName: accountFormData.accountName,
      c5AppKey: accountFormData.c5AppKey,
      steamTradeUrl: accountFormData.steamTradeUrl,
      remark: accountFormData.remark,
    });
    MessagePlugin.success(`${accountFormData.id ? "账号已更新" : "账号已创建"}`);
    accountDialogVisible.value = false;
    await loadData();
  } finally {
    accountSubmitLoading.value = false;
  }
};

const onDeleteAccount = async (row: C5SnipingAccount) => {
  if (!canDeleteAccount.value) {
    return;
  }
  if (!row.id) {
    return;
  }
  if (row.boundTaskId) {
    MessagePlugin.warning(`账号已绑定任务【${row.boundTaskName}】，无法删除`);
    return;
  }
  await c5SnipingAccountApi.deleteAccount(row.id);
  MessagePlugin.success("账号已删除");
  await loadData();
};

const handleDetailPageChange = async (pageInfo: PageInfo) => {
  detailPagination.current = pageInfo.current;
  detailPagination.pageSize = pageInfo.pageSize;
  await fetchDetailTasks();
};

const handlePageChange = (pageInfo: PageInfo) => {
  pagination.current = pageInfo.current;
  pagination.pageSize = pageInfo.pageSize;
};

onMounted(async () => {
  await loadData();
});
</script>

<style scoped>
:deep(.c5-sniping-account-action-btn.t-button) {
  min-width: 88px;
  border-radius: 4px;
  box-shadow: none;
}

:deep(.c5-sniping-account-table .t-table__header th) {
  padding-top: 10px;
  padding-bottom: 10px;
}

:deep(.c5-sniping-account-table .t-table__body td) {
  padding-top: 8px;
  padding-bottom: 8px;
}

:deep(.c5-sniping-account-table__action-btn.t-button) {
  min-width: 58px;
  padding-right: 10px;
  padding-left: 10px;
  border-radius: 4px;
  box-shadow: none;
}

.account-detail-drawer__body {
  min-height: 100%;
  padding: 20px 24px;
  background: #fff;
}

.form-container {
  max-height: min(640px, calc(100vh - 260px));
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 20px 24px;
  background: #fff;
}

:deep(.compact-form .t-form__item) {
  margin-bottom: 16px;
}

:deep(.compact-form .t-form__item.t-is-error) {
  margin-bottom: 28px;
}

:deep(.compact-form .t-form__item .t-form__controls-content) {
  display: flex;
  flex-direction: column;
  align-items: stretch;
}

:deep(.compact-form .t-form__label) {
  padding-right: 12px !important;
}

:deep(.compact-form .t-form__item .t-form__verify-message) {
  position: relative !important;
  display: block !important;
  min-height: auto !important;
  margin-top: 6px;
  margin-bottom: 0;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 768px) {
  .account-detail-drawer__body,
  .form-container {
    padding: 16px;
  }

  :deep(.compact-form .t-form__item) {
    margin-bottom: 14px;
  }

  :deep(.compact-form .t-form__label) {
    width: 88px !important;
    padding-right: 8px;
  }
}

@media (max-width: 640px) {
  :deep(.compact-form .t-form__item) {
    display: flex;
    flex-direction: column;
    align-items: stretch;
  }

  :deep(.compact-form .t-form__label) {
    width: 100% !important;
    min-width: 0 !important;
    padding-right: 0;
    margin-bottom: 8px;
    text-align: left;
    line-height: 1.5;
  }

  :deep(.compact-form .t-form__controls) {
    width: 100%;
    margin-left: 0 !important;
  }
}
</style>
