<template>
  <PageFrame
    :is-mobile="isMobile"
    :on-body-ref-change="handleBodyRefChange"
    body-class="invite-code-body"
    desktop-outer-class="!p-0"
    desktop-content-class="px-4 pt-0 pb-0"
    mobile-content-class="px-3 pt-3 pb-0"
  >
    <section class="overflow-hidden bg-white">
      <div class="flex flex-col gap-3 bg-white px-0 py-4">
        <div
          :class="[
            'jsh-filter-layout grid grid-cols-1 gap-3 xl:items-end',
            showAdvancedFilters
              ? 'xl:grid-cols-[minmax(0,280px)_minmax(0,180px)_minmax(0,180px)_minmax(0,180px)_minmax(0,320px)_auto]'
              : 'xl:grid-cols-[minmax(0,280px)_minmax(0,180px)_auto]',
          ]"
        >
          <label class="jsh-filter-item flex min-w-0 flex-col gap-1.5">
            <span class="jsh-label text-sm font-medium text-slate-700">邀请码关键词</span>
            <t-input
              v-model="queryParams.keyword"
              placeholder="请输入邀请码 / 邮箱 / 昵称"
              clearable
              class="jsh-filter-input"
              :class="toolbarFieldClass"
              @enter="handleSearch"
              @clear="handleSearch"
            />
          </label>

          <label class="jsh-filter-item flex min-w-0 flex-col gap-1.5">
            <span class="jsh-label text-sm font-medium text-slate-700">状态</span>
            <t-select
              v-model="queryParams.status"
              clearable
              placeholder="请选择状态"
              class="jsh-filter-select"
              :class="toolbarFieldClass"
            >
              <t-option
                v-for="item in statusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </t-select>
          </label>

          <label v-if="showAdvancedFilters" class="jsh-filter-item flex min-w-0 flex-col gap-1.5">
            <span class="jsh-label text-sm font-medium text-slate-700">可用性</span>
            <t-select
              v-model="queryParams.availability"
              clearable
              placeholder="请选择可用性"
              class="jsh-filter-select"
              :class="toolbarFieldClass"
            >
              <t-option
                v-for="item in availabilityOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </t-select>
          </label>

          <label v-if="showAdvancedFilters" class="jsh-filter-item flex min-w-0 flex-col gap-1.5">
            <span class="jsh-label text-sm font-medium text-slate-700">创建人</span>
            <t-select
              v-model="queryParams.creator"
              clearable
              placeholder="请选择创建人"
              class="jsh-filter-select"
              :class="toolbarFieldClass"
            >
              <t-option v-for="item in creatorOptions" :key="item" :label="item" :value="item" />
            </t-select>
          </label>

          <label v-if="showAdvancedFilters" class="jsh-filter-item flex min-w-0 flex-col gap-1.5">
            <span class="jsh-label text-sm font-medium text-slate-700">创建时间</span>
            <t-date-range-picker
              v-model="dateRange"
              clearable
              value-type="YYYY-MM-DD"
              format="YYYY-MM-DD"
              class="jsh-filter-select"
              :class="toolbarFieldClass"
              :placeholder="['开始日期', '结束日期']"
            />
          </label>

          <div class="jsh-filter-actions flex flex-wrap items-center gap-2">
            <t-button theme="primary" class="jsh-action-btn" @click="handleSearch">查询</t-button>
            <t-button variant="outline" theme="default" class="jsh-action-btn" @click="handleReset">
              重置
            </t-button>
            <button type="button" class="jsh-expand-link" @click="toggleAdvancedFilters">
              {{ showAdvancedFilters ? "收起" : "展开" }}
            </button>
          </div>
        </div>

        <div class="jsh-toolbar flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div
            v-if="canCreateInviteCode || canBatchCreateInviteCode || canDisableInviteCode"
            class="table-operator flex flex-wrap items-center gap-2"
            :class="{ 'table-operator--mobile': isMobile }"
          >
            <t-button
              v-if="canCreateInviteCode"
              theme="primary"
              class="jsh-action-btn jsh-action-btn--primary"
              @click="openCreateDialog"
            >
              新建邀请码
            </t-button>
            <t-button
              v-if="canBatchCreateInviteCode"
              variant="outline"
              theme="default"
              class="jsh-action-btn"
              @click="openBatchDialog"
            >
              批量生成
            </t-button>
            <t-popconfirm v-if="canDisableInviteCode" content="确认批量停用选中的邀请码吗？" @confirm="handleBatchDisable">
              <t-button
                variant="outline"
                theme="default"
                class="jsh-action-btn"
                :disabled="selectedRowKeys.length === 0"
              >
                批量停用
              </t-button>
            </t-popconfirm>
          </div>

          <div
            v-if="canDisableInviteCode"
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

    <div class="relative min-h-0 flex-1">
      <div v-if="!isMobile" class="relative flex h-full min-h-0 flex-col overflow-hidden bg-white">
        >
        <div class="min-h-0 flex-1 overflow-hidden">
          <t-table
            row-key="id"
            :data="pagedRecords"
            :columns="columns"
            :loading="loading"
            :pagination="undefined"
            :selected-row-keys="canManageInviteCodes ? selectedRowKeys : []"
            :select-on-row-click="canManageInviteCodes"
            hover
            class="invite-code-table w-full bg-white"
            @select-change="handleSelectChange"
          >
            <template #empty>
              <div class="jsh-ledger-empty">
                <t-empty description="暂无邀请码数据" />
              </div>
            </template>

            <template #code="{ row }">
              <div class="flex min-w-0 flex-col gap-1.5 py-0.5">
                <div class="font-mono text-[13px] font-semibold tracking-[0.04em] text-slate-800">
                  {{ row.code }}
                </div>
                <div v-if="canCopyInviteCode" class="flex flex-wrap items-center gap-3 text-xs">
                  <button type="button" class="invite-link-btn" @click="copyInviteCode(row)">
                    复制邀请码
                  </button>
                  <button type="button" class="invite-link-btn" @click="copyInviteLink(row)">
                    复制链接
                  </button>
                </div>
              </div>
            </template>

            <template #status="{ row }">
              <t-tag :theme="getStatusMeta(row.adminStatus).theme" variant="light">
                {{ getStatusMeta(row.adminStatus).label }}
              </t-tag>
            </template>

            <template #availability="{ row }">
              <t-tag :theme="getAvailabilityMeta(getAvailability(row)).theme" variant="light">
                {{ getAvailabilityMeta(getAvailability(row)).label }}
              </t-tag>
            </template>

            <template #account="{ row }">
              <div v-if="row.registration" class="flex min-w-0 flex-col gap-1">
                <div class="flex min-w-0 items-center gap-2">
                  <t-tooltip :content="row.registration.nickname" placement="top-left">
                    <span class="truncate font-medium text-slate-700">
                      {{ row.registration.nickname }}
                    </span>
                  </t-tooltip>
                  <t-tag
                    :theme="row.registration.accountStatus === '正常' ? 'success' : 'danger'"
                    variant="light"
                    size="small"
                  >
                    {{ row.registration.accountStatus }}
                  </t-tag>
                </div>
                <div class="text-xs text-slate-400">用户 ID：{{ row.registration.userId }}</div>
              </div>
              <span v-else class="text-slate-400">-</span>
            </template>

            <template #email="{ row }">
              <span v-if="row.registration" class="text-sm text-slate-700">
                {{ row.registration.email }}
              </span>
              <span v-else class="text-slate-400">-</span>
            </template>

            <template #usedAt="{ row }">
              <span v-if="row.registration" class="text-sm text-slate-700">
                {{ formatDateTime(row.registration.usedAt) }}
              </span>
              <span v-else class="text-slate-400">-</span>
            </template>

            <template #expireAt="{ row }">
              <span class="text-sm text-slate-700">{{ formatExpireAt(row) }}</span>
            </template>

            <template #remark="{ row }">
              <t-tooltip v-if="row.remark" :content="row.remark">
                <div class="max-w-[220px] truncate text-sm text-slate-600">{{ row.remark }}</div>
              </t-tooltip>
              <span v-else class="text-slate-400">-</span>
            </template>

            <template #createdAt="{ row }">
              <span class="text-sm text-slate-700">{{ formatDateTime(row.createdAt) }}</span>
            </template>

            <template #operation="{ row }">
              <div
                v-if="hasVisibleOperation(row)"
                class="invite-code-table__actions flex flex-wrap gap-1.5"
              >
                <t-button
                  variant="outline"
                  class="invite-code-table__action-btn"
                  @click="openDetailDrawer(row)"
                >
                  查看注册信息
                </t-button>
                <t-button
                  variant="outline"
                  class="invite-code-table__action-btn"
                  @click="openEditDialog(row)"
                >
                  编辑
                </t-button>
                <t-popconfirm
                  v-if="canToggleStatus(row)"
                  :content="
                    row.adminStatus === 'enabled' ? '确认停用该邀请码吗？' : '确认启用该邀请码吗？'
                  "
                  @confirm="toggleStatus(row)"
                >
                  <t-button
                    variant="outline"
                    :theme="row.adminStatus === 'enabled' ? 'warning' : 'success'"
                    class="invite-code-table__action-btn"
                  >
                    {{ row.adminStatus === "enabled" ? "停用" : "启用" }}
                  </t-button>
                </t-popconfirm>
                <t-button
                  v-else
                  variant="outline"
                  theme="default"
                  disabled
                  class="invite-code-table__action-btn"
                >
                  {{ row.adminStatus === "enabled" ? "停用" : "启用" }}
                </t-button>
                <t-button
                  variant="outline"
                  theme="default"
                  class="invite-code-table__action-btn"
                  v-if="canCopyInviteCode"
                  @click="copyInviteLink(row)"
                >
                  复制链接
                </t-button>
              </div>
            </template>
          </t-table>
        </div>

        <div v-if="pagination.total > 0" class="bg-white px-4 py-3">
          <t-pagination
            :current="pagination.current"
            :page-size="pagination.pageSize"
            :total="pagination.total"
            @change="onPageChange"
          />
        </div>
      </div>

      <div v-else class="invite-mobile min-h-0">
        <div v-if="loading" class="invite-mobile__empty text-sm text-[#909399]">加载中...</div>
        <div v-else-if="pagedRecords.length === 0" class="invite-mobile__empty">
          <t-empty description="暂无邀请码数据" />
        </div>
        <div v-else class="invite-mobile__list">
          <div v-for="row in pagedRecords" :key="row.id" class="invite-mobile-card">
            <div class="invite-mobile-card__header">
              <div class="flex min-w-0 items-start gap-3">
                <t-checkbox
                  v-if="canDisableInviteCode"
                  :checked="selectedRowKeys.includes(row.id)"
                  @change="(checked) => handleMobileSelectChange(row.id, checked)"
                />
                <div class="min-w-0 flex-1">
                  <t-tooltip :content="row.code" placement="top-left">
                    <div class="truncate font-mono text-sm font-semibold text-slate-800">
                      {{ row.code }}
                    </div>
                  </t-tooltip>
                  <div class="mt-1 flex flex-wrap gap-1.5">
                    <t-tag
                      :theme="getStatusMeta(row.adminStatus).theme"
                      variant="light"
                      size="small"
                    >
                      {{ getStatusMeta(row.adminStatus).label }}
                    </t-tag>
                    <t-tag
                      :theme="getAvailabilityMeta(getAvailability(row)).theme"
                      variant="light"
                      size="small"
                    >
                      {{ getAvailabilityMeta(getAvailability(row)).label }}
                    </t-tag>
                  </div>
                </div>
              </div>
            </div>

            <div class="invite-mobile-card__meta">
              <div class="invite-mobile-card__meta-item">
                <span class="invite-mobile-card__meta-label">创建人：</span>
                <span class="invite-mobile-card__meta-value">{{ row.creator }}</span>
              </div>
              <div class="invite-mobile-card__meta-item">
                <span class="invite-mobile-card__meta-label">有效期：</span>
                <span class="invite-mobile-card__meta-value">{{ formatExpireAt(row) }}</span>
              </div>
              <div class="invite-mobile-card__meta-item">
                <span class="invite-mobile-card__meta-label">注册账号：</span>
                <span class="invite-mobile-card__meta-value">
                  {{
                    row.registration
                      ? `${row.registration.nickname}（${row.registration.userId}）`
                      : "-"
                  }}
                </span>
              </div>
              <div class="invite-mobile-card__meta-item">
                <span class="invite-mobile-card__meta-label">注册邮箱：</span>
                <span class="invite-mobile-card__meta-value">
                  {{ row.registration?.email || "-" }}
                </span>
              </div>
              <div class="invite-mobile-card__meta-item">
                <span class="invite-mobile-card__meta-label">使用时间：</span>
                <span class="invite-mobile-card__meta-value">
                  {{ row.registration ? formatDateTime(row.registration.usedAt) : "-" }}
                </span>
              </div>
              <div class="invite-mobile-card__meta-item invite-mobile-card__meta-item--full">
                <span class="invite-mobile-card__meta-label">备注：</span>
                <span class="invite-mobile-card__meta-value">{{ row.remark || "-" }}</span>
              </div>
            </div>

            <div v-if="hasVisibleOperation(row)" class="invite-mobile-card__actions">
              <t-button variant="outline" theme="default" @click="openDetailDrawer(row)">
                详情
              </t-button>
              <t-button v-if="canUpdateInviteCode" variant="outline" theme="default" @click="openEditDialog(row)">
                编辑
              </t-button>
              <t-button v-if="canCopyInviteCode" variant="outline" theme="default" @click="copyInviteCode(row)">
                复制码
              </t-button>
              <t-button v-if="canCopyInviteCode" variant="outline" theme="default" @click="copyInviteLink(row)">
                复制链接
              </t-button>
            </div>
          </div>
        </div>

        <div v-if="!loading && pagination.total > 0" class="invite-mobile__pagination">
          <t-pagination
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

    <t-drawer
      v-model:visible="detailDrawerVisible"
      size="440px"
      header="注册信息"
      :footer="false"
      :close-btn="true"
    >
      <template v-if="currentDetailRecord">
        <div class="flex flex-col gap-4 p-1">
          <section class="invite-drawer-section">
            <div class="invite-drawer-section__title">邀请码基础信息</div>
            <div class="invite-drawer-grid">
              <div class="invite-drawer-item">
                <div class="invite-drawer-item__label">邀请码</div>
                <div class="invite-drawer-item__value font-mono font-semibold">
                  {{ currentDetailRecord.code }}
                </div>
              </div>
              <div class="invite-drawer-item">
                <div class="invite-drawer-item__label">状态 / 可用性</div>
                <div class="invite-drawer-item__value">
                  {{ getStatusMeta(currentDetailRecord.adminStatus).label }} /
                  {{ getAvailabilityMeta(getAvailability(currentDetailRecord)).label }}
                </div>
              </div>
              <div class="invite-drawer-item">
                <div class="invite-drawer-item__label">创建人</div>
                <div class="invite-drawer-item__value">{{ currentDetailRecord.creator }}</div>
              </div>
              <div class="invite-drawer-item">
                <div class="invite-drawer-item__label">创建时间</div>
                <div class="invite-drawer-item__value">
                  {{ formatDateTime(currentDetailRecord.createdAt) }}
                </div>
              </div>
              <div class="invite-drawer-item">
                <div class="invite-drawer-item__label">有效期</div>
                <div class="invite-drawer-item__value">
                  {{ formatExpireAt(currentDetailRecord) }}
                </div>
              </div>
              <div class="invite-drawer-item">
                <div class="invite-drawer-item__label">备注</div>
                <div class="invite-drawer-item__value">{{ currentDetailRecord.remark || "-" }}</div>
              </div>
            </div>
          </section>

          <section class="invite-drawer-section">
            <div class="invite-drawer-section__title">注册绑定信息</div>
            <template v-if="currentDetailRecord.registration">
              <div class="invite-drawer-grid">
                <div class="invite-drawer-item">
                  <div class="invite-drawer-item__label">用户 ID</div>
                  <div class="invite-drawer-item__value">
                    {{ currentDetailRecord.registration.userId }}
                  </div>
                </div>
                <div class="invite-drawer-item">
                  <div class="invite-drawer-item__label">昵称 / 账号</div>
                  <div class="invite-drawer-item__value">
                    {{ currentDetailRecord.registration.nickname }}
                  </div>
                </div>
                <div class="invite-drawer-item">
                  <div class="invite-drawer-item__label">注册邮箱</div>
                  <div class="invite-drawer-item__value">
                    {{ currentDetailRecord.registration.email }}
                  </div>
                </div>
                <div class="invite-drawer-item">
                  <div class="invite-drawer-item__label">注册时间</div>
                  <div class="invite-drawer-item__value">
                    {{ formatDateTime(currentDetailRecord.registration.usedAt) }}
                  </div>
                </div>
                <div class="invite-drawer-item">
                  <div class="invite-drawer-item__label">账号状态</div>
                  <div class="invite-drawer-item__value">
                    {{ currentDetailRecord.registration.accountStatus }}
                  </div>
                </div>
                <div class="invite-drawer-item">
                  <div class="invite-drawer-item__label">注册链接</div>
                  <div class="invite-drawer-item__value break-all">
                    {{ buildInviteLink(currentDetailRecord.code) }}
                  </div>
                </div>
              </div>
            </template>
            <div v-else class="invite-drawer-empty">
              <t-empty description="该邀请码尚未被使用" />
            </div>
          </section>
        </div>
      </template>
    </t-drawer>

    <t-dialog
      v-model:visible="createDialogVisible"
      header="新建邀请码"
      width="620px"
      :footer="false"
    >
      <t-form :data="createForm" label-align="top" class="overflow-x-hidden p-1">
        <div class="grid grid-cols-2 gap-4">
          <t-form-item label="邀请码" class="col-span-2">
            <t-input v-model="createForm.code" placeholder="留空则自动生成，例如 NIRO-8X2Q" />
          </t-form-item>
          <t-form-item label="过期时间">
            <t-date-picker
              v-model="createForm.expireAt"
              enable-time-picker
              clearable
              value-type="YYYY-MM-DD HH:mm:ss"
              format="YYYY-MM-DD HH:mm:ss"
            />
          </t-form-item>
          <t-form-item label="永不过期">
            <div class="invite-dialog-switch-row">
              <t-checkbox v-model="createForm.forever">开启后忽略过期时间</t-checkbox>
            </div>
          </t-form-item>
          <t-form-item label="备注" class="col-span-2">
            <t-textarea
              v-model="createForm.remark"
              :autosize="{ minRows: 3, maxRows: 5 }"
              placeholder="例如：给首批种子用户"
            />
          </t-form-item>
        </div>
        <div class="mt-6 flex justify-end gap-3">
          <t-button variant="outline" @click="createDialogVisible = false">取消</t-button>
          <t-button theme="primary" :disabled="!canCreateInviteCode" @click="submitCreate">创建邀请码</t-button>
        </div>
      </t-form>
    </t-dialog>

    <t-dialog
      v-model:visible="batchDialogVisible"
      header="批量生成邀请码"
      width="760px"
      :footer="false"
    >
      <template v-if="!batchResultMode">
        <t-form :data="batchForm" label-align="top" class="overflow-x-hidden p-1">
          <div class="grid grid-cols-2 gap-4">
            <t-form-item label="生成数量">
              <t-input
                v-model="batchForm.quantity"
                type="number"
                placeholder="请输入 1-20 之间的数量"
              />
            </t-form-item>
            <t-form-item label="前缀（可选）">
              <t-input v-model="batchForm.prefix" placeholder="例如 SPRING / C5 / BETA" />
            </t-form-item>
            <t-form-item label="过期时间">
              <t-date-picker
                v-model="batchForm.expireAt"
                enable-time-picker
                clearable
                value-type="YYYY-MM-DD HH:mm:ss"
                format="YYYY-MM-DD HH:mm:ss"
              />
            </t-form-item>
            <t-form-item label="永不过期">
              <div class="invite-dialog-switch-row">
                <t-checkbox v-model="batchForm.forever">开启后统一生成永久码</t-checkbox>
              </div>
            </t-form-item>
            <t-form-item label="备注" class="col-span-2">
              <t-textarea
                v-model="batchForm.remark"
                :autosize="{ minRows: 3, maxRows: 5 }"
                placeholder="例如：4 月活动测试批次"
              />
            </t-form-item>
          </div>
          <div class="mt-6 flex justify-end gap-3">
            <t-button variant="outline" @click="batchDialogVisible = false">取消</t-button>
            <t-button theme="primary" :disabled="!canBatchCreateInviteCode" @click="submitBatchGenerate">开始生成</t-button>
          </div>
        </t-form>
      </template>
      <div v-else class="flex flex-col gap-4 p-1">
        <div class="rounded border border-sky-100 bg-sky-50 px-4 py-3 text-sm text-sky-700">
          已生成 {{ batchResult.length }} 个邀请码。当前结果保留在弹窗中，方便直接复制并发码。
        </div>
        <div class="flex flex-wrap gap-2">
          <t-button theme="primary" @click="copyAllCodes">复制全部邀请码</t-button>
          <t-button variant="outline" theme="default" @click="copyAllLinks">
            复制全部注册链接
          </t-button>
          <t-button variant="outline" theme="default" @click="resetBatchDialog">
            返回重新生成
          </t-button>
        </div>
        <div class="overflow-hidden rounded border border-slate-200 bg-white">
          <div
            v-for="item in batchResult"
            :key="item.id"
            class="grid grid-cols-[180px_minmax(0,1fr)_auto] items-center gap-3 border-b border-slate-200 px-4 py-3 last:border-b-0"
          >
            <div class="font-mono text-sm font-semibold text-slate-800">{{ item.code }}</div>
            <div class="truncate text-sm text-slate-500">{{ buildInviteLink(item.code) }}</div>
            <t-button variant="outline" size="small" @click="copyInviteCode(item)">复制</t-button>
          </div>
        </div>
      </div>
    </t-dialog>

    <t-dialog v-model:visible="editDialogVisible" header="编辑邀请码" width="620px" :footer="false">
      <t-form :data="editForm" label-align="top" class="overflow-x-hidden p-1">
        <div class="grid grid-cols-2 gap-4">
          <t-form-item label="邀请码" class="col-span-2">
            <div class="invite-dialog-readonly">{{ editForm.code || "-" }}</div>
          </t-form-item>
          <t-form-item label="当前状态">
            <div class="invite-dialog-readonly">
              {{ currentEditRecord ? getStatusMeta(currentEditRecord.adminStatus).label : "-" }}
            </div>
          </t-form-item>
          <t-form-item label="可用性">
            <div class="invite-dialog-readonly">
              {{
                currentEditRecord
                  ? getAvailabilityMeta(getAvailability(currentEditRecord)).label
                  : "-"
              }}
            </div>
          </t-form-item>
          <template v-if="currentEditRecord && !currentEditRecord.registration">
            <t-form-item label="过期时间">
              <t-date-picker
                v-model="editForm.expireAt"
                enable-time-picker
                clearable
                value-type="YYYY-MM-DD HH:mm:ss"
                format="YYYY-MM-DD HH:mm:ss"
              />
            </t-form-item>
            <t-form-item label="永不过期">
              <div class="invite-dialog-switch-row">
                <t-checkbox v-model="editForm.forever">开启后忽略过期时间</t-checkbox>
              </div>
            </t-form-item>
          </template>
          <t-form-item v-else label="注册信息" class="col-span-2">
            <div class="invite-dialog-readonly leading-6">
              <template v-if="currentEditRecord?.registration">
                已绑定账号：{{ currentEditRecord.registration.nickname }}（ID：{{
                  currentEditRecord.registration.userId
                }}）
                <br />
                注册邮箱：{{ currentEditRecord.registration.email }}
                <br />
                注册时间：{{ formatDateTime(currentEditRecord.registration.usedAt) }}
                <br />
                账号状态：{{ currentEditRecord.registration.accountStatus }}
              </template>
            </div>
          </t-form-item>
          <t-form-item label="备注" class="col-span-2">
            <t-textarea
              v-model="editForm.remark"
              :autosize="{ minRows: 3, maxRows: 5 }"
              placeholder="补充对当前邀请码的说明"
            />
          </t-form-item>
        </div>
        <div class="mt-6 flex justify-end gap-3">
          <t-button variant="outline" @click="editDialogVisible = false">取消</t-button>
          <t-button theme="primary" @click="submitEdit">保存</t-button>
        </div>
      </t-form>
    </t-dialog>
  </PageFrame>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useWindowSize } from "@vueuse/core";
import dayjs from "dayjs";
import {
  MessagePlugin,
  type DateRangeValue,
  type PrimaryTableCol,
  type TableRowData,
  type TagProps,
} from "tdesign-vue-next";
import PageFrame from "@/components/PageFrame.vue";
import { inviteCodeApi } from "@/api/invite-code";
import { PermissionConstant } from "@/constant/PermissionConstant";
import useNewPermission from "@/hooks/useNewPermission";
import usePermission from "@/hooks/usePermission";
import { useNewPermissionStore } from "@/store/new-permission";
import type {
  InviteCodeBatchCreateResult,
  InviteCodeDetail,
  InviteCodePageItem,
  InviteCodeUpdateParam,
} from "@/types/invite-code";

type AdminStatus = "enabled" | "disabled";
type RuntimeAvailability = "available" | "used" | "expired" | "disabled";
type AccountStatus = "正常" | "禁用" | string;

interface InviteRegistration {
  userId: number;
  nickname: string;
  email: string;
  usedAt: string;
  accountStatus: AccountStatus;
}

interface InviteCodeRecord extends TableRowData {
  id: number;
  code: string;
  adminStatus: AdminStatus;
  creator: string;
  createdAt: string;
  expireAt: string | null;
  forever: boolean;
  remark: string;
  registration: InviteRegistration | null;
}

interface InviteStatusOption {
  label: string;
  value: AdminStatus;
}

interface InviteAvailabilityOption {
  label: string;
  value: RuntimeAvailability;
}

interface InviteQueryParams {
  keyword: string;
  status: AdminStatus | undefined;
  availability: RuntimeAvailability | undefined;
  creator: string | undefined;
}

interface PaginationChangeContext {
  current: number;
  pageSize: number;
}

interface CreateInviteForm {
  code: string;
  expireAt: string;
  forever: boolean;
  remark: string;
}

interface BatchInviteForm {
  quantity: string;
  prefix: string;
  expireAt: string;
  forever: boolean;
  remark: string;
}

interface EditInviteForm {
  code: string;
  expireAt: string;
  forever: boolean;
  remark: string;
}

type PermissionMode = "legacy" | "new";

interface Props {
  permissionMode?: PermissionMode;
}

const props = withDefaults(defineProps<Props>(), {
  permissionMode: "legacy",
});

const INVITE_CODE_MANAGE_PERMISSION_CODE = PermissionConstant.INVITE_CODE_MANAGE;

const mapAdminStatus = (status: number): AdminStatus => (status === 1 ? "enabled" : "disabled");

const mapAccountStatus = (status?: string | null): AccountStatus => {
  if (status === "正常" || status === "禁用") {
    return status;
  }
  return status || "正常";
};

const mapRecord = (item: InviteCodePageItem | InviteCodeDetail): InviteCodeRecord => ({
  id: item.id,
  code: item.code,
  adminStatus: mapAdminStatus(item.status),
  creator: item.creatorName,
  createdAt: item.createdAt,
  expireAt: item.expireTime || null,
  forever: item.forever,
  remark: item.remark || "",
  registration:
    item.usedUserId && item.usedUserId > 0
      ? {
          userId: item.usedUserId,
          nickname: item.registrationNickname || `用户 ${item.usedUserId}`,
          email: item.registrationEmail || "-",
          usedAt: item.usedAt || "",
          accountStatus: mapAccountStatus(item.registrationAccountStatus),
        }
      : null,
});

const statusOptions: InviteStatusOption[] = [
  { label: "启用", value: "enabled" },
  { label: "停用", value: "disabled" },
];

const availabilityOptions: InviteAvailabilityOption[] = [
  { label: "可用", value: "available" },
  { label: "已使用", value: "used" },
  { label: "已过期", value: "expired" },
  { label: "已停用", value: "disabled" },
];

const toolbarFieldClass =
  "w-full [&_.t-input__wrap]:min-h-9 [&_.t-input__wrap]:rounded [&_.t-input__wrap]:border-slate-200 [&_.t-input__wrap]:bg-white [&_.t-input__wrap]:shadow-none [&_.t-input__wrap:hover]:border-slate-300 [&_.t-is-focused]:border-sky-500 [&_.t-is-focused]:shadow-[0_0_0_3px_rgb(14_165_233_/_0.12)]";
const inviteTableHeaderClass =
  "!bg-white !text-slate-500 !text-sm !font-semibold whitespace-nowrap";
const inviteTableBodyClass = "!py-2 text-sm text-slate-700 align-middle";

const { hasPermission } = usePermission();
const { hasButtonPermission } = useNewPermission();
const newPermissionStore = useNewPermissionStore();

const canManageInviteCodes = computed(() =>
  props.permissionMode === "new"
    ? hasButtonPermission(INVITE_CODE_MANAGE_PERMISSION_CODE)
    : hasPermission(INVITE_CODE_MANAGE_PERMISSION_CODE)
);
const hasInviteCodeAction = (permissionCode: string) =>
  props.permissionMode === "new" ? hasButtonPermission(permissionCode) : canManageInviteCodes.value;
const canCreateInviteCode = computed(() => hasInviteCodeAction(PermissionConstant.INVITE_CODE_CREATE));
const canBatchCreateInviteCode = computed(() =>
  hasInviteCodeAction(PermissionConstant.INVITE_CODE_BATCH_CREATE)
);
const canUpdateInviteCode = computed(() => hasInviteCodeAction(PermissionConstant.INVITE_CODE_UPDATE));
const canEnableInviteCode = computed(() => hasInviteCodeAction(PermissionConstant.INVITE_CODE_ENABLE));
const canDisableInviteCode = computed(() => hasInviteCodeAction(PermissionConstant.INVITE_CODE_DISABLE));
const canCopyInviteCode = computed(() => hasInviteCodeAction(PermissionConstant.INVITE_CODE_COPY));

const hasVisibleOperation = (record: InviteCodeRecord) =>
  canUpdateInviteCode.value || canCopyInviteCode.value || canToggleStatus(record);

const { width } = useWindowSize();
const isMobile = computed(() => width.value <= 768);
const loading = ref(false);
const inviteRecords = ref<InviteCodeRecord[]>([]);
const selectedRowKeys = ref<(string | number)[]>([]);
const showAdvancedFilters = ref(!isMobile.value);
const bodyRef = ref<HTMLElement | null>(null);
const handleBodyRefChange = (element: HTMLElement | null) => {
  bodyRef.value = element;
};

const queryParams = reactive<InviteQueryParams>({
  keyword: "",
  status: undefined,
  availability: undefined,
  creator: undefined,
});
const dateRange = ref<DateRangeValue>([]);
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
});

const detailDrawerVisible = ref(false);
const createDialogVisible = ref(false);
const batchDialogVisible = ref(false);
const batchResultMode = ref(false);
const editDialogVisible = ref(false);
const currentDetailRecord = ref<InviteCodeRecord | null>(null);
const currentEditRecordId = ref<number | null>(null);
const batchResult = ref<InviteCodeRecord[]>([]);

const createForm = reactive<CreateInviteForm>({
  code: "",
  expireAt: "",
  forever: false,
  remark: "",
});

const batchForm = reactive<BatchInviteForm>({
  quantity: "5",
  prefix: "",
  expireAt: "",
  forever: false,
  remark: "",
});

const editForm = reactive<EditInviteForm>({
  code: "",
  expireAt: "",
  forever: false,
  remark: "",
});

const creatorOptions = computed(() =>
  Array.from(new Set(inviteRecords.value.map((item) => item.creator)))
);

const creatorIdMap = computed<Record<string, number>>(() => {
  const map: Record<string, number> = {};
  inviteRecords.value.forEach((item) => {
    if (item.creator === "系统") {
      map[item.creator] = 0;
    }
  });
  return map;
});

const normalizeDateValue = (value: unknown) => (typeof value === "string" ? value : undefined);

const loadInviteCodes = async () => {
  loading.value = true;
  try {
    const [startDate, endDate] = Array.isArray(dateRange.value) ? dateRange.value : [];
    const data = await inviteCodeApi.getPage({
      page: pagination.current,
      pageSize: pagination.pageSize,
      keyword: queryParams.keyword || undefined,
      status:
        queryParams.status === undefined ? undefined : queryParams.status === "enabled" ? 1 : 0,
      availability: queryParams.availability,
      issuerUserId: queryParams.creator ? creatorIdMap.value[queryParams.creator] : undefined,
      startDate: normalizeDateValue(startDate),
      endDate: normalizeDateValue(endDate),
    });
    inviteRecords.value = data.records.map(mapRecord);
    pagination.total = data.total;
    pagination.current = data.current;
    pagination.pageSize = data.size;
  } finally {
    loading.value = false;
  }
};

const getAvailability = (record: InviteCodeRecord): RuntimeAvailability => {
  if (record.registration) {
    return "used";
  }
  if (record.adminStatus === "disabled") {
    return "disabled";
  }
  if (!record.forever && record.expireAt && dayjs(record.expireAt).isBefore(dayjs())) {
    return "expired";
  }
  return "available";
};

const getStatusMeta = (
  status: AdminStatus
): { label: string; theme: NonNullable<TagProps["theme"]> } => {
  if (status === "enabled") {
    return { label: "启用", theme: "primary" };
  }
  return { label: "停用", theme: "default" };
};

const getAvailabilityMeta = (
  availability: RuntimeAvailability
): { label: string; theme: NonNullable<TagProps["theme"]> } => {
  if (availability === "available") {
    return { label: "可用", theme: "success" };
  }
  if (availability === "used") {
    return { label: "已使用", theme: "primary" };
  }
  if (availability === "expired") {
    return { label: "已过期", theme: "warning" };
  }
  return { label: "已停用", theme: "default" };
};

const formatDateTime = (value?: string | null) =>
  value ? dayjs(value).format("YYYY-MM-DD HH:mm:ss") : "-";
const formatExpireAt = (record: InviteCodeRecord) =>
  record.forever ? "永不过期" : formatDateTime(record.expireAt);
const buildInviteLink = (code: string) => {
  const url = new URL("/login", window.location.origin);
  url.searchParams.set("mode", "register");
  url.searchParams.set("inviteCode", code);
  return url.toString();
};
onMounted(() => {
  if (props.permissionMode === "new") {
    void newPermissionStore.loadButtonPermissions();
  }
  void loadInviteCodes();
});

const pagedRecords = computed(() => inviteRecords.value);
const shouldShowOperationColumn = computed(() => {
  if (!canManageInviteCodes.value) {
    return false;
  }
  if (props.permissionMode !== "new") {
    return true;
  }
  return pagedRecords.value.some((record) => hasVisibleOperation(record));
});

const columns = computed<PrimaryTableCol[]>(() => {
  const baseColumns: PrimaryTableCol[] = [
    {
      colKey: "code",
      title: "邀请码",
      width: 220,
      cell: "code",
      className: inviteTableBodyClass,
      thClassName: inviteTableHeaderClass,
    },
    {
      colKey: "status",
      title: "状态",
      width: 110,
      cell: "status",
      className: inviteTableBodyClass,
      thClassName: inviteTableHeaderClass,
    },
    {
      colKey: "availability",
      title: "可用性",
      width: 110,
      cell: "availability",
      className: inviteTableBodyClass,
      thClassName: inviteTableHeaderClass,
    },
    {
      colKey: "account",
      title: "注册账号",
      width: 190,
      cell: "account",
      className: inviteTableBodyClass,
      thClassName: inviteTableHeaderClass,
    },
    {
      colKey: "email",
      title: "注册邮箱",
      width: 220,
      cell: "email",
      className: inviteTableBodyClass,
      thClassName: inviteTableHeaderClass,
    },
    {
      colKey: "usedAt",
      title: "使用时间",
      width: 180,
      cell: "usedAt",
      className: inviteTableBodyClass,
      thClassName: inviteTableHeaderClass,
    },
    {
      colKey: "creator",
      title: "创建人",
      width: 120,
      className: inviteTableBodyClass,
      thClassName: inviteTableHeaderClass,
    },
    {
      colKey: "expireAt",
      title: "有效期",
      width: 180,
      cell: "expireAt",
      className: inviteTableBodyClass,
      thClassName: inviteTableHeaderClass,
    },
    {
      colKey: "remark",
      title: "备注",
      width: 220,
      cell: "remark",
      className: inviteTableBodyClass,
      thClassName: inviteTableHeaderClass,
    },
    {
      colKey: "createdAt",
      title: "创建时间",
      width: 180,
      cell: "createdAt",
      className: inviteTableBodyClass,
      thClassName: inviteTableHeaderClass,
    },
  ];

  if (canManageInviteCodes.value) {
    baseColumns.unshift({
      colKey: "row-select",
      type: "multiple",
      width: 56,
      fixed: "left",
      className: `${inviteTableBodyClass} !bg-white`,
      thClassName: inviteTableHeaderClass,
    });

    if (shouldShowOperationColumn.value) {
      baseColumns.push({
        colKey: "operation",
        title: "操作",
        width: 260,
        fixed: "right",
        cell: "operation",
        className: `${inviteTableBodyClass} !bg-white`,
        thClassName: inviteTableHeaderClass,
      });
    }
  }

  return baseColumns;
});

const selectedRecords = computed(() => {
  const selectedSet = new Set(selectedRowKeys.value.map((item) => Number(item)));
  return inviteRecords.value.filter((item) => selectedSet.has(Number(item.id)));
});

const currentEditRecord = computed(() => {
  if (currentEditRecordId.value === null) {
    return null;
  }
  return inviteRecords.value.find((item) => item.id === currentEditRecordId.value) ?? null;
});

watch(
  [() => pagination.current, () => pagination.pageSize],
  () => {
    void loadInviteCodes();
  },
  { immediate: true }
);

watch(isMobile, (mobile) => {
  showAdvancedFilters.value = !mobile;
});

const syncTableState = () => {
  pagination.current = 1;
  selectedRowKeys.value = [];
};

const handleSearch = () => {
  syncTableState();
  void loadInviteCodes();
};

const handleReset = () => {
  queryParams.keyword = "";
  queryParams.status = undefined;
  queryParams.availability = undefined;
  queryParams.creator = undefined;
  dateRange.value = [];
  syncTableState();
};

const handleSelectChange = (value: (string | number)[]) => {
  selectedRowKeys.value = value;
};

const handleMobileSelectChange = (id: number, checked: boolean) => {
  const nextKey = Number(id);
  if (checked) {
    selectedRowKeys.value = Array.from(new Set([...selectedRowKeys.value, nextKey]));
    return;
  }
  selectedRowKeys.value = selectedRowKeys.value.filter((item) => Number(item) !== nextKey);
};

const onPageChange = (pageInfo: PaginationChangeContext) => {
  pagination.current = pageInfo.current;
  pagination.pageSize = pageInfo.pageSize;
  selectedRowKeys.value = [];
  void loadInviteCodes();
};

const clearSelection = () => {
  selectedRowKeys.value = [];
};

const toggleAdvancedFilters = () => {
  showAdvancedFilters.value = !showAdvancedFilters.value;
};

const copyText = async (text: string, successMessage: string) => {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
    } else {
      const textarea = document.createElement("textarea");
      textarea.value = text;
      textarea.style.position = "fixed";
      textarea.style.top = "-9999px";
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand("copy");
      document.body.removeChild(textarea);
    }
    MessagePlugin.success(successMessage);
  } catch (error) {
    console.error("复制失败", error);
    MessagePlugin.error("复制失败，请手动复制");
  }
};

const copyInviteCode = (record: InviteCodeRecord) => copyText(record.code, "邀请码已复制");
const copyInviteLink = (record: InviteCodeRecord) =>
  copyText(buildInviteLink(record.code), "注册链接已复制");
const copyAllCodes = () =>
  copyText(batchResult.value.map((item) => item.code).join("\n"), "全部邀请码已复制");
const copyAllLinks = () =>
  copyText(
    batchResult.value.map((item) => buildInviteLink(item.code)).join("\n"),
    "全部注册链接已复制"
  );

const canToggleStatus = (record: InviteCodeRecord) => {
  const statusPermission =
    record.adminStatus === "enabled" ? canDisableInviteCode.value : canEnableInviteCode.value;
  return statusPermission && !record.registration && getAvailability(record) !== "expired";
};

const toggleStatus = async (record: InviteCodeRecord) => {
  if (!canToggleStatus(record)) {
    MessagePlugin.warning("当前邀请码不可直接启停，请先检查使用状态或有效期");
    return;
  }
  const nextStatus = record.adminStatus === "enabled" ? 0 : 1;
  await inviteCodeApi.updateStatus(record.id, nextStatus);
  MessagePlugin.success(nextStatus === 1 ? "邀请码已启用" : "邀请码已停用");
  await loadInviteCodes();
};

const handleBatchDisable = async () => {
  if (!canDisableInviteCode.value) {
    return;
  }
  if (selectedRecords.value.length === 0) {
    MessagePlugin.warning("请先选择邀请码");
    return;
  }
  const ids = selectedRecords.value
    .filter((record) => canToggleStatus(record))
    .map((record) => record.id);
  const skippedCount = selectedRecords.value.length - ids.length;
  if (ids.length > 0) {
    await inviteCodeApi.batchDisable(ids);
    MessagePlugin.success(`已停用 ${ids.length} 个邀请码`);
  }
  if (skippedCount > 0) {
    MessagePlugin.warning(`${skippedCount} 个邀请码因已使用或已过期而跳过`);
  }
  clearSelection();
  await loadInviteCodes();
};

const resetCreateForm = () => {
  createForm.code = "";
  createForm.expireAt = "";
  createForm.forever = false;
  createForm.remark = "";
};

const openCreateDialog = () => {
  if (!canCreateInviteCode.value) {
    return;
  }
  resetCreateForm();
  createDialogVisible.value = true;
};

const submitCreate = async () => {
  if (!canCreateInviteCode.value) {
    return;
  }
  if (!createForm.forever && !createForm.expireAt) {
    MessagePlugin.warning("请选择过期时间，或开启永不过期");
    return;
  }
  const created = await inviteCodeApi.create({
    code: createForm.code.trim() || undefined,
    forever: createForm.forever,
    expireTime: createForm.forever ? undefined : createForm.expireAt || undefined,
    remark: createForm.remark.trim() || undefined,
  });
  createDialogVisible.value = false;
  syncTableState();
  await loadInviteCodes();
  MessagePlugin.success(`邀请码 ${created.code} 创建成功`);
};

const resetBatchForm = () => {
  batchForm.quantity = "5";
  batchForm.prefix = "";
  batchForm.expireAt = "";
  batchForm.forever = false;
  batchForm.remark = "";
};

const resetBatchDialog = () => {
  batchResultMode.value = false;
  batchResult.value = [];
  resetBatchForm();
};

const openBatchDialog = () => {
  if (!canBatchCreateInviteCode.value) {
    return;
  }
  resetBatchDialog();
  batchDialogVisible.value = true;
};

const submitBatchGenerate = async () => {
  if (!canBatchCreateInviteCode.value) {
    return;
  }
  const quantity = Number(batchForm.quantity);
  if (!Number.isInteger(quantity) || quantity < 1 || quantity > 100) {
    MessagePlugin.warning("生成数量需为 1-100 之间的整数");
    return;
  }
  if (!batchForm.forever && !batchForm.expireAt) {
    MessagePlugin.warning("请选择过期时间，或开启永不过期");
    return;
  }
  const result: InviteCodeBatchCreateResult = await inviteCodeApi.batchCreate({
    quantity,
    prefix: batchForm.prefix.trim() || undefined,
    forever: batchForm.forever,
    expireTime: batchForm.forever ? undefined : batchForm.expireAt || undefined,
    remark: batchForm.remark.trim() || undefined,
  });
  batchResult.value = result.records.map((item) => ({
    id: item.id,
    code: item.code,
    adminStatus: "enabled",
    creator: "系统",
    createdAt: dayjs().format("YYYY-MM-DD HH:mm:ss"),
    expireAt: item.expireTime || null,
    forever: item.forever,
    remark: item.remark,
    registration: null,
  }));
  batchResultMode.value = true;
  syncTableState();
  await loadInviteCodes();
  MessagePlugin.success(`已生成 ${quantity} 个邀请码`);
};

const openDetailDrawer = async (record: InviteCodeRecord) => {
  const detail = await inviteCodeApi.getDetail(record.id);
  currentDetailRecord.value = mapRecord(detail);
  detailDrawerVisible.value = true;
};

const openEditDialog = async (record: InviteCodeRecord) => {
  if (!canUpdateInviteCode.value) {
    return;
  }
  const detail = await inviteCodeApi.getDetail(record.id);
  const mapped = mapRecord(detail);
  currentEditRecordId.value = mapped.id;
  editForm.code = mapped.code;
  editForm.expireAt = mapped.expireAt || "";
  editForm.forever = mapped.forever;
  editForm.remark = mapped.remark;
  editDialogVisible.value = true;
};

const submitEdit = async () => {
  if (!canUpdateInviteCode.value) {
    return;
  }
  if (!currentEditRecord.value) {
    return;
  }
  if (!currentEditRecord.value.registration && !editForm.forever && !editForm.expireAt) {
    MessagePlugin.warning("请选择过期时间，或开启永不过期");
    return;
  }
  const payload: InviteCodeUpdateParam = {
    id: currentEditRecord.value.id,
    remark: editForm.remark.trim() || undefined,
  };
  if (!currentEditRecord.value.registration) {
    payload.forever = editForm.forever;
    payload.expireTime = editForm.forever ? undefined : editForm.expireAt || undefined;
  }
  await inviteCodeApi.update(payload);
  editDialogVisible.value = false;
  await loadInviteCodes();
  MessagePlugin.success("邀请码信息已更新");
};
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
  user-select: none;
}

.jsh-expand-link:hover {
  color: rgb(15 23 42);
}

:deep(.jsh-action-btn.t-button) {
  min-width: 88px;
  border-radius: 4px;
  box-shadow: none;
}

.jsh-ledger-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100%;
}

:deep(.invite-code-table .t-table__header th) {
  padding-top: 10px;
  padding-bottom: 10px;
}

:deep(.invite-code-table .t-table__body td) {
  padding-top: 8px;
  padding-bottom: 8px;
}

:deep(.invite-code-table .t-table) {
  border: none;
  border-radius: 0;
  box-shadow: none;
}

:deep(.invite-code-table .t-table__content) {
  border: none;
  border-radius: 0;
}

:deep(.invite-code-table .t-table__header) {
  overflow: visible;
  border-top-left-radius: 0;
  border-top-right-radius: 0;
}

:deep(.invite-code-table .t-table__row--hover td) {
  background: #fcfcfc !important;
}

.invite-link-btn {
  border: 0;
  background: transparent;
  padding: 0;
  color: rgb(37 99 235);
  cursor: pointer;
}

.invite-link-btn:hover {
  color: rgb(29 78 216);
}

:deep(.invite-code-table__action-btn.t-button) {
  min-width: 64px;
  padding-right: 12px;
  padding-left: 12px;
  border-radius: 4px;
  box-shadow: none;
}

.invite-drawer-section {
  border: 1px solid rgb(226 232 240);
  border-radius: 6px;
  background: #fff;
  padding: 16px;
}

.invite-drawer-section__title {
  margin-bottom: 12px;
  color: rgb(71 85 105);
  font-size: 13px;
  font-weight: 700;
}

.invite-drawer-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 16px;
}

.invite-drawer-item {
  min-width: 0;
}

.invite-drawer-item__label {
  margin-bottom: 6px;
  color: rgb(148 163 184);
  font-size: 12px;
}

.invite-drawer-item__value {
  color: rgb(51 65 85);
  font-size: 13px;
  line-height: 1.6;
  word-break: break-all;
}

.invite-drawer-empty {
  padding: 12px 0;
}

.invite-dialog-switch-row {
  display: flex;
  min-height: 40px;
  align-items: center;
}

.invite-dialog-readonly {
  min-height: 40px;
  border: 1px solid rgb(226 232 240);
  border-radius: 6px;
  background: rgb(248 250 252);
  padding: 9px 12px;
  color: rgb(51 65 85);
  line-height: 1.6;
}

.invite-mobile__empty {
  padding: 24px 0;
}

.invite-mobile__list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.invite-mobile-card {
  border: 1px solid rgb(226 232 240);
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.05);
  padding: 14px 12px;
}

.invite-mobile-card__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
  margin-top: 12px;
}

.invite-mobile-card__meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.invite-mobile-card__meta-item--full {
  grid-column: 1 / -1;
}

.invite-mobile-card__meta-label {
  color: rgb(148 163 184);
  font-size: 12px;
}

.invite-mobile-card__meta-value {
  color: rgb(51 65 85);
  font-size: 13px;
  line-height: 1.5;
}

.invite-mobile-card__actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 14px;
}

.invite-mobile__pagination {
  padding: 16px 0 8px;
}

@media (max-width: 768px) {
  .invite-code-body {
    overscroll-behavior: contain;
    -webkit-overflow-scrolling: touch;
  }

  .jsh-filter-item {
    display: flex;
    flex-direction: column;
    width: 100%;
    align-items: stretch;
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

  .task-selection-summary {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
  }

  .invite-drawer-grid,
  .invite-mobile-card__meta {
    grid-template-columns: 1fr;
  }
}
</style>
