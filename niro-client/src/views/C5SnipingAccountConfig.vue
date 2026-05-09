<template>
  <PageFrame
    :is-mobile="isMobile"
    body-class="c5-sniping-account-body"
    desktop-outer-class="!p-0"
    desktop-content-class="px-4 pt-0 pb-0"
    mobile-content-class="px-3 pt-3 pb-0"
  >
    <PageHeader title="C5 账号配置">
      <template #icon>
        <svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"
          />
        </svg>
      </template>
      <template #extra>
        <div v-if="isMobile" class="flex items-center gap-2 mr-1">
          <t-button
            v-if="canCreateAccount"
            variant="outline"
            size="small"
            theme="primary"
            @click="openCreateDialog"
          >
            <template #icon><t-icon name="plus" /></template>
            新增
          </t-button>
          <t-button
            variant="outline"
            size="small"
            theme="default"
            @click="showFilters = !showFilters"
          >
            <template #icon><t-icon :name="showFilters ? 'chevron-up' : 'filter'" /></template>
            {{ showFilters ? '收起' : '筛选' }}
          </t-button>
        </div>
        <div v-if="!isMobile && accounts.length > 0" class="flex flex-col items-end">
          <span class="text-[10px] font-bold tracking-wider text-slate-400 uppercase">
            C5 资金总资产
          </span>
          <div class="flex items-baseline gap-1">
            <span class="text-[10px] font-bold text-orange-500/80 italic">¥</span>
            <span
              class="font-numeric text-lg font-bold tracking-tight text-orange-500 tabular-nums"
            >
              {{ formatCurrency(totalAssetBalance).replace("¥", "") }}
            </span>
          </div>
        </div>
      </template>
    </PageHeader>

    <div :class="['flex flex-col bg-white px-0 py-4', isMobile ? 'gap-3' : 'gap-6']">
      <!-- 移动端统计数据条 -->
      <div
        v-if="isMobile && accounts.length > 0"
        class="mx-0 flex items-center justify-between rounded-lg bg-orange-50/50 px-3 py-2 text-xs"
      >
        <div class="flex items-center gap-1.5">
          <span class="text-orange-400">总资产:</span>
          <span class="font-bold text-orange-600">
            {{ formatCurrency(totalAssetBalance) }}
          </span>
        </div>
      </div>


      <section class="overflow-hidden bg-white">
        <div :class="['flex flex-col px-0 py-4', isMobile ? 'gap-2' : 'gap-3']">
        <div
          v-if="!isMobile || showFilters"
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
          <!-- <label class="flex min-w-0 flex-col gap-1.5">
            <span class="text-sm font-medium text-slate-700">状态</span>
            <t-select
              v-model="queryParams.status"
              :options="statusOptions"
              clearable
              placeholder="请选择状态"
              class="w-full"
            />
          </label> -->
          <div class="flex flex-wrap items-center gap-2">
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
            <t-button
              v-if="canReadAccountDetail"
              variant="outline"
              theme="primary"
              class="c5-sniping-account-action-btn"
              :loading="refreshBalanceLoading"
              :disabled="!accounts.length"
              @click="onRefreshBalance"
            >
              刷新余额
            </t-button>
          </div>
        </div>
        </div>
      </section>
    </div>


    <div class="relative min-h-0 flex-1">
      <div class="relative flex h-full min-h-0 flex-col overflow-hidden bg-white">
        <div class="min-h-0 flex-1 overflow-hidden">
          <!-- 移动端卡片视图 -->
          <div v-if="isMobile" class="h-full overflow-y-auto bg-slate-50 p-3">
            <div v-if="listLoading" class="flex h-32 items-center justify-center">
              <t-loading size="medium" text="加载中..." />
            </div>
            <div v-else-if="pagedAccounts.length === 0" class="py-8">
              <t-empty description="暂无扫货账号" />
            </div>
            <div v-else class="flex flex-col gap-3">
              <div
                v-for="row in pagedAccounts"
                :key="row.id"
                class="flex flex-col overflow-hidden rounded-lg bg-white shadow-sm ring-1 ring-slate-100"
              >
                <!-- 头部：账号名称 -->
                <div class="flex items-center justify-between border-b border-slate-100 p-3 bg-slate-50/50">
                  <div class="min-w-0 flex-1">
                    <div class="truncate text-sm font-bold text-slate-800">
                      {{ row.accountName }}
                    </div>
                    <div v-if="row.remark" class="mt-0.5 truncate text-[11px] text-slate-500">
                      备注：{{ row.remark }}
                    </div>
                  </div>
                </div>

                <!-- 资产信息 -->
                <div class="p-3">
                  <div class="flex w-full flex-col gap-1 px-0.5 py-0.5">
                    <!-- 资产统计 (最重要，置顶) -->
                    <div
                      class="mb-0.5 flex w-full items-center justify-between border-b border-orange-100/60 pb-1"
                    >
                      <span
                        class="rounded-[2px] bg-orange-50 px-1.5 py-0 text-[9px] font-bold tracking-tight text-orange-600 uppercase"
                      >
                        资产统计
                      </span>
                      <span class="text-[13px] font-bold text-orange-600 tabular-nums">
                        {{ formatCurrency(getRowTotal(row)) }}
                      </span>
                    </div>

                    <!-- 可用余额 -->
                    <div class="mb-1 flex w-full items-center justify-between px-0.5">
                      <span
                        class="rounded-[2px] bg-emerald-50 px-1.5 py-0 text-[9px] font-bold tracking-tight text-emerald-600 uppercase"
                      >
                        可用余额
                      </span>
                      <span class="text-[13px] font-bold text-emerald-600 tabular-nums">
                        {{ formatCurrency(resolveMoneyAmount(row)) }}
                      </span>
                    </div>

                    <!-- 次要资产网格 (2列布局压缩高度) -->
                    <div
                      class="grid grid-cols-2 gap-x-2 gap-y-1 border-t border-slate-100/60 px-0.5 pt-1"
                    >
                      <div class="flex items-center justify-between">
                        <span class="mr-1 shrink-0 text-[10px] text-slate-400">待结</span>
                        <span class="truncate text-[11px] font-medium text-slate-600 tabular-nums">
                          {{ formatCurrency(row.pendingBalance) }}
                        </span>
                      </div>
                      <div class="flex items-center justify-between">
                        <span class="mr-1 shrink-0 text-[10px] text-slate-400">保证金</span>
                        <span class="truncate text-[11px] font-medium text-slate-600 tabular-nums">
                          {{ formatCurrency(row.depositAmount) }}
                        </span>
                      </div>
                      <div class="flex items-center justify-between">
                        <span class="mr-1 shrink-0 text-[10px] text-slate-400">秒到</span>
                        <span class="truncate text-[11px] font-medium text-slate-600 tabular-nums">
                          {{ formatCurrency(row.creditMoney) }}
                        </span>
                      </div>
                      <div class="flex items-center justify-between">
                        <span class="mr-1 shrink-0 text-[10px] text-slate-400">秒保</span>
                        <span class="truncate text-[11px] font-medium text-slate-600 tabular-nums">
                          {{ formatCurrency(row.creditDeposit) }}
                        </span>
                      </div>
                    </div>
                  </div>

                  <!-- 密钥和配置信息 -->
                  <div class="mt-2 flex flex-col gap-1.5 rounded bg-slate-50 p-2 text-[11px] text-slate-500">
                    <div class="flex items-center justify-between gap-2">
                      <span class="shrink-0 text-slate-400">AppKey:</span>
                      <div class="flex min-w-0 flex-1 items-center justify-end gap-1">
                        <span class="truncate font-mono text-slate-700">{{ row.id ? revealedAppKeys[row.id] || row.c5AppKeyMasked || "-" : row.c5AppKeyMasked || "-" }}</span>
                        <t-button
                          v-if="canReadAccountDetail && row.hasC5AppKey"
                          variant="text"
                          theme="primary"
                          class="!p-0 h-4 text-[10px]"
                          :loading="revealingAccountId === row.id"
                          @click="revealRowAppKey(row)"
                        >
                          显示
                        </t-button>
                      </div>
                    </div>
                    <div class="flex items-center justify-between gap-2">
                      <span class="shrink-0 text-slate-400">Steam ID:</span>
                      <span class="truncate font-mono text-slate-700">{{ row.steamId || "-" }}</span>
                    </div>
                    <div class="flex items-center justify-between gap-2">
                      <span class="shrink-0 text-slate-400">交易链接:</span>
                      <span class="truncate font-mono text-slate-700">{{ row.steamTradeUrl || "-" }}</span>
                    </div>
                  </div>
                </div>

                <!-- 底部操作区 -->
                <div class="flex flex-wrap items-center justify-end gap-1.5 border-t border-slate-50 bg-slate-50/50 p-2">
                  <t-button
                    v-if="canUpdateAccount"
                    variant="outline"
                    theme="default"
                    class="h-7 px-2 text-xs"
                    @click="openEditDialog(row)"
                  >
                    编辑
                  </t-button>
                  <t-button
                    v-if="canReadAccountDetail"
                    variant="outline"
                    theme="primary"
                    class="h-7 px-2 text-xs"
                    @click="openDetailDialog(row)"
                  >
                    详情
                  </t-button>
                  <t-button
                    v-if="canDeleteAccount && row.boundTaskId"
                    variant="outline"
                    theme="danger"
                    class="h-7 px-2 text-xs"
                    disabled
                  >
                    删除
                  </t-button>
                  <t-popconfirm
                    v-else-if="canDeleteAccount"
                    content="确定删除该账号吗？"
                    @confirm="onDeleteAccount(row)"
                  >
                    <t-button
                      variant="outline"
                      theme="danger"
                      class="h-7 px-2 text-xs"
                    >
                      删除
                    </t-button>
                  </t-popconfirm>
                </div>
              </div>
            </div>
          </div>

          <t-table
            v-else
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
                  <div class="truncate text-[15px] font-bold text-slate-800">
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
              <div class="flex max-w-[220px] items-center gap-2">
                <t-tooltip
                  :content="revealedAppKeys[row.id] || row.c5AppKeyMasked || '未配置'"
                  placement="top-left"
                >
                  <div class="min-w-0 flex-1 truncate font-mono text-[14px] text-slate-700">
                    {{ row.id ? revealedAppKeys[row.id] || row.c5AppKeyMasked || "-" : row.c5AppKeyMasked || "-" }}
                  </div>
                </t-tooltip>
                <t-button
                  v-if="canReadAccountDetail && row.hasC5AppKey"
                  variant="text"
                  theme="primary"
                  size="small"
                  :loading="revealingAccountId === row.id"
                  @click="revealRowAppKey(row)"
                >
                  显示
                </t-button>
              </div>
            </template>

            <template #steamTradeUrl="{ row }">
              <t-tooltip :content="row.steamTradeUrl || '未配置'" placement="top-left">
                <div class="max-w-[220px] truncate font-mono text-[14px] text-slate-700">
                  {{ row.steamTradeUrl || "-" }}
                </div>
              </t-tooltip>
            </template>

            <template #steamId="{ row }">
              <t-tooltip :content="row.steamId || '未配置'" placement="top-left">
                <div class="max-w-[160px] truncate font-mono text-[14px] text-slate-700">
                  {{ row.steamId || "-" }}
                </div>
              </t-tooltip>
            </template>

            <template #balance="{ row }">
              <div class="flex w-full flex-col gap-1 px-0.5 py-0.5">
                <!-- 资产统计 (最重要，置顶) -->
                <div
                  class="mb-0.5 flex w-full items-center justify-between border-b border-orange-100/60 pb-1"
                >
                  <span
                    class="rounded-[2px] bg-orange-50 px-1.5 py-0 text-[9px] font-bold tracking-tight text-orange-600 uppercase"
                  >
                    资产统计
                  </span>
                  <span class="text-[13px] font-bold text-orange-600 tabular-nums">
                    {{ formatCurrency(getRowTotal(row)) }}
                  </span>
                </div>

                <!-- 可用余额 -->
                <div class="mb-1 flex w-full items-center justify-between px-0.5">
                  <span
                    class="rounded-[2px] bg-emerald-50 px-1.5 py-0 text-[9px] font-bold tracking-tight text-emerald-600 uppercase"
                  >
                    可用余额
                  </span>
                  <span class="text-[13px] font-bold text-emerald-600 tabular-nums">
                    {{ formatCurrency(resolveMoneyAmount(row)) }}
                  </span>
                </div>

                <!-- 次要资产网格 (2列布局压缩高度) -->
                <div
                  class="grid grid-cols-2 gap-x-2 gap-y-1 border-t border-slate-100/60 px-0.5 pt-1"
                >
                  <div class="flex items-center justify-between">
                    <span class="mr-1 shrink-0 text-[10px] text-slate-400">待结</span>
                    <span class="truncate text-[11px] font-medium text-slate-600 tabular-nums">
                      {{ formatCurrency(row.pendingBalance) }}
                    </span>
                  </div>
                  <div class="flex items-center justify-between">
                    <span class="mr-1 shrink-0 text-[10px] text-slate-400">保证金</span>
                    <span class="truncate text-[11px] font-medium text-slate-600 tabular-nums">
                      {{ formatCurrency(row.depositAmount) }}
                    </span>
                  </div>
                  <div class="flex items-center justify-between">
                    <span class="mr-1 shrink-0 text-[10px] text-slate-400">秒到</span>
                    <span class="truncate text-[11px] font-medium text-slate-600 tabular-nums">
                      {{ formatCurrency(row.creditMoney) }}
                    </span>
                  </div>
                  <div class="flex items-center justify-between">
                    <span class="mr-1 shrink-0 text-[10px] text-slate-400">秒保</span>
                    <span class="truncate text-[11px] font-medium text-slate-600 tabular-nums">
                      {{ formatCurrency(row.creditDeposit) }}
                    </span>
                  </div>
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
                <t-popconfirm
                  v-else-if="canDeleteAccount"
                  content="确定删除该账号吗？"
                  @confirm="onDeleteAccount(row)"
                >
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
            :size="isMobile ? 'small' : 'medium'"
            :theme="isMobile ? 'simple' : 'default'"
            :show-page-size="isMobile ? false : undefined"
            v-model="pagination.current"
            v-model:page-size="pagination.pageSize"
            :total="filteredAccounts.length"
            :page-size-options="pageSizeOptions"
            show-jumper
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
              <t-tooltip
                v-if="row.marketHashName"
                :content="row.marketHashName"
                placement="top-left"
              >
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
            :size="isMobile ? 'small' : 'medium'"
            :theme="isMobile ? 'simple' : 'default'"
            :show-page-size="isMobile ? false : undefined"
            v-model="detailPagination.current"
            v-model:page-size="detailPagination.pageSize"
            :total="detailPagination.total"
            :page-size-options="detailPageSizeOptions"
            show-jumper
            @change="handleDetailPageChange"
          />
        </div>
      </div>
    </t-drawer>

    <AppDialog
      v-model:visible="accountDialogVisible"
      :title="accountDialogTitle"
      width="min(520px, calc(100vw - 32px))"
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

          <t-form-item label="C5 AppKey" name="c5AppKeyPlain">
            <t-input
              v-model="accountFormData.c5AppKeyPlain"
              :placeholder="
                accountFormData.id ? '留空则不修改当前 AppKey' : '请输入该账号使用的 C5 AppKey'
              "
              type="password"
              clearable
              @blur="() => trimStringField(accountFormData, 'c5AppKeyPlain')"
            />
            <div
              v-if="accountFormData.id && accountFormData.c5AppKeyMasked"
              class="mt-1 text-xs text-slate-400"
            >
              当前已配置：{{ accountFormData.c5AppKeyMasked }}
            </div>
          </t-form-item>

          <t-form-item label="Steam 链接" name="steamTradeUrl">
            <t-input
              v-model="accountFormData.steamTradeUrl"
              placeholder="请输入该账号使用的 Steam 交易链接"
              @blur="() => trimStringField(accountFormData, 'steamTradeUrl')"
            />
          </t-form-item>

          <t-form-item label="Steam ID" name="steamId">
            <t-input
              v-model="accountFormData.steamId"
              placeholder="请输入库存接口使用的 Steam ID"
              @blur="() => trimStringField(accountFormData, 'steamId')"
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
          <t-button
            theme="primary"
            type="submit"
            :loading="accountSubmitLoading"
            :disabled="!canSubmitAccountForm"
          >
            提交
          </t-button>
        </div>
      </t-form>
    </AppDialog>
  </PageFrame>
</template>

<script setup lang="ts">
import { useWindowSize } from "@vueuse/core";
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import {
  MessagePlugin,
  type FormRule,
  type PageInfo,
  type PrimaryTableCol,
  type SubmitContext,
} from "tdesign-vue-next";
import { c5SnipingAccountApi } from "@/api/c5-sniping-account";
import { c5SnipingV2Api } from "@/api/c5-sniping-v2";
import PageFrame from "@/components/PageFrame.vue";
import PageHeader from "@/components/PageHeader.vue";
import AppDialog from "@/components/AppDialog.vue";
import { PermissionConstant } from "@/constant/PermissionConstant";
import useNewPermission from "@/hooks/useNewPermission";
import type { C5SnipingAccount, C5SnipingAccountStatus } from "@/types/c5-sniping-account";
import type { C5SnipingTaskV2Item } from "@/types/c5-sniping-v2";

interface AccountFormData extends C5SnipingAccount {
  c5AppKeyPlain: string;
}

const { hasButtonPermission } = useNewPermission();
const { width } = useWindowSize();
const showFilters = ref(false);
const isMobile = computed(() => width.value <= 768);
const canCreateAccount = computed(() =>
  hasButtonPermission(PermissionConstant.C5_SNIPING_ACCOUNT_CREATE)
);
const canUpdateAccount = computed(() =>
  hasButtonPermission(PermissionConstant.C5_SNIPING_ACCOUNT_UPDATE)
);
const canDeleteAccount = computed(() =>
  hasButtonPermission(PermissionConstant.C5_SNIPING_ACCOUNT_DELETE)
);
const canReadAccountDetail = computed(() =>
  hasButtonPermission(PermissionConstant.C5_SNIPING_ACCOUNT_DETAIL)
);

const listLoading = ref(false);
const accounts = ref<C5SnipingAccount[]>([]);
const totalAssetBalance = ref(0);
const queryParams = reactive<{ keyword: string; status?: C5SnipingAccountStatus }>({
  keyword: "",
  status: undefined,
});
const pagination = reactive({ current: 1, pageSize: 10 });
const pageSizeOptions = [10, 20, 50];
const accountDialogVisible = ref(false);
const accountDialogTitle = ref("新增扫货账号");
const accountSubmitLoading = ref(false);
const refreshBalanceLoading = ref(false);
const revealingAccountId = ref<number>();
const revealedAppKeys = reactive<Record<number, string>>({});
const revealTimers = new Map<number, number>();
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

const accountFormData = reactive<AccountFormData>({
  accountName: "",
  c5AppKeyPlain: "",
  c5AppKeyMasked: "",
  hasC5AppKey: false,
  steamTradeUrl: "",
  steamId: "",
  status: "NORMAL",
  balance: 0,
  moneyAmount: 0,
  pendingBalance: 0,
  depositAmount: 0,
  creditMoney: 0,
  creditDeposit: 0,
  totalBalance: 0,
  remark: "",
});

const accountRules: Record<string, FormRule[]> = {
  accountName: [{ required: true, message: "账号名称不能为空", type: "error" }],
  c5AppKeyPlain: [
    {
      validator: (value) => Boolean(accountFormData.id || String(value || "").trim()),
      message: "C5 AppKey 不能为空",
      type: "error",
    },
  ],
  steamTradeUrl: [{ required: true, message: "Steam 交易链接不能为空", type: "error" }],
  steamId: [{ required: true, message: "Steam ID 不能为空", type: "error" }],
};

const tableHeaderClass =
  "!bg-slate-50 !text-slate-500 !text-xs !font-semibold uppercase whitespace-nowrap";
const tableBodyClass = "!py-2 text-sm text-slate-700 align-middle";

const detailTaskColumns = computed<PrimaryTableCol[]>(() => [
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

const columns = computed<PrimaryTableCol[]>(() => [
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
    colKey: "steamId",
    title: "Steam ID",
    width: 180,
    cell: "steamId",
    className: tableBodyClass,
    thClassName: tableHeaderClass,
  },
  {
    colKey: "balance",
    title: "余额",
    width: 220,
    align: "left" as const,
    cell: "balance",
    className: tableBodyClass,
    thClassName: `${tableHeaderClass} !text-left !pl-6`,
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

const base64ToBytes = (value: string) => {
  const binary = window.atob(value);
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
};

const bytesToBase64 = (value: ArrayBuffer) => {
  const bytes = new Uint8Array(value);
  let binary = "";
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return window.btoa(binary);
};

const importRsaPublicKey = (publicKey: string) =>
  window.crypto.subtle.importKey(
    "spki",
    base64ToBytes(publicKey),
    { name: "RSA-OAEP", hash: "SHA-256" },
    false,
    ["encrypt"]
  );

const encryptAppKeyWithPublicKey = async (appKey: string, publicKey: string) => {
  const key = await importRsaPublicKey(publicKey);
  const encrypted = await window.crypto.subtle.encrypt(
    { name: "RSA-OAEP" },
    key,
    new TextEncoder().encode(appKey)
  );
  return bytesToBase64(encrypted);
};

const generateRevealKeyPair = () =>
  window.crypto.subtle.generateKey(
    {
      name: "RSA-OAEP",
      modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: "SHA-256",
    },
    true,
    ["encrypt", "decrypt"]
  );

const exportPublicKey = async (key: CryptoKey) =>
  bytesToBase64(await window.crypto.subtle.exportKey("spki", key));

const decryptRevealAppKey = async (encryptedAppKey: string, privateKey: CryptoKey) => {
  const decrypted = await window.crypto.subtle.decrypt(
    { name: "RSA-OAEP" },
    privateKey,
    base64ToBytes(encryptedAppKey)
  );
  return new TextDecoder().decode(decrypted);
};

const formatCurrency = (value?: number) =>
  value === undefined || value === null ? "-" : priceFormatter.format(value);

const resolveMoneyAmount = (account: C5SnipingAccount) => account.moneyAmount ?? account.balance;
const getRowTotal = (row: C5SnipingAccount) => row.totalBalance ?? 0;
const getDetailTaskTitle = (row: C5SnipingTaskV2Item) =>
  row.name || row.goodsDisplayName || row.marketHashName || `任务 #${row.id}`;
const getTaskStatusMeta = (status?: string) => {
  const map: Record<
    string,
    {
      label: string;
      theme: "default" | "primary" | "danger" | "warning" | "success";
      dotClass: string;
    }
  > = {
    DRAFT: { label: "待开启", theme: "default", dotClass: "bg-slate-400" },
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
    ? map[status] || { label: status, theme: "default" as const, dotClass: "bg-slate-300" }
    : { label: "未知", theme: "default" as const, dotClass: "bg-slate-300" };
};
const formatTaskProgress = (row: C5SnipingTaskV2Item) => {
  if (row.stopMode === "BUY_COUNT" && row.targetBuyCount) {
    return `${row.successBuyCount ?? 0}/${row.targetBuyCount}`;
  }
  return `已买 ${row.successBuyCount ?? 0}`;
};

const fetchAccounts = async () => {
  const res = await c5SnipingAccountApi.getAccounts();
  accounts.value = res?.records || [];
  totalAssetBalance.value = res?.totalBalance || 0;
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
    c5AppKeyPlain: "",
    c5AppKeyMasked: "",
    hasC5AppKey: false,
    steamTradeUrl: "",
    steamId: "",
    status: "NORMAL",
    balance: 0,
    moneyAmount: 0,
    pendingBalance: 0,
    depositAmount: 0,
    creditMoney: 0,
    creditDeposit: 0,
    totalBalance: 0,
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
  Object.assign(accountFormData, {
    ...row,
    c5AppKeyPlain: "",
  });
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
    const isCreate = !accountFormData.id;
    const existingAccountIds = new Set(
      accounts.value
        .map((account) => account.id)
        .filter((id): id is number => typeof id === "number")
    );

    trimStringField(accountFormData, "accountName");
    trimStringField(accountFormData, "c5AppKeyPlain");
    trimStringField(accountFormData, "steamTradeUrl");
    trimStringField(accountFormData, "steamId");
    trimStringField(accountFormData, "remark");

    const encryptedC5AppKey = accountFormData.c5AppKeyPlain
      ? await encryptAppKeyWithPublicKey(
          accountFormData.c5AppKeyPlain,
          (await c5SnipingAccountApi.getAppKeyPublicKey()).publicKey
        )
      : undefined;

    await c5SnipingAccountApi.saveAccount({
      id: accountFormData.id,
      accountName: accountFormData.accountName,
      encryptedC5AppKey,
      steamTradeUrl: accountFormData.steamTradeUrl,
      steamId: accountFormData.steamId,
      remark: accountFormData.remark,
    });
    accountDialogVisible.value = false;
    await loadData();

    if (isCreate) {
      const createdAccountId = accounts.value.find(
        (account) => typeof account.id === "number" && !existingAccountIds.has(account.id)
      )?.id;
      if (createdAccountId) {
        try {
          await c5SnipingAccountApi.refreshBalance({ accountIds: [createdAccountId] });
        } finally {
          await loadData();
        }
      }
    }

    MessagePlugin.success(`${isCreate ? "账号已创建" : "账号已更新"}`);
  } finally {
    accountSubmitLoading.value = false;
  }
};

const onRefreshBalance = async () => {
  const accountIds = accounts.value
    .map((account) => account.id)
    .filter((id): id is number => typeof id === "number");
  if (!accountIds.length) {
    MessagePlugin.warning("暂无可刷新账号");
    return;
  }

  refreshBalanceLoading.value = true;
  try {
    const results = await c5SnipingAccountApi.refreshBalance({ accountIds });
    const successCount = results.filter((item) => item.success).length;
    const failCount = accountIds.length - successCount;

    if (successCount === accountIds.length) {
      MessagePlugin.success(`余额刷新完成，成功 ${successCount} 个`);
    } else if (successCount > 0) {
      MessagePlugin.warning(`余额刷新完成，成功 ${successCount} 个，失败 ${failCount} 个`);
    } else {
      MessagePlugin.error(`余额刷新失败，失败 ${failCount} 个`);
    }

    await fetchData();
  } finally {
    refreshBalanceLoading.value = false;
  }
};

const clearRevealedAppKey = (accountId: number) => {
  delete revealedAppKeys[accountId];
  const timer = revealTimers.get(accountId);
  if (timer) {
    window.clearTimeout(timer);
    revealTimers.delete(accountId);
  }
};

const revealRowAppKey = async (row: C5SnipingAccount) => {
  if (!row.id || !canReadAccountDetail.value) {
    return;
  }
  revealingAccountId.value = row.id;
  try {
    const keyPair = await generateRevealKeyPair();
    const publicKey = await exportPublicKey(keyPair.publicKey);
    const result = await c5SnipingAccountApi.revealAppKey(row.id, { publicKey });
    revealedAppKeys[row.id] = await decryptRevealAppKey(
      result.encryptedC5AppKey,
      keyPair.privateKey
    );
    const existingTimer = revealTimers.get(row.id);
    if (existingTimer) {
      window.clearTimeout(existingTimer);
    }
    revealTimers.set(
      row.id,
      window.setTimeout(() => clearRevealedAppKey(row.id as number), 30000)
    );
    MessagePlugin.success("AppKey 明文已显示，30 秒后自动隐藏");
  } finally {
    revealingAccountId.value = undefined;
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

onBeforeUnmount(() => {
  [...revealTimers.keys()].forEach(clearRevealedAppKey);
});

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
  padding-top: 4px;
  padding-bottom: 4px;
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
