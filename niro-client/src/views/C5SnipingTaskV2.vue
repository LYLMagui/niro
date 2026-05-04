<template>
  <PageFrame
    :is-mobile="isMobile"
    body-class="c5-sniping-v2-body"
    desktop-outer-class="!p-0"
    desktop-content-class="px-4 pt-0 pb-0"
    mobile-content-class="px-3 pt-3 pb-0"
  >
    <PageHeader title="C5 扫货任务">
      <template #icon>
        <svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
          />
        </svg>
      </template>
      <template #extra>
        <div v-if="isMobile" class="flex items-center gap-2 mr-1">
          <t-button
            v-if="canCreateTask"
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
        <div v-if="!isMobile" class="flex flex-col items-end">
          <span class="text-[10px] font-bold tracking-wider text-slate-400 uppercase">
            任务总数
          </span>
          <span class="font-numeric text-base font-bold text-slate-900">
            {{ pagination.total }}
            <small class="text-[10px] font-medium text-slate-400">项</small>
          </span>
        </div>
      </template>
    </PageHeader>

    <div :class="['flex flex-col bg-white px-0 py-4', isMobile ? 'gap-3' : 'gap-6']">
      <!-- 移动端统计数据条 -->
      <div
        v-if="isMobile"
        class="mx-0 flex items-center justify-between rounded-lg bg-slate-50/80 px-3 py-2 text-xs"
      >
        <div class="flex items-center gap-1.5">
          <span class="text-slate-400">任务总数:</span>
          <span class="font-bold text-slate-700">{{ pagination.total }} 项</span>
        </div>
      </div>
      <div class="flex flex-col gap-3 px-0 py-4">
        <div
          v-if="!isMobile || showFilters"
          class="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,280px)_minmax(0,200px)_minmax(0,160px)_auto] xl:items-end"
        >
          <label class="flex min-w-0 flex-col gap-1.5">
            <span class="text-sm font-medium text-slate-700">任务关键词</span>
            <t-input
              v-model="queryParams.keyword"
              placeholder="任务/商品关键词"
              clearable
              class="w-full"
              @enter="fetchData"
            />
          </label>
          <label class="flex min-w-0 flex-col gap-1.5">
            <span class="text-sm font-medium text-slate-700">任务状态</span>
            <t-select
              v-model="queryParams.taskStatus"
              :options="taskStatusOptions"
              placeholder="请选择任务状态"
              clearable
              class="w-full"
              @change="fetchData"
            />
          </label>
          <label class="flex min-w-0 flex-col gap-1.5">
            <span class="text-sm font-medium text-slate-700">C5 账号</span>
            <t-select
              v-model="queryParams.accountId"
              clearable
              filterable
              :loading="accountsLoading"
              placeholder="请选择 C5 账号"
              class="w-full"
              @change="fetchData"
            >
              <t-option
                v-for="item in c5Accounts"
                :key="item.id"
                :value="item.id"
                :label="getAccountOptionLabel(item)"
                :disabled="!isNormalAccount(item)"
              >
                <div class="flex w-full items-center justify-between gap-2 overflow-hidden">
                  <span class="truncate">{{ getAccountOptionLabel(item) }}</span>
                  <t-tag
                    :theme="getAccountStatusMeta(item.status).theme"
                    variant="light"
                    size="small"
                    class="shrink-0"
                  >
                    {{ getAccountStatusMeta(item.status).label }}
                  </t-tag>
                </div>
              </t-option>
            </t-select>
          </label>
          <div class="flex flex-wrap items-center gap-2">
            <t-button theme="primary" class="c5-sniping-v2-action-btn" @click="fetchData">
              查询
            </t-button>
            <t-button
              variant="outline"
              theme="default"
              class="c5-sniping-v2-action-btn"
              @click="resetQuery"
            >
              重置
            </t-button>
          </div>
        </div>

        <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div class="flex flex-wrap items-center gap-2">
            <t-button
              v-if="canCreateTask"
              theme="primary"
              class="c5-sniping-v2-action-btn"
              @click="openCreateDialog"
            >
              新增扫货任务
            </t-button>
          </div>
        </div>
      </div>

    <div class="relative min-h-0 flex-1">
      <div class="relative flex h-full min-h-0 flex-col overflow-hidden">
        <div class="min-h-0 flex-1 overflow-hidden">
          <!-- 移动端卡片视图 -->
          <div v-if="isMobile" class="h-full overflow-y-auto bg-slate-50 p-3">
            <div v-if="loading" class="flex h-32 items-center justify-center">
              <t-loading size="medium" text="加载中..." />
            </div>
            <div v-else-if="dataList.length === 0" class="py-8">
              <t-empty description="暂无扫货任务" />
            </div>
            <div v-else class="flex flex-col gap-3">
              <div
                v-for="row in dataList"
                :key="row.id"
                class="flex flex-col overflow-hidden rounded-lg bg-white shadow-sm ring-1 ring-slate-100"
              >
                <!-- 头部：状态和基础信息 -->
                <div class="flex items-start justify-between border-b border-slate-100 p-3">
                  <div class="flex min-w-0 flex-1 items-center gap-2">
                    <t-image
                      v-if="row.goodsIconUrl"
                      :src="row.goodsIconUrl"
                      referrerpolicy="no-referrer"
                      class="h-10 w-10 shrink-0 rounded bg-slate-50"
                    />
                    <div class="min-w-0 flex-1">
                      <div class="truncate text-sm font-bold text-slate-800">
                        {{ row.goodsDisplayName || row.name || "未命名任务" }}
                      </div>
                      <div class="mt-0.5 truncate text-[11px] text-slate-500">
                        账号：{{ getTaskAccountName(row.accountId) }}
                      </div>
                    </div>
                  </div>
                  <div class="ml-2 flex shrink-0 items-center gap-1.5">
                    <div
                      :class="[
                        'h-1.5 w-1.5 rounded-full',
                        getTaskStatusMeta(row.taskStatus).dotClass,
                      ]"
                    ></div>
                    <t-tag
                      :theme="getTaskStatusMeta(row.taskStatus).theme"
                      variant="light-outline"
                      size="small"
                      class="origin-right scale-90 font-medium"
                    >
                      {{ getTaskStatusMeta(row.taskStatus).label }}
                    </t-tag>
                  </div>
                </div>

                <!-- 内容区：目标配置、实战数据 -->
                <div class="flex flex-col gap-2 p-3 text-xs text-slate-600">
                  <div class="flex items-center justify-between">
                    <span class="text-slate-400">最高价格</span>
                    <span class="font-medium text-red-600">{{ formatPrice(row.maxPrice) }}</span>
                  </div>
                  <div class="flex items-center justify-between">
                    <span class="text-slate-400">磨损范围</span>
                    <span>{{ formatPaintwear(row) }}</span>
                  </div>
                  <div class="flex items-center justify-between">
                    <span class="text-slate-400">停止规则</span>
                    <div class="text-right">
                      <div>{{ getStopModeLabel(row.stopMode) }}</div>
                      <div v-if="row.stopMode === 'BUY_COUNT'" class="text-[10px] text-slate-500">
                        目标: {{ row.targetBuyCount ?? "-" }}
                      </div>
                      <div
                        v-else-if="row.balanceGuardMode === 'RESERVE_BALANCE'"
                        class="text-[10px] text-slate-500"
                      >
                        保底: {{ formatPrice(row.reserveBalance) }}
                      </div>
                    </div>
                  </div>

                  <div class="mt-1 rounded bg-slate-50 p-2">
                    <div class="mb-1 flex items-center justify-between">
                      <span class="text-slate-500">成功/预占 (命中: {{ row.hitCount ?? 0 }})</span>
                      <span class="font-bold text-emerald-600">
                        {{ row.successBuyCount ?? 0 }} / {{ row.reservedBuyCount ?? 0 }}
                      </span>
                    </div>
                    <div class="h-1 w-full overflow-hidden rounded-full bg-slate-200">
                      <div
                        class="h-full bg-emerald-500 transition-all duration-500"
                        :style="{ width: getSuccessProgress(row) + '%' }"
                      ></div>
                    </div>
                  </div>

                  <div v-if="row.lastErrorMessage" class="mt-1 truncate text-[11px] text-rose-500">
                    错误: {{ row.lastErrorMessage }}
                  </div>
                </div>

                <!-- 底部操作区 -->
                <div
                  class="flex flex-wrap items-center justify-end gap-1.5 border-t border-slate-50 bg-slate-50/50 p-2"
                >
                  <t-button
                    v-if="canEnableTask && isEnableVisible(row.taskStatus)"
                    variant="outline"
                    theme="success"
                    class="h-7 px-2 text-xs"
                    @click="enableTask(row)"
                  >
                    开启
                  </t-button>
                  <t-button
                    v-else-if="canDisableTask && isDisableVisible(row.taskStatus)"
                    variant="outline"
                    theme="warning"
                    class="h-7 px-2 text-xs"
                    @click="disableTask(row)"
                  >
                    停止
                  </t-button>

                  <t-button
                    v-if="canReadTaskDetail || canUpdateTask"
                    variant="outline"
                    theme="default"
                    class="h-7 px-2 text-xs"
                    :disabled="row.taskStatus === 'RUNNING'"
                    @click="
                      row.taskStatus === 'COMPLETED' ? openViewDialog(row) : openEditDialog(row)
                    "
                  >
                    {{ row.taskStatus === "COMPLETED" ? "详情" : "编辑" }}
                  </t-button>

                  <t-popconfirm
                    v-if="canDeleteTask"
                    content="确定删除该任务吗？"
                    :disabled="row.taskStatus === 'RUNNING'"
                    @confirm="deleteTask(row)"
                  >
                    <t-button
                      variant="outline"
                      theme="danger"
                      class="h-7 px-2 text-xs"
                      :disabled="row.taskStatus === 'RUNNING'"
                    >
                      删除
                    </t-button>
                  </t-popconfirm>

                  <t-dropdown
                    :options="getTaskOperationOptions(row)"
                    trigger="click"
                    @click="(data) => handleOpDropdown(String(data.value || ''), row)"
                  >
                    <t-button variant="text" shape="square" class="h-7 w-7 !text-slate-400">
                      <t-icon name="ellipsis" />
                    </t-button>
                  </t-dropdown>
                </div>
              </div>
            </div>
          </div>

          <!-- 桌面端表格视图 -->
          <t-table
            v-else
            row-key="id"
            :data="dataList"
            :columns="columns"
            :loading="loading"
            :pagination="undefined"
            hover
            class="c5-sniping-v2-table w-full bg-white"
          >
            <template #empty>
              <div class="py-8">
                <t-empty description="暂无扫货任务" />
              </div>
            </template>

            <template #baseInfo="{ row }">
              <div class="flex min-w-0 items-center">
                <t-image
                  v-if="row.goodsIconUrl"
                  :src="row.goodsIconUrl"
                  referrerpolicy="no-referrer"
                  class="mr-2 h-9 w-9 rounded"
                />
                <div class="min-w-0">
                  <t-tooltip :content="row.goodsDisplayName || row.name" placement="top-left">
                    <div class="max-w-[220px] truncate font-medium text-slate-800">
                      {{ row.goodsDisplayName || row.name || "未命名任务" }}
                    </div>
                  </t-tooltip>
                  <div class="mt-1 text-sm text-slate-500">
                    绑定账号：{{ getTaskAccountName(row.accountId) }}
                  </div>
                  <t-tooltip
                    v-if="row.marketHashName"
                    :content="row.marketHashName"
                    placement="top-left"
                  >
                    <div class="mt-1 max-w-[220px] truncate text-sm text-slate-400">
                      {{ row.marketHashName }}
                    </div>
                  </t-tooltip>
                </div>
              </div>
            </template>

            <template #status="{ row }">
              <div class="flex items-center">
                <div
                  :class="[
                    'mr-2 h-2 w-2 rounded-full ring-2 ring-white',
                    getTaskStatusMeta(row.taskStatus).dotClass,
                  ]"
                ></div>
                <t-tag
                  :theme="getTaskStatusMeta(row.taskStatus).theme"
                  variant="light-outline"
                  class="rounded-[4px] px-2 font-medium"
                >
                  {{ getTaskStatusMeta(row.taskStatus).label }}
                </t-tag>
              </div>
            </template>

            <template #target="{ row }">
              <div class="space-y-1 text-sm text-slate-600">
                <div>
                  最高价格：
                  <span class="font-medium text-red-600">{{ formatPrice(row.maxPrice) }}</span>
                </div>
                <div>磨损范围：{{ formatPaintwear(row) }}</div>
                <div>扫描间隔：{{ formatScanInterval(row.scanIntervalMs) }}</div>
              </div>
            </template>

            <template #stopRule="{ row }">
              <div class="space-y-1 text-sm text-slate-600">
                <div>{{ getStopModeLabel(row.stopMode) }}</div>
                <div v-if="row.stopMode === 'BUY_COUNT'">
                  目标购买数：{{ row.targetBuyCount ?? "-" }}
                </div>
                <div v-else-if="row.balanceGuardMode === 'RESERVE_BALANCE'">
                  保底余额：{{ formatPrice(row.reserveBalance) }}
                </div>
                <div v-else>余额口径：{{ getBalanceGuardModeLabel(row.balanceGuardMode) }}</div>
              </div>
            </template>

            <template #summary="{ row }">
              <div class="flex flex-col gap-1.5 py-1">
                <div class="flex items-center justify-between text-[13px]">
                  <span class="text-slate-500">成功/预占</span>
                  <span class="font-bold text-emerald-600">
                    {{ row.successBuyCount ?? 0 }} / {{ row.reservedBuyCount ?? 0 }}
                  </span>
                </div>
                <div class="h-1 w-full overflow-hidden rounded-full bg-slate-100">
                  <div
                    class="h-full bg-emerald-500 transition-all duration-500"
                    :style="{ width: getSuccessProgress(row) + '%' }"
                  ></div>
                </div>
                <div class="flex items-center justify-between text-[13px]">
                  <span class="text-slate-500">命中次数</span>
                  <span class="font-medium text-slate-700">{{ row.hitCount ?? 0 }}</span>
                </div>
              </div>
            </template>

            <template #lastError="{ row }">
              <t-tooltip :content="row.lastErrorMessage || '-'" placement="top-left">
                <div class="max-w-[220px] truncate text-sm text-slate-600">
                  {{ row.lastErrorMessage || "-" }}
                </div>
              </t-tooltip>
            </template>

            <template #op="{ row }">
              <div class="flex flex-wrap items-center gap-1.5">
                <t-button
                  v-if="canEnableTask && isEnableVisible(row.taskStatus)"
                  variant="outline"
                  theme="success"
                  class="h-7 px-2 text-xs"
                  @click="enableTask(row)"
                >
                  开启
                </t-button>
                <t-button
                  v-else-if="canDisableTask && isDisableVisible(row.taskStatus)"
                  variant="outline"
                  theme="warning"
                  class="h-7 px-2 text-xs"
                  @click="disableTask(row)"
                >
                  停止
                </t-button>

                <t-button
                  v-if="canReadTaskDetail || canUpdateTask"
                  variant="outline"
                  theme="default"
                  class="h-7 px-2 text-xs"
                  :disabled="row.taskStatus === 'RUNNING'"
                  @click="
                    row.taskStatus === 'COMPLETED' ? openViewDialog(row) : openEditDialog(row)
                  "
                >
                  {{ row.taskStatus === "COMPLETED" ? "详情" : "编辑" }}
                </t-button>

                <t-popconfirm
                  v-if="canDeleteTask"
                  content="确定删除该任务吗？"
                  :disabled="row.taskStatus === 'RUNNING'"
                  @confirm="deleteTask(row)"
                >
                  <t-button
                    variant="outline"
                    theme="danger"
                    class="h-7 px-2 text-xs"
                    :disabled="row.taskStatus === 'RUNNING'"
                  >
                    删除
                  </t-button>
                </t-popconfirm>

                <t-dropdown
                  :options="getTaskOperationOptions(row)"
                  trigger="hover"
                  @click="(data) => handleOpDropdown(String(data.value || ''), row)"
                >
                  <t-button variant="text" shape="square" class="h-7 w-7 !text-slate-400">
                    <t-icon name="ellipsis" />
                  </t-button>
                </t-dropdown>
              </div>
            </template>
          </t-table>
        </div>

        <div v-if="pagination.total > 0" class="border-t border-slate-200 bg-white px-4 py-3">
          <t-pagination
            :size="isMobile ? 'small' : 'medium'"
            :theme="isMobile ? 'simple' : 'default'"
            :show-page-size="isMobile ? false : undefined"
            v-model="pagination.current"
            v-model:page-size="pagination.pageSize"
            :total="pagination.total"
            show-jumper
            @change="onPageChange"
          />
        </div>
      </div>
    </div>

    <AppDialog
      v-model:visible="formVisible"
      :title="formTitle"
      width="min(620px, calc(100vw - 32px))"
      :confirm-loading="submitLoading"
      :close-on-overlay-click="false"
      @close="closeFormDialog"
    >
      <t-form
        ref="formRef"
        :data="formData"
        :rules="formRules"
        :disabled="isFormReadonly"
        :label-width="100"
        label-align="right"
        class="compact-form"
        scroll-to-first-error="smooth"
        validation-trigger="submit"
        prevent-submit-default
        @submit="onFormSubmit"
      >
        <div class="form-container">
          <div class="grid grid-cols-1 gap-x-4 lg:grid-cols-2">
            <t-form-item
              label="C5 账号："
              name="accountId"
              requiredMark
              class="col-span-1 lg:col-span-2"
            >
              <t-select
                v-model="formData.accountId"
                clearable
                filterable
                :loading="accountsLoading"
                placeholder="请选择 C5 账号"
                class="task-config-select"
              >
                <t-option
                  v-for="item in c5Accounts"
                  :key="item.id"
                  :value="item.id"
                  :label="getAccountOptionLabel(item)"
                  :disabled="!isNormalAccount(item)"
                >
                  <div class="flex w-full items-center justify-between gap-2 overflow-hidden">
                    <span class="truncate">{{ getAccountOptionLabel(item) }}</span>
                    <t-tag
                      :theme="getAccountStatusMeta(item.status).theme"
                      variant="light"
                      size="small"
                      class="shrink-0"
                    >
                      {{ getAccountStatusMeta(item.status).label }}
                    </t-tag>
                  </div>
                </t-option>
              </t-select>
            </t-form-item>

            <t-form-item
              label="选择商品："
              name="cs2GoodsId"
              requiredMark
              class="col-span-1 lg:col-span-2"
            >
              <t-select
                v-model="formData.cs2GoodsId"
                filterable
                clearable
                :disabled="isGoodsSelectDisabled"
                :loading="goodsLoading"
                :on-search="searchGoods"
                placeholder="输入商品名称搜索"
                class="task-config-select"
              >
                <t-option
                  v-for="item in goodsOptions"
                  :key="item.id"
                  :value="item.id"
                  :label="item.displayName"
                >
                  {{ item.displayName }}
                </t-option>
              </t-select>
            </t-form-item>

            <t-form-item label="任务名称：" name="name" class="col-span-1 lg:col-span-2">
              <t-input
                v-model="formData.name"
                clearable
                placeholder="不填则由后端按商品生成"
                class="task-config-select"
              />
            </t-form-item>
            <t-form-item label="优先级：" name="priority" class="col-span-1 lg:col-span-2">
              <t-input-number
                v-model="formData.priority"
                :min="0"
                :step="1"
                theme="column"
                class="task-config-input w-full"
              />
            </t-form-item>

            <t-form-item label="最高价格：" name="maxPrice" requiredMark class="col-span-1">
              <t-input-number
                v-model="formData.maxPrice"
                :min="0.01"
                :step="0.1"
                :decimal-places="2"
                suffix="元"
                theme="column"
                class="task-config-input w-full"
              />
            </t-form-item>
            <t-form-item
              label="请求频率："
              name="scanIntervalSeconds"
              requiredMark
              class="col-span-1"
            >
              <t-input-number
                v-model="formData.scanIntervalSeconds"
                :min="1"
                :step="1"
                suffix="秒/次"
                theme="column"
                class="task-config-input w-full"
              />
            </t-form-item>
            <t-form-item label="磨损范围：" name="wear" class="col-span-1 lg:col-span-2">
              <div class="task-config-range flex items-center gap-3">
                <t-input-number
                  v-model="formData.minPaintwear"
                  :min="0"
                  :max="1"
                  :step="0.001"
                  :decimal-places="3"
                  placeholder="最小"
                  theme="column"
                  class="task-config-range__input"
                />
                <span class="task-config-range__separator text-gray-400">至</span>
                <t-input-number
                  v-model="formData.maxPaintwear"
                  :min="0"
                  :max="1"
                  :step="0.001"
                  :decimal-places="3"
                  placeholder="最大"
                  theme="column"
                  class="task-config-range__input"
                />
              </div>
            </t-form-item>

            <t-form-item
              v-if="formData.stopMode === 'BUY_COUNT'"
              label="目标购买数："
              name="targetBuyCount"
              requiredMark
              class="col-span-1 lg:col-span-2"
            >
              <t-input-number
                v-model="formData.targetBuyCount"
                :min="1"
                :step="1"
                theme="column"
                class="task-config-input w-full"
              />
            </t-form-item>
            <t-form-item
              v-if="formData.stopMode === 'BALANCE_GUARD'"
              label="余额停止口径："
              name="balanceGuardMode"
              requiredMark
              class="col-span-1"
            >
              <t-select
                v-model="formData.balanceGuardMode"
                :options="balanceGuardModeOptions"
                class="task-config-select w-full"
              />
            </t-form-item>
            <t-form-item
              v-if="
                formData.stopMode === 'BALANCE_GUARD' &&
                formData.balanceGuardMode === 'RESERVE_BALANCE'
              "
              label="保底余额："
              name="reserveBalance"
              requiredMark
              class="col-span-1"
            >
              <t-input-number
                v-model="formData.reserveBalance"
                :min="0"
                :step="1"
                :decimal-places="2"
                suffix="元"
                theme="column"
                class="task-config-input task-config-input--price w-full"
              />
            </t-form-item>
          </div>
        </div>

        <div class="form-footer">
          <t-button
            variant="outline"
            theme="default"
            :disabled="!isFormReadonly && submitLoading"
            @click="closeFormDialog"
          >
            {{ isFormReadonly ? "关闭" : "取消" }}
          </t-button>
          <t-button v-if="!isFormReadonly" theme="primary" type="submit" :loading="submitLoading">
            保存
          </t-button>
        </div>
      </t-form>
    </AppDialog>

    <t-drawer
      v-model:visible="detailDrawer.visible"
      :header="detailDrawer.title"
      size="min(960px, 100vw)"
      placement="right"
      @close="closeDetailDrawer"
    >
      <div class="mb-3 flex justify-end">
        <t-button
          variant="outline"
          theme="default"
          :loading="detailDrawer.loading"
          @click="fetchDetailData"
        >
          刷新
        </t-button>
      </div>
      <t-table
        row-key="id"
        :data="detailDrawer.data"
        :columns="detailDrawer.columns"
        :loading="detailDrawer.loading"
        :pagination="undefined"
        hover
      >
        <template #empty>
          <div class="py-8">
            <t-empty :description="detailDrawer.emptyText" />
          </div>
        </template>

        <template #slotReserved="{ row }">
          <t-tag :theme="getSlotReserveMeta(row).theme" variant="light">
            {{ getSlotReserveMeta(row).label }}
          </t-tag>
        </template>
      </t-table>
      <div v-if="detailDrawer.pagination.total > 0" class="mt-3 flex justify-end">
        <t-pagination
            :size="isMobile ? 'small' : 'medium'"
            :theme="isMobile ? 'simple' : 'default'"
            :show-page-size="isMobile ? false : undefined"
          v-model="detailDrawer.pagination.current"
          v-model:page-size="detailDrawer.pagination.pageSize"
          :total="detailDrawer.pagination.total"
          show-jumper
          @change="onDetailPageChange"
        />
      </div>

      <template #footer>
        <t-button variant="outline" theme="default" @click="closeDetailDrawer">关闭</t-button>
      </template>
    </t-drawer>
  </PageFrame>
</template>

<script setup lang="ts">
defineOptions({ name: "C5SnipingTaskV2" });
import { computed, h, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useWindowSize } from "@vueuse/core";
import dayjs from "dayjs";
import {
  MessagePlugin,
  DialogPlugin,
  Icon as tIcon,
  type FormRules,
  type PageInfo,
  type PrimaryTableCol,
  type SubmitContext,
  type TagProps,
} from "tdesign-vue-next";
import { c5SnipingV2Api, c5SnipingV2BaseUrl } from "@/api/c5-sniping-v2";
import { cs2GoodsApi } from "@/api/cs2-goods";
import PageFrame from "@/components/PageFrame.vue";
import PageHeader from "@/components/PageHeader.vue";
import AppDialog from "@/components/AppDialog.vue";
import type { C5SnipingAccount, C5SnipingAccountStatus } from "@/types/c5-sniping-account";
import { BuffAccountStatusEnum, BuffAccountStatusMap } from "@/enums/BuffAccountStatusEnum";
import type { Cs2GoodsOption } from "@/types/cs2-goods";
import { PermissionConstant } from "@/constant/PermissionConstant";
import useNewPermission from "@/hooks/useNewPermission";
import type {
  C5SnipingBuyAttemptV2Item,
  C5SnipingHitRecordV2Item,
  C5SnipingTaskV2BalanceGuardMode,
  C5SnipingTaskV2EventPayload,
  C5SnipingTaskV2Item,
  C5SnipingTaskV2QueryParam,
  C5SnipingTaskV2SaveParam,
} from "@/types/c5-sniping-v2";

interface C5SnipingTaskV2FormData extends Omit<C5SnipingTaskV2SaveParam, "scanIntervalMs"> {
  scanIntervalSeconds?: number;
}

const { hasButtonPermission } = useNewPermission();
const { width } = useWindowSize();
const isMobile = computed(() => width.value <= 768);
const canCreateTask = computed(() =>
  hasButtonPermission(PermissionConstant.C5_SNIPING_TASK_CREATE)
);
const canUpdateTask = computed(() =>
  hasButtonPermission(PermissionConstant.C5_SNIPING_TASK_UPDATE)
);
const canEnableTask = computed(() =>
  hasButtonPermission(PermissionConstant.C5_SNIPING_TASK_ENABLE)
);
const canDisableTask = computed(() =>
  hasButtonPermission(PermissionConstant.C5_SNIPING_TASK_DISABLE)
);
const canDeleteTask = computed(() =>
  hasButtonPermission(PermissionConstant.C5_SNIPING_TASK_DELETE)
);
const canReadTaskDetail = computed(() =>
  hasButtonPermission(PermissionConstant.C5_SNIPING_TASK_DETAIL)
);

const loading = ref(false);
const showFilters = ref(false);
const dataList = ref<C5SnipingTaskV2Item[]>([]);
const queryParams = reactive<C5SnipingTaskV2QueryParam>({
  page: 1,
  pageSize: 10,
  keyword: "",
  taskStatus: undefined,
  accountId: undefined,
});
const pagination = reactive({ current: 1, pageSize: 10, total: 0 });
const accountsLoading = ref(false);
const c5Accounts = ref<C5SnipingAccount[]>([]);

const taskStatusOptions = [
  { label: "待开启", value: "DRAFT" },
  { label: "待运行", value: "READY" },
  { label: "运行中", value: "RUNNING" },
  { label: "已停止", value: "STOPPED" },
  { label: "已完成", value: "COMPLETED" },
  { label: "异常", value: "ERROR" },
];

const balanceGuardModeOptions = [
  { label: "余额低于最高价格", value: "MAX_PRICE" },
  { label: "余额低于保底余额", value: "RESERVE_BALANCE" },
];

const taskTableHeaderClass =
  "!bg-slate-50 !text-slate-500 !text-xs !font-semibold uppercase whitespace-nowrap";
const taskTableBodyClass = "!py-2 text-sm text-slate-700 align-middle";

const getTaskOperationOptions = (row: C5SnipingTaskV2Item) => [
  ...(canReadTaskDetail.value
    ? [
        {
          content: "命中记录",
          value: "hits",
          prefixIcon: () => h(tIcon, { name: "history" }),
        },
        {
          content: "下单记录",
          value: "attempts",
          prefixIcon: () => h(tIcon, { name: "assignment" }),
        },
      ]
    : []),
  ...(canCreateTask.value && row.taskStatus === "COMPLETED"
    ? [
        {
          content: "复制任务",
          value: "copy",
          prefixIcon: () => h(tIcon, { name: "file-copy" }),
        },
      ]
    : []),
];

const columns = computed<PrimaryTableCol[]>(() => [
  {
    colKey: "baseInfo",
    title: "基础信息",
    width: 260,
    cell: "baseInfo",
    fixed: "left" as const,
    className: taskTableBodyClass,
    thClassName: taskTableHeaderClass,
  },
  {
    colKey: "status",
    title: "任务状态",
    width: 140,
    align: "center",
    cell: "status",
    className: taskTableBodyClass,
    thClassName: taskTableHeaderClass,
  },
  {
    colKey: "target",
    title: "目标配置",
    width: 180,
    cell: "target",
    className: taskTableBodyClass,
    thClassName: taskTableHeaderClass,
  },
  {
    colKey: "stopRule",
    title: "停止规则",
    width: 180,
    cell: "stopRule",
    className: taskTableBodyClass,
    thClassName: taskTableHeaderClass,
  },
  {
    colKey: "summary",
    title: "实战数据",
    width: 160,
    cell: "summary",
    className: taskTableBodyClass,
    thClassName: taskTableHeaderClass,
  },
  {
    colKey: "lastError",
    title: "错误详情",
    width: 200,
    cell: "lastError",
    className: taskTableBodyClass,
    thClassName: taskTableHeaderClass,
  },
  {
    colKey: "op",
    title: "操作",
    width: 220,
    cell: "op",
    fixed: "right" as const,
    className: `${taskTableBodyClass} !bg-white`,
    thClassName: taskTableHeaderClass,
  },
]);

const priceFormatter = new Intl.NumberFormat("zh-CN", {
  style: "currency",
  currency: "CNY",
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

const formatPrice = (value?: number) =>
  value === undefined || value === null ? "-" : priceFormatter.format(value);
const formatDateTime = (value?: string) =>
  value ? dayjs(value).format("YYYY-MM-DD HH:mm:ss") : "-";
const getDecisionResultLabel = (value?: string) => {
  const map: Record<string, string> = {
    HIT: "已命中",
    BUY_SUCCESS: "购买成功",
    BUY_FAILED: "购买失败",
    SKIPPED_DUPLICATE: "重复尝试，已跳过",
    NO_ACCOUNT_IN_FLIGHT_SLOT: "账号在途已满",
  };
  return value ? map[value] || value : "-";
};
const formatPaintwear = (row: C5SnipingTaskV2Item) =>
  `${row.minPaintwear ?? 0} - ${row.maxPaintwear ?? 1}`;
const formatScanInterval = (value?: number) => (value ? `${Math.ceil(value / 1000)}秒/次` : "-");

const getTaskStatusMeta = (status: string) => {
  const map: Record<string, { label: string; theme: TagProps["theme"]; dotClass: string }> = {
    DRAFT: { label: "待开启", theme: "default", dotClass: "bg-slate-400" },
    READY: { label: "待运行", theme: "primary", dotClass: "bg-blue-400" },
    RUNNING: {
      label: "运行中",
      theme: "success",
      dotClass: "bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.3)]",
    },
    STOPPED: { label: "已停止", theme: "warning", dotClass: "bg-amber-400" },
    COMPLETED: { label: "已完成", theme: "success", dotClass: "bg-emerald-600" },
    ERROR: { label: "异常", theme: "danger", dotClass: "bg-rose-500 animate-pulse" },
  };
  return map[status] || { label: status, theme: "default", dotClass: "bg-slate-300" };
};

const getSuccessProgress = (row: C5SnipingTaskV2Item) => {
  if (row.stopMode === "BUY_COUNT" && row.targetBuyCount) {
    return Math.min(100, Math.round(((row.successBuyCount ?? 0) / row.targetBuyCount) * 100));
  }
  return row.successBuyCount ? 100 : 0;
};

const getStopModeLabel = (value?: string) =>
  value === "BUY_COUNT" ? "按购买数量停止" : value === "BALANCE_GUARD" ? "按余额停止" : "-";
const getBalanceGuardModeLabel = (value?: string) =>
  value === "MAX_PRICE"
    ? "余额低于最高价格"
    : value === "RESERVE_BALANCE"
      ? "余额低于保底余额"
      : "-";
const isNormalAccount = (account: C5SnipingAccount) =>
  account.status === BuffAccountStatusEnum.NORMAL;
const getAccountOptionLabel = (account: C5SnipingAccount) =>
  `${account.accountName}${account.id ? `（${account.id}）` : ""}`;
const getTaskAccountName = (accountId?: number) =>
  c5Accounts.value.find((item) => item.id === accountId)?.accountName || "-";
const getAccountStatusMeta = (
  status?: C5SnipingAccountStatus
): { label: string; theme: NonNullable<TagProps["theme"]> } => {
  const meta = status ? BuffAccountStatusMap[status as BuffAccountStatusEnum] : undefined;
  return meta
    ? { label: meta.label, theme: meta.theme as NonNullable<TagProps["theme"]> }
    : { label: "异常", theme: "danger" };
};
const getSlotReserveMeta = (
  row: C5SnipingBuyAttemptV2Item
): { label: string; theme: NonNullable<TagProps["theme"]> } => {
  if (!row.slotReserved) {
    return { label: "未预占", theme: "default" };
  }
  return row.slotReleased
    ? { label: "已结算", theme: "success" }
    : { label: "预占中", theme: "warning" };
};
const isEnableVisible = (status?: string) => ["DRAFT", "STOPPED", "ERROR"].includes(status || "");
const isDisableVisible = (status?: string) => ["READY", "RUNNING"].includes(status || "");

const fetchAccounts = async () => {
  accountsLoading.value = true;
  try {
    c5Accounts.value = await c5SnipingV2Api.getAvailableAccounts();
  } catch {
    MessagePlugin.error("C5 账号列表加载失败");
  } finally {
    accountsLoading.value = false;
  }
};

const refreshData = async (silent = false) => {
  if (!silent) {
    loading.value = true;
  }
  try {
    const res = await c5SnipingV2Api.getPage(queryParams);
    dataList.value = res.records;
    pagination.total = res.total;
    pagination.current = res.current || queryParams.page;
    pagination.pageSize = res.size || queryParams.pageSize;
  } finally {
    if (!silent) {
      loading.value = false;
    }
  }
};

const fetchData = () => refreshData(false);

const onPageChange = (pageInfo: PageInfo) => {
  queryParams.page = pageInfo.current;
  queryParams.pageSize = pageInfo.pageSize;
  fetchData();
};

const resetQuery = () => {
  queryParams.keyword = "";
  queryParams.taskStatus = undefined;
  queryParams.accountId = undefined;
  queryParams.page = 1;
  fetchData();
};

type FormMode = "create" | "edit" | "copy" | "view";
const formVisible = ref(false);
const formMode = ref<FormMode>("create");
const formTitle = ref("新增扫货任务");
const submitLoading = ref(false);
const isFormReadonly = computed(() => formMode.value === "view");
const isGoodsSelectDisabled = computed(
  () => formMode.value === "copy" || formMode.value === "view"
);
const formRef = ref<{
  submit: (options?: { showErrorMessage?: boolean }) => void;
  clearValidate: () => void;
} | null>(null);
const defaultFormData = (): C5SnipingTaskV2FormData => ({
  id: undefined,
  copySourceTaskId: undefined,
  accountId: undefined,
  cs2GoodsId: undefined,
  name: "",
  maxPrice: undefined,
  minPaintwear: 0,
  maxPaintwear: 1,
  stopMode: "BUY_COUNT",
  targetBuyCount: 1,
  balanceGuardMode: "MAX_PRICE",
  reserveBalance: undefined,
  priority: 0,
  scanIntervalSeconds: 1,
});
const formData = reactive<C5SnipingTaskV2FormData>(defaultFormData());

const formRules: FormRules<C5SnipingTaskV2FormData> = {
  accountId: [{ required: true, message: "C5 账号不能为空", trigger: "submit" }],
  cs2GoodsId: [{ required: true, message: "商品不能为空", trigger: "submit" }],
  maxPrice: [{ required: true, message: "最高价格不能为空", trigger: "submit" }],
  stopMode: [{ required: true, message: "停止模式不能为空", trigger: "submit" }],
  targetBuyCount: [{ required: true, message: "目标购买数不能为空", trigger: "submit" }],
  balanceGuardMode: [{ required: true, message: "余额停止口径不能为空", trigger: "submit" }],
  reserveBalance: [{ required: true, message: "保底余额不能为空", trigger: "submit" }],
  scanIntervalSeconds: [{ required: true, message: "请求频率不能为空", trigger: "submit" }],
};

const goodsLoading = ref(false);
const goodsOptions = ref<Cs2GoodsOption[]>([]);
const searchGoods = async (keyword: string) => {
  goodsLoading.value = true;
  try {
    goodsOptions.value = await cs2GoodsApi.getC5TaskOptions(keyword);
  } finally {
    goodsLoading.value = false;
  }
};

const resetForm = () => Object.assign(formData, defaultFormData());
const fillFormFromRow = (row: C5SnipingTaskV2Item, mode: FormMode) => {
  resetForm();
  Object.assign(formData, {
    id: mode === "edit" || mode === "view" ? row.id : undefined,
    copySourceTaskId: mode === "copy" ? row.id : undefined,
    accountId: row.accountId,
    cs2GoodsId: row.cs2GoodsId,
    name: row.name || "",
    maxPrice: row.maxPrice,
    minPaintwear: row.minPaintwear ?? 0,
    maxPaintwear: row.maxPaintwear ?? 1,
    stopMode: "BUY_COUNT",
    targetBuyCount: row.targetBuyCount ?? 1,
    balanceGuardMode: (row.balanceGuardMode || "MAX_PRICE") as C5SnipingTaskV2BalanceGuardMode,
    reserveBalance: row.reserveBalance,
    priority: row.priority ?? 0,
    scanIntervalSeconds: Math.max(1, Math.ceil((row.scanIntervalMs ?? 1000) / 1000)),
  });
  goodsOptions.value = row.cs2GoodsId
    ? [
        {
          id: row.cs2GoodsId,
          displayName: row.goodsDisplayName || row.name || String(row.cs2GoodsId),
          marketHashName: row.marketHashName,
          hasExterior: row.hasExterior,
          imageUrl: row.goodsIconUrl,
        },
      ]
    : [];
};
const openCreateDialog = () => {
  if (!canCreateTask.value) {
    return;
  }
  formMode.value = "create";
  resetForm();
  goodsOptions.value = [];
  formTitle.value = "新增扫货任务";
  formVisible.value = true;
};

const openEditDialog = (row: C5SnipingTaskV2Item) => {
  if (!canUpdateTask.value) {
    return;
  }
  formMode.value = "edit";
  fillFormFromRow(row, "edit");
  formTitle.value = "编辑扫货任务";
  formVisible.value = true;
};

const openCopyDialog = (row: C5SnipingTaskV2Item) => {
  if (!canCreateTask.value) {
    return;
  }
  if (row.taskStatus !== "COMPLETED") {
    MessagePlugin.warning("仅已完成任务允许复制");
    return;
  }
  formMode.value = "copy";
  fillFormFromRow(row, "copy");
  formTitle.value = "复制扫货任务";
  formVisible.value = true;
};

const openViewDialog = (row: C5SnipingTaskV2Item) => {
  if (!canReadTaskDetail.value) {
    return;
  }
  formMode.value = "view";
  fillFormFromRow(row, "view");
  formTitle.value = "扫货任务详情";
  formVisible.value = true;
};

const closeFormDialog = () => {
  formVisible.value = false;
};

const buildSubmitPayload = () => {
  const { scanIntervalSeconds, ...restFormData } = formData;
  const payload: C5SnipingTaskV2SaveParam = {
    ...restFormData,
    scanIntervalMs: Math.max(1, scanIntervalSeconds ?? 1) * 1000,
  };
  if (payload.stopMode === "BUY_COUNT") {
    payload.balanceGuardMode = undefined;
    payload.reserveBalance = undefined;
  }
  if (payload.stopMode === "BALANCE_GUARD") {
    payload.targetBuyCount = undefined;
    if (payload.balanceGuardMode !== "RESERVE_BALANCE") {
      payload.reserveBalance = undefined;
    }
  }
  return payload;
};

const onFormSubmit = async (context: SubmitContext) => {
  if (isFormReadonly.value) {
    return;
  }
  if (context.validateResult !== true) {
    return;
  }
  if ((formData.minPaintwear ?? 0) > (formData.maxPaintwear ?? 1)) {
    MessagePlugin.warning("最小磨损不能大于最大磨损");
    return;
  }
  submitLoading.value = true;
  try {
    const payload = buildSubmitPayload();
    if (payload.id) {
      await c5SnipingV2Api.update(payload.id, payload);
      MessagePlugin.success("更新成功");
    } else {
      await c5SnipingV2Api.create(payload);
      MessagePlugin.success("创建成功");
    }
    formVisible.value = false;
    fetchData();
  } finally {
    submitLoading.value = false;
  }
};

const enableTask = async (row: C5SnipingTaskV2Item) => {
  if (!canEnableTask.value) {
    return;
  }
  await c5SnipingV2Api.enable(row.id);
  MessagePlugin.success("已启用");
  fetchData();
};

const disableTask = async (row: C5SnipingTaskV2Item) => {
  if (!canDisableTask.value) {
    return;
  }
  await c5SnipingV2Api.disable(row.id);
  MessagePlugin.success("已停用");
  fetchData();
};

const deleteTask = async (row: C5SnipingTaskV2Item) => {
  if (!canDeleteTask.value) {
    return;
  }
  await c5SnipingV2Api.delete(row.id);
  MessagePlugin.success("删除成功");
  fetchData();
};

const handleOpDropdown = (val: string, row: C5SnipingTaskV2Item) => {
  if (val === "hits") {
    openHitsDrawer(row);
  } else if (val === "attempts") {
    openAttemptsDrawer(row);
  } else if (val === "copy") {
    openCopyDialog(row);
  } else if (val === "delete") {
    const confirmDia = DialogPlugin.confirm({
      header: "确认删除",
      body: "确认删除任务吗？此操作不可恢复。",
      theme: "danger",
      onConfirm: async () => {
        await deleteTask(row);
        confirmDia.hide();
      },
      onClose: () => confirmDia.hide(),
    });
  }
};

type DetailMode = "hits" | "attempts";
const detailDrawer = reactive<{
  visible: boolean;
  mode: DetailMode;
  taskId?: number;
  title: string;
  emptyText: string;
  loading: boolean;
  data: Array<C5SnipingHitRecordV2Item | C5SnipingBuyAttemptV2Item>;
  columns: PrimaryTableCol[];
  pagination: { current: number; pageSize: number; total: number };
}>({
  visible: false,
  mode: "hits",
  taskId: undefined,
  title: "命中明细",
  emptyText: "暂无明细数据",
  loading: false,
  data: [],
  columns: [],
  pagination: { current: 1, pageSize: 10, total: 0 },
});

const hitColumns: PrimaryTableCol[] = [
  {
    colKey: "listingPrice",
    title: "命中价格",
    width: 120,
    cell: (_, { row }) => formatPrice((row as C5SnipingHitRecordV2Item).listingPrice),
  },
  { colKey: "paintwear", title: "磨损", width: 120 },
  {
    colKey: "decisionResult",
    title: "处理结果",
    width: 160,
    cell: (_, { row }) => getDecisionResultLabel((row as C5SnipingHitRecordV2Item).decisionResult),
  },
  {
    colKey: "hitAt",
    title: "命中时间",
    width: 180,
    cell: (_, { row }) => formatDateTime((row as C5SnipingHitRecordV2Item).hitAt),
  },
];

const attemptColumns: PrimaryTableCol[] = [
  { colKey: "orderRecordId", title: "订单 ID", width: 120 },
  { colKey: "attemptStatus", title: "下单结果", width: 120 },
  {
    colKey: "inFlightAmount",
    title: "下单金额",
    width: 120,
    cell: (_, { row }) => formatPrice((row as C5SnipingBuyAttemptV2Item).inFlightAmount),
  },
  { colKey: "failureMessage", title: "失败原因", width: 220 },
  {
    colKey: "createdAt",
    title: "下单时间",
    width: 180,
    cell: (_, { row }) => formatDateTime((row as C5SnipingBuyAttemptV2Item).createdAt),
  },
  {
    colKey: "finishedAt",
    title: "完成时间",
    width: 180,
    cell: (_, { row }) => formatDateTime((row as C5SnipingBuyAttemptV2Item).finishedAt),
  },
];

const fetchDetailData = async () => {
  if (!detailDrawer.taskId) {
    return;
  }
  detailDrawer.loading = true;
  try {
    const params = {
      page: detailDrawer.pagination.current,
      pageSize: detailDrawer.pagination.pageSize,
    };
    const res =
      detailDrawer.mode === "hits"
        ? await c5SnipingV2Api.getHits(detailDrawer.taskId, params)
        : await c5SnipingV2Api.getBuyAttempts(detailDrawer.taskId, params);
    detailDrawer.data = res.records;
    detailDrawer.pagination.total = res.total;
    detailDrawer.pagination.current = res.current || params.page;
    detailDrawer.pagination.pageSize = res.size || params.pageSize;
  } finally {
    detailDrawer.loading = false;
  }
};

const openHitsDrawer = (row: C5SnipingTaskV2Item) => {
  detailDrawer.mode = "hits";
  detailDrawer.taskId = row.id;
  detailDrawer.title = `命中明细 - ${row.goodsDisplayName || row.name || row.id}`;
  detailDrawer.emptyText = "暂无命中明细";
  detailDrawer.columns = hitColumns;
  detailDrawer.pagination.current = 1;
  detailDrawer.visible = true;
  fetchDetailData();
};

const openAttemptsDrawer = (row: C5SnipingTaskV2Item) => {
  detailDrawer.mode = "attempts";
  detailDrawer.taskId = row.id;
  detailDrawer.title = `下单尝试 - ${row.goodsDisplayName || row.name || row.id}`;
  detailDrawer.emptyText = "暂无下单尝试";
  detailDrawer.columns = attemptColumns;
  detailDrawer.pagination.current = 1;
  detailDrawer.visible = true;
  fetchDetailData();
};

const onDetailPageChange = (pageInfo: PageInfo) => {
  detailDrawer.pagination.current = pageInfo.current;
  detailDrawer.pagination.pageSize = pageInfo.pageSize;
  fetchDetailData();
};

const closeDetailDrawer = () => {
  detailDrawer.visible = false;
};

let snipingEventSource: EventSource | null = null;
const taskRefreshQueue = new Set<number>();
let taskRefreshTimer: number | undefined;

const buildSnipingEventsUrl = () => {
  const baseApi = import.meta.env.VITE_BASE_API || "";
  const url = `${baseApi}${c5SnipingV2BaseUrl}/events`;
  const token = localStorage.getItem("niro-web-token");

  if (!token) {
    return url;
  }

  const separator = url.includes("?") ? "&" : "?";
  return `${url}${separator}niro-web-token=${encodeURIComponent(`Bearer ${token}`)}`;
};

const refreshQueuedTasks = async () => {
  if (document.hidden || taskRefreshQueue.size === 0) {
    return;
  }

  const taskIds = Array.from(taskRefreshQueue);
  taskRefreshQueue.clear();

  await Promise.allSettled(
    taskIds.map(async (taskId) => {
      const index = dataList.value.findIndex((item) => item.id === taskId);
      if (index === -1) {
        return;
      }
      const task = await c5SnipingV2Api.get(taskId);
      dataList.value.splice(index, 1, task);
    })
  );
};

const scheduleTaskRefresh = (taskId: number) => {
  taskRefreshQueue.add(taskId);
  if (taskRefreshTimer || document.hidden) {
    return;
  }

  taskRefreshTimer = window.setTimeout(() => {
    taskRefreshTimer = undefined;
    refreshQueuedTasks();
  }, 500);
};

const stopTaskRefresh = () => {
  if (taskRefreshTimer) {
    window.clearTimeout(taskRefreshTimer);
    taskRefreshTimer = undefined;
  }
  taskRefreshQueue.clear();
};

const applyTaskEventPayload = (payload: C5SnipingTaskV2EventPayload) => {
  const index = dataList.value.findIndex((item) => item.id === payload.taskId);
  if (index === -1) {
    return false;
  }

  const current = dataList.value[index];
  const next = { ...current };
  let changed = false;

  if (payload.taskStatus !== undefined) {
    next.taskStatus = payload.taskStatus;
    changed = true;
  }
  if (payload.stopRequested !== undefined) {
    next.stopRequested = payload.stopRequested;
    changed = true;
  }
  if (payload.successBuyCount !== undefined) {
    next.successBuyCount = payload.successBuyCount;
    changed = true;
  }
  if (payload.reservedBuyCount !== undefined) {
    next.reservedBuyCount = payload.reservedBuyCount;
    changed = true;
  }
  if (payload.hitCount !== undefined) {
    next.hitCount = payload.hitCount;
    changed = true;
  }
  if (payload.lastErrorMessage !== undefined) {
    next.lastErrorMessage = payload.lastErrorMessage;
    changed = true;
  }

  if (changed) {
    dataList.value.splice(index, 1, next);
  }
  return changed;
};

const handleVisibilityChange = () => {
  if (document.hidden) {
    if (taskRefreshTimer) {
      window.clearTimeout(taskRefreshTimer);
      taskRefreshTimer = undefined;
    }
    return;
  }
  refreshQueuedTasks();
};

const isCurrentTaskEvent = (payload: C5SnipingTaskV2EventPayload) => {
  if (
    dataList.value.some((item) => item.id === payload.taskId) ||
    detailDrawer.taskId === payload.taskId
  ) {
    return true;
  }

  if (queryParams.keyword || queryParams.accountId) {
    return false;
  }

  return !queryParams.taskStatus || payload.taskStatus === queryParams.taskStatus;
};

const handleSnipingEvent = (payload: C5SnipingTaskV2EventPayload) => {
  if (!payload.taskId || !isCurrentTaskEvent(payload)) {
    return;
  }

  if (!applyTaskEventPayload(payload)) {
    scheduleTaskRefresh(payload.taskId);
  }
};

const connectSnipingEvents = () => {
  snipingEventSource?.close();
  snipingEventSource = new EventSource(buildSnipingEventsUrl());

  snipingEventSource.onmessage = (event) => {
    if (!event.data) {
      return;
    }

    try {
      handleSnipingEvent(JSON.parse(event.data) as C5SnipingTaskV2EventPayload);
    } catch (error) {
      console.warn("C5 扫货 2.0 实时事件解析失败", error);
    }
  };

  snipingEventSource.onerror = (error) => {
    console.warn("C5 扫货 2.0 实时事件连接异常，浏览器将自动重连", error);
  };
};

const closeSnipingEvents = () => {
  snipingEventSource?.close();
  snipingEventSource = null;
  stopTaskRefresh();
};

watch(formVisible, (visible) => {
  if (visible) {
    setTimeout(() => formRef.value?.clearValidate(), 0);
  }
});

onMounted(() => {
  fetchAccounts();
  fetchData();
  connectSnipingEvents();
  document.addEventListener("visibilitychange", handleVisibilityChange);
});

onBeforeUnmount(() => {
  closeSnipingEvents();
  document.removeEventListener("visibilitychange", handleVisibilityChange);
});
</script>

<style scoped>
:deep(.c5-sniping-v2-action-btn.t-button) {
  min-width: 88px;
  border-radius: 4px;
  box-shadow: none;
}

:deep(.c5-sniping-v2-table .t-table__header th) {
  padding-top: 10px;
  padding-bottom: 10px;
}

:deep(.c5-sniping-v2-table .t-table__body td) {
  padding-top: 8px;
  padding-bottom: 8px;
}

:deep(.c5-sniping-v2-table__action-btn.t-button) {
  min-width: 58px;
  padding-right: 10px;
  padding-left: 10px;
  border-radius: 4px;
  box-shadow: none;
}

/* 弹窗样式优化 - 对齐任务列表弹窗 */
:deep(.c5-sniping-v2-dialog .t-dialog__body) {
  padding: 0;
}

.dialog-shell {
  display: flex;
  flex-direction: column;
  min-height: 0;
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
  padding-right: 0 !important;
}

:deep(.compact-form .t-form__item .t-input__tips) {
  position: relative !important;
  display: block !important;
  min-height: auto !important;
  margin-top: 6px;
  margin-bottom: 0;
  line-height: 1.5;
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

:deep(.compact-form .t-form__item .t-input__tips + .t-form__verify-message) {
  margin-top: 6px;
}

.task-config-select {
  width: min(100%, 360px);
}

.task-config-input {
  width: 120px;
}

.task-config-input--price {
  width: 140px;
}

.task-config-range__input {
  width: 110px;
}

.task-config-interval {
  flex-wrap: wrap;
  gap: 10px;
}

@media (max-width: 768px) {
  .c5-sniping-v2-body {
    overscroll-behavior: contain;
    -webkit-overflow-scrolling: touch;
  }

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

  .task-config-select,
  .task-config-input,
  .task-config-input--price,
  .task-config-range__input {
    width: 100%;
  }

  .task-config-range,
  .task-config-interval {
    flex-wrap: wrap;
    gap: 10px;
  }

  .task-config-range__separator {
    width: 100%;
    line-height: 1;
  }
}

@media (max-width: 640px) {
  .form-container {
    padding: 14px;
  }

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
    max-width: none;
    margin-left: 0 !important;
  }

  :deep(.compact-form .t-form__controls-content) {
    width: 100%;
  }

  :deep(.compact-form .t-input-number),
  :deep(.compact-form .t-select),
  :deep(.compact-form .t-input),
  :deep(.compact-form .t-input-number__inner) {
    width: 100%;
    max-width: 100%;
  }

  .task-config-range,
  .task-config-interval {
    align-items: stretch;
  }
}
</style>
