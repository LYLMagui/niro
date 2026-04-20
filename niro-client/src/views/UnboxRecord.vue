<template>
  <PageFrame
    :is-mobile="isMobile"
    desktop-content-class="px-4 pt-3 pb-4"
    mobile-content-class="px-3 pt-3 pb-3"
  >
    <div
      ref="pageHostRef"
      class="unbox-record-page relative flex min-h-0 flex-1 flex-col gap-4"
      :class="editorVisible ? 'overflow-hidden' : 'overflow-y-auto overscroll-contain'"
    >
      <section class="grid grid-cols-1 gap-2 md:grid-cols-2 xl:grid-cols-4">
        <article
          v-for="card in pageSummaryCards"
          :key="card.label"
          class="rounded-lg border border-slate-200 bg-white px-3 py-2 shadow-sm"
          :class="summaryLoading ? 'opacity-70' : ''"
        >
          <div class="truncate text-sm font-medium tracking-[0.03em] text-slate-500">
            {{ card.label }}
          </div>
          <div
            class="font-numeric mt-1 text-[22px] leading-none font-semibold"
            :class="card.valueClass"
          >
            {{ card.value }}
          </div>
          <div class="mt-1 truncate text-sm leading-5 text-slate-400">{{ card.hint }}</div>
        </article>
      </section>

      <section class="overflow-hidden rounded-md border border-slate-200 bg-white shadow-sm">
        <div
          class="flex flex-col gap-3 border-b border-slate-200 bg-slate-50/70 px-4 py-3 lg:flex-row lg:items-center lg:justify-between"
        >
          <div class="flex min-w-0 flex-1 flex-col gap-3">
            <div class="flex flex-wrap items-center gap-2" aria-label="周期快捷筛选">
              <t-button
                v-for="period in periodOptions"
                :key="period.value"
                variant="outline"
                :theme="activePeriod === period.value ? 'primary' : 'default'"
                class="touch-manipulation"
                @click="setActivePeriod(period.value)"
              >
                {{ period.label }}
              </t-button>
            </div>

            <div class="flex flex-col gap-2 xl:flex-row xl:items-center xl:gap-3">
              <t-date-range-picker
                :value="dateRangeValue"
                clearable
                allow-input
                :first-day-of-week="1"
                value-type="YYYY-MM-DD"
                format="YYYY-MM-DD"
                :placeholder="['开始日期', '结束日期']"
                class="w-full min-w-0 xl:max-w-[340px]"
                :class="summaryToolbarFieldClass"
                @change="handleDateRangeChange"
              />

              <div class="flex flex-wrap items-center gap-2">
                <t-button theme="primary" class="touch-manipulation" @click="handleSummarySearch">
                  查询
                </t-button>
                <t-button variant="text" theme="default" @click="resetFilters">重置</t-button>
              </div>
            </div>
          </div>

          <div class="flex shrink-0 items-center justify-end gap-2">
            <t-button theme="primary" class="touch-manipulation" @click="openCreateEditor">
              新增开箱记录
            </t-button>
          </div>
        </div>

        <div class="overflow-x-auto">
          <t-table
            row-key="key"
            :data="pagedBatchSummaryRows"
            :columns="batchColumns"
            v-model:sort="batchSummarySort"
            table-layout="fixed"
            hover
            class="unbox-summary-table w-full bg-white"
          >
            <template #empty>
              <t-empty :description="listLoading ? '加载中...' : '当前筛选条件下暂无批次记录'" />
            </template>

            <template #date="{ row }">
              <span class="text-slate-600">{{ formatDateText(row.batch.date) }}</span>
            </template>

            <template #totalCount="{ row }">
              <span class="font-numeric text-slate-700">{{ row.summary.totalCount }}</span>
            </template>

            <template #purchaseCost="{ row }">
              <span class="font-numeric text-slate-700">
                {{ formatCurrency(row.summary.totalPurchaseCost) }}
              </span>
            </template>

            <template #totalFee="{ row }">
              <span class="font-numeric text-amber-600">
                {{ formatCurrency(row.summary.totalActualFee) }}
              </span>
            </template>

            <template #actualNetProfit="{ row }">
              <span class="font-numeric" :class="profitClass(row.summary.totalActualNetProfit)">
                {{ formatSignedCurrency(row.summary.totalActualNetProfit) }}
              </span>
            </template>

            <template #actualProfitRate="{ row }">
              <span
                class="font-numeric"
                :class="profitClass(row.summary.totalActualProfitRate ?? 0)"
              >
                {{ formatPercent(row.summary.totalActualProfitRate) }}
              </span>
            </template>

            <template #status="{ row }">
              <t-tag :theme="historyStatusTheme(row.status)" variant="light-outline">
                {{ row.status }}
              </t-tag>
            </template>

            <template #operation="{ row }">
              <div class="flex flex-wrap gap-1.5">
                <t-button variant="outline" :loading="detailLoading && loadingDetailBatchId === row.batch.id" @click="openEditEditor(row.batch.id)">编辑</t-button>
                <t-popconfirm
                  content="确认删除该批次吗？"
                  theme="danger"
                  :popup-props="{ attach: 'body' }"
                  @confirm="removeBatch(row.batch.id)"
                >
                  <t-button theme="danger" variant="outline">删除</t-button>
                </t-popconfirm>
              </div>
            </template>
          </t-table>
        </div>

        <div v-if="batchPagination.total > 0" class="border-t border-slate-200 bg-white px-4 py-3">
          <t-pagination
            :current="batchPagination.current"
            :page-size="batchPagination.pageSize"
            :total="batchPagination.total"
            @change="handleBatchPageChange"
          />
        </div>
      </section>
    </div>

    <t-dialog
      v-model:visible="editorVisible"
      :close-btn="false"
      :close-on-overlay-click="false"
      :confirm-btn="null"
      :cancel-btn="null"
      :destroy-on-close="true"
      :attach="editorDialogAttach"
      :showInAttachedElement="!isMobile"
      :footer="false"
      :header="false"
      :mode="isMobile || isEditorFullscreen ? 'full-screen' : 'modal'"
      :show-overlay="isEditorFullscreen || isMobile"
      :dialog-style="editorDialogStyle"
      :dialog-class-name="editorDialogClassName"
      placement="center"
    >
      <input
        ref="ocrInputRef"
        type="file"
        accept="image/*"
        class="hidden"
        @change="handleRowOcrFileChange"
      />
      <div class="flex min-h-0 flex-1 flex-col overflow-hidden bg-white" :class="editorBodyClass">
        <div class="border-b border-slate-200 bg-white px-2.5 py-3 sm:px-3 sm:py-3">
          <div class="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
            <div class="min-w-0 space-y-2">
              <div class="space-y-1">
                <h2 class="truncate text-base font-semibold text-[#303133] sm:text-lg">
                  {{ editingBatchId ? getBatchDisplayName(draftBatch) : "新增开箱记录" }}
                </h2>
              </div>
            </div>

            <div class="flex shrink-0 flex-wrap items-center justify-end gap-1.5 sm:gap-2">
              <t-button variant="outline" @click="toggleEditorFullscreen">
                {{ isEditorFullscreen ? "缩小" : "全屏" }}
              </t-button>
              <t-button variant="outline" @click="editorVisible = false">取消</t-button>
              <t-button theme="primary" :loading="savingBatch" @click="saveDraftBatch">
                {{ savingBatch ? "保存中..." : "保存批次" }}
              </t-button>
              <button
                type="button"
                class="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 text-slate-500 transition-colors hover:border-slate-300 hover:text-slate-700 focus-visible:ring-2 focus-visible:ring-sky-500/60 focus-visible:ring-offset-1 focus-visible:outline-none"
                aria-label="关闭编辑器"
                @click="editorVisible = false"
              >
                <svg
                  viewBox="0 0 16 16"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                  class="h-3.5 w-3.5"
                  aria-hidden="true"
                >
                  <path
                    d="M4 4L12 12M12 4L4 12"
                    stroke="currentColor"
                    stroke-width="1.6"
                    stroke-linecap="round"
                  />
                </svg>
              </button>
            </div>
          </div>
        </div>

        <div
          ref="editorContentRef"
          class="min-h-0 flex-1 overflow-hidden px-2.5 py-2.5 sm:px-3 sm:py-3"
        >
          <div class="flex h-full min-h-0 flex-col gap-3">
            <section
              ref="batchInfoSectionRef"
              class="shrink-0 overflow-hidden rounded-[10px] border border-slate-200/80 bg-white px-3 py-2.5 shadow-sm"
            >
              <div class="flex items-start justify-between gap-3">
                <div class="min-w-0 space-y-1">
                  <div class="text-sm font-medium tracking-[0.14em] text-slate-400 uppercase">
                    记录信息
                  </div>
                  <div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-slate-600">
                    <span>{{ draftBatch.boxName || "未选箱子" }}</span>
                    <span>{{ draftBatch.date || "未选日期" }}</span>
                    <span>
                      默认折扣
                      {{
                        hasDiscountValue(draftBatch.defaultDiscount)
                          ? clampDiscount(draftBatch.defaultDiscount).toFixed(2)
                          : "未填写"
                      }}
                    </span>
                  </div>
                </div>
                <button
                  type="button"
                  class="flex h-8 min-w-16 items-center justify-center rounded-lg border border-slate-200 px-2 text-sm font-medium text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-800 focus-visible:ring-2 focus-visible:ring-sky-500/60 focus-visible:ring-offset-1 focus-visible:outline-none"
                  :aria-expanded="!isBatchInfoCollapsed"
                  :aria-label="isBatchInfoCollapsed ? '展开批次信息' : '收起批次信息'"
                  @click="toggleBatchInfoCollapsed"
                >
                  <span>{{ isBatchInfoCollapsed ? "展开" : "收起" }}</span>
                  <svg
                    viewBox="0 0 16 16"
                    fill="none"
                    xmlns="http://www.w3.org/2000/svg"
                    class="ml-1 h-3.5 w-3.5 transition-transform duration-200"
                    :class="isBatchInfoCollapsed ? 'rotate-180' : 'rotate-90'"
                    aria-hidden="true"
                  >
                    <path
                      d="M5.5 3.5L10 8L5.5 12.5"
                      stroke="currentColor"
                      stroke-width="1.6"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    />
                  </svg>
                </button>
              </div>

              <div
                class="grid transition-all duration-200 ease-out"
                :class="
                  isBatchInfoCollapsed
                    ? 'mt-0 grid-rows-[0fr] opacity-0'
                    : 'mt-3 grid-rows-[1fr] opacity-100'
                "
              >
                <div class="min-h-0 overflow-hidden">
                  <div class="grid grid-cols-1 gap-3 xl:grid-cols-12 xl:items-end">
                    <label class="space-y-1.5 xl:col-span-3">
                      <span class="text-sm font-medium text-slate-700">开箱日期</span>
                      <t-date-picker
                        v-model="draftBatch.date"
                        allow-input
                        clearable
                        :class="fieldBaseClass"
                        format="YYYY-MM-DD"
                        value-type="YYYY-MM-DD"
                        placeholder="选择日期"
                      />
                    </label>

                    <label class="space-y-1.5 xl:col-span-3">
                      <span class="text-sm font-medium text-slate-700">箱子</span>
                      <t-select
                        v-model="draftBatch.goodsId"
                        :class="fieldBaseClass"
                        clearable
                        filterable
                        :loading="goodsLoading"
                        :options="goodsOptions"
                        placeholder="搜索并选择箱子"
                        @popup-visible-change="handleGoodsPopupVisibleChange"
                        @search="handleGoodsSearch"
                      />
                    </label>

                    <label class="flex flex-col gap-1.5 xl:col-span-3">
                      <span class="text-sm font-medium text-slate-700">
                        默认折扣
                        <span class="text-rose-500">*</span>
                      </span>
                      <t-input-number
                        v-model="draftBatch.defaultDiscount"
                        :decimal-places="2"
                        :max="1"
                        :min="0"
                        :step="0.01"
                        align="left"
                        :class="batchDiscountFieldClass"
                        :input-props="{ inputClass: 'w-full text-left' }"
                        :status="hasDiscountValue(draftBatch.defaultDiscount) ? 'default' : 'error'"
                        :tips="hasDiscountValue(draftBatch.defaultDiscount) ? '' : '请填写默认折扣'"
                        placeholder="请输入默认折扣"
                        theme="normal"
                      />
                    </label>

                    <label class="space-y-1.5 xl:col-span-3">
                      <span class="text-sm font-medium text-slate-700">备注</span>
                      <t-input
                        v-model="draftBatch.note"
                        :class="fieldBaseClass"
                        maxlength="120"
                        placeholder="记录这一批的来源、玩法、特别说明"
                      />
                    </label>
                  </div>
                </div>
              </div>
            </section>

            <section
              class="flex min-h-0 flex-col overflow-hidden rounded-[10px] border border-slate-200/80 bg-white shadow-sm"
            >
              <div class="shrink-0 border-b border-slate-200/80 px-3 py-2">
                <div class="space-y-2">
                  <div class="flex flex-col gap-2 xl:flex-row xl:items-center xl:justify-between">
                    <div class="flex min-w-0 items-center gap-2">
                      <div
                        class="shrink-0 text-sm font-medium tracking-[0.14em] text-slate-400 uppercase"
                      >
                        开箱明细
                      </div>
                    </div>

                    <div class="overflow-x-auto">
                      <div class="inline-flex min-w-max items-center gap-1.5 pb-0.5">
                        <t-button theme="primary" variant="outline" @click="handleAddRow()">
                          +1
                        </t-button>
                        <t-button variant="outline" @click="handleBulkAdd(10)">+10</t-button>
                        <t-button variant="outline" @click="handleBulkAdd(50)">+50</t-button>
                        <div
                          class="inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-slate-50/80 p-1"
                        >
                          <span class="px-1 text-sm text-slate-500">自定义</span>
                          <t-input-number
                            v-model="bulkAddCount"
                            :decimal-places="0"
                            :min="1"
                            :step="1"
                            align="left"
                            :class="`${numberFieldBaseClass} w-20`"
                            placeholder="20"
                            theme="normal"
                          />
                          <t-button
                            variant="text"
                            class="!px-2"
                            @click="handleBulkAdd(bulkAddCount)"
                          >
                            添加
                          </t-button>
                        </div>
                        <div
                          class="inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-slate-50/80 p-1"
                        >
                          <span class="px-1 text-sm text-slate-500">箱子购入价</span>
                          <t-input-number
                            v-model="toolbarBoxPurchasePrice"
                            :decimal-places="2"
                            :min="0"
                            :step="0.01"
                            align="right"
                            :class="`${numberFieldBaseClass} w-24`"
                            placeholder="0.00"
                            theme="normal"
                          />
                        </div>
                        <t-button variant="outline" @click="applyToolbarBoxPurchasePriceToAllRows">
                          应用箱子购入价到全部
                        </t-button>
                        <t-button variant="outline" @click="applyDefaultsToEmptyRows">
                          应用到未填写行
                        </t-button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div v-if="!isMobile" class="min-h-0 flex-1 overflow-hidden bg-white">
                <div
                  class="min-h-0 overflow-hidden overscroll-contain"
                  :style="draftTableViewportStyle"
                >
                  <t-table
                    row-key="row.id"
                    :data="draftRowEntries"
                    :columns="draftTableColumns"
                    :foot-data="draftFooterRows"
                    :max-height="draftTableMaxHeight"
                    v-model:sort="draftTableSort"
                    table-layout="fixed"
                    vertical-align="middle"
                    hover
                    class="draft-detail-table w-full [&_.t-table]:w-full [&_.t-table__content]:relative [&_.t-table__content]:w-full [&_.t-table__content-inner]:min-w-full [&_.t-table__header]:bg-white"
                  >
                    <template #index="{ rowIndex }">
                      <div class="font-numeric text-sm leading-4 font-semibold text-slate-600">
                        {{ rowIndex + 1 }}
                      </div>
                    </template>

                    <template #boxPurchasePrice="{ row: entry }">
                      <div :class="draftCellControlClass">
                        <t-input-number
                          v-model="entry.row.boxPurchasePrice"
                          :decimal-places="2"
                          :disabled="!isRowEditable(entry.row)"
                          :min="0"
                          :step="0.1"
                          align="right"
                          :class="draftNumberFieldClass"
                          placeholder="0.00"
                          theme="normal"
                        />
                      </div>
                    </template>

                    <template #purchaseState="{ row: entry }">
                      <div class="flex flex-wrap gap-1">
                        <button
                          v-for="option in selectableDraftHandlingStatusOptions"
                          :key="option.value"
                          type="button"
                          :class="[
                            `h-7 ${draftStatusButtonBaseClass}`,
                            entry.row.handlingStatus === option.value
                              ? getDraftStatusButtonActiveClass(option.value)
                              : 'text-slate-500',
                          ]"
                          @click="setHandlingStatus(entry.row, option.value)"
                        >
                          {{ option.label }}
                        </button>
                      </div>
                    </template>

                    <template #weaponName="{ row: entry }">
                      <div class="flex items-center gap-2" :class="draftCellControlClass">
                        <t-select
                          v-model="entry.row.weaponName"
                          :class="`flex-1 ${draftSelectFieldClass}`"
                          :disabled="!isRowEditable(entry.row)"
                          :options="weaponNameOptions"
                          clearable
                          filterable
                          placeholder="请选择饰品名称"
                        />
                        <t-popup
                          :visible="activeOcrPopupRowId === entry.row.id"
                          trigger="hover"
                          placement="top"
                          show-arrow
                          attach="body"
                          overlay-inner-class-name="unbox-ocr-popup__inner"
                          :disabled="
                            !isRowEditable(entry.row) || getRowOcrState(entry.row.id).status === 'uploading'
                          "
                          @visible-change="(visible) => handleRowOcrPopupVisibleChange(entry.row.id, visible)"
                        >
                          <template #content>
                            <div class="grid min-w-[148px] grid-cols-2 gap-2">
                              <t-button
                                size="small"
                                variant="outline"
                                class="w-full"
                                @click.stop="triggerRowOcrFileSelect(entry.row)"
                              >
                                上传
                              </t-button>
                              <t-button
                                size="small"
                                variant="outline"
                                class="w-full"
                                @click.stop="handlePasteRowOcrImage(entry.row)"
                              >
                                粘贴
                              </t-button>
                            </div>
                          </template>
                          <button
                            type="button"
                            class="group inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md border border-slate-200 bg-white transition-colors hover:border-slate-300 hover:bg-slate-50 focus-visible:ring-2 focus-visible:ring-sky-500/60 focus-visible:ring-offset-1 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-60"
                            :disabled="
                              !isRowEditable(entry.row) || getRowOcrState(entry.row.id).status === 'uploading'
                            "
                            :aria-label="getRowOcrTooltip(entry.row.id)"
                          >
                            <component
                              :is="getRowOcrIcon(entry.row.id)"
                              class="h-4 w-4 transition-colors"
                              :class="getRowOcrIconClass(entry.row.id)"
                            />
                          </button>
                        </t-popup>
                      </div>
                    </template>

                    <template #inGamePrice="{ row: entry }">
                      <div :class="draftCellControlClass">
                        <t-input-number
                          v-model="entry.row.inGamePrice"
                          :decimal-places="2"
                          :disabled="!isRowEditable(entry.row)"
                          :min="0"
                          :step="0.1"
                          align="right"
                          :class="draftNumberFieldClass"
                          placeholder="0.00"
                          theme="normal"
                        />
                      </div>
                    </template>

                    <template #discount="{ row: entry }">
                      <div :class="draftCellControlClass">
                        <t-input-number
                          v-model="entry.row.discount"
                          :decimal-places="2"
                          :disabled="!isRowEditable(entry.row)"
                          :max="1"
                          :min="0"
                          :step="0.01"
                          align="right"
                          :class="draftNumberFieldClass"
                          placeholder="0.72"
                          theme="normal"
                        />
                      </div>
                    </template>

                    <template #wear="{ row: entry }">
                      <div :class="draftCellControlClass">
                        <t-input-number
                          v-model="entry.row.wear"
                          :decimal-places="16"
                          :disabled="!isRowEditable(entry.row)"
                          :format="formatWearDisplay"
                          :max="1"
                          :min="0"
                          :step="0.000000001"
                          align="right"
                          :class="draftNumberFieldClass"
                          :placeholder="getWearPlaceholder()"
                          theme="normal"
                        />
                      </div>
                    </template>

                    <template #exterior="{ row: entry }">
                      <div :class="draftCellControlClass">
                        <t-select
                          v-model="entry.row.exterior"
                          :class="draftSelectFieldClass"
                          :disabled="!isRowEditable(entry.row)"
                          :options="EXTERIOR_OPTIONS"
                          placeholder="请选择外观"
                        />
                      </div>
                    </template>

                    <template #purchaseCost="{ row: entry }">
                      <div class="text-right leading-4">
                        <div class="font-numeric text-sm font-semibold text-[#303133]">
                          {{ formatPendingCurrency(entry.metrics.purchaseCost) }}
                        </div>
                      </div>
                    </template>

                    <template #actualSellPrice="{ row: entry }">
                      <div class="space-y-1" :class="draftCellControlClass">
                        <div class="flex items-center gap-2">
                          <t-input-number
                            v-model="entry.row.actualSellPrice"
                            :decimal-places="2"
                            :disabled="!isRowEditable(entry.row)"
                            :min="0"
                            :step="0.1"
                            align="right"
                            :class="`flex-1 ${draftNumberFieldPrimaryClass}`"
                            placeholder="优先录这里"
                            theme="normal"
                          />
                          <t-popup
                            :visible="activeC5PopupRowId === entry.row.id"
                            trigger="click"
                            placement="left-top"
                            show-arrow
                            attach="body"
                            overlay-inner-class-name="unbox-c5-popup__inner"
                            :disabled="!isRowEditable(entry.row) || !entry.row.weaponName.trim()"
                            @visible-change="(visible) => handleRowC5PopupVisibleChange(entry.row, visible)"
                          >
                            <template #content>
                              <div class="flex w-[320px] flex-col gap-2">
                                <div class="flex items-start justify-between gap-3 px-1 pt-2">
                                  <div class="min-w-0">
                                    <div class="text-sm font-semibold text-slate-700">C5 在售挂单</div>
                                    <div class="mt-1 text-xs text-slate-500">
                                      {{ getRowC5TriggerTooltip(entry.row) }}
                                    </div>
                                    <div class="mt-1 text-xs text-slate-500">
                                      {{ getRowC5QuerySummary(entry.row) }}
                                    </div>
                                    <div v-if="getRowC5WearHint(entry.row)" class="mt-1 text-xs text-slate-400">
                                      {{ getRowC5WearHint(entry.row) }}
                                    </div>
                                  </div>
                                  <button
                                    type="button"
                                    class="inline-flex shrink-0 items-center rounded-md border border-slate-200 bg-white px-2 py-1 text-xs font-medium text-slate-600 transition-colors hover:border-sky-300 hover:bg-sky-50 hover:text-sky-600"
                                    @click.stop="runRowC5Query(entry.row, { force: true })"
                                  >
                                    刷新
                                  </button>
                                </div>
                                <div class="space-y-2 px-1">
                                  <t-select
                                    :value="getRowC5State(entry.row.id).selectedRangeKey"
                                    :options="getRowC5RangeOptions(entry.row)"
                                    placeholder="选择磨损区间"
                                    size="small"
                                    @change="(value) => handleRowC5RangeChange(entry.row, value)"
                                  />
                                  <div v-if="getRowC5State(entry.row.id).selectedRangeKey === C5_WEAR_RANGE_CUSTOM_KEY" class="space-y-2 rounded-lg border border-slate-200 bg-slate-50/70 px-2 py-2">
                                    <div class="grid grid-cols-2 gap-2">
                                      <t-input-number
                                        :value="getRowC5State(entry.row.id).customWearMin ?? ''"
                                        :decimal-places="4"
                                        :theme="'normal'"
                                        :step="0.0001"
                                        align="right"
                                        placeholder="最小磨损"
                                        @change="(value) => handleRowC5CustomWearMinChange(entry.row, value)"
                                      />
                                      <t-input-number
                                        :value="getRowC5State(entry.row.id).customWearMax ?? ''"
                                        :decimal-places="4"
                                        :theme="'normal'"
                                        :step="0.0001"
                                        align="right"
                                        placeholder="最大磨损"
                                        @change="(value) => handleRowC5CustomWearMaxChange(entry.row, value)"
                                      />
                                    </div>
                                    <div class="text-xs text-slate-500">
                                      {{ getRowC5CustomRangeHint(entry.row) }}
                                    </div>
                                  </div>
                                  <div v-if="getRowC5PresetMissHint(entry.row)" class="text-xs text-amber-600">
                                    {{ getRowC5PresetMissHint(entry.row) }}
                                  </div>
                                  <div class="flex justify-end">
                                    <t-button size="small" theme="primary" @click="runRowC5Query(entry.row, { force: true })">
                                      查询
                                    </t-button>
                                  </div>
                                </div>
                                <div
                                  class="max-h-[360px] overflow-y-auto pr-1"
                                  @scroll.passive="(event) => handleRowC5ListScroll(entry.row, event)"
                                >
                                  <div
                                    v-if="getRowC5State(entry.row.id).status === 'loading'"
                                    class="flex items-center justify-center py-8 text-sm text-slate-500"
                                  >
                                    加载中...
                                  </div>
                                  <div
                                    v-else-if="getRowC5State(entry.row.id).status === 'error' && !getRowC5Listings(entry.row).length"
                                    class="space-y-2 rounded-lg border border-rose-200 bg-rose-50/70 px-3 py-3 text-sm text-rose-600"
                                  >
                                    <div>{{ getRowC5State(entry.row.id).errorMessage || '获取 C5 在售列表失败' }}</div>
                                    <button
                                      type="button"
                                      class="inline-flex items-center rounded-md border border-rose-200 bg-white px-2 py-1 text-xs font-medium text-rose-600 transition-colors hover:bg-rose-50"
                                      @click.stop="runRowC5Query(entry.row, { force: true })"
                                    >
                                      重试
                                    </button>
                                  </div>
                                  <div
                                    v-else-if="!getRowC5Listings(entry.row).length"
                                    class="py-8 text-center text-sm text-slate-500"
                                  >
                                    {{ getRowC5State(entry.row.id).appliedWearMin === null ? '暂无在售挂单' : '当前磨损区间暂无在售挂单' }}
                                  </div>
                                  <div v-else class="space-y-2 px-1">
                                    <button
                                      v-for="listing in getRowC5Listings(entry.row)"
                                      :key="listing.productId"
                                      type="button"
                                      class="flex w-full items-center justify-between rounded-lg border border-slate-200 px-3 py-2 text-left transition-colors hover:border-sky-300 hover:bg-sky-50/70"
                                      @click.stop="applyC5Listing(entry.row, listing)"
                                    >
                                      <div class="min-w-0">
                                        <div class="font-numeric text-sm font-semibold text-slate-800">
                                          {{ formatCurrency(listing.price) }}
                                        </div>
                                        <div class="mt-1 truncate text-xs text-slate-500">
                                          {{ listing.sellerName || listing.sellerUid || '卖家未知' }}
                                        </div>
                                      </div>
                                      <div class="text-right text-xs text-slate-500">
                                        <div>
                                          磨损
                                          {{ listing.wear === null ? '--' : formatWearDisplay(listing.wear) }}
                                        </div>
                                      </div>
                                    </button>
                                    <div v-if="getRowC5State(entry.row.id).loadingMore" class="py-2 text-center text-xs text-slate-500">
                                      加载中...
                                    </div>
                                    <div v-else-if="getRowC5State(entry.row.id).errorMessage" class="py-2 text-center text-xs text-rose-500">
                                      {{ getRowC5State(entry.row.id).errorMessage }}
                                    </div>
                                    <div v-else-if="!getRowC5State(entry.row.id).hasMore" class="py-2 text-center text-xs text-slate-400">
                                      已加载全部挂单
                                    </div>
                                  </div>
                                </div>
                              </div>
                            </template>
                            <button
                              type="button"
                              class="group inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md border border-slate-200 bg-white transition-colors hover:border-sky-300 hover:bg-sky-50 focus-visible:ring-2 focus-visible:ring-sky-500/60 focus-visible:ring-offset-1 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-60"
                              :disabled="!isRowEditable(entry.row) || !entry.row.weaponName.trim()"
                              :aria-label="getRowC5TriggerTooltip(entry.row)"
                            >
                              <svg
                                viewBox="0 0 16 16"
                                aria-hidden="true"
                                :class="[
                                  'h-4 w-4 text-slate-400 transition-all group-hover:text-sky-500',
                                  activeC5PopupRowId === entry.row.id ? 'rotate-180 text-sky-500' : '',
                                ]"
                              >
                                <path
                                  d="M4.47 6.97a.75.75 0 0 1 1.06 0L8 9.44l2.47-2.47a.75.75 0 1 1 1.06 1.06l-3 3a.75.75 0 0 1-1.06 0l-3-3a.75.75 0 0 1 0-1.06Z"
                                  fill="currentColor"
                                />
                              </svg>
                            </button>
                          </t-popup>
                        </div>
                      </div>
                    </template>

                    <template #actualNetProfit="{ row: entry }">
                      <div class="px-1 leading-4">
                        <div
                          class="font-numeric text-sm font-semibold"
                          :class="profitClass(entry.metrics.actualNetProfit ?? 0)"
                        >
                          {{ formatActualProfit(entry.metrics.actualNetProfit) }}
                        </div>
                        <div class="mt-1 text-[14px] font-medium text-slate-500">
                          {{
                            entry.metrics.actualNetIncome === null
                              ? ""
                              : `到账 ${formatCurrency(entry.metrics.actualNetIncome)}`
                          }}
                        </div>
                      </div>
                    </template>

                    <template #actualProfitRate="{ row: entry }">
                      <div class="px-1 leading-4">
                        <div
                          class="font-numeric text-sm font-semibold"
                          :class="profitClass(entry.metrics.actualProfitRate ?? 0)"
                        >
                          {{ formatPercent(entry.metrics.actualProfitRate) }}
                        </div>
                      </div>
                    </template>

                    <template #actualFee="{ row: entry }">
                      <div class="px-1 leading-4">
                        <div class="font-numeric text-sm font-semibold text-amber-600">
                          {{ formatPendingCurrency(entry.metrics.actualFee) }}
                        </div>
                      </div>
                    </template>

                    <template #operation="{ row: entry, rowIndex }">
                      <div class="flex flex-col items-start gap-0.5">
                        <t-button
                          variant="text"
                          class="text-slate-600"
                          @click="handleAddRow(rowIndex + 1)"
                        >
                          插入
                        </t-button>
                        <t-button
                          theme="danger"
                          variant="text"
                          @click="handleRemoveRow(entry.row.id)"
                        >
                          删除
                        </t-button>
                      </div>
                    </template>

                    <template #footerIndex>
                      <div :class="draftTableFooterClass">
                        <div class="font-semibold text-slate-600">汇总</div>
                      </div>
                    </template>

                    <template #footerBoxPurchasePrice>
                      <div :class="draftTableFooterClass">
                        <div>明细数</div>
                        <div class="font-numeric mt-1 text-sm font-semibold text-slate-700">
                          {{ draftSummary.totalCount }} 条
                        </div>
                      </div>
                    </template>

                    <template #footerPurchaseState>
                      <div :class="draftTableFooterClass">
                        <div>已购买数量</div>
                        <div class="font-numeric mt-1 text-sm font-semibold text-emerald-600">
                          {{ draftSummary.boughtCount }} 条
                        </div>
                      </div>
                    </template>

                    <template #footerWeaponName>
                      <div :class="draftTableFooterClass">
                        <div class="text-slate-400">—</div>
                      </div>
                    </template>

                    <template #footerInGamePrice>
                      <div :class="draftTableFooterClass">
                        <div>购买总价</div>
                        <div class="font-numeric mt-1 text-sm font-semibold text-slate-700">
                          {{ formatCurrency(draftSummary.totalInGamePrice) }}
                        </div>
                      </div>
                    </template>

                    <template #footerDiscount>
                      <div :class="draftTableFooterClass">
                        <div class="text-slate-400">—</div>
                      </div>
                    </template>

                    <template #footerPurchaseCost>
                      <div :class="draftTableFooterClass">
                        <div>实际购入价</div>
                        <div class="font-numeric mt-1 text-sm font-semibold text-slate-700">
                          {{ formatCurrency(draftSummary.totalPurchaseCost) }}
                        </div>
                      </div>
                    </template>

                    <template #footerActualSellPrice>
                      <div :class="draftTableFooterClass">
                        <div>总手续费</div>
                        <div class="font-numeric mt-1 text-sm font-semibold text-amber-600">
                          {{ formatCurrency(draftSummary.totalActualFee) }}
                        </div>
                      </div>
                    </template>

                    <template #footerActualFee>
                      <div :class="draftTableFooterClass">
                        <div>到账汇总</div>
                        <div class="font-numeric mt-1 text-sm font-semibold text-slate-700">
                          {{ formatCurrency(draftSummary.totalActualNetIncome) }}
                        </div>
                      </div>
                    </template>

                    <template #footerActualNetProfit>
                      <div :class="draftTableFooterFixedClass">
                        <div>净利润</div>
                        <div
                          class="font-numeric mt-1 text-sm font-semibold"
                          :class="profitClass(draftSummary.totalActualNetProfit)"
                        >
                          {{ formatSignedCurrency(draftSummary.totalActualNetProfit) }}
                        </div>
                      </div>
                    </template>

                    <template #footerActualProfitRate>
                      <div :class="draftTableFooterFixedClass">
                        <div>总利润率</div>
                        <div
                          class="font-numeric mt-1 text-sm font-semibold"
                          :class="profitClass(draftSummary.totalActualProfitRate ?? 0)"
                        >
                          {{ formatPercent(draftSummary.totalActualProfitRate) }}
                        </div>
                      </div>
                    </template>

                    <template #footerOperation>
                      <div :class="draftTableFooterFixedClass">
                        <div class="text-slate-400">—</div>
                      </div>
                    </template>
                  </t-table>
                </div>
              </div>

              <div v-else class="space-y-3 p-2.5">
                <article
                  v-for="(
                    { row, metrics, stage, stageTheme, stageDescription }, index
                  ) in draftRowEntries"
                  :key="row.id"
                  class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm"
                >
                  <div
                    class="flex items-start justify-between gap-3 border-b border-slate-100 bg-white px-4 py-3"
                  >
                    <div class="min-w-0">
                      <div class="text-sm font-medium tracking-[0.14em] text-slate-400 uppercase">
                        明细 {{ index + 1 }}
                      </div>
                      <div class="mt-1 text-sm font-semibold text-[#303133]">
                        {{ row.weaponName || "未填写饰品名称" }}
                      </div>
                      <div class="mt-1 text-sm text-slate-500">
                        {{ stageDescription }}
                      </div>
                    </div>
                    <t-tag :theme="stageTheme" variant="light-outline">
                      {{ stage }}
                    </t-tag>
                  </div>

                  <div class="p-4">
                    <div class="grid grid-cols-1 gap-3">
                      <div class="grid grid-cols-2 gap-3">
                        <label class="space-y-1.5">
                          <span class="text-sm font-medium text-slate-600">处理状态</span>
                          <div class="flex flex-wrap gap-2">
                            <button
                              v-for="option in selectableDraftHandlingStatusOptions"
                              :key="option.value"
                              type="button"
                              :class="[
                                `h-8 touch-manipulation ${draftStatusButtonBaseClass}`,
                                row.handlingStatus === option.value
                                  ? getDraftStatusButtonActiveClass(option.value)
                                  : 'text-slate-500',
                              ]"
                              @click="setHandlingStatus(row, option.value)"
                            >
                              {{ option.label }}
                            </button>
                          </div>
                        </label>
                        <label class="space-y-1.5">
                          <span class="text-sm font-medium text-slate-600">饰品名称</span>
                          <div class="flex items-center gap-2">
                            <t-select
                              v-model="row.weaponName"
                              :class="`flex-1 ${fieldBaseClass}`"
                              :disabled="!isRowEditable(row)"
                              :options="weaponNameOptions"
                              clearable
                              filterable
                              placeholder="请选择饰品名称"
                            />
                            <t-popup
                              :visible="activeOcrPopupRowId === row.id"
                              trigger="hover"
                              placement="top"
                              show-arrow
                              attach="body"
                              overlay-inner-class-name="unbox-ocr-popup__inner"
                              :disabled="!isRowEditable(row) || getRowOcrState(row.id).status === 'uploading'"
                              @visible-change="(visible) => handleRowOcrPopupVisibleChange(row.id, visible)"
                            >
                              <template #content>
                                <div class="grid min-w-[148px] grid-cols-2 gap-2">
                                  <t-button
                                    size="small"
                                    variant="outline"
                                    class="w-full"
                                    @click.stop="triggerRowOcrFileSelect(row)"
                                  >
                                    上传
                                  </t-button>
                                  <t-button
                                    size="small"
                                    variant="outline"
                                    class="w-full"
                                    @click.stop="handlePasteRowOcrImage(row)"
                                  >
                                    粘贴
                                  </t-button>
                                </div>
                              </template>
                              <button
                                type="button"
                                class="group inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-md border border-slate-200 bg-white transition-colors hover:border-slate-300 hover:bg-slate-50 focus-visible:ring-2 focus-visible:ring-sky-500/60 focus-visible:ring-offset-1 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-60"
                                :disabled="!isRowEditable(row) || getRowOcrState(row.id).status === 'uploading'"
                                :aria-label="getRowOcrTooltip(row.id)"
                              >
                                <component
                                  :is="getRowOcrIcon(row.id)"
                                  class="h-4 w-4 transition-colors"
                                  :class="getRowOcrIconClass(row.id)"
                                />
                              </button>
                            </t-popup>
                          </div>
                        </label>
                      </div>

                      <div class="grid grid-cols-2 gap-3">
                        <label class="space-y-1.5">
                          <span class="text-sm font-medium text-slate-600">游戏内价格</span>
                          <t-input-number
                            v-model="row.inGamePrice"
                            :decimal-places="2"
                            :disabled="!isRowEditable(row)"
                            :min="0"
                            :step="0.1"
                            align="left"
                            :class="draftNumberFieldClass"
                            placeholder="0.00"
                            theme="normal"
                          />
                        </label>
                        <label class="space-y-1.5">
                          <span class="text-sm font-medium text-slate-600">折扣</span>
                          <t-input-number
                            v-model="row.discount"
                            :decimal-places="2"
                            :disabled="!isRowEditable(row)"
                            :max="1"
                            :min="0"
                            :step="0.01"
                            align="left"
                            :class="draftNumberFieldClass"
                            placeholder="0.72"
                            theme="normal"
                          />
                        </label>
                      </div>

                      <div class="grid grid-cols-2 gap-3">
                        <label class="space-y-1.5">
                          <span class="text-sm font-medium text-slate-600">磨损</span>
                          <t-input-number
                            v-model="row.wear"
                            :decimal-places="16"
                            :disabled="!isRowEditable(row)"
                            :format="formatWearDisplay"
                            :max="1"
                            :min="0"
                            :step="0.000000001"
                            align="left"
                            :class="draftNumberFieldClass"
                            :placeholder="getWearPlaceholder()"
                            theme="normal"
                          />
                        </label>
                        <label class="space-y-1.5">
                          <span class="text-sm font-medium text-slate-600">外观</span>
                          <t-select
                            v-model="row.exterior"
                            :class="fieldBaseClass"
                            :disabled="!isRowEditable(row)"
                            :options="EXTERIOR_OPTIONS"
                            placeholder="请选择外观"
                          />
                        </label>
                      </div>

                      <div class="grid grid-cols-1 gap-3">
                        <div class="rounded-[4px] border border-slate-200/80 bg-white p-3">
                          <div class="text-sm text-slate-500">实际价格</div>
                          <div class="font-numeric mt-2 text-base font-semibold text-[#303133]">
                            {{ formatPendingCurrency(metrics.purchaseCost) }}
                          </div>
                        </div>
                      </div>

                      <div class="grid grid-cols-2 gap-3">
                        <label class="space-y-1.5">
                          <span class="text-sm font-semibold text-sky-700">平台卖出价</span>
                          <div class="flex items-center gap-2">
                            <t-input-number
                              v-model="row.actualSellPrice"
                              :decimal-places="2"
                              :disabled="!isRowEditable(row)"
                              :min="0"
                              :step="0.1"
                              align="left"
                              :class="`flex-1 ${numberFieldPrimaryClass}`"
                              placeholder="优先录这里"
                              theme="normal"
                            />
                            <t-popup
                              :visible="activeC5PopupRowId === row.id"
                              trigger="click"
                              placement="left-top"
                              show-arrow
                              attach="body"
                              overlay-inner-class-name="unbox-c5-popup__inner"
                              :disabled="!isRowEditable(row) || !row.weaponName.trim()"
                              @visible-change="(visible) => handleRowC5PopupVisibleChange(row, visible)"
                            >
                              <template #content>
                                <div class="flex w-[320px] flex-col gap-2">
                                  <div class="flex items-start justify-between gap-3 px-1 pt-2">
                                    <div class="min-w-0">
                                      <div class="text-sm font-semibold text-slate-700">C5 在售挂单</div>
                                      <div class="mt-1 text-xs text-slate-500">
                                        {{ getRowC5TriggerTooltip(row) }}
                                      </div>
                                      <div class="mt-1 text-xs text-slate-500">
                                        {{ getRowC5QuerySummary(row) }}
                                      </div>
                                      <div v-if="getRowC5WearHint(row)" class="mt-1 text-xs text-slate-400">
                                        {{ getRowC5WearHint(row) }}
                                      </div>
                                    </div>
                                    <button
                                      type="button"
                                      class="inline-flex shrink-0 items-center rounded-md border border-slate-200 bg-white px-2 py-1 text-xs font-medium text-slate-600 transition-colors hover:border-sky-300 hover:bg-sky-50 hover:text-sky-600"
                                      @click.stop="runRowC5Query(row, { force: true })"
                                    >
                                      刷新
                                    </button>
                                  </div>
                                  <div class="space-y-2 px-1">
                                    <t-select
                                      :value="getRowC5State(row.id).selectedRangeKey"
                                      :options="getRowC5RangeOptions(row)"
                                      placeholder="选择磨损区间"
                                      size="small"
                                      @change="(value) => handleRowC5RangeChange(row, value)"
                                    />
                                    <div v-if="getRowC5State(row.id).selectedRangeKey === C5_WEAR_RANGE_CUSTOM_KEY" class="space-y-2 rounded-lg border border-slate-200 bg-slate-50/70 px-2 py-2">
                                      <div class="grid grid-cols-2 gap-2">
                                        <t-input-number
                                          :value="getRowC5State(row.id).customWearMin ?? ''"
                                          :decimal-places="4"
                                          :theme="'normal'"
                                          :step="0.0001"
                                          align="right"
                                          placeholder="最小磨损"
                                          @change="(value) => handleRowC5CustomWearMinChange(row, value)"
                                        />
                                        <t-input-number
                                          :value="getRowC5State(row.id).customWearMax ?? ''"
                                          :decimal-places="4"
                                          :theme="'normal'"
                                          :step="0.0001"
                                          align="right"
                                          placeholder="最大磨损"
                                          @change="(value) => handleRowC5CustomWearMaxChange(row, value)"
                                        />
                                      </div>
                                      <div class="text-xs text-slate-500">
                                        {{ getRowC5CustomRangeHint(row) }}
                                      </div>
                                    </div>
                                    <div v-if="getRowC5PresetMissHint(row)" class="text-xs text-amber-600">
                                      {{ getRowC5PresetMissHint(row) }}
                                    </div>
                                    <div class="flex justify-end">
                                      <t-button size="small" theme="primary" @click="runRowC5Query(row, { force: true })">
                                        查询
                                      </t-button>
                                    </div>
                                  </div>
                                  <div
                                    class="max-h-[360px] overflow-y-auto pr-1"
                                    @scroll.passive="(event) => handleRowC5ListScroll(row, event)"
                                  >
                                    <div
                                      v-if="getRowC5State(row.id).status === 'loading'"
                                      class="flex items-center justify-center py-8 text-sm text-slate-500"
                                    >
                                      加载中...
                                    </div>
                                    <div
                                      v-else-if="getRowC5State(row.id).status === 'error' && !getRowC5Listings(row).length"
                                      class="space-y-2 rounded-lg border border-rose-200 bg-rose-50/70 px-3 py-3 text-sm text-rose-600"
                                    >
                                      <div>{{ getRowC5State(row.id).errorMessage || '获取 C5 在售列表失败' }}</div>
                                      <button
                                        type="button"
                                        class="inline-flex items-center rounded-md border border-rose-200 bg-white px-2 py-1 text-xs font-medium text-rose-600 transition-colors hover:bg-rose-50"
                                        @click.stop="runRowC5Query(row, { force: true })"
                                      >
                                        重试
                                      </button>
                                    </div>
                                    <div
                                      v-else-if="!getRowC5Listings(row).length"
                                      class="py-8 text-center text-sm text-slate-500"
                                    >
                                      {{ getRowC5State(row.id).appliedWearMin === null ? '暂无在售挂单' : '当前磨损区间暂无在售挂单' }}
                                    </div>
                                    <div v-else class="space-y-2 px-1">
                                      <button
                                        v-for="listing in getRowC5Listings(row)"
                                        :key="listing.productId"
                                        type="button"
                                        class="flex w-full items-center justify-between rounded-lg border border-slate-200 px-3 py-2 text-left transition-colors hover:border-sky-300 hover:bg-sky-50/70"
                                        @click.stop="applyC5Listing(row, listing)"
                                      >
                                        <div class="min-w-0">
                                          <div class="font-numeric text-sm font-semibold text-slate-800">
                                            {{ formatCurrency(listing.price) }}
                                          </div>
                                          <div class="mt-1 truncate text-xs text-slate-500">
                                            {{ listing.sellerName || listing.sellerUid || '卖家未知' }}
                                          </div>
                                        </div>
                                        <div class="text-right text-xs text-slate-500">
                                          <div>
                                            磨损
                                            {{ listing.wear === null ? '--' : formatWearDisplay(listing.wear) }}
                                          </div>
                                        </div>
                                      </button>
                                      <div v-if="getRowC5State(row.id).loadingMore" class="py-2 text-center text-xs text-slate-500">
                                        加载中...
                                      </div>
                                      <div v-else-if="getRowC5State(row.id).errorMessage" class="py-2 text-center text-xs text-rose-500">
                                        {{ getRowC5State(row.id).errorMessage }}
                                      </div>
                                      <div v-else-if="!getRowC5State(row.id).hasMore" class="py-2 text-center text-xs text-slate-400">
                                        已加载全部挂单
                                      </div>
                                    </div>
                                  </div>
                                </div>
                              </template>
                              <button
                                type="button"
                                class="group inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md border border-slate-200 bg-white transition-colors hover:border-sky-300 hover:bg-sky-50 focus-visible:ring-2 focus-visible:ring-sky-500/60 focus-visible:ring-offset-1 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-60"
                                :disabled="!isRowEditable(row) || !row.weaponName.trim()"
                                :aria-label="getRowC5TriggerTooltip(row)"
                              >
                                <svg
                                  viewBox="0 0 16 16"
                                  aria-hidden="true"
                                  :class="[
                                    'h-4 w-4 text-slate-400 transition-all group-hover:text-sky-500',
                                    activeC5PopupRowId === row.id ? 'rotate-180 text-sky-500' : '',
                                  ]"
                                >
                                  <path
                                    d="M4.47 6.97a.75.75 0 0 1 1.06 0L8 9.44l2.47-2.47a.75.75 0 1 1 1.06 1.06l-3 3a.75.75 0 0 1-1.06 0l-3-3a.75.75 0 0 1 0-1.06Z"
                                    fill="currentColor"
                                  />
                                </svg>
                              </button>
                            </t-popup>
                          </div>
                        </label>
                        <div class="rounded-[4px] border border-slate-200/80 bg-white p-3">
                          <div class="text-sm text-slate-500">手续费</div>
                          <div class="font-numeric mt-2 text-base font-semibold text-amber-600">
                            {{ formatPendingCurrency(metrics.actualFee) }}
                          </div>
                          <div class="mt-1 text-sm text-slate-500">
                            {{ metrics.actualFee === null ? "等平台卖出价" : "平台卖出价 × 1%" }}
                          </div>
                        </div>
                      </div>

                      <div class="grid grid-cols-1 gap-3">
                        <div class="p-1">
                          <div class="text-sm font-medium text-slate-700">净利润</div>
                          <div
                            class="font-numeric mt-2 text-base font-semibold"
                            :class="profitClass(metrics.actualNetProfit ?? 0)"
                          >
                            {{ formatActualProfit(metrics.actualNetProfit) }}
                          </div>
                          <div class="font-numeric mt-1 text-sm text-slate-500">
                            {{
                              metrics.actualNetIncome === null
                                ? "等平台卖出价"
                                : `到账 ${formatCurrency(metrics.actualNetIncome)}`
                            }}
                          </div>
                          <div
                            class="font-numeric mt-1 text-sm font-medium"
                            :class="profitClass(metrics.actualProfitRate ?? 0)"
                          >
                            {{ formatPercent(metrics.actualProfitRate) }}
                          </div>
                        </div>
                      </div>

                      <label class="space-y-1.5">
                        <span class="text-sm font-medium text-slate-600">备注</span>
                        <t-input
                          v-model="row.note"
                          :class="fieldBaseClass"
                          clearable
                          maxlength="50"
                          placeholder="补充判断、卖出节奏或特殊说明"
                        />
                      </label>
                    </div>

                    <div class="mt-4 flex gap-2">
                      <t-button variant="outline" class="flex-1" @click="handleAddRow(index + 1)">
                        下方新增
                      </t-button>
                      <t-button
                        theme="danger"
                        variant="outline"
                        class="flex-1"
                        @click="handleRemoveRow(row.id)"
                      >
                        删除
                      </t-button>
                    </div>
                  </div>
                </article>
              </div>
            </section>
          </div>
        </div>
      </div>
    </t-dialog>
  </PageFrame>
</template>

<script setup lang="ts">
import dayjs, { type Dayjs } from "dayjs";
import { computed, h, onMounted, ref, resolveComponent, watch } from "vue";
import { useElementSize, useWindowSize } from "@vueuse/core";
import { MessagePlugin } from "tdesign-vue-next";
import {
  CheckCircleIcon,
  CloseCircleIcon,
  HelpCircleIcon,
  ImageIcon,
  LoadingIcon,
} from "tdesign-icons-vue-next";
import type {
  AttachNode,
  DateRangeValue as TDateRangeValue,
  PageInfo,
  PrimaryTableCol,
  SelectProps,
  Styles,
  TableSort,
} from "tdesign-vue-next";
import PageFrame from "@/components/PageFrame.vue";
import { goodsApi } from "@/api/goods";
import { unboxApi } from "@/api/unbox";
import type { GoodsSimple } from "@/types/goods";
import type {
  DraftHandlingStatus,
  UnboxRecordC5Listing,
  UnboxRecordC5ListingPageResult,
  UnboxRecordC5ListingQueryParam,
  UnboxRecordDTO,
  UnboxRecordOcrResult,
  UnboxRecordPageDTO,
  UnboxRecordSaveParam,
  UnboxRecordSummaryDTO,
} from "@/types/unbox";

type DiscountValue = number | "";

interface UnboxRow {
  id: string;
  handlingStatus: DraftHandlingStatus;
  boxPurchasePrice: number;
  weaponName: string;
  inGamePrice: number;
  discount: DiscountValue;
  actualSellPrice: number;
  wear: number | "";
  exterior: number;
  note: string;
}

interface UnboxBatch {
  id: number;
  goodsId?: number;
  boxName: string;
  date: string;
  defaultDiscount: DiscountValue;
  note: string;
  rows: UnboxRow[];
}

interface BatchListRecord {
  id: number;
  goodsId?: number;
  boxName: string;
  date: string;
  defaultDiscount: DiscountValue;
  note: string;
}

type BatchStatus = "未结算" | "部分结算" | "已结算";

type DraftRowStage = "待处理" | "丢弃" | "暂存" | "已买";
type DraftRowStageTheme = "default" | "danger" | "warning" | "success";
type SelectableDraftHandlingStatus = Exclude<DraftHandlingStatus, "pending">;

interface SelectableDraftHandlingStatusOption {
  value: SelectableDraftHandlingStatus;
  label: Exclude<DraftRowStage, "待处理">;
  theme: Exclude<DraftRowStageTheme, "default">;
}

interface RowMetrics {
  purchaseCost: number | null;
  actualFee: number | null;
  actualNetIncome: number | null;
  actualNetProfit: number | null;
  actualProfitRate: number | null;
}

interface BatchSummary {
  totalCount: number;
  countedRowCount: number;
  boughtCount: number;
  totalInGamePrice: number;
  totalPurchaseCost: number;
  soldCount: number;
  unsoldCount: number;
  totalActualNetIncome: number;
  totalActualFee: number;
  totalActualNetProfit: number;
  totalActualProfitRate: number | null;
}

interface SummaryCard {
  label: string;
  value: string;
  hint: string;
  valueClass: string;
}

type PeriodFilter = "week" | "month" | "year";
type DateRangeValue = TDateRangeValue;

interface BatchPageSummary {
  totalCount: number;
  totalPurchaseCost: number;
  totalActualFee: number;
  totalActualNetProfit: number;
  totalActualProfitRate: number | null;
}

interface BatchSummaryRow {
  key: number;
  batch: BatchListRecord;
  summary: BatchPageSummary;
  status: BatchStatus;
}

interface DraftRowEntry {
  row: UnboxRow;
  metrics: RowMetrics;
  stage: DraftRowStage;
  stageTheme: DraftRowStageTheme;
  stageDescription: string;
}

interface DraftFooterRow {
  type: "summary";
}

type OcrStatus = "idle" | "uploading" | "success" | "error";

interface RowOcrState {
  status: OcrStatus;
  errorMessage: string;
}

type C5RangeMode = "all" | "preset" | "custom";
type C5QueryStatus = "idle" | "loading" | "success" | "error";

interface C5WearRangeOption {
  label: string;
  value: string;
  mode: C5RangeMode;
  wearMin?: number;
  wearMax?: number;
  exteriorMin?: number;
  exteriorMax?: number;
}

interface RowC5State {
  status: C5QueryStatus;
  errorMessage: string;
  listings: UnboxRecordC5Listing[];
  queryKey: string;
  initialized: boolean;
  loadingMore: boolean;
  hasMore: boolean;
  pageNum: number;
  pageSize: number;
  selectedRangeKey: string;
  customWearMin: number | null;
  customWearMax: number | null;
  appliedWearMin: number | null;
  appliedWearMax: number | null;
  appliedRangeKey: string;
  invalidationKey: string;
}

const OCR_FILE_SIZE_LIMIT = 5 * 1024 * 1024;
const OCR_FIELD_MISSING_MESSAGE = "未识别到价格，请重新上传";
const OCR_REQUEST_FAILURE_MESSAGE = "图片识别失败";
const C5_POPUP_PAGE_SIZE = 20;
const C5_WEAR_RANGE_ALL_KEY = "all";
const C5_WEAR_RANGE_CUSTOM_KEY = "custom";
const EXTERIOR_OPTIONS = [
  { label: "崭新出厂", value: 0 },
  { label: "略有磨损", value: 1 },
  { label: "久经沙场", value: 2 },
  { label: "破损不堪", value: 3 },
  { label: "战痕累累", value: 4 },
];
const BASE_WEAPON_NAME_OPTIONS = [
  { label: "SCAR-20 | 牢笼", value: "SCAR-20 | 牢笼" },
  { label: "AUG | 后发制人", value: "AUG | 后发制人" },
  { label: "P2000 | 红翼", value: "P2000 | 红翼" },
  { label: "MP9 | 打口碟", value: "MP9 | 打口碟" },
  { label: "P250 | 牛蛙", value: "P250 | 牛蛙" },
  { label: "MAG-7 | 震级", value: "MAG-7 | 震级" },
  { label: "MP5-SD | 专注", value: "MP5-SD | 专注" },
  { label: "新星 | 目镜", value: "新星 | 目镜" },
  { label: "M4A1消音版 | 液化", value: "M4A1消音版 | 液化" },
  { label: "双持贝瑞塔 | 天矢之眼", value: "双持贝瑞塔 | 天矢之眼" },
  { label: "MAC-10 | 纸老虎", value: "MAC-10 | 纸老虎" },
  { label: "UMP-45 | 连续体", value: "UMP-45 | 连续体" },
  { label: "AWP | 可燃冰", value: "AWP | 可燃冰" },
  { label: "MP7 | 吸烟有害健康", value: "MP7 | 吸烟有害健康" },
  { label: "格洛克18型 | 镜面马赛克", value: "格洛克18型 | 镜面马赛克" },
  { label: "M4A4 | 破浪狂飙", value: "M4A4 | 破浪狂飙" },
  { label: "AK-47 | 流金王朝", value: "AK-47 | 流金王朝" },
];
const C5_EXTERIOR_TOTAL_RANGES: Record<number, { min: number; max: number }> = {
  0: { min: 0, max: 0.07 },
  1: { min: 0.07, max: 0.15 },
  2: { min: 0.15, max: 0.38 },
  3: { min: 0.38, max: 0.45 },
  4: { min: 0.45, max: 1 },
};
const C5_EXTERIOR_PRESET_RANGES: Record<number, Array<{ min: number; max: number }>> = {
  0: [
    { min: 0, max: 0.01 },
    { min: 0.01, max: 0.02 },
    { min: 0.02, max: 0.03 },
    { min: 0.03, max: 0.04 },
    { min: 0.04, max: 0.07 },
  ],
  1: [
    { min: 0.07, max: 0.08 },
    { min: 0.08, max: 0.09 },
    { min: 0.09, max: 0.1 },
    { min: 0.1, max: 0.11 },
    { min: 0.11, max: 0.15 },
  ],
  2: [
    { min: 0.15, max: 0.18 },
    { min: 0.18, max: 0.21 },
    { min: 0.21, max: 0.24 },
    { min: 0.24, max: 0.27 },
    { min: 0.27, max: 0.38 },
  ],
  3: [
    { min: 0.38, max: 0.39 },
    { min: 0.39, max: 0.4 },
    { min: 0.4, max: 0.41 },
    { min: 0.41, max: 0.42 },
    { min: 0.42, max: 0.45 },
  ],
  4: [
    { min: 0.45, max: 0.5 },
    { min: 0.5, max: 0.63 },
    { min: 0.63, max: 0.7 },
  ],
};

const currencyFormatter = new Intl.NumberFormat("zh-CN", {
  style: "currency",
  currency: "CNY",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const dateFormatter = new Intl.DateTimeFormat("zh-CN", {
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

const { width } = useWindowSize();
const isMobile = computed(() => width.value <= 640);

const pageHostRef = ref<HTMLElement | null>(null);
const editorDialogAttach = computed<AttachNode>(() => {
  if (isMobile.value) return "body";
  return () => pageHostRef.value ?? document.body;
});
const editorContentRef = ref<HTMLElement | null>(null);
const batchInfoSectionRef = ref<HTMLElement | null>(null);
const { height: editorContentHeight } = useElementSize(editorContentRef);
const { height: batchInfoSectionHeight } = useElementSize(batchInfoSectionRef);

const editorVisible = ref(false);
const isEditorFullscreen = ref(false);
const editingBatchId = ref<number | null>(null);
const isBatchInfoCollapsed = ref(true);
const bulkAddCount = ref(20);
const toolbarBoxPurchasePrice = ref(0);
const batchSummarySort = ref<TableSort>();
const batchPagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
});
const draftTableSort = ref<TableSort>();
const activePeriod = ref<PeriodFilter>("month");
const customDateRange = ref<DateRangeValue>([]);
const appliedDateRange = ref<DateRangeValue>([]);
const appliedPeriod = ref<PeriodFilter>("month");
const listLoading = ref(false);
const summaryLoading = ref(false);
const detailLoading = ref(false);
const savingBatch = ref(false);
const goodsLoading = ref(false);
const goodsCatalog = ref<GoodsSimple[]>([]);
const rowOcrStateMap = ref<Record<string, RowOcrState>>({});
const rowC5StateMap = ref<Record<string, RowC5State>>({});
const ocrInputRef = ref<HTMLInputElement | null>(null);
const activeOcrRowId = ref<string | null>(null);
const activeOcrPopupRowId = ref<string | null>(null);
const activeC5PopupRowId = ref<string | null>(null);
const loadingDetailBatchId = ref<number | null>(null);

const editorDialogClassName = computed(() => {
  if (isMobile.value || isEditorFullscreen.value) {
    return "!absolute !inset-0 !m-0 flex h-full min-h-full w-full max-w-none overflow-visible rounded-none";
  }
  return "!absolute !inset-0 !m-0 flex h-full min-h-full w-full max-w-none overflow-visible rounded-[1.25rem]";
});

const editorDialogStyle = computed((): Styles | undefined => {
  if (isMobile.value) return undefined;
  return {
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    width: "100%",
    maxWidth: "none",
    height: "100%",
    minHeight: "100%",
    padding: 0,
    margin: 0,
    boxShadow: "none",
  } as Styles;
});

const editorBodyClass = computed(() => {
  if (isMobile.value) {
    return "h-full min-h-0 overflow-hidden w-dvw overscroll-contain";
  }
  return "h-full min-h-0 overflow-hidden overscroll-contain";
});

const createId = () => {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
};

const round = (value: number, digits = 2) => {
  const base = 10 ** digits;
  return Math.round((Number.isFinite(value) ? value : 0) * base) / base;
};

const formatWearDisplay = (
  value?: number | string | null,
  context?: { fixedNumber?: number | string },
) => {
  const normalizedValue = context?.fixedNumber ?? value;
  if (normalizedValue === undefined || normalizedValue === null || normalizedValue === "") {
    return "";
  }

  const numericValue = typeof normalizedValue === "string" ? Number(normalizedValue) : normalizedValue;
  if (!Number.isFinite(numericValue)) {
    return "";
  }

  return numericValue.toFixed(9).replace(/\.0+$/, "").replace(/\.?0+$/, "");
};

const LISTING_FEE_RATE = 0.01;
const TOTAL_FEE_RATE = LISTING_FEE_RATE;

const clampDiscount = (value: DiscountValue | null | undefined) => {
  if (value === "" || value === null || value === undefined) return 0;
  const safeValue = Number.isFinite(value) ? value : 0;
  return round(Math.min(Math.max(safeValue, 0), 1), 2);
};

function hasDiscountValue(value: DiscountValue | null | undefined): value is number {
  return value !== "" && value !== null && value !== undefined && Number.isFinite(value);
}

function getWearPlaceholder() {
  return editingBatchId.value ? "-" : "";
}

const getFee = (sellPrice: number) => round(Math.max(sellPrice, 0) * TOTAL_FEE_RATE);

const periodOptions: Array<{ label: string; value: PeriodFilter }> = [
  { label: "本周", value: "week" },
  { label: "本月", value: "month" },
  { label: "本年", value: "year" },
];

const formatDayKey = (value: Dayjs) => value.format("YYYY-MM-DD");

function getCurrentWeekRange(baseDate = dayjs()): [string, string] {
  const currentDay = baseDate.day();
  const offsetToMonday = currentDay === 0 ? 6 : currentDay - 1;
  const start = baseDate.subtract(offsetToMonday, "day");
  const end = start.add(6, "day");
  return [formatDayKey(start), formatDayKey(end)];
}

function getPeriodRange(period: PeriodFilter, baseDate = dayjs()): [string, string] {
  if (period === "week") {
    return getCurrentWeekRange(baseDate);
  }

  if (period === "year") {
    return [formatDayKey(baseDate.startOf("year")), formatDayKey(baseDate.endOf("year"))];
  }

  return [formatDayKey(baseDate.startOf("month")), formatDayKey(baseDate.endOf("month"))];
}

const currentPeriodRange = computed<[string, string]>(() => getPeriodRange(appliedPeriod.value));

const initialSummaryRange = getPeriodRange(activePeriod.value);
customDateRange.value = [...initialSummaryRange];
appliedDateRange.value = [...initialSummaryRange];

const dateRangeValue = computed<DateRangeValue>(() => customDateRange.value);

function normalizeDateRange(value: DateRangeValue): [string, string] | [] {
  if (!Array.isArray(value) || value.length !== 2 || !value[0] || !value[1]) {
    return [];
  }

  const start = dayjs(value[0]);
  const end = dayjs(value[1]);

  if (!start.isValid() || !end.isValid()) {
    return [];
  }

  return start.isAfter(end)
    ? [formatDayKey(end), formatDayKey(start)]
    : [formatDayKey(start), formatDayKey(end)];
}

function handleDateRangeChange(value: DateRangeValue) {
  customDateRange.value = normalizeDateRange(value);
}

function applySummaryFilters() {
  appliedPeriod.value = activePeriod.value;
  appliedDateRange.value = customDateRange.value.length === 2 ? [...customDateRange.value] : [];
  batchPagination.value.current = 1;
  void loadBatchPageAndSummary();
}

function setActivePeriod(period: PeriodFilter) {
  if (activePeriod.value === period) return;
  activePeriod.value = period;
  customDateRange.value = getPeriodRange(period);
  applySummaryFilters();
}

function handleSummarySearch() {
  applySummaryFilters();
}

function resetFilters() {
  activePeriod.value = "month";
  customDateRange.value = [];
  applySummaryFilters();
}

const createRow = (
  defaults?: Partial<UnboxRow>,
  batch?: Pick<UnboxBatch, "defaultDiscount">
): UnboxRow => ({
  id: createId(),
  handlingStatus: defaults?.handlingStatus ?? "pending",
  boxPurchasePrice: defaults?.boxPurchasePrice ?? 0,
  weaponName: defaults?.weaponName ?? "",
  inGamePrice: defaults?.inGamePrice ?? 0,
  discount:
    defaults?.discount ?? (hasDiscountValue(batch?.defaultDiscount) ? batch.defaultDiscount : ""),
  actualSellPrice: defaults?.actualSellPrice ?? 0,
  wear: defaults?.wear ?? "",
  exterior: defaults?.exterior ?? 0,
  note: defaults?.note ?? "",
});

const createBlankBatch = (): UnboxBatch => ({
  id: 0,
  goodsId: undefined,
  boxName: "",
  date: dayjs().format("YYYY-MM-DD"),
  defaultDiscount: "",
  note: "",
  rows: [],
});

const EMPTY_SUMMARY: UnboxRecordSummaryDTO = {
  totalBatches: 0,
  totalPurchaseCost: 0,
  totalFee: 0,
  totalActualNetProfit: 0,
};

const weaponNameOptions = computed(() => {
  const optionMap = new Map(BASE_WEAPON_NAME_OPTIONS.map((item) => [item.value, item]));
  for (const row of draftBatch.value.rows) {
    const weaponName = row.weaponName.trim();
    if (!weaponName || optionMap.has(weaponName)) continue;
    optionMap.set(weaponName, { label: weaponName, value: weaponName });
  }
  return Array.from(optionMap.values());
});

const draftBatch = ref<UnboxBatch>(createBlankBatch());
const currentPageBatchSummaryRows = ref<BatchSummaryRow[]>([]);
const summaryState = ref<UnboxRecordSummaryDTO>({ ...EMPTY_SUMMARY });

function normalizeGoodsKeyword(keyword: string) {
  return keyword.trim();
}

function getGoodsOptionLabel(goods: Pick<GoodsSimple, "name" | "parentCategoryName">) {
  return goods.parentCategoryName ? `${goods.name}（${goods.parentCategoryName}）` : goods.name;
}

function getGoodsOptionValue(goods: GoodsSimple) {
  return goods.id ?? goods.goodsId;
}

function ensureGoodsInCatalog(goodsId: number | undefined, boxName: string) {
  if (!goodsId || !boxName) return;
  const exists = goodsCatalog.value.some((item) => getGoodsOptionValue(item) === goodsId);
  if (exists) return;
  goodsCatalog.value = [{ id: goodsId, goodsId, name: boxName }, ...goodsCatalog.value];
}

const goodsOptions = computed(() =>
  goodsCatalog.value.map((item) => ({
    label: getGoodsOptionLabel(item),
    value: getGoodsOptionValue(item),
  }))
);

function getBatchDisplayName(batch: Pick<UnboxBatch, "date" | "boxName">) {
  if (batch.date && batch.boxName) return `${batch.date} ${batch.boxName}`;
  if (batch.boxName) return batch.boxName;
  if (batch.date) return `${batch.date} 开箱批次`;
  return "未命名批次";
}

function createDefaultRowOcrState(): RowOcrState {
  return {
    status: "idle",
    errorMessage: "",
  };
}

function createDefaultRowC5State(): RowC5State {
  return {
    status: "idle",
    errorMessage: "",
    listings: [],
    queryKey: "",
    initialized: false,
    loadingMore: false,
    hasMore: false,
    pageNum: 1,
    pageSize: C5_POPUP_PAGE_SIZE,
    selectedRangeKey: C5_WEAR_RANGE_ALL_KEY,
    customWearMin: null,
    customWearMax: null,
    appliedWearMin: null,
    appliedWearMax: null,
    appliedRangeKey: C5_WEAR_RANGE_ALL_KEY,
    invalidationKey: "",
  };
}

function resetRowStates(rows: UnboxRow[]) {
  rowOcrStateMap.value = Object.fromEntries(
    rows.map((row) => [row.id, createDefaultRowOcrState()])
  );
  rowC5StateMap.value = Object.fromEntries(
    rows.map((row) => [row.id, createDefaultRowC5State()])
  );
  activeOcrRowId.value = null;
  activeOcrPopupRowId.value = null;
  activeC5PopupRowId.value = null;
}

function ensureRowOcrState(rowId: string) {
  if (!rowOcrStateMap.value[rowId]) {
    rowOcrStateMap.value[rowId] = createDefaultRowOcrState();
  }
  return rowOcrStateMap.value[rowId];
}

function ensureRowC5State(rowId: string) {
  if (!rowC5StateMap.value[rowId]) {
    rowC5StateMap.value[rowId] = createDefaultRowC5State();
  }
  return rowC5StateMap.value[rowId];
}

function getRowOcrState(rowId: string) {
  return ensureRowOcrState(rowId);
}

function getRowC5State(rowId: string) {
  return ensureRowC5State(rowId);
}

function getRowOcrTooltip(rowId: string) {
  const state = getRowOcrState(rowId);
  if (state.status === "success") {
    return "识别成功，可重新上传或粘贴";
  }
  if (state.status === "error") {
    return state.errorMessage || "识别失败，可重新上传或粘贴";
  }
  if (state.status === "uploading") {
    return "识别中...";
  }
  return "上传或粘贴图片识别价格和磨损";
}

function getOcrErrorMessage(error: unknown, fallback = OCR_REQUEST_FAILURE_MESSAGE) {
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallback;
}

function normalizeOcrResultPrice(result: UnboxRecordOcrResult) {
  const rawValue = result.price;
  if (rawValue === null || rawValue === undefined || rawValue === "") {
    return null;
  }
  const numericValue = Number(rawValue);
  return Number.isFinite(numericValue) ? round(numericValue) : null;
}

function normalizeOcrResultWear(result: UnboxRecordOcrResult) {
  const rawValue = result.wear;
  if (rawValue === null || rawValue === undefined || rawValue === "") {
    return null;
  }
  const numericValue = Number(rawValue);
  if (!Number.isFinite(numericValue)) {
    return null;
  }
  return numericValue >= 0 && numericValue <= 1 ? numericValue : null;
}

function normalizeOcrResultExterior(result: UnboxRecordOcrResult) {
  const rawValue = result.exterior;
  if (rawValue === null || rawValue === undefined || rawValue === "") {
    return null;
  }
  const numericValue = Number(rawValue);
  if (!Number.isInteger(numericValue)) {
    return null;
  }
  return numericValue >= 0 && numericValue <= 4 ? numericValue : null;
}

function handleOcrValidationFailure(rowId: string, message: string) {
  const state = ensureRowOcrState(rowId);
  state.status = "error";
  state.errorMessage = message;
  MessagePlugin.warning(message);
}

function validateOcrImage(file: File, rowId: string) {
  if (!file.type.startsWith("image/")) {
    handleOcrValidationFailure(rowId, "仅支持上传图片文件");
    return false;
  }
  if (file.size > OCR_FILE_SIZE_LIMIT) {
    handleOcrValidationFailure(rowId, "图片大小不能超过 5MB");
    return false;
  }
  return true;
}

function handleRowOcrPopupVisibleChange(rowId: string, visible: boolean) {
  activeOcrPopupRowId.value = visible ? rowId : activeOcrPopupRowId.value === rowId ? null : activeOcrPopupRowId.value;
}

function handleRowC5PopupVisibleChange(row: UnboxRow, visible: boolean) {
  activeC5PopupRowId.value = visible ? row.id : activeC5PopupRowId.value === row.id ? null : activeC5PopupRowId.value;
  if (visible) {
    initializeRowC5State(row);
    void runRowC5Query(row);
  }
}

function formatC5WearBoundary(value: number) {
  return value.toFixed(4).replace(/0+$/, "").replace(/\.$/, ".0");
}

function formatC5WearRangeLabel(min: number, max: number) {
  return `${formatC5WearBoundary(min)} - ${formatC5WearBoundary(max)}`;
}

function normalizeC5WearValue(value: number | null | undefined) {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return null;
  }
  return Number(value.toFixed(4));
}

function getRowExteriorValue(row: UnboxRow) {
  return Number.isInteger(row.exterior) ? row.exterior : null;
}

function getRowWearValue(row: UnboxRow) {
  if (row.wear === "") {
    return null;
  }
  return Number.isFinite(row.wear) ? row.wear : null;
}

function getC5ExteriorRange(exterior: number | null) {
  if (exterior === null) return null;
  return C5_EXTERIOR_TOTAL_RANGES[exterior] ?? null;
}

function isWearInRange(wear: number, min: number, max: number) {
  const normalizedWear = normalizeC5WearValue(wear);
  if (normalizedWear === null) return false;
  if (max >= 1) {
    return normalizedWear >= min && normalizedWear <= max;
  }
  return normalizedWear >= min && normalizedWear < max;
}

function getRowC5RangeOptions(row: UnboxRow): C5WearRangeOption[] {
  const options: C5WearRangeOption[] = [
    {
      label: "不筛磨损（按价格）",
      value: C5_WEAR_RANGE_ALL_KEY,
      mode: "all",
    },
  ];
  const exterior = getRowExteriorValue(row);
  if (exterior === null) {
    return options;
  }
  const presets = C5_EXTERIOR_PRESET_RANGES[exterior] ?? [];
  options.push(
    ...presets.map((item) => ({
      label: formatC5WearRangeLabel(item.min, item.max),
      value: `${item.min}-${item.max}`,
      mode: "preset" as const,
      wearMin: item.min,
      wearMax: item.max,
    }))
  );
  const exteriorRange = getC5ExteriorRange(exterior);
  if (exteriorRange) {
    options.push({
      label: "自定义区间",
      value: C5_WEAR_RANGE_CUSTOM_KEY,
      mode: "custom",
      exteriorMin: exteriorRange.min,
      exteriorMax: exteriorRange.max,
    });
  }
  return options;
}

function getRowC5SelectedOption(row: UnboxRow) {
  const state = getRowC5State(row.id);
  return getRowC5RangeOptions(row).find((item) => item.value === state.selectedRangeKey) ?? null;
}

function getDefaultRowC5RangeKey(row: UnboxRow) {
  const wear = getRowWearValue(row);
  if (wear === null) {
    return C5_WEAR_RANGE_ALL_KEY;
  }
  const options = getRowC5RangeOptions(row);
  const presetOption = options.find(
    (item) => item.mode === "preset" && item.wearMin !== undefined && item.wearMax !== undefined && isWearInRange(wear, item.wearMin, item.wearMax)
  );
  if (presetOption) {
    return presetOption.value;
  }
  const exterior = getRowExteriorValue(row);
  const exteriorRange = getC5ExteriorRange(exterior);
  if (exteriorRange && isWearInRange(wear, exteriorRange.min, exteriorRange.max)) {
    return C5_WEAR_RANGE_CUSTOM_KEY;
  }
  return C5_WEAR_RANGE_ALL_KEY;
}

function getRowC5InvalidationKey(row: UnboxRow) {
  return JSON.stringify({
    weaponName: row.weaponName.trim(),
    exterior: getRowExteriorValue(row),
    wear: getRowWearValue(row),
  });
}

function initializeRowC5State(row: UnboxRow) {
  const state = ensureRowC5State(row.id);
  const invalidationKey = getRowC5InvalidationKey(row);
  if (state.invalidationKey === invalidationKey && state.initialized) {
    return;
  }
  const defaultRangeKey = getDefaultRowC5RangeKey(row);
  state.status = "idle";
  state.errorMessage = "";
  state.listings = [];
  state.queryKey = "";
  state.initialized = true;
  state.loadingMore = false;
  state.hasMore = false;
  state.pageNum = 1;
  state.pageSize = C5_POPUP_PAGE_SIZE;
  state.selectedRangeKey = defaultRangeKey;
  state.customWearMin = null;
  state.customWearMax = null;
  state.appliedWearMin = null;
  state.appliedWearMax = null;
  state.appliedRangeKey = C5_WEAR_RANGE_ALL_KEY;
  state.invalidationKey = invalidationKey;
}

function resetRowC5Pagination(state: RowC5State) {
  state.pageNum = 1;
  state.hasMore = false;
  state.listings = [];
}

function getRowC5QuerySummary(row: UnboxRow) {
  const state = getRowC5State(row.id);
  const option = getRowC5SelectedOption(row);
  if (state.appliedRangeKey === C5_WEAR_RANGE_ALL_KEY) {
    return "当前区间：不筛磨损（按价格）";
  }
  if (state.appliedRangeKey !== C5_WEAR_RANGE_CUSTOM_KEY) {
    const appliedOption = getRowC5RangeOptions(row).find((item) => item.value === state.appliedRangeKey);
    return `当前区间：${appliedOption?.label ?? option?.label ?? "未选择"}`;
  }
  if (state.appliedWearMin !== null && state.appliedWearMax !== null) {
    return `当前区间：自定义 ${formatC5WearRangeLabel(state.appliedWearMin, state.appliedWearMax)}`;
  }
  return "当前区间：自定义区间";
}

function getRowC5WearHint(row: UnboxRow) {
  const wear = getRowWearValue(row);
  if (wear === null) {
    return "";
  }
  const exterior = EXTERIOR_OPTIONS.find((item) => item.value === getRowExteriorValue(row));
  return `当前磨损：${formatWearDisplay(wear)}${exterior ? ` · 当前外观：${exterior.label}` : ""}`;
}

function getRowC5CustomRangeHint(row: UnboxRow) {
  const option = getRowC5SelectedOption(row);
  if (!option || option.mode !== "custom") {
    return "";
  }
  return `自定义区间需落在 ${formatC5WearRangeLabel(option.exteriorMin ?? 0, option.exteriorMax ?? 1)}`;
}

function getRowC5PresetMissHint(row: UnboxRow) {
  const wear = getRowWearValue(row);
  if (wear === null) {
    return "";
  }
  const state = getRowC5State(row.id);
  if (state.selectedRangeKey !== C5_WEAR_RANGE_CUSTOM_KEY) {
    return "";
  }
  return `当前磨损 ${formatWearDisplay(wear)} 不在预设区间内，请手动设置`;
}

function buildRowC5Query(row: UnboxRow, pageNum: number): UnboxRecordC5ListingQueryParam {
  const state = getRowC5State(row.id);
  return {
    weaponName: row.weaponName.trim(),
    wearMin: state.appliedWearMin,
    wearMax: state.appliedWearMax,
    exterior: getRowExteriorValue(row),
    pageNum,
    pageSize: state.pageSize,
  };
}

function getRowC5QueryKey(row: UnboxRow, pageNum: number) {
  return JSON.stringify(buildRowC5Query(row, pageNum));
}

function validateRowC5CustomRange(row: UnboxRow) {
  const state = getRowC5State(row.id);
  const option = getRowC5SelectedOption(row);
  if (!option || option.mode !== "custom") {
    return { valid: true as const, wearMin: null, wearMax: null };
  }
  const wearMin = state.customWearMin;
  const wearMax = state.customWearMax;
  if (wearMin === null) {
    state.errorMessage = "最小磨损不能为空";
    return { valid: false as const, wearMin: null, wearMax: null };
  }
  if (wearMax === null) {
    state.errorMessage = "最大磨损不能为空";
    return { valid: false as const, wearMin: null, wearMax: null };
  }
  if (!Number.isFinite(wearMin) || !Number.isFinite(wearMax)) {
    state.errorMessage = "自定义区间必须是数字";
    return { valid: false as const, wearMin: null, wearMax: null };
  }
  const normalizedMin = normalizeC5WearValue(wearMin);
  const normalizedMax = normalizeC5WearValue(wearMax);
  if (normalizedMin === null || normalizedMax === null) {
    state.errorMessage = "自定义区间必须是数字";
    return { valid: false as const, wearMin: null, wearMax: null };
  }
  if (normalizedMin < 0 || normalizedMax > 1) {
    state.errorMessage = "自定义区间必须在 0 到 1 之间";
    return { valid: false as const, wearMin: null, wearMax: null };
  }
  const exteriorMin = option.exteriorMin ?? 0;
  const exteriorMax = option.exteriorMax ?? 1;
  if (normalizedMin < exteriorMin || normalizedMax > exteriorMax) {
    state.errorMessage = `自定义区间必须落在当前外观范围内（${formatC5WearRangeLabel(exteriorMin, exteriorMax)}）`;
    return { valid: false as const, wearMin: null, wearMax: null };
  }
  if (normalizedMin >= normalizedMax) {
    state.errorMessage = "最小磨损必须小于最大磨损";
    return { valid: false as const, wearMin: null, wearMax: null };
  }
  return { valid: true as const, wearMin: normalizedMin, wearMax: normalizedMax };
}

function applyRowC5Filters(row: UnboxRow) {
  const state = getRowC5State(row.id);
  const option = getRowC5SelectedOption(row);
  state.errorMessage = "";
  if (!option || option.mode === "all") {
    state.appliedWearMin = null;
    state.appliedWearMax = null;
    state.appliedRangeKey = C5_WEAR_RANGE_ALL_KEY;
    return true;
  }
  if (option.mode === "preset") {
    state.appliedWearMin = option.wearMin ?? null;
    state.appliedWearMax = option.wearMax ?? null;
    state.appliedRangeKey = option.value;
    return true;
  }
  const result = validateRowC5CustomRange(row);
  if (!result.valid) {
    MessagePlugin.warning(state.errorMessage);
    return false;
  }
  state.appliedWearMin = result.wearMin;
  state.appliedWearMax = result.wearMax;
  state.appliedRangeKey = C5_WEAR_RANGE_CUSTOM_KEY;
  return true;
}

async function fetchRowC5Listings(row: UnboxRow, pageNum: number) {
  const state = getRowC5State(row.id);
  const query = buildRowC5Query(row, pageNum);
  const result = await unboxApi.queryC5Listings(query);
  const pageResult: UnboxRecordC5ListingPageResult = result;
  state.queryKey = getRowC5QueryKey(row, pageNum);
  state.pageNum = pageResult.pageNum || pageNum;
  state.pageSize = pageResult.pageSize || state.pageSize;
  state.hasMore = Boolean(pageResult.hasMore);
  state.listings = pageNum === 1 ? pageResult.records : [...state.listings, ...pageResult.records];
}

async function runRowC5Query(row: UnboxRow, options?: { force?: boolean }) {
  const state = getRowC5State(row.id);
  if (!row.weaponName.trim()) {
    state.status = "error";
    state.errorMessage = "请先填写饰品名称";
    state.listings = [];
    state.queryKey = "";
    return;
  }
  if (!applyRowC5Filters(row)) {
    state.status = "error";
    return;
  }
  const nextQueryKey = getRowC5QueryKey(row, 1);
  if (!options?.force && state.status === "success" && state.queryKey === nextQueryKey) {
    return;
  }
  if (state.status === "loading") {
    return;
  }
  state.status = "loading";
  state.errorMessage = "";
  resetRowC5Pagination(state);
  try {
    await fetchRowC5Listings(row, 1);
    state.status = "success";
  } catch (error) {
    state.status = "error";
    state.errorMessage = getOcrErrorMessage(error, "获取 C5 在售列表失败");
    state.listings = [];
    state.hasMore = false;
  }
}

async function loadMoreRowC5Listings(row: UnboxRow) {
  const state = getRowC5State(row.id);
  if (state.status !== "success" || state.loadingMore || !state.hasMore) {
    return;
  }
  state.loadingMore = true;
  state.errorMessage = "";
  try {
    await fetchRowC5Listings(row, state.pageNum + 1);
  } catch (error) {
    state.errorMessage = getOcrErrorMessage(error, "加载更多失败");
  } finally {
    state.loadingMore = false;
  }
}

function handleRowC5ListScroll(row: UnboxRow, event: Event) {
  const target = event.target as HTMLElement | null;
  if (!target) {
    return;
  }
  const distanceToBottom = target.scrollHeight - target.scrollTop - target.clientHeight;
  if (distanceToBottom <= 24) {
    void loadMoreRowC5Listings(row);
  }
}

function handleRowC5RangeChange(row: UnboxRow, value: SelectProps["value"]) {
  const state = getRowC5State(row.id);
  state.selectedRangeKey = String(value ?? C5_WEAR_RANGE_ALL_KEY);
  state.errorMessage = "";
}

function handleRowC5CustomWearMinChange(row: UnboxRow, value: string | number) {
  const state = getRowC5State(row.id);
  if (value === "") {
    state.customWearMin = null;
    return;
  }
  const numericValue = Number(value);
  state.customWearMin = Number.isFinite(numericValue) ? numericValue : null;
}

function handleRowC5CustomWearMaxChange(row: UnboxRow, value: string | number) {
  const state = getRowC5State(row.id);
  if (value === "") {
    state.customWearMax = null;
    return;
  }
  const numericValue = Number(value);
  state.customWearMax = Number.isFinite(numericValue) ? numericValue : null;
}

function getRowC5Listings(row: UnboxRow) {
  return getRowC5State(row.id).listings;
}

function getRowC5TriggerTooltip(row: UnboxRow) {
  if (!row.weaponName.trim()) {
    return "请先填写饰品名称";
  }
  const state = getRowC5State(row.id);
  if (state.status === "loading") {
    return "C5 在售列表加载中";
  }
  if (state.status === "error") {
    return state.errorMessage || "获取 C5 在售列表失败，点击重试";
  }
  if (getRowWearValue(row) === null) {
    return "查看 C5 在售最低价并回填卖出价";
  }
  return "按磨损区间查看 C5 在售列表并回填卖出价";
}

function applyC5Listing(row: UnboxRow, listing: UnboxRecordC5Listing) {
  row.actualSellPrice = round(Number(listing.price ?? 0));
  activeC5PopupRowId.value = null;
  MessagePlugin.success(`已回填平台卖出价 ${formatCurrency(row.actualSellPrice)}`);
}

function triggerRowOcrFileSelect(row: UnboxRow) {
  if (!isRowEditable(row)) return;
  const state = getRowOcrState(row.id);
  if (state.status === "uploading") return;
  activeOcrPopupRowId.value = null;
  activeOcrRowId.value = row.id;
  ocrInputRef.value?.click();
}

async function handlePasteRowOcrImage(row: UnboxRow) {
  if (!isRowEditable(row)) return;
  const state = getRowOcrState(row.id);
  if (state.status === "uploading") return;
  if (!navigator.clipboard?.read) {
    handleOcrValidationFailure(row.id, "当前环境不支持读取剪贴板图片");
    return;
  }

  try {
    const clipboardItems = await navigator.clipboard.read();
    for (const clipboardItem of clipboardItems) {
      const imageType = clipboardItem.types.find((type) => type.startsWith("image/"));
      if (!imageType) continue;
      const blob = await clipboardItem.getType(imageType);
      const extension = imageType.split("/")[1] || "png";
      const file = new File([blob], `ocr-paste-${row.id}.${extension}`, { type: imageType });
      activeOcrPopupRowId.value = null;
      await uploadRowOcrFile(row.id, file);
      return;
    }
    handleOcrValidationFailure(row.id, "剪贴板中没有图片，请先复制图片再粘贴");
  } catch (error) {
    handleOcrValidationFailure(row.id, getOcrErrorMessage(error, "读取剪贴板图片失败"));
  }
}

function normalizeOcrResult(result: UnboxRecordOcrResult) {
  const normalizedPrice = normalizeOcrResultPrice(result);
  const normalizedWear = normalizeOcrResultWear(result);
  const normalizedExterior = normalizeOcrResultExterior(result);
  const normalizedName = result.name?.trim() || "";
  const hasPrice = result.price !== null && result.price !== undefined && result.price !== "";
  return {
    name: normalizedName,
    price: normalizedPrice,
    wear: normalizedWear,
    exterior: normalizedExterior,
    valid: hasPrice && normalizedPrice !== null,
  };
}

async function uploadRowOcrFile(rowId: string, file: File) {
  if (!validateOcrImage(file, rowId)) {
    return;
  }

  const state = getRowOcrState(rowId);
  state.status = "uploading";
  state.errorMessage = "";

  try {
    const result = await unboxApi.ocrImage(file);
    const targetRow = draftBatch.value.rows.find((item) => item.id === rowId);
    const latestState = rowOcrStateMap.value[rowId];
    if (!targetRow || !latestState) return;
    const normalized = normalizeOcrResult(result);
    if (!normalized.valid || normalized.price === null) {
      latestState.status = "error";
      latestState.errorMessage = OCR_FIELD_MISSING_MESSAGE;
      return;
    }
    if (normalized.name) {
      targetRow.weaponName = normalized.name;
    }
    targetRow.inGamePrice = normalized.price;
    if (normalized.wear !== null) {
      targetRow.wear = normalized.wear;
    }
    if (normalized.exterior !== null) {
      targetRow.exterior = normalized.exterior;
    }
    latestState.status = "success";
    latestState.errorMessage = "";
  } catch (error) {
    const latestState = rowOcrStateMap.value[rowId];
    if (!latestState) return;
    latestState.status = "error";
    latestState.errorMessage = getOcrErrorMessage(error);
  } finally {
    activeOcrRowId.value = null;
  }
}

async function handleRowOcrFileChange(event: Event) {
  const input = event.target as HTMLInputElement | null;
  const rowId = activeOcrRowId.value;
  const file = input?.files?.[0];
  if (!input) return;
  input.value = "";
  if (!rowId) return;
  if (!file) {
    activeOcrRowId.value = null;
    return;
  }
  activeOcrPopupRowId.value = null;
  await uploadRowOcrFile(rowId, file);
}

function getRowOcrIcon(rowId: string) {
  const state = getRowOcrState(rowId);
  if (state.status === "uploading") return LoadingIcon;
  if (state.status === "success") return CheckCircleIcon;
  if (state.status === "error") return CloseCircleIcon;
  return ImageIcon;
}

function getRowOcrIconClass(rowId: string) {
  const state = getRowOcrState(rowId);
  if (state.status === "uploading") return "text-sky-500 animate-spin";
  if (state.status === "success") return "text-emerald-500";
  if (state.status === "error") return "text-rose-500";
  return "text-slate-400 group-hover:text-slate-500";
}

function mapRecordItemToRow(
  item: UnboxRecordDTO["items"][number],
  defaultDiscount: number
): UnboxRow {
  const wearValue = Number(item.wear ?? 0);
  return {
    id: String(item.id ?? createId()),
    handlingStatus: item.handlingStatus,
    boxPurchasePrice: Number(item.boxPurchasePrice ?? 0),
    weaponName: item.weaponName ?? "",
    inGamePrice: Number(item.inGamePrice ?? 0),
    discount: clampDiscount(item.discount ?? defaultDiscount),
    actualSellPrice: Number(item.actualSellPrice ?? 0),
    wear: wearValue > 0 ? wearValue : "",
    exterior: Number(item.exterior ?? 0),
    note: item.note ?? "",
  };
}

function mapRecordToBatch(record: UnboxRecordDTO): UnboxBatch {
  const defaultDiscount = clampDiscount(Number(record.defaultDiscount ?? 0));
  return {
    id: record.id,
    goodsId: record.goodsId,
    boxName: record.boxName ?? "",
    date: record.unboxDate ?? "",
    defaultDiscount,
    note: record.note ?? "",
    rows: record.items.map((item) => mapRecordItemToRow(item, defaultDiscount)),
  };
}

function normalizeBatchStatus(status?: string): BatchStatus {
  if (status === "已结算" || status === "部分结算") {
    return status;
  }
  return "未结算";
}

function mapPageRecordToSummaryRow(record: UnboxRecordPageDTO): BatchSummaryRow {
  const defaultDiscount = clampDiscount(Number(record.defaultDiscount ?? 0));
  return {
    key: record.id,
    batch: {
      id: record.id,
      goodsId: record.goodsId,
      boxName: record.boxName ?? "",
      date: record.unboxDate ?? "",
      defaultDiscount,
      note: record.note ?? "",
    },
    summary: {
      totalCount: Number(record.totalCount ?? 0),
      totalPurchaseCost: Number(record.totalPurchaseCost ?? 0),
      totalActualFee: Number(record.totalActualFee ?? 0),
      totalActualNetProfit: Number(record.totalActualNetProfit ?? 0),
      totalActualProfitRate:
        record.totalActualProfitRate === null || record.totalActualProfitRate === undefined
          ? null
          : Number(record.totalActualProfitRate),
    },
    status: normalizeBatchStatus(record.status),
  };
}

function buildSaveParam(batch: UnboxBatch): UnboxRecordSaveParam {
  return {
    goodsId: batch.goodsId!,
    unboxDate: batch.date,
    defaultDiscount: clampDiscount(batch.defaultDiscount),
    note: batch.note.trim(),
    items: batch.rows.map((row) => ({
      handlingStatus: row.handlingStatus,
      boxPurchasePrice: round(row.boxPurchasePrice),
      weaponName: row.weaponName.trim(),
      inGamePrice: round(row.inGamePrice),
      discount: hasDiscountValue(row.discount) ? clampDiscount(row.discount) : null,
      actualSellPrice: round(row.actualSellPrice),
      wear: row.wear === "" ? null : Number(row.wear),
      exterior: row.exterior,
      note: row.note.trim(),
    })),
  };
}

async function fetchGoodsOptions(keyword = "") {
  goodsLoading.value = true;
  try {
    const items = await goodsApi.getSimpleList(normalizeGoodsKeyword(keyword));
    goodsCatalog.value = items;
    ensureGoodsInCatalog(draftBatch.value.goodsId, draftBatch.value.boxName);
  } catch (error) {
    console.error(error);
    MessagePlugin.error("获取箱子商品失败");
  } finally {
    goodsLoading.value = false;
  }
}

function getAppliedRequestRange(): [string, string] {
  return appliedDateRange.value.length === 2
    ? (normalizeDateRange(appliedDateRange.value) as [string, string])
    : currentPeriodRange.value;
}

async function loadBatchPage(targetPage = batchPagination.value.current) {
  listLoading.value = true;
  try {
    const [startDate, endDate] = getAppliedRequestRange();
    const result = await unboxApi.page({
      page: targetPage,
      pageSize: batchPagination.value.pageSize,
      startDate,
      endDate,
    });
    currentPageBatchSummaryRows.value = result.records.map(mapPageRecordToSummaryRow);
    batchPagination.value.current = result.total > 0 ? result.current : 1;
    batchPagination.value.pageSize = result.size;
    batchPagination.value.total = result.total;
  } catch (error) {
    console.error(error);
    currentPageBatchSummaryRows.value = [];
    batchPagination.value.total = 0;
    MessagePlugin.error("获取开箱记录失败");
  } finally {
    listLoading.value = false;
  }
}

async function loadBatchSummary() {
  summaryLoading.value = true;
  try {
    const [startDate, endDate] = getAppliedRequestRange();
    summaryState.value = await unboxApi.summary({ startDate, endDate });
  } catch (error) {
    console.error(error);
    summaryState.value = { ...EMPTY_SUMMARY };
    MessagePlugin.error("获取开箱记录汇总失败");
  } finally {
    summaryLoading.value = false;
  }
}

async function loadBatchPageAndSummary(targetPage = batchPagination.value.current) {
  await Promise.all([loadBatchPage(targetPage), loadBatchSummary()]);
}

async function refreshCurrentBatchPageAndSummary() {
  const currentPage = batchPagination.value.current;
  await Promise.all([loadBatchPage(currentPage), loadBatchSummary()]);
  if (
    currentPage > 1 &&
    batchPagination.value.total > 0 &&
    currentPageBatchSummaryRows.value.length === 0
  ) {
    await loadBatchPage(currentPage - 1);
  }
}

function handleGoodsPopupVisibleChange(visible: boolean) {
  if (!visible || goodsCatalog.value.length > 0 || goodsLoading.value) return;
  void fetchGoodsOptions();
}

function handleGoodsSearch(keyword: string) {
  void fetchGoodsOptions(keyword);
}

onMounted(() => {
  void loadBatchPageAndSummary();
});

function isRowEditable(row: UnboxRow) {
  return row.handlingStatus !== "discarded";
}

function shouldIncludeRowInSummary(row: UnboxRow) {
  return row.handlingStatus !== "stored";
}

const getRowMetrics = (row: UnboxRow): RowMetrics => {
  const purchaseCost = round(row.inGamePrice * clampDiscount(row.discount));

  const shouldCalculateActualMetrics =
    row.actualSellPrice > 0 || row.boxPurchasePrice > 0 || purchaseCost > 0;
  const actualFee = shouldCalculateActualMetrics ? getFee(row.actualSellPrice) : null;
  const actualNetIncome =
    shouldCalculateActualMetrics && actualFee !== null
      ? round(row.actualSellPrice - actualFee)
      : null;
  const actualNetProfit =
    shouldCalculateActualMetrics && actualFee !== null
      ? round(row.actualSellPrice - row.boxPurchasePrice - purchaseCost - actualFee)
      : null;
  const actualProfitRate =
    actualNetProfit !== null && purchaseCost > 0
      ? round((actualNetProfit / purchaseCost) * 100)
      : null;

  return {
    purchaseCost,
    actualFee,
    actualNetIncome,
    actualNetProfit,
    actualProfitRate,
  };
};

function buildBatchSummary(
  totalCount: number,
  entries: Array<Pick<DraftRowEntry, "row" | "metrics">>
): BatchSummary {
  let countedRowCount = 0;
  let boughtCount = 0;
  let totalInGamePrice = 0;
  let totalPurchaseCost = 0;
  let soldCount = 0;
  let totalActualNetIncome = 0;
  let totalActualFee = 0;
  let totalActualNetProfit = 0;

  entries.forEach(({ row, metrics }) => {
    if (shouldIncludeRowInSummary(row)) {
      countedRowCount += 1;
      if (row.handlingStatus === "purchased") {
        boughtCount += 1;
      }
      totalInGamePrice += row.inGamePrice;
      totalPurchaseCost += metrics.purchaseCost ?? 0;

      if (row.actualSellPrice > 0) {
        soldCount += 1;
      }
    }

    if (shouldIncludeRowInSummary(row) && metrics.actualNetProfit !== null) {
      totalActualNetIncome += metrics.actualNetIncome ?? 0;
      totalActualFee += metrics.actualFee ?? 0;
      totalActualNetProfit += metrics.actualNetProfit ?? 0;
    }
  });

  const totalActualProfitRate =
    totalPurchaseCost > 0 ? round((totalActualNetProfit / totalPurchaseCost) * 100) : null;

  return {
    totalCount,
    countedRowCount,
    boughtCount,
    totalInGamePrice: round(totalInGamePrice),
    totalPurchaseCost: round(totalPurchaseCost),
    soldCount,
    unsoldCount: countedRowCount - soldCount,
    totalActualNetIncome: round(totalActualNetIncome),
    totalActualFee: round(totalActualFee),
    totalActualNetProfit: round(totalActualNetProfit),
    totalActualProfitRate,
  };
}

const filteredBatchSummaryRows = computed<BatchSummaryRow[]>(() => currentPageBatchSummaryRows.value);

function sortableNumber(value: number | null | undefined) {
  return Number.isFinite(value) ? Number(value) : 0;
}

const summaryTableHeaderClass =
  "!bg-slate-50 !text-slate-500 !text-sm !font-semibold !tracking-[0.06em] uppercase whitespace-nowrap";
const summaryToolbarFieldClass =
  "[&_.t-input__wrap]:min-h-10 [&_.t-input__wrap]:rounded-md [&_.t-input__wrap]:border-slate-200 [&_.t-input__wrap]:bg-white [&_.t-input__wrap]:shadow-none [&_.t-input__wrap:hover]:border-slate-300 [&_.t-is-focused]:border-sky-500 [&_.t-is-focused]:shadow-[0_0_0_3px_rgb(14_165_233_/_0.12)]";
const summaryTableBodyClass = "!py-2 text-sm text-slate-700 align-middle";
const fieldBaseClass =
  "w-full [&_.t-input__wrap]:min-h-10 [&_.t-input__wrap]:rounded-[0.9rem] [&_.t-input__wrap]:border-slate-200 [&_.t-input__wrap]:shadow-none [&_.t-input__wrap:hover]:border-slate-400 [&_.t-is-focused]:border-sky-500 [&_.t-is-focused]:shadow-[0_0_0_3px_rgb(14_165_233_/_0.12)]";
const numberFieldBaseClass =
  "w-full [&_.t-input-number]:w-full [&_.t-input-number]:min-w-0 [&_.t-input__wrap]:min-h-10 [&_.t-input__wrap]:w-full [&_.t-input__wrap]:rounded-[0.9rem] [&_.t-input__wrap]:border-slate-200 [&_.t-input__wrap]:shadow-none [&_.t-input__wrap:hover]:border-slate-400 [&_.t-is-focused]:border-sky-500 [&_.t-is-focused]:shadow-[0_0_0_3px_rgb(14_165_233_/_0.12)]";
const batchDiscountFieldClass = `!w-full min-w-0 ${numberFieldBaseClass} [&_.t-input__wrap]:px-0 [&_.t-input]:w-full [&_.t-input]:px-3 [&_.t-input__inner]:w-full [&_.t-input__inner]:text-left`;
const numberFieldPrimaryClass = `${numberFieldBaseClass} [&_.t-input__wrap]:border-sky-300 [&_.t-input__wrap]:bg-sky-50 [&_.t-input__wrap:hover]:border-sky-400`;
const draftSelectFieldClass = `min-w-0 max-w-full ${fieldBaseClass}`;
const draftNumberFieldClass = `min-w-0 max-w-full ${numberFieldBaseClass}`;
const draftNumberFieldPrimaryClass = `min-w-0 max-w-full ${numberFieldPrimaryClass}`;
const draftStatusButtonBaseClass =
  "inline-flex min-w-0 shrink-0 items-center justify-center rounded-md border border-slate-200 bg-white px-2 text-sm font-medium whitespace-nowrap text-slate-500 transition-colors hover:bg-slate-50 focus-visible:ring-2 focus-visible:ring-sky-500/60 focus-visible:ring-offset-1 focus-visible:outline-none";
const draftCellControlClass = "min-w-0 max-w-full";

const batchColumns = computed<PrimaryTableCol[]>(() => [
  {
    colKey: "date",
    title: createTooltipTitle(
      "日期",
      "使用批次日期作为该次开箱记录的唯一标识，不再额外展示批次列。",
      "日期列说明"
    ),
    width: 120,
    cell: "date",
    align: "left",
    className: summaryTableBodyClass,
    thClassName: summaryTableHeaderClass,
  },
  {
    colKey: "totalCount",
    title: createTooltipTitle("开箱数量", "对应该单次记录开箱的数量汇总。", "开箱数量说明"),
    width: 108,
    cell: "totalCount",
    align: "left",
    className: summaryTableBodyClass,
    thClassName: summaryTableHeaderClass,
  },
  {
    colKey: "purchaseCost",
    title: createTooltipTitle("购买总花费", "单次开箱实际花费的总金额", "购买总花费说明"),
    width: 148,
    cell: "purchaseCost",
    align: "left",
    className: summaryTableBodyClass,
    thClassName: summaryTableHeaderClass,
  },
  {
    colKey: "totalFee",
    title: createTooltipTitle("总手续费", "饰品上架的总手续费", "总手续费说明"),
    width: 120,
    cell: "totalFee",
    align: "left",
    className: summaryTableBodyClass,
    thClassName: summaryTableHeaderClass,
  },
  {
    colKey: "actualNetProfit",
    title: createTooltipTitle("总利润", "单次开箱扣除手续费后的总利润", "总利润说明"),
    width: 130,
    cell: "actualNetProfit",
    align: "left",
    sorter: (a, b) =>
      sortableNumber(a.summary.totalActualNetProfit) -
      sortableNumber(b.summary.totalActualNetProfit),
    sortType: "all",
    className: summaryTableBodyClass,
    thClassName: summaryTableHeaderClass,
  },
  {
    colKey: "actualProfitRate",
    title: createTooltipTitle("总利润率", "单次开箱的总利润率", "总利润率说明"),
    width: 120,
    cell: "actualProfitRate",
    align: "left",
    sorter: (a, b) =>
      sortableNumber(a.summary.totalActualProfitRate) -
      sortableNumber(b.summary.totalActualProfitRate),
    sortType: "all",
    className: summaryTableBodyClass,
    thClassName: summaryTableHeaderClass,
  },
  {
    colKey: "status",
    title: createTooltipTitle(
      "状态",
      "根据该批次内明细处理情况自动汇总，用于快速判断当前批次是否仍有待处理项目。",
      "状态列说明"
    ),
    width: 120,
    cell: "status",
    align: "left",
    className: summaryTableBodyClass,
    thClassName: summaryTableHeaderClass,
  },
  {
    colKey: "operation",
    title: "操作",
    width: 138,
    cell: "operation",
    fixed: "right",
    align: "left",
    className: `${summaryTableBodyClass} !bg-white`,
    thClassName: summaryTableHeaderClass,
  },
]);

const pageSummary = computed(() => summaryState.value);

const pageSummaryCards = computed<SummaryCard[]>(() => [
  {
    label: "开箱数量",
    value: `${pageSummary.value.totalBatches}`,
    hint: `当前筛选结果共 ${pageSummary.value.totalBatches} 批`,
    valueClass: "text-[#303133]",
  },
  {
    label: "购买总花费",
    value: formatCurrency(pageSummary.value.totalPurchaseCost),
    hint: "按当前筛选结果中的实际购入价口径汇总",
    valueClass: "text-[#303133]",
  },
  {
    label: "总手续费",
    value: formatCurrency(pageSummary.value.totalFee),
    hint: "按平台卖出价 × 1% 计算",
    valueClass: "text-amber-600",
  },
  {
    label: "总利润",
    value: formatSignedCurrency(pageSummary.value.totalActualNetProfit),
    hint: "卖出价 - 箱子购入价 - 实际购入价 - 手续费",
    valueClass: profitClass(pageSummary.value.totalActualNetProfit),
  },
]);

const sortedBatchSummaryRows = computed(() => {
  const currentSort = Array.isArray(batchSummarySort.value)
    ? batchSummarySort.value[0]
    : batchSummarySort.value;

  if (!currentSort?.sortBy) {
    return filteredBatchSummaryRows.value;
  }

  const direction = currentSort.descending ? -1 : 1;
  return [...filteredBatchSummaryRows.value].sort((left, right) => {
    if (currentSort.sortBy === "actualNetProfit") {
      return (
        (sortableNumber(left.summary.totalActualNetProfit) -
          sortableNumber(right.summary.totalActualNetProfit)) *
        direction
      );
    }

    if (currentSort.sortBy === "actualProfitRate") {
      return (
        (sortableNumber(left.summary.totalActualProfitRate) -
          sortableNumber(right.summary.totalActualProfitRate)) *
        direction
      );
    }

    return 0;
  });
});

const pagedBatchSummaryRows = computed(() => sortedBatchSummaryRows.value);

watch(
  () => draftBatch.value.goodsId,
  (goodsId) => {
    if (!goodsId) {
      draftBatch.value.boxName = "";
      return;
    }
    const selected = goodsCatalog.value.find((item) => getGoodsOptionValue(item) === goodsId);
    if (selected?.name) {
      draftBatch.value.boxName = selected.name;
    }
  }
);

watch(
  () => draftBatch.value.rows.map((row) => ({ id: row.id, invalidationKey: getRowC5InvalidationKey(row) })),
  (rows) => {
    const rowIds = rows.map((row) => row.id);
    const nextIds = new Set(rowIds);
    rowOcrStateMap.value = Object.fromEntries(
      rowIds.map((rowId) => [rowId, rowOcrStateMap.value[rowId] ?? createDefaultRowOcrState()])
    );
    rowC5StateMap.value = Object.fromEntries(
      rows.map(({ id, invalidationKey }) => {
        const prevState = rowC5StateMap.value[id] ?? createDefaultRowC5State();
        if (prevState.invalidationKey && prevState.invalidationKey !== invalidationKey) {
          return [id, createDefaultRowC5State()];
        }
        return [id, prevState];
      })
    );
    if (activeOcrRowId.value && !nextIds.has(activeOcrRowId.value)) {
      activeOcrRowId.value = null;
    }
    if (activeOcrPopupRowId.value && !nextIds.has(activeOcrPopupRowId.value)) {
      activeOcrPopupRowId.value = null;
    }
    if (activeC5PopupRowId.value && !nextIds.has(activeC5PopupRowId.value)) {
      activeC5PopupRowId.value = null;
    }
  },
  { immediate: true }
);

function handleBatchPageChange(pageInfo: PageInfo) {
  batchPagination.value.current = pageInfo.current;
  batchPagination.value.pageSize = pageInfo.pageSize;
  void loadBatchPage(pageInfo.current);
}

const selectableDraftHandlingStatusOptions: SelectableDraftHandlingStatusOption[] = [
  { value: "discarded", label: "丢弃", theme: "danger" },
  { value: "stored", label: "暂存", theme: "warning" },
  { value: "purchased", label: "已买", theme: "success" },
];

function getDraftStatusButtonActiveClass(status: SelectableDraftHandlingStatus) {
  if (status === "discarded") {
    return "!border-rose-200 !bg-rose-50 !text-rose-600";
  }
  if (status === "stored") {
    return "!border-amber-200 !bg-amber-50 !text-amber-600";
  }
  return "!border-emerald-200 !bg-emerald-50 !text-emerald-600";
}

function setHandlingStatus(row: UnboxRow, handlingStatus: SelectableDraftHandlingStatus) {
  const nextHandlingStatus = row.handlingStatus === handlingStatus ? "pending" : handlingStatus;
  row.handlingStatus = nextHandlingStatus;

  if (nextHandlingStatus === "discarded") {
    row.boxPurchasePrice = 0;
    row.inGamePrice = 0;
    row.actualSellPrice = 0;
  }
}

function getDraftRowStage(row: UnboxRow): DraftRowStage {
  if (row.handlingStatus === "discarded") return "丢弃";
  if (row.handlingStatus === "stored") return "暂存";
  if (row.handlingStatus === "purchased") return "已买";
  return "待处理";
}

function getDraftRowStageTheme(stage: DraftRowStage): DraftRowStageTheme {
  if (stage === "丢弃") return "danger";
  if (stage === "暂存") return "warning";
  if (stage === "已买") return "success";
  return "default";
}

function getDraftRowStageDescription(stage: DraftRowStage) {
  if (stage === "丢弃") return "确认放弃该饰品，并清空价格输入后不再允许编辑";
  if (stage === "暂存") return "继续计算单条净利润和利润率，但不计入汇总利润率";
  if (stage === "已买") return "确认接手，参与成本、卖价和净利润统计";
  return "未选中任何状态时，按未处理参与成本、卖价和净利润统计";
}

const draftRowEntries = computed<DraftRowEntry[]>(() =>
  draftBatch.value.rows.map((row) => {
    const stage = getDraftRowStage(row);
    return {
      row,
      metrics: getRowMetrics(row),
      stage,
      stageTheme: getDraftRowStageTheme(stage),
      stageDescription: getDraftRowStageDescription(stage),
    };
  })
);

const draftTableHeaderClass =
  "!bg-slate-100 !text-slate-600 !text-sm !font-semibold !tracking-[0.08em] whitespace-nowrap";
const draftTableBodyClass = "!py-2 px-3 text-slate-700 align-middle";
const draftTableFixedBodyClass = `${draftTableBodyClass} !bg-white`;
const draftTableFooterClass =
  "flex min-h-[56px] flex-col justify-center gap-1 whitespace-nowrap px-2 py-2 text-sm leading-4 font-semibold text-slate-500";
const draftTableFooterFixedClass = draftTableFooterClass;
const Tooltip = resolveComponent("t-tooltip");
const tooltipTriggerClass =
  "inline-flex items-center rounded-sm text-slate-400 transition-colors hover:text-slate-600 focus-visible:ring-2 focus-visible:ring-sky-500/60 focus-visible:ring-offset-1 focus-visible:outline-none";

function createTooltipTitle(label: string, content: string, ariaLabel: string) {
  return () =>
    h("div", { class: "inline-flex items-center gap-1" }, [
      h("span", label),
      h(
        Tooltip,
        { content, placement: "top", theme: "light" },
        {
          default: () =>
            h(
              "button",
              {
                type: "button",
                class: tooltipTriggerClass,
                "aria-label": ariaLabel,
              },
              [h(HelpCircleIcon, { class: "h-3.5 w-3.5" })]
            ),
        }
      ),
    ]);
}

const purchaseCostTitle = createTooltipTitle("实际购入价", "游戏买入价 × 折扣", "实际购入价说明");
const actualNetProfitTitle = createTooltipTitle(
  "净利润",
  "平台卖出价 - 箱子购入价 - 实际购入价 - 手续费",
  "净利润说明"
);
const actualProfitRateTitle = createTooltipTitle("利润率", "利润 / 实际买入价", "利润率说明");
const actualFeeTitle = createTooltipTitle("手续费", "平台卖出价 × 1%", "手续费说明");

const purchaseStateTitle = () =>
  h("div", { class: "inline-flex items-center gap-1" }, [
    h("span", "处理状态"),
    h(
      Tooltip,
      {
        placement: "top",
        theme: "light",
        content: () =>
          h("div", { class: "space-y-1 text-slate-600" }, [
            h("div", [
              h("span", { class: "font-semibold text-slate-700" }, "待处理："),
              "未选中任何状态时，按未处理参与成本、卖价和净利润统计",
            ]),
            h("div", [
              h("span", { class: "font-semibold text-slate-700" }, "丢弃："),
              "确认放弃该饰品，并清空价格输入后不再允许编辑",
            ]),
            h("div", [
              h("span", { class: "font-semibold text-slate-700" }, "暂存："),
              "继续计算单条净利润和利润率，但不计入汇总利润率",
            ]),
            h("div", [
              h("span", { class: "font-semibold text-slate-700" }, "已买："),
              "确认接手，参与成本、卖价和净利润统计",
            ]),
          ]),
      },
      {
        default: () =>
          h(
            "button",
            {
              type: "button",
              class: tooltipTriggerClass,
              "aria-label": "处理状态说明",
            },
            [h(HelpCircleIcon, { class: "h-3.5 w-3.5" })]
          ),
      }
    ),
  ]);

const draftTableColumns = computed<PrimaryTableCol[]>(() => [
  {
    colKey: "index",
    title: "#",
    width: 54,
    minWidth: 54,
    cell: "index",
    foot: "footerIndex",
    align: "left",
    className: `${draftTableBodyClass} whitespace-nowrap`,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "boxPurchasePrice",
    title: "箱子购入价",
    width: 132,
    minWidth: 132,
    cell: "boxPurchasePrice",
    foot: "footerBoxPurchasePrice",
    align: "left",
    className: draftTableBodyClass,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "purchaseState",
    title: purchaseStateTitle,
    width: 168,
    minWidth: 148,
    cell: "purchaseState",
    foot: "footerPurchaseState",
    align: "left",
    className: draftTableBodyClass,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "weaponName",
    title: "饰品名称",
    width: 192,
    minWidth: 192,
    cell: "weaponName",
    foot: "footerWeaponName",
    align: "left",
    className: draftTableBodyClass,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "inGamePrice",
    title: "游戏买入价",
    width: 126,
    minWidth: 126,
    cell: "inGamePrice",
    foot: "footerInGamePrice",
    align: "left",
    className: draftTableBodyClass,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "discount",
    title: "折扣",
    width: 102,
    minWidth: 102,
    cell: "discount",
    foot: "footerDiscount",
    align: "left",
    className: draftTableBodyClass,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "wear",
    title: "磨损",
    width: 132,
    minWidth: 132,
    cell: "wear",
    align: "left",
    className: draftTableBodyClass,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "exterior",
    title: "外观",
    width: 124,
    minWidth: 124,
    cell: "exterior",
    align: "left",
    className: draftTableBodyClass,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "purchaseCost",
    title: purchaseCostTitle,
    width: 118,
    minWidth: 118,
    cell: "purchaseCost",
    foot: "footerPurchaseCost",
    align: "left",
    className: draftTableBodyClass,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "actualSellPrice",
    title: "平台卖出价",
    width: 130,
    minWidth: 130,
    cell: "actualSellPrice",
    foot: "footerActualSellPrice",
    align: "left",
    className: draftTableBodyClass,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "actualFee",
    title: actualFeeTitle,
    width: 132,
    minWidth: 132,
    cell: "actualFee",
    foot: "footerActualFee",
    align: "left",
    className: draftTableBodyClass,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "actualNetProfit",
    title: actualNetProfitTitle,
    width: 116,
    minWidth: 116,
    cell: "actualNetProfit",
    foot: "footerActualNetProfit",
    align: "left",
    sorter: (a, b) =>
      sortableNumber(a.metrics.actualNetProfit) - sortableNumber(b.metrics.actualNetProfit),
    sortType: "all",
    fixed: "right",
    className: draftTableFixedBodyClass,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "actualProfitRate",
    title: actualProfitRateTitle,
    width: 108,
    minWidth: 108,
    cell: "actualProfitRate",
    foot: "footerActualProfitRate",
    align: "left",
    sorter: (a, b) =>
      sortableNumber(a.metrics.actualProfitRate) - sortableNumber(b.metrics.actualProfitRate),
    sortType: "all",
    fixed: "right",
    className: draftTableFixedBodyClass,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "operation",
    title: "操作",
    width: 72,
    minWidth: 72,
    cell: "operation",
    foot: "footerOperation",
    align: "left",
    fixed: "right",
    className: draftTableFixedBodyClass,
    thClassName: draftTableHeaderClass,
  },
]);

const DRAFT_TABLE_MIN_HEIGHT = 320;
const DRAFT_TABLE_HEADER_TOOLBAR_HEIGHT = 68;
const DRAFT_TABLE_SECTION_PADDING = 24;

const draftTableMaxHeight = computed(() => {
  const editorContentAvailableHeight =
    editorContentHeight.value - batchInfoSectionHeight.value - DRAFT_TABLE_SECTION_PADDING;
  const sectionAvailableHeight = editorContentAvailableHeight - DRAFT_TABLE_HEADER_TOOLBAR_HEIGHT;
  return Math.max(Math.floor(sectionAvailableHeight), DRAFT_TABLE_MIN_HEIGHT);
});

const draftTableViewportStyle = computed(() => {
  if (draftRowEntries.value.length === 0) {
    return {
      minHeight: `${DRAFT_TABLE_MIN_HEIGHT}px`,
      maxHeight: `${draftTableMaxHeight.value}px`,
    };
  }

  return {
    maxHeight: `${draftTableMaxHeight.value}px`,
  };
});

const draftSummary = computed(() =>
  buildBatchSummary(draftBatch.value.rows.length, draftRowEntries.value)
);
const draftFooterRows = computed<DraftFooterRow[]>(() => [{ type: "summary" }]);

function formatCurrency(value: number) {
  return currencyFormatter.format(round(value));
}

function formatSignedCurrency(value: number) {
  const normalized = round(value);
  const prefix = normalized > 0 ? "+" : "";
  return `${prefix}${currencyFormatter.format(normalized)}`;
}

function formatPendingCurrency(value: number | null) {
  if (value === null) return "—";
  return formatCurrency(value);
}

function formatActualProfit(value: number | null) {
  if (value === null) return "—";
  return formatSignedCurrency(value);
}

function formatPercent(value: number | null) {
  if (value === null) return "—";
  const normalized = round(value);
  const prefix = normalized > 0 ? "+" : "";
  return `${prefix}${normalized.toFixed(2)}%`;
}

function formatDateText(value: string) {
  if (!value) return "-";
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return dateFormatter.format(date);
}

function profitClass(value: number) {
  if (value > 0) return "text-emerald-600";
  if (value < 0) return "text-rose-600";
  return "text-[#303133]";
}

function historyStatusTheme(status: BatchStatus) {
  if (status === "已结算") return "success";
  if (status === "部分结算") return "warning";
  return "default";
}

function toggleEditorFullscreen() {
  if (isMobile.value) return;
  isEditorFullscreen.value = !isEditorFullscreen.value;
}

function toggleBatchInfoCollapsed() {
  isBatchInfoCollapsed.value = !isBatchInfoCollapsed.value;
}

function openCreateEditor() {
  editingBatchId.value = null;
  loadingDetailBatchId.value = null;
  draftBatch.value = createBlankBatch();
  resetRowStates(draftBatch.value.rows);
  ensureGoodsInCatalog(draftBatch.value.goodsId, draftBatch.value.boxName);
  isEditorFullscreen.value = false;
  isBatchInfoCollapsed.value = false;
  editorVisible.value = true;
}

async function openEditEditor(batchId: number) {
  if (!batchId || detailLoading.value) {
    return;
  }
  detailLoading.value = true;
  loadingDetailBatchId.value = batchId;
  try {
    const detail = await unboxApi.getDetail(batchId);
    const nextBatch = mapRecordToBatch(detail);
    editingBatchId.value = batchId;
    draftBatch.value = nextBatch;
    resetRowStates(draftBatch.value.rows);
    ensureGoodsInCatalog(nextBatch.goodsId, nextBatch.boxName);
    isEditorFullscreen.value = false;
    isBatchInfoCollapsed.value = true;
    editorVisible.value = true;
  } catch (error) {
    console.error(error);
    MessagePlugin.error("获取批次详情失败");
  } finally {
    detailLoading.value = false;
    loadingDetailBatchId.value = null;
  }
}

async function saveDraftBatch() {
  if (savingBatch.value) return;
  if (!draftBatch.value.goodsId) {
    MessagePlugin.warning("请选择箱子商品");
    return;
  }
  const selected = goodsCatalog.value.find(
    (item) => getGoodsOptionValue(item) === draftBatch.value.goodsId
  );
  draftBatch.value.boxName = selected?.name ?? draftBatch.value.boxName;
  if (!draftBatch.value.date) {
    MessagePlugin.warning("请选择开箱日期");
    return;
  }
  if (!hasDiscountValue(draftBatch.value.defaultDiscount)) {
    MessagePlugin.warning("请填写默认折扣");
    return;
  }
  savingBatch.value = true;
  try {
    const payload = buildSaveParam(draftBatch.value);
    if (editingBatchId.value) {
      await unboxApi.update(editingBatchId.value, payload);
      MessagePlugin.success("批次已更新");
    } else {
      await unboxApi.create(payload);
      MessagePlugin.success("批次已创建");
    }
    editorVisible.value = false;
    await refreshCurrentBatchPageAndSummary();
  } catch (error) {
    console.error(error);
    MessagePlugin.error(editingBatchId.value ? "更新批次失败" : "创建批次失败");
  } finally {
    savingBatch.value = false;
  }
}

async function removeBatch(batchId: number) {
  try {
    await unboxApi.delete(batchId);
    await refreshCurrentBatchPageAndSummary();
    MessagePlugin.success("批次已删除");
  } catch (error) {
    console.error(error);
    MessagePlugin.error("删除批次失败");
  }
}

function createToolbarDefaultRow(boxPurchasePrice = round(toolbarBoxPurchasePrice.value)) {
  return createRow({ boxPurchasePrice }, draftBatch.value);
}

function handleAddRow(index?: number) {
  const nextRow = createToolbarDefaultRow();
  if (typeof index === "number") {
    draftBatch.value.rows.splice(index, 0, nextRow);
    return;
  }
  draftBatch.value.rows.push(nextRow);
}

function handleBulkAdd(count: number) {
  const normalizedCount = Math.max(1, Math.min(200, Math.floor(Number(count) || 0)));
  const nextBoxPurchasePrice = round(toolbarBoxPurchasePrice.value);
  const newRows = Array.from({ length: normalizedCount }, () =>
    createToolbarDefaultRow(nextBoxPurchasePrice)
  );
  draftBatch.value.rows.push(...newRows);
  MessagePlugin.success(`已新增 ${normalizedCount} 条明细`);
}

function applyToolbarBoxPurchasePriceToAllRows() {
  if (!draftBatch.value.rows.length) {
    MessagePlugin.info("当前没有可应用的明细");
    return;
  }
  const nextBoxPurchasePrice = round(toolbarBoxPurchasePrice.value);
  let updatedCount = 0;
  draftBatch.value.rows.forEach((row) => {
    if (row.boxPurchasePrice === nextBoxPurchasePrice) return;
    row.boxPurchasePrice = nextBoxPurchasePrice;
    updatedCount += 1;
  });
  if (!updatedCount) {
    MessagePlugin.info("当前明细的箱子购入价已一致");
    return;
  }
  MessagePlugin.success(`已将箱子购入价应用到 ${updatedCount} 条明细`);
}

function applyDefaultsToEmptyRows() {
  if (!hasDiscountValue(draftBatch.value.defaultDiscount)) {
    MessagePlugin.warning("请先填写默认折扣");
    return;
  }

  let updatedCount = 0;
  draftBatch.value.rows.forEach((row) => {
    let changed = false;
    if (!hasDiscountValue(row.discount) || row.discount <= 0) {
      row.discount = draftBatch.value.defaultDiscount;
      changed = true;
    }
    if (changed) updatedCount += 1;
  });

  if (!updatedCount) {
    MessagePlugin.info("没有可应用默认值的空白明细");
    return;
  }
  MessagePlugin.success(`已为 ${updatedCount} 条明细补齐默认值`);
}

function handleRemoveRow(id: string) {
  draftBatch.value.rows = draftBatch.value.rows.filter((item) => item.id !== id);
}
</script>

<style scoped>
:deep(.t-dialog) {
  overflow: hidden;
}

:deep(.t-dialog__body) {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: 0;
  overflow: hidden;
}

:deep(.draft-detail-table .t-table__content) {
  width: 100%;
  max-height: 100%;
  overflow: auto;
  scrollbar-gutter: stable both-edges;
}

:deep(.unbox-ocr-popup__inner) {
  padding: 8px;
}

:deep(.unbox-ocr-popup__inner .t-button) {
  justify-content: center;
}

:deep(.unbox-c5-popup__inner) {
  padding: 8px;
}

:deep(.draft-detail-table .t-table) {
  width: 100%;
  table-layout: fixed;
}

:deep(.draft-detail-table .t-table__header),
:deep(.draft-detail-table .t-table__body),
:deep(.draft-detail-table .t-table__footer) {
  table-layout: fixed;
}

:deep(.draft-detail-table .t-table__body td),
:deep(.draft-detail-table .t-table__header th),
:deep(.draft-detail-table .t-table__footer td) {
  overflow: visible;
}

:deep(.draft-detail-table .t-input-number),
:deep(.draft-detail-table .t-input),
:deep(.draft-detail-table .t-input-number__wrap),
:deep(.draft-detail-table .t-input__wrap) {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  box-sizing: border-box;
}

:deep(.draft-detail-table .t-table__content-inner) {
  min-width: 100%;
}

:deep(.draft-detail-table .t-table__table) {
  width: max-content;
  min-width: 100%;
}

:deep(.draft-detail-table .t-table__fixed-right-column),
:deep(.draft-detail-table .t-table__fixed-left-column) {
  box-sizing: border-box;
  background: rgb(255 255 255);
}

:deep(.draft-detail-table tfoot td) {
  background: rgb(241 245 249 / 0.7);
  border-top: none;
  vertical-align: top;
  white-space: nowrap;
  word-break: keep-all;
}

:deep(.draft-detail-table tfoot .t-table__fixed-right-column),
:deep(.draft-detail-table tfoot .t-table__fixed-left-column) {
  background: rgb(241 245 249 / 0.7);
  vertical-align: top;
}

.scrollbar-stable {
  scrollbar-gutter: stable both-edges;
}

.scrollbar-stable::-webkit-scrollbar {
  width: 10px;
  height: 10px;
}

.scrollbar-stable::-webkit-scrollbar-thumb {
  background: rgb(203 213 225);
  border: 2px solid transparent;
  border-radius: 9999px;
  background-clip: content-box;
}

.scrollbar-stable::-webkit-scrollbar-track {
  background: rgb(248 250 252);
}

:deep(.unbox-summary-table .t-table__header th) {
  padding-top: 10px;
  padding-bottom: 10px;
}

:deep(.unbox-summary-table .t-table__body td) {
  padding-top: 8px;
  padding-bottom: 8px;
}

:deep(.unbox-summary-table .t-table) {
  border: none;
  border-radius: 0;
  box-shadow: none;
}

:deep(.unbox-summary-table .t-table__content) {
  border: none;
  border-radius: 0;
}

:deep(.unbox-summary-table .t-table__header) {
  overflow: visible;
  border-top-left-radius: 0;
  border-top-right-radius: 0;
}
</style>
