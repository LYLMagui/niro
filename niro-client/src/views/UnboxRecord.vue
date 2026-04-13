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
      <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div class="min-w-0 space-y-3">
            <div class="flex flex-wrap items-center gap-2 text-[11px] text-slate-500">
              <span
                class="rounded-full border border-sky-200 bg-sky-50 px-3 py-1 font-medium text-sky-700"
              >
                开箱记录
              </span>
            </div>
            <div class="space-y-1">
              <h1 class="text-xl font-semibold tracking-tight text-[#303133]">开箱记录</h1>
            </div>
          </div>

          <div class="flex flex-wrap items-center gap-2">
            <t-button theme="primary" class="touch-manipulation" @click="openCreateEditor">
              新增批次
            </t-button>
          </div>
        </div>
      </section>

      <section class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        <article
          v-for="card in pageSummaryCards"
          :key="card.label"
          class="rounded-2xl border border-slate-200 bg-white p-3.5 shadow-sm sm:p-4"
        >
          <div class="text-sm font-medium text-slate-500">{{ card.label }}</div>
          <div class="font-numeric mt-3 text-2xl font-semibold" :class="card.valueClass">
            {{ card.value }}
          </div>
          <div class="mt-2 text-xs leading-5 text-slate-400">{{ card.hint }}</div>
        </article>
      </section>

      <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div class="flex items-center justify-between gap-3">
          <div>
            <h2 class="text-base font-semibold text-[#303133]">历史批次</h2>
          </div>
          <t-tag theme="primary" variant="light-outline">共 {{ batches.length }} 批</t-tag>
        </div>

        <div class="mt-4 overflow-x-auto">
          <t-table
            row-key="key"
            :data="batchSummaryRows"
            :columns="batchColumns"
            v-model:sort="batchSummarySort"
            size="small"
            table-layout="fixed"
            hover
            bordered
            class="w-full overflow-hidden rounded-2xl bg-white shadow-sm"
          >
            <template #empty>
              <t-empty description="暂无批次记录" />
            </template>

            <template #date="{ row }">
              <span class="text-slate-600">{{ formatDateText(row.batch.date) }}</span>
            </template>

            <template #batchName="{ row }">
              <div class="font-medium text-[#303133]">{{ row.batch.name }}</div>
              <div class="mt-1 text-xs text-slate-400">
                {{ row.batch.boxType || "未设置箱子类型" }}
              </div>
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
              <span class="font-numeric" :class="profitClass(row.summary.totalActualProfitRate ?? 0)">
                {{ formatPercent(row.summary.totalActualProfitRate) }}
              </span>
            </template>

            <template #status="{ row }">
              <t-tag :theme="historyStatusTheme(row.status)" variant="light-outline">
                {{ row.status }}
              </t-tag>
            </template>

            <template #operation="{ row }">
              <div class="flex flex-wrap gap-2">
                <t-button size="small" variant="outline" @click="openEditEditor(row.batch.id)">
                  编辑
                </t-button>
                <t-popconfirm
                  content="确认删除该批次吗？"
                  theme="danger"
                  :popup-props="{ attach: 'body' }"
                  @confirm="removeBatch(row.batch.id)"
                >
                  <t-button size="small" theme="danger" variant="outline">删除</t-button>
                </t-popconfirm>
              </div>
            </template>
          </t-table>
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
      <div class="flex min-h-0 flex-1 flex-col overflow-hidden bg-white" :class="editorBodyClass">
        <div class="border-b border-slate-200 bg-white px-2.5 py-3 sm:px-3 sm:py-3">
          <div class="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
            <div class="min-w-0 space-y-2">
              <div class="flex flex-wrap items-center gap-1.5 text-[11px] text-slate-500">
                <span class="rounded-full bg-slate-100 px-2.5 py-1 font-medium text-slate-600">
                  {{ editingBatchId ? "编辑批次" : "新增批次" }}
                </span>
                <span class="rounded-full bg-sky-50 px-2.5 py-1 text-sky-700">
                  当前 {{ draftSummary.totalCount }} 条明细
                </span>
              </div>
              <div class="space-y-1">
                <h2 class="truncate text-base font-semibold text-[#303133] sm:text-lg">
                  {{ editingBatchId ? draftBatch.name || "编辑开箱批次" : "新增开箱批次" }}
                </h2>
              </div>
            </div>

            <div class="flex shrink-0 flex-wrap items-center justify-end gap-1.5 sm:gap-2">
              <t-button size="small" variant="outline" @click="toggleEditorFullscreen">
                {{ isEditorFullscreen ? "缩小" : "全屏" }}
              </t-button>
              <t-button size="small" variant="outline" @click="editorVisible = false">
                取消
              </t-button>
              <t-button size="small" theme="primary" @click="saveDraftBatch">保存批次</t-button>
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

        <div class="min-h-0 flex-1 overflow-hidden px-2.5 py-2.5 sm:px-3 sm:py-3">
          <div class="flex h-full min-h-0 flex-col gap-3">
            <section
              class="shrink-0 overflow-hidden rounded-[10px] border border-slate-200/80 bg-white px-3 py-2.5 shadow-sm"
            >
              <div class="flex items-start justify-between gap-3">
                <div class="min-w-0 space-y-1">
                  <div class="text-[11px] font-medium tracking-[0.14em] text-slate-400 uppercase">
                    批次基础信息
                  </div>
                  <div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-600">
                    <span class="font-medium text-slate-800">
                      {{ draftBatch.name || "未命名批次" }}
                    </span>
                    <span>{{ draftBatch.boxType || "未填箱型" }}</span>
                    <span>{{ draftBatch.date || "未选日期" }}</span>
                    <span>默认折扣 {{ clampDiscount(draftBatch.defaultDiscount).toFixed(2) }}</span>
                  </div>
                </div>
                <button
                  type="button"
                  class="flex h-8 min-w-16 items-center justify-center rounded-lg border border-slate-200 px-2 text-xs font-medium text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-800 focus-visible:ring-2 focus-visible:ring-sky-500/60 focus-visible:ring-offset-1 focus-visible:outline-none"
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
                      <span class="text-[11px] font-medium text-slate-700 sm:text-xs">
                        开箱日期
                      </span>
                      <t-date-picker
                        v-model="draftBatch.date"
                        allow-input
                        clearable
                        :class="fieldBaseClass"
                        format="YYYY-MM-DD"
                        value-type="YYYY-MM-DD"
                        placeholder="选择日期"
                        size="small"
                      />
                    </label>

                    <label class="space-y-1.5 xl:col-span-3">
                      <span class="text-[11px] font-medium text-slate-700 sm:text-xs">
                        箱子类型
                      </span>
                      <t-input
                        v-model="draftBatch.boxType"
                        :class="fieldBaseClass"
                        clearable
                        maxlength="30"
                        placeholder="例如：创世终端机"
                        size="small"
                      />
                    </label>

                    <label class="flex flex-col gap-1.5 xl:col-span-3">
                      <span class="text-[11px] font-medium text-slate-700 sm:text-xs">
                        默认折扣
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
                        placeholder="0.72"
                        size="small"
                        theme="normal"
                      />
                    </label>

                    <label class="space-y-1.5 xl:col-span-3">
                      <span class="text-[11px] font-medium text-slate-700 sm:text-xs">备注</span>
                      <t-input
                        v-model="draftBatch.note"
                        :class="fieldBaseClass"
                        maxlength="120"
                        placeholder="记录这一批的来源、玩法、特别说明"
                        size="small"
                      />
                    </label>
                  </div>
                </div>
              </div>
            </section>

            <section
              class="flex h-full min-h-0 flex-1 flex-col overflow-hidden rounded-[10px] border border-slate-200/80 bg-white shadow-sm"
            >
              <div class="shrink-0 border-b border-slate-200/80 px-3 py-2">
                <div class="space-y-2">
                  <div class="flex flex-col gap-2 xl:flex-row xl:items-center xl:justify-between">
                    <div class="flex min-w-0 items-center gap-2">
                      <div
                        class="shrink-0 text-[11px] font-medium tracking-[0.14em] text-slate-400 uppercase"
                      >
                        批次明细
                      </div>
                    </div>

                    <div class="overflow-x-auto">
                      <div class="inline-flex min-w-max items-center gap-1.5 pb-0.5">
                        <t-button
                          size="small"
                          theme="primary"
                          variant="outline"
                          @click="handleAddRow()"
                        >
                          +1
                        </t-button>
                        <t-button size="small" variant="outline" @click="handleBulkAdd(10)">
                          +10
                        </t-button>
                        <t-button size="small" variant="outline" @click="handleBulkAdd(50)">
                          +50
                        </t-button>
                        <div
                          class="inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-slate-50/80 p-1"
                        >
                          <span class="px-1 text-[11px] text-slate-500">自定义</span>
                          <t-input-number
                            v-model="bulkAddCount"
                            :decimal-places="0"
                            :min="1"
                            :step="1"
                            align="left"
                            :class="`${numberFieldBaseClass} w-20`"
                            placeholder="20"
                            size="small"
                            theme="normal"
                          />
                          <t-button
                            size="small"
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
                          <span class="px-1 text-[11px] text-slate-500">箱子购入价</span>
                          <t-input-number
                            v-model="toolbarBoxPurchasePrice"
                            :decimal-places="2"
                            :min="0"
                            :step="0.01"
                            align="right"
                            :class="`${numberFieldBaseClass} w-24`"
                            placeholder="0.00"
                            size="small"
                            theme="normal"
                          />
                        </div>
                        <t-button
                          size="small"
                          variant="outline"
                          @click="applyToolbarBoxPurchasePriceToAllRows"
                        >
                          应用箱子购入价到全部
                        </t-button>
                        <t-button size="small" variant="outline" @click="applyDefaultsToEmptyRows">
                          应用到未填写行
                        </t-button>
                      </div>
                    </div>
                  </div>

                </div>
              </div>

              <div v-if="!isMobile" class="min-h-0 flex-1 overflow-hidden bg-white">
                <div ref="draftTableViewportRef" class="h-full min-h-0 overflow-hidden overscroll-contain">
                  <t-table
                    row-key="row.id"
                    :data="draftRowEntries"
                    :columns="draftTableColumns"
                    :foot-data="draftFooterRows"
                    :height="draftTableHeight"
                    :header-affixed-top="{ offsetTop: 0, container: draftTableScrollContainer }"
                    v-model:sort="draftTableSort"
                    size="small"
                    table-layout="auto"
                    vertical-align="middle"
                    hover
                    class="draft-detail-table h-full w-full [&_.t-table]:h-full [&_.t-table]:w-full [&_.t-table__content]:relative [&_.t-table__content]:h-full [&_.t-table__content]:w-full [&_.t-table__content-inner]:min-w-full [&_.t-table__header]:bg-white [&_.t-table__table]:min-w-full [&_table]:h-full [&_table]:w-full"
                  >
                    <template #index="{ rowIndex }">
                      <div class="font-numeric text-[11px] leading-4 font-semibold text-slate-600">
                        {{ rowIndex + 1 }}
                      </div>
                    </template>

                    <template #boxPurchasePrice="{ row: entry }">
                      <t-input-number
                        v-model="entry.row.boxPurchasePrice"
                        :decimal-places="2"
                        :disabled="!isRowEditable(entry.row)"
                        :min="0"
                        :step="0.1"
                        align="right"
                        :class="numberFieldBaseClass"
                        placeholder="0.00"
                        size="small"
                        theme="normal"
                      />
                    </template>

                    <template #purchaseState="{ row: entry }">
                      <div class="space-y-1.5">
                        <div class="flex">
                          <t-tag :theme="entry.stageTheme" size="small" variant="light-outline">
                            {{ entry.stage }}
                          </t-tag>
                        </div>
                        <div class="flex flex-wrap gap-1">
                          <button
                            v-for="option in draftHandlingStatusOptions"
                            :key="option.value"
                            type="button"
                            class="inline-flex h-6 items-center rounded-md border px-1.5 text-[10px] font-medium transition-colors focus-visible:ring-2 focus-visible:ring-sky-500/60 focus-visible:ring-offset-1 focus-visible:outline-none"
                            :class="
                              entry.row.handlingStatus === option.value
                                ? {
                                    'border-rose-200 bg-rose-50 text-rose-600': option.value === 'discarded',
                                    'border-amber-200 bg-amber-50 text-amber-600': option.value === 'stored',
                                    'border-emerald-200 bg-emerald-50 text-emerald-600': option.value === 'purchased',
                                  }
                                : 'border-slate-200 bg-white text-slate-500 hover:border-slate-300 hover:text-slate-700'
                            "
                            @click="setHandlingStatus(entry.row, option.value)"
                          >
                            {{ option.label }}
                          </button>
                        </div>
                      </div>
                    </template>

                    <template #weaponName="{ row: entry }">
                      <t-input
                        v-model="entry.row.weaponName"
                        :disabled="!isRowEditable(entry.row)"
                        :class="fieldBaseClass"
                        clearable
                        maxlength="40"
                        placeholder="例如：AK-47 | 血腥运动"
                        size="small"
                      />
                    </template>

                    <template #inGamePrice="{ row: entry }">
                      <t-input-number
                        v-model="entry.row.inGamePrice"
                        :decimal-places="2"
                        :disabled="!isRowEditable(entry.row)"
                        :min="0"
                        :step="0.1"
                        align="right"
                        :class="numberFieldBaseClass"
                        placeholder="0.00"
                        size="small"
                        theme="normal"
                      />
                    </template>

                    <template #discount="{ row: entry }">
                      <t-input-number
                        v-model="entry.row.discount"
                        :decimal-places="2"
                        :disabled="!isRowEditable(entry.row)"
                        :max="1"
                        :min="0"
                        :step="0.01"
                        align="right"
                        :class="numberFieldBaseClass"
                        placeholder="0.72"
                        size="small"
                        theme="normal"
                      />
                    </template>

                    <template #purchaseCost="{ row: entry }">
                      <div class="text-right leading-4">
                        <div class="font-numeric text-sm font-semibold text-[#303133]">
                          {{ formatPendingCurrency(entry.metrics.purchaseCost) }}
                        </div>
                      </div>
                    </template>

                    <template #actualSellPrice="{ row: entry }">
                      <div class="space-y-1">
                        <t-input-number
                          v-model="entry.row.actualSellPrice"
                          :decimal-places="2"
                          :disabled="!isRowEditable(entry.row)"
                          :min="0"
                          :step="0.1"
                          align="right"
                          :class="numberFieldPrimaryClass"
                          placeholder="优先录这里"
                          size="small"
                          theme="normal"
                        />
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
                          size="small"
                          variant="text"
                          class="!h-7 !px-1 text-slate-600"
                          @click="handleAddRow(rowIndex + 1)"
                        >
                          插入
                        </t-button>
                        <t-popconfirm
                          content="确认删除该条明细吗？"
                          theme="danger"
                          :popup-props="{ attach: 'body' }"
                          @confirm="handleRemoveRow(entry.row.id)"
                        >
                          <t-button
                            size="small"
                            theme="danger"
                            variant="text"
                            class="!h-7 !px-1"
                            :disabled="draftBatch.rows.length === 1"
                          >
                            删除
                          </t-button>
                        </t-popconfirm>
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
                        <div class="mt-1 font-numeric text-sm font-semibold text-slate-700">
                          {{ draftSummary.totalCount }} 条
                        </div>
                      </div>
                    </template>

                    <template #footerPurchaseState>
                      <div :class="draftTableFooterClass">
                        <div>已购买数量</div>
                        <div class="mt-1 font-numeric text-sm font-semibold text-emerald-600">
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
                        <div class="mt-1 font-numeric text-sm font-semibold text-slate-700">
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
                        <div class="mt-1 font-numeric text-sm font-semibold text-slate-700">
                          {{ formatCurrency(draftSummary.totalPurchaseCost) }}
                        </div>
                      </div>
                    </template>

                    <template #footerActualSellPrice>
                      <div :class="draftTableFooterClass">
                        <div>总手续费</div>
                        <div class="mt-1 font-numeric text-sm font-semibold text-amber-600">
                          {{ formatCurrency(draftSummary.totalActualFee) }}
                        </div>
                      </div>
                    </template>

                    <template #footerActualFee>
                      <div :class="draftTableFooterClass">
                        <div>到账汇总</div>
                        <div class="mt-1 font-numeric text-sm font-semibold text-slate-700">
                          {{ formatCurrency(draftSummary.totalActualNetIncome) }}
                        </div>
                      </div>
                    </template>

                    <template #footerActualNetProfit>
                      <div :class="draftTableFooterFixedClass">
                        <div>净利润</div>
                        <div
                          class="mt-1 font-numeric text-sm font-semibold"
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
                          class="mt-1 font-numeric text-sm font-semibold"
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
                      <div
                        class="text-[11px] font-medium tracking-[0.14em] text-slate-400 uppercase"
                      >
                        明细 {{ index + 1 }}
                      </div>
                      <div class="mt-1 text-sm font-semibold text-[#303133]">
                        {{ row.weaponName || "未填写饰品名称" }}
                      </div>
                      <div class="mt-1 text-[11px] text-slate-500">
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
                        <div class="space-y-1.5">
                          <span class="text-[11px] font-medium text-slate-600">处理状态</span>
                          <div class="flex flex-wrap gap-2">
                            <button
                              v-for="option in draftHandlingStatusOptions"
                              :key="option.value"
                              type="button"
                              class="inline-flex h-9 min-w-0 flex-1 touch-manipulation items-center justify-center rounded-lg border text-sm font-medium transition-colors focus-visible:ring-2 focus-visible:ring-sky-500/60 focus-visible:ring-offset-1 focus-visible:outline-none"
                              :class="
                                row.handlingStatus === option.value
                                  ? {
                                      'border-rose-200 bg-rose-50 text-rose-600': option.value === 'discarded',
                                      'border-amber-200 bg-amber-50 text-amber-600': option.value === 'stored',
                                      'border-emerald-200 bg-emerald-50 text-emerald-600': option.value === 'purchased',
                                    }
                                  : 'border-slate-200 bg-white text-slate-500'
                              "
                              @click="setHandlingStatus(row, option.value)"
                            >
                              {{ option.label }}
                            </button>
                          </div>
                        </div>
                        <label class="space-y-1.5">
                          <span class="text-[11px] font-medium text-slate-600">饰品名称</span>
                          <t-input
                            v-model="row.weaponName"
                            :disabled="!isRowEditable(row)"
                            :class="fieldBaseClass"
                            clearable
                            maxlength="40"
                            placeholder="例如：M4A1-S | 印花集"
                          />
                        </label>
                      </div>

                      <div class="grid grid-cols-2 gap-3">
                        <label class="space-y-1.5">
                          <span class="text-[11px] font-medium text-slate-600">游戏内价格</span>
                          <t-input-number
                            v-model="row.inGamePrice"
                            :decimal-places="2"
                            :disabled="!isRowEditable(row)"
                            :min="0"
                            :step="0.1"
                            align="left"
                            :class="numberFieldBaseClass"
                            placeholder="0.00"
                            theme="normal"
                          />
                        </label>
                        <label class="space-y-1.5">
                          <span class="text-[11px] font-medium text-slate-600">折扣</span>
                          <t-input-number
                            v-model="row.discount"
                            :decimal-places="2"
                            :disabled="!isRowEditable(row)"
                            :max="1"
                            :min="0"
                            :step="0.01"
                            align="left"
                            :class="numberFieldBaseClass"
                            placeholder="0.72"
                            theme="normal"
                          />
                        </label>
                      </div>

                      <div class="grid grid-cols-1 gap-3">
                        <div class="rounded-[4px] border border-slate-200/80 bg-white p-3">
                          <div class="text-[11px] text-slate-500">实际价格</div>
                          <div class="font-numeric mt-2 text-base font-semibold text-[#303133]">
                            {{ formatPendingCurrency(metrics.purchaseCost) }}
                          </div>
                        </div>
                      </div>

                      <div class="grid grid-cols-2 gap-3">
                        <label class="space-y-1.5">
                          <span class="text-[11px] font-semibold text-sky-700">平台卖出价</span>
                          <t-input-number
                            v-model="row.actualSellPrice"
                            :decimal-places="2"
                            :disabled="!isRowEditable(row)"
                            :min="0"
                            :step="0.1"
                            align="left"
                            :class="numberFieldPrimaryClass"
                            placeholder="优先录这里"
                            theme="normal"
                          />
                        </label>
                        <div class="rounded-[4px] border border-slate-200/80 bg-white p-3">
                          <div class="text-[11px] text-slate-500">手续费</div>
                          <div class="font-numeric mt-2 text-base font-semibold text-amber-600">
                            {{ formatPendingCurrency(metrics.actualFee) }}
                          </div>
                          <div class="mt-1 text-xs text-slate-500">
                            {{ metrics.actualFee === null ? "等平台卖出价" : "平台卖出价 × 1%" }}
                          </div>
                        </div>
                      </div>

                      <div class="grid grid-cols-1 gap-3">
                        <div class="p-1">
                          <div class="text-[11px] font-medium text-slate-700">净利润</div>
                          <div
                            class="font-numeric mt-2 text-base font-semibold"
                            :class="profitClass(metrics.actualNetProfit ?? 0)"
                          >
                            {{ formatActualProfit(metrics.actualNetProfit) }}
                          </div>
                          <div class="font-numeric mt-1 text-xs text-slate-500">
                            {{
                              metrics.actualNetIncome === null
                                ? "等平台卖出价"
                                : `到账 ${formatCurrency(metrics.actualNetIncome)}`
                            }}
                          </div>
                          <div
                            class="font-numeric mt-1 text-xs font-medium"
                            :class="profitClass(metrics.actualProfitRate ?? 0)"
                          >
                            {{ formatPercent(metrics.actualProfitRate) }}
                          </div>
                        </div>
                      </div>

                      <label class="space-y-1.5">
                        <span class="text-[11px] font-medium text-slate-600">备注</span>
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
                      <t-popconfirm
                        content="确认删除该条明细吗？"
                        theme="danger"
                        :popup-props="{ attach: 'body' }"
                        @confirm="handleRemoveRow(row.id)"
                      >
                        <t-button
                          theme="danger"
                          variant="outline"
                          class="flex-1"
                          :disabled="draftBatch.rows.length === 1"
                        >
                          删除
                        </t-button>
                      </t-popconfirm>
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
import { computed, h, ref, resolveComponent } from "vue";
import { useElementSize, useWindowSize } from "@vueuse/core";
import { MessagePlugin } from "tdesign-vue-next";
import { HelpCircleIcon } from "tdesign-icons-vue-next";
import type { AttachNode, PrimaryTableCol, Styles, TableSort } from "tdesign-vue-next";
import PageFrame from "@/components/PageFrame.vue";

interface UnboxRow {
  id: string;
  handlingStatus: DraftHandlingStatus;
  boxPurchasePrice: number;
  weaponName: string;
  inGamePrice: number;
  discount: number;
  actualSellPrice: number;
  note: string;
}

interface UnboxBatch {
  id: string;
  name: string;
  date: string;
  boxType: string;
  defaultDiscount: number;
  note: string;
  rows: UnboxRow[];
}

type BatchStatus = "未结算" | "部分结算" | "已结算";
type DraftHandlingStatus = "pending" | "discarded" | "stored" | "purchased";

type DraftRowStage = "待处理" | "丢弃" | "暂存" | "已买";
type DraftRowStageTheme = "default" | "danger" | "warning" | "success";

interface DraftHandlingStatusOption {
  value: DraftHandlingStatus;
  label: DraftRowStage;
  theme: DraftRowStageTheme;
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

interface BatchSummaryRow {
  key: string;
  batch: UnboxBatch;
  summary: BatchSummary;
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
const draftTableViewportRef = ref<HTMLElement | null>(null);
const { height: draftTableViewportHeight } = useElementSize(draftTableViewportRef);

const editorVisible = ref(false);
const isEditorFullscreen = ref(false);
const editingBatchId = ref<string | null>(null);
const isBatchInfoCollapsed = ref(true);
const bulkAddCount = ref(20);
const toolbarBoxPurchasePrice = ref(0);
const batchSummarySort = ref<TableSort>();
const draftTableSort = ref<TableSort>();

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

const LISTING_FEE_RATE = 0.01;
const TOTAL_FEE_RATE = LISTING_FEE_RATE;

const clampDiscount = (value: number) => {
  const safeValue = Number.isFinite(value) ? value : 0;
  return round(Math.min(Math.max(safeValue, 0), 1), 2);
};

const getFee = (sellPrice: number) => round(Math.max(sellPrice, 0) * TOTAL_FEE_RATE);

const createRow = (
  defaults?: Partial<UnboxRow>,
  batch?: Pick<UnboxBatch, "defaultDiscount">
): UnboxRow => ({
  id: createId(),
  handlingStatus: defaults?.handlingStatus ?? "pending",
  boxPurchasePrice: defaults?.boxPurchasePrice ?? 0,
  weaponName: defaults?.weaponName ?? "",
  inGamePrice: defaults?.inGamePrice ?? 0,
  discount: defaults?.discount ?? batch?.defaultDiscount ?? 0.72,
  actualSellPrice: defaults?.actualSellPrice ?? 0,
  note: defaults?.note ?? "",
});

const createBlankBatch = (): UnboxBatch => ({
  id: createId(),
  name: "新的开箱批次",
  date: "2026-04-06",
  boxType: "创世终端机",
  defaultDiscount: 0.72,
  note: "",
  rows: [createRow(), createRow(), createRow()],
});

const cloneBatch = (batch: UnboxBatch): UnboxBatch =>
  JSON.parse(JSON.stringify(batch)) as UnboxBatch;

const draftBatch = ref<UnboxBatch>(createBlankBatch());

const batches = ref<UnboxBatch[]>([
  {
    id: createId(),
    name: "2026-04-06 晚场开箱",
    date: "2026-04-06",
    boxType: "创世终端机",
    defaultDiscount: 0.72,
    note: "待补充批次备注。",
    rows: [
      createRow(
        {
          handlingStatus: "purchased",
          weaponName: "AK-47 | 血腥运动",
          inGamePrice: 85,
          discount: 0.72,
          actualSellPrice: 77,
          note: "首批挂单已成交",
        },
        { defaultDiscount: 0.72 }
      ),
      createRow(
        {
          handlingStatus: "discarded",
          note: "价差不够，直接放弃",
        },
        { defaultDiscount: 0.72 }
      ),
      createRow(
        {
          handlingStatus: "stored",
          weaponName: "AWP | 二西莫夫",
          inGamePrice: 132,
          discount: 0.7,
          note: "待观察两天后再卖",
        },
        { defaultDiscount: 0.72 }
      ),
    ],
  },
  {
    id: createId(),
    name: "2026-04-03 下午开箱",
    date: "2026-04-03",
    boxType: "创世终端机",
    defaultDiscount: 0.71,
    note: "历史样例批次。",
    rows: [
      createRow(
        {
          handlingStatus: "purchased",
          weaponName: "M4A1-S | 印花集",
          inGamePrice: 58,
          discount: 0.73,
          actualSellPrice: 50,
          note: "已小幅止盈",
        },
        { defaultDiscount: 0.71 }
      ),
      createRow(
        {
          handlingStatus: "discarded",
          note: "这一箱直接记损",
        },
        { defaultDiscount: 0.71 }
      ),
      createRow(
        {
          handlingStatus: "stored",
          weaponName: "USP-S | 杀出重围",
          inGamePrice: 44,
          discount: 0.71,
          actualSellPrice: 0,
          note: "还没卖",
        },
        { defaultDiscount: 0.71 }
      ),
    ],
  },
  {
    id: createId(),
    name: "2026-04-01 深夜冲刺",
    date: "2026-04-01",
    boxType: "创世终端机",
    defaultDiscount: 0.69,
    note: "夜间集中开箱批次。",
    rows: [
      createRow(
        {
          handlingStatus: "purchased",
          weaponName: "AK-47 | 霓虹骑士",
          inGamePrice: 102,
          discount: 0.69,
          actualSellPrice: 90,
          note: "当天卖出",
        },
        { defaultDiscount: 0.69 }
      ),
      createRow(
        {
          handlingStatus: "stored",
          weaponName: "AWP | 渐变之色",
          inGamePrice: 136,
          discount: 0.68,
          note: "继续观察",
        },
        { defaultDiscount: 0.69 }
      ),
      createRow(
        {
          handlingStatus: "discarded",
          note: "未达到收益线",
        },
        { defaultDiscount: 0.69 }
      ),
    ],
  },
]);

function isRowEditable(row: UnboxRow) {
  return row.handlingStatus !== "discarded";
}

function shouldCountRow(row: UnboxRow) {
  return row.handlingStatus !== "stored";
}

const getRowMetrics = (row: UnboxRow): RowMetrics => {
  if (!shouldCountRow(row)) {
    return {
      purchaseCost: null,
      actualFee: null,
      actualNetIncome: null,
      actualNetProfit: null,
      actualProfitRate: null,
    };
  }

  const purchaseCost = round(row.inGamePrice * clampDiscount(row.discount));

  const shouldCalculateActualMetrics =
    row.actualSellPrice > 0 || row.boxPurchasePrice > 0 || purchaseCost > 0;
  const actualFee = shouldCalculateActualMetrics ? getFee(row.actualSellPrice) : null;
  const actualNetIncome =
    shouldCalculateActualMetrics && actualFee !== null ? round(row.actualSellPrice - actualFee) : null;
  const actualNetProfit =
    shouldCalculateActualMetrics && actualFee !== null
      ? round(row.actualSellPrice - row.boxPurchasePrice - purchaseCost - actualFee)
      : null;
  const actualProfitRate =
    actualNetProfit !== null && purchaseCost > 0 ? round((actualNetProfit / purchaseCost) * 100) : null;

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
    if (!shouldCountRow(row)) return;

    countedRowCount += 1;
    if (row.handlingStatus === "purchased") {
      boughtCount += 1;
    }
    totalInGamePrice += row.inGamePrice;
    totalPurchaseCost += metrics.purchaseCost ?? 0;

    if (row.actualSellPrice > 0) {
      soldCount += 1;
    }
    if (metrics.actualNetProfit !== null) {
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

const getBatchSummary = (batch: UnboxBatch): BatchSummary =>
  buildBatchSummary(
    batch.rows.length,
    batch.rows.map((row) => ({
      row,
      metrics: getRowMetrics(row),
    }))
  );

const getBatchStatus = (summary: BatchSummary): BatchStatus => {
  if (summary.countedRowCount === 0 || summary.soldCount === 0) return "未结算";
  if (summary.unsoldCount === 0) return "已结算";
  return "部分结算";
};

const batchSummaryRows = computed<BatchSummaryRow[]>(() =>
  batches.value.map((batch) => {
    const summary = getBatchSummary(batch);
    return {
      key: batch.id,
      batch,
      summary,
      status: getBatchStatus(summary),
    };
  })
);

function sortableNumber(value: number | null | undefined) {
  return Number.isFinite(value) ? Number(value) : 0;
}

const summaryTableHeaderClass =
  "!bg-slate-50 !text-slate-500 !text-xs !font-medium !tracking-[0.08em] uppercase whitespace-nowrap";
const summaryTableBodyClass = "!py-3 text-slate-700 align-middle";
const fieldBaseClass =
  "w-full [&_.t-input__wrap]:min-h-8 [&_.t-input__wrap]:rounded-[0.9rem] [&_.t-input__wrap]:border-slate-200 [&_.t-input__wrap]:shadow-none [&_.t-input__wrap:hover]:border-slate-400 [&_.t-is-focused]:border-sky-500 [&_.t-is-focused]:shadow-[0_0_0_3px_rgb(14_165_233_/_0.12)]";
const numberFieldBaseClass =
  "w-full [&_.t-input-number]:w-full [&_.t-input-number]:min-w-0 [&_.t-input__wrap]:min-h-8 [&_.t-input__wrap]:w-full [&_.t-input__wrap]:rounded-[0.9rem] [&_.t-input__wrap]:border-slate-200 [&_.t-input__wrap]:shadow-none [&_.t-input__wrap:hover]:border-slate-400 [&_.t-is-focused]:border-sky-500 [&_.t-is-focused]:shadow-[0_0_0_3px_rgb(14_165_233_/_0.12)]";
const batchDiscountFieldClass = `!w-full min-w-0 ${numberFieldBaseClass} [&_.t-input__wrap]:px-0 [&_.t-input]:w-full [&_.t-input]:px-3 [&_.t-input__inner]:w-full [&_.t-input__inner]:text-left`;
const numberFieldPrimaryClass = `${numberFieldBaseClass} [&_.t-input__wrap]:border-sky-300 [&_.t-input__wrap]:bg-sky-50 [&_.t-input__wrap:hover]:border-sky-400`;

const batchColumns = computed<PrimaryTableCol[]>(() => [
  {
    colKey: "date",
    title: "日期",
    width: 120,
    cell: "date",
    align: "left",
    className: summaryTableBodyClass,
    thClassName: summaryTableHeaderClass,
  },
  {
    colKey: "batchName",
    title: "批次",
    minWidth: 220,
    cell: "batchName",
    align: "left",
    className: summaryTableBodyClass,
    thClassName: summaryTableHeaderClass,
  },
  {
    colKey: "totalCount",
    title: "总条数",
    width: 100,
    cell: "totalCount",
    align: "left",
    className: summaryTableBodyClass,
    thClassName: summaryTableHeaderClass,
  },
  {
    colKey: "purchaseCost",
    title: "总实际购入价",
    width: 140,
    cell: "purchaseCost",
    align: "left",
    className: summaryTableBodyClass,
    thClassName: summaryTableHeaderClass,
  },
  {
    colKey: "totalFee",
    title: "总手续费",
    width: 120,
    cell: "totalFee",
    align: "left",
    className: summaryTableBodyClass,
    thClassName: summaryTableHeaderClass,
  },
  {
    colKey: "actualNetProfit",
    title: "总实际净利润",
    width: 150,
    cell: "actualNetProfit",
    align: "left",
    sorter: (a, b) => sortableNumber(a.summary.totalActualNetProfit) - sortableNumber(b.summary.totalActualNetProfit),
    sortType: "all",
    className: summaryTableBodyClass,
    thClassName: summaryTableHeaderClass,
  },
  {
    colKey: "actualProfitRate",
    title: "总利润率",
    width: 120,
    cell: "actualProfitRate",
    align: "left",
    sorter: (a, b) => sortableNumber(a.summary.totalActualProfitRate) - sortableNumber(b.summary.totalActualProfitRate),
    sortType: "all",
    className: summaryTableBodyClass,
    thClassName: summaryTableHeaderClass,
  },
  {
    colKey: "status",
    title: "状态",
    width: 120,
    cell: "status",
    align: "left",
    className: summaryTableBodyClass,
    thClassName: summaryTableHeaderClass,
  },
  {
    colKey: "operation",
    title: "操作",
    width: 150,
    cell: "operation",
    fixed: "right",
    align: "left",
    className: `${summaryTableBodyClass} !bg-white`,
    thClassName: summaryTableHeaderClass,
  },
]);

const pageSummary = computed(() => {
  return batchSummaryRows.value.reduce(
    (result, item) => {
      result.totalBatches += 1;
      result.totalBoxes += item.summary.totalCount;
      result.totalPurchaseCost += item.summary.totalPurchaseCost;
      result.totalFee += item.summary.totalActualFee;
      result.totalActualNetProfit += item.summary.totalActualNetProfit;
      return result;
    },
    {
      totalBatches: 0,
      totalBoxes: 0,
      totalPurchaseCost: 0,
      totalFee: 0,
      totalActualNetProfit: 0,
    }
  );
});

const pageSummaryCards = computed<SummaryCard[]>(() => [
  {
    label: "批次数量",
    value: `${pageSummary.value.totalBatches}`,
    hint: `累计记录 ${pageSummary.value.totalBoxes} 条明细`,
    valueClass: "text-[#303133]",
  },
  {
    label: "总实际购入价",
    value: formatCurrency(pageSummary.value.totalPurchaseCost),
    hint: "游戏买入价 × 折扣后的汇总成本",
    valueClass: "text-[#303133]",
  },
  {
    label: "总手续费",
    value: formatCurrency(pageSummary.value.totalFee),
    hint: "按平台卖出价 × 1% 计算",
    valueClass: "text-amber-600",
  },
  {
    label: "总实际净利润",
    value: formatSignedCurrency(pageSummary.value.totalActualNetProfit),
    hint: "仅按当前平台卖出价口径统计",
    valueClass: profitClass(pageSummary.value.totalActualNetProfit),
  },
]);

const draftHandlingStatusOptions: DraftHandlingStatusOption[] = [
  { value: "pending", label: "待处理", theme: "default" },
  { value: "discarded", label: "丢弃", theme: "danger" },
  { value: "stored", label: "暂存", theme: "warning" },
  { value: "purchased", label: "已买", theme: "success" },
];

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
  if (stage === "丢弃") return "确认放弃该饰品，但保留已录入价格并继续参与成本与利润统计";
  if (stage === "暂存") return "先放在这里，等全部开完后还有闲钱再买，不参与任何计算";
  if (stage === "已买") return "确认接手，参与成本、卖价和净利润统计";
  return "默认参与成本、卖价和净利润统计";
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
  "!bg-slate-100 !text-slate-600 !text-xs !font-semibold !tracking-[0.08em] whitespace-nowrap";
const draftTableBodyClass = "!py-2 text-slate-700 align-middle";
const draftTableFixedBodyClass = `${draftTableBodyClass} !bg-white`;
const draftTableFooterClass =
  "!bg-slate-100/70 !py-2 align-top text-[14px] leading-4.5 font-semibold text-slate-500";
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
            h("div", [h("span", { class: "font-semibold text-slate-700" }, "待处理："), "默认参与成本、卖价和净利润统计"]),
            h("div", [h("span", { class: "font-semibold text-slate-700" }, "丢弃："), "确认放弃该饰品，但保留已录入价格并继续参与成本与利润统计"]),
            h("div", [h("span", { class: "font-semibold text-slate-700" }, "暂存："), "先放在这里，等全部开完后还有闲钱再买，不参与任何计算"]),
            h("div", [h("span", { class: "font-semibold text-slate-700" }, "已买："), "确认接手，参与成本、卖价和净利润统计"]),
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
    width: 118,
    cell: "boxPurchasePrice",
    foot: "footerBoxPurchasePrice",
    align: "left",
    className: draftTableBodyClass,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "purchaseState",
    title: purchaseStateTitle,
    width: 138,
    cell: "purchaseState",
    foot: "footerPurchaseState",
    align: "left",
    className: draftTableBodyClass,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "weaponName",
    title: "饰品名称",
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
    width: 112,
    cell: "inGamePrice",
    foot: "footerInGamePrice",
    align: "left",
    className: draftTableBodyClass,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "discount",
    title: "折扣",
    width: 88,
    cell: "discount",
    foot: "footerDiscount",
    align: "left",
    className: draftTableBodyClass,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "purchaseCost",
    title: purchaseCostTitle,
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
    sorter: (a, b) => sortableNumber(a.metrics.actualNetProfit) - sortableNumber(b.metrics.actualNetProfit),
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
    sorter: (a, b) => sortableNumber(a.metrics.actualProfitRate) - sortableNumber(b.metrics.actualProfitRate),
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

const draftTableHeight = computed(() => Math.max(Math.floor(draftTableViewportHeight.value), 320));

const draftTableScrollContainer = () => draftTableViewportRef.value ?? document.body;

const draftSummary = computed(() => buildBatchSummary(draftBatch.value.rows.length, draftRowEntries.value));
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
  draftBatch.value = createBlankBatch();
  isEditorFullscreen.value = false;
  isBatchInfoCollapsed.value = true;
  editorVisible.value = true;
}

function openEditEditor(batchId: string) {
  const target = batches.value.find((item) => item.id === batchId);
  if (!target) {
    MessagePlugin.error("批次不存在或已删除");
    return;
  }
  editingBatchId.value = batchId;
  draftBatch.value = cloneBatch(target);
  isEditorFullscreen.value = false;
  isBatchInfoCollapsed.value = true;
  editorVisible.value = true;
}

function saveDraftBatch() {
  if (!draftBatch.value.date) {
    MessagePlugin.warning("请选择开箱日期");
    return;
  }
  if (!draftBatch.value.rows.length) {
    MessagePlugin.warning("请至少保留一条明细");
    return;
  }

  const normalized = cloneBatch(draftBatch.value);
  if (editingBatchId.value) {
    const index = batches.value.findIndex((item) => item.id === editingBatchId.value);
    if (index === -1) {
      MessagePlugin.error("批次不存在或已删除");
      return;
    }
    batches.value.splice(index, 1, normalized);
    MessagePlugin.success("批次已更新");
  } else {
    batches.value.unshift(normalized);
    MessagePlugin.success("批次已创建");
  }
  editorVisible.value = false;
}

function removeBatch(batchId: string) {
  batches.value = batches.value.filter((item) => item.id !== batchId);
  MessagePlugin.success("批次已删除");
}

function setHandlingStatus(row: UnboxRow, handlingStatus: DraftHandlingStatus) {
  row.handlingStatus = handlingStatus;
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
  const newRows = Array.from({ length: normalizedCount }, () => createToolbarDefaultRow(nextBoxPurchasePrice));
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
  let updatedCount = 0;
  draftBatch.value.rows.forEach((row) => {
    let changed = false;
    if (row.discount <= 0) {
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
  if (draftBatch.value.rows.length === 1) {
    MessagePlugin.warning("至少保留一条明细，避免批次编辑区为空");
    return;
  }
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
  height: 100%;
  overflow: auto;
}

:deep(.draft-detail-table .t-table) {
  height: 100%;
}

:deep(.draft-detail-table .t-table__content-inner) {
  min-height: 100%;
}

:deep(.draft-detail-table .t-table__table) {
  min-width: 100%;
}

:deep(.draft-detail-table .t-table__fixed-right-column),
:deep(.draft-detail-table .t-table__fixed-left-column) {
  background: rgb(255 255 255);
}


:deep(.draft-detail-table tfoot td) {
  background: rgb(241 245 249 / 0.7);
  border-top: none;
}

:deep(.draft-detail-table tfoot .t-table__fixed-right-column),
:deep(.draft-detail-table tfoot .t-table__fixed-left-column) {
  background: rgb(241 245 249 / 0.7);
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

</style>
