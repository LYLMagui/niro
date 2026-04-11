<template>
  <PageFrame
    :is-mobile="isMobile"
    desktop-content-class="px-4 pt-3 pb-4"
    mobile-content-class="px-3 pt-3 pb-3"
  >
    <div
      ref="pageHostRef"
      class="unbox-record-page relative flex min-h-0 flex-1 flex-col gap-4 bg-slate-50"
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
              <span>主表看批次，弹窗编辑明细</span>
            </div>
            <div class="space-y-1">
              <h1 class="text-xl font-semibold tracking-tight text-[#303133]">开箱记录</h1>
              <p class="max-w-3xl text-sm leading-6 text-slate-500">
                以批次列表作为主表。点击新增或编辑后，在弹窗里维护整批开箱明细、预估收益和实际收益。
              </p>
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
            <p class="mt-1 text-sm text-slate-500">
              主页面只看批次汇总。新增批次或编辑批次时，再进入弹窗维护明细。
            </p>
          </div>
          <t-tag theme="primary" variant="light-outline">共 {{ batches.length }} 批</t-tag>
        </div>

        <div class="mt-4 overflow-x-auto">
          <t-table
            row-key="key"
            :data="batchSummaryRows"
            :columns="batchColumns"
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

            <template #marketNetProfit="{ row }">
              <span class="font-numeric" :class="profitClass(row.summary.totalMarketNetProfit)">
                {{ formatSignedCurrency(row.summary.totalMarketNetProfit) }}
              </span>
            </template>

            <template #actualNetProfit="{ row }">
              <span class="font-numeric" :class="profitClass(row.summary.totalActualNetProfit)">
                {{ formatSignedCurrency(row.summary.totalActualNetProfit) }}
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
                  <div class="grid grid-cols-1 gap-3 xl:grid-cols-12">
                    <label class="space-y-1.5 xl:col-span-3">
                      <span class="text-[11px] font-medium text-slate-700 sm:text-xs">
                        批次名称
                      </span>
                      <t-input
                        v-model="draftBatch.name"
                        :class="fieldBaseClass"
                        clearable
                        maxlength="40"
                        placeholder="例如：2026-04-06 晚场开箱"
                        size="small"
                      />
                    </label>

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

                    <div class="xl:col-span-3">
                      <label class="space-y-1.5">
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
                          :class="numberFieldBaseClass"
                          placeholder="0.72"
                          size="small"
                          theme="normal"
                        />
                      </label>
                    </div>

                    <label class="space-y-1.5 xl:col-span-12">
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
                        <t-button size="small" variant="outline" @click="applyDefaultsToEmptyRows">
                          应用到未填写行
                        </t-button>
                        <t-button size="small" variant="outline" @click="mockQueryC5Price">
                          拉取市场参考卖价
                        </t-button>
                      </div>
                    </div>
                  </div>

                  <div class="top-0 z-4 -mx-0.5 overflow-x-auto px-0.5">
                    <div
                      class="inline-flex min-w-max items-stretch gap-2 rounded-xl border border-slate-200 bg-slate-50/80 p-1.5 shadow-sm xl:flex xl:w-full xl:min-w-0"
                    >
                      <article
                        class="flex min-w-[280px] items-center justify-between gap-3 rounded-[10px] border border-sky-100 bg-white px-3 py-2.5 shadow-sm xl:min-w-0 xl:flex-1"
                      >
                        <div class="min-w-0 flex-1">
                          <div
                            class="text-[10px] font-medium tracking-[0.12em] text-slate-500 uppercase"
                          >
                            当前净利润
                          </div>
                          <div class="mt-1 flex items-baseline gap-2">
                            <span
                              class="font-numeric text-xl font-semibold"
                              :class="profitClass(draftSummary.totalActualNetProfit)"
                            >
                              {{ formatSignedCurrency(draftSummary.totalActualNetProfit) }}
                            </span>
                            <span class="text-[11px] text-slate-400">实时汇总</span>
                          </div>
                          <div
                            class="mt-1 text-[11px] font-medium"
                            :class="
                              draftSummary.unsoldCount > 0 ? 'text-amber-700' : 'text-emerald-600'
                            "
                          >
                            {{
                              draftSummary.unsoldCount > 0
                                ? `还有 ${draftSummary.unsoldCount} 条待录平台卖出价`
                                : "平台卖出价已录齐，可直接复核净利润"
                            }}
                          </div>
                        </div>
                        <div class="shrink-0 text-right">
                          <div class="text-[10px] text-slate-400">成本 / 手续费</div>
                          <div class="font-numeric mt-1 text-xs text-slate-600">
                            {{ formatCurrency(draftSummary.totalPurchaseCost) }} /
                            {{ formatCurrency(draftSummary.totalActualFee) }}
                          </div>
                        </div>
                      </article>

                      <div
                        class="flex shrink-0 items-center gap-0.5 rounded-[10px] bg-white px-2 py-1.5 shadow-sm xl:ml-auto"
                      >
                        <div class="min-w-[100px] px-2 py-1">
                          <div class="text-[10px] tracking-[0.08em] text-slate-400 uppercase">
                            待录卖出价
                          </div>
                          <div
                            class="font-numeric mt-1 text-sm font-semibold"
                            :class="
                              draftSummary.unsoldCount > 0 ? 'text-amber-700' : 'text-emerald-600'
                            "
                          >
                            {{ draftSummary.unsoldCount }} 条
                          </div>
                        </div>
                        <div class="h-8 w-px bg-slate-200"></div>
                        <div class="min-w-[100px] px-2 py-1">
                          <div class="text-[10px] tracking-[0.08em] text-slate-400 uppercase">
                            已结算
                          </div>
                          <div class="font-numeric mt-1 text-sm font-semibold text-[#303133]">
                            {{ draftSummary.soldCount }} / {{ draftSummary.purchasedCount }}
                          </div>
                        </div>
                        <div class="h-8 w-px bg-slate-200"></div>
                        <div class="min-w-[100px] px-2 py-1">
                          <div class="text-[10px] tracking-[0.08em] text-slate-400 uppercase">
                            总手续费
                          </div>
                          <div class="font-numeric mt-1 text-sm font-semibold text-amber-600">
                            {{ formatCurrency(draftSummary.totalActualFee) }}
                          </div>
                        </div>
                        <div class="h-8 w-px bg-slate-200"></div>
                        <div class="min-w-[112px] px-2 py-1">
                          <div class="text-[10px] tracking-[0.08em] text-slate-400 uppercase">
                            参考净利润
                          </div>
                          <div
                            class="font-numeric mt-1 text-sm font-semibold"
                            :class="profitClass(draftSummary.totalMarketNetProfit)"
                          >
                            {{ formatSignedCurrency(draftSummary.totalMarketNetProfit) }}
                          </div>
                        </div>
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
                    :height="draftTableHeight"
                    :header-affixed-top="{ offsetTop: 0, container: draftTableScrollContainer }"
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

                    <template #purchaseState="{ row: entry }">
                      <div class="space-y-1">
                        <div class="flex">
                          <t-tag :theme="entry.stageTheme" size="small" variant="light-outline">
                            {{ entry.stage }}
                          </t-tag>
                        </div>
                        <button
                          type="button"
                          class="inline-flex h-6 items-center rounded px-1 text-[10px] font-medium text-slate-500 transition-colors focus-visible:ring-2 focus-visible:ring-sky-500/60 focus-visible:ring-offset-1 focus-visible:outline-none"
                          :class="
                            entry.row.purchased
                              ? 'hover:bg-rose-50 hover:text-rose-600'
                              : 'hover:bg-emerald-50 hover:text-emerald-600'
                          "
                          @click="setPurchaseState(entry.row, !entry.row.purchased)"
                        >
                          {{ entry.row.purchased ? "改未购" : "标已购" }}
                        </button>
                      </div>
                    </template>

                    <template #weaponName="{ row: entry }">
                      <t-input
                        v-model="entry.row.weaponName"
                        :disabled="!entry.row.purchased"
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
                        :disabled="!entry.row.purchased"
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
                        :disabled="!entry.row.purchased"
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
                      <div class="rounded-lg bg-slate-50/90 px-2 py-1.5 text-right leading-4">
                        <div class="font-numeric text-sm font-semibold text-[#303133]">
                          {{ formatPendingCurrency(entry.metrics.purchaseCost) }}
                        </div>
                        <div class="mt-1 text-[10px] text-slate-400">买入价 × 折扣</div>
                      </div>
                    </template>

                    <template #c5Price="{ row: entry }">
                      <div class="space-y-1">
                        <t-input-number
                          v-model="entry.row.c5Price"
                          :decimal-places="2"
                          :disabled="!entry.row.purchased"
                          :min="0"
                          :step="0.1"
                          align="right"
                          :class="numberFieldMutedClass"
                          placeholder="参考价"
                          size="small"
                          theme="normal"
                        />
                      </div>
                    </template>

                    <template #marketNetProfit="{ row: entry }">
                      <div class="rounded-lg bg-slate-50/70 px-2 py-1.5 leading-4">
                        <div
                          class="font-numeric text-sm font-semibold"
                          :class="profitClass(entry.metrics.marketNetProfit ?? 0)"
                        >
                          {{ formatActualProfit(entry.metrics.marketNetProfit) }}
                        </div>
                        <div class="mt-1 text-[10px] text-slate-400">
                          {{
                            entry.metrics.marketFee === null
                              ? "待参考价"
                              : `参考费 ${formatCurrency(entry.metrics.marketFee)}`
                          }}
                        </div>
                      </div>
                    </template>

                    <template #actualSellPrice="{ row: entry }">
                      <div class="space-y-1">
                        <t-input-number
                          v-model="entry.row.actualSellPrice"
                          :decimal-places="2"
                          :disabled="!entry.row.purchased"
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
                      <div
                        class="rounded-lg border border-slate-200 bg-white px-2 py-1.5 leading-4 shadow-sm"
                      >
                        <div
                          class="font-numeric text-sm font-semibold"
                          :class="profitClass(entry.metrics.actualNetProfit ?? 0)"
                        >
                          {{ formatActualProfit(entry.metrics.actualNetProfit) }}
                        </div>
                        <div class="mt-1 text-[10px] text-slate-500">
                          {{
                            entry.metrics.actualNetIncome === null
                              ? "待卖价"
                              : `到账 ${formatCurrency(entry.metrics.actualNetIncome)}`
                          }}
                        </div>
                      </div>
                    </template>

                    <template #note="{ row: entry }">
                      <t-input
                        v-model="entry.row.note"
                        :class="fieldBaseClass"
                        clearable
                        maxlength="50"
                        placeholder="补充判断或出售说明"
                        size="small"
                      />
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
                          <span class="text-[11px] font-medium text-slate-600">购买状态</span>
                          <div
                            class="inline-flex w-full rounded-lg border border-slate-200 bg-slate-50/80 p-1"
                          >
                            <button
                              type="button"
                              class="inline-flex h-9 flex-1 touch-manipulation items-center justify-center rounded-md text-sm font-medium focus-visible:ring-2 focus-visible:ring-sky-500/60 focus-visible:ring-offset-1 focus-visible:outline-none"
                              :class="
                                row.purchased
                                  ? 'bg-emerald-50 text-emerald-700 shadow-sm'
                                  : 'text-slate-500'
                              "
                              @click="setPurchaseState(row, true)"
                            >
                              已购
                            </button>
                            <button
                              type="button"
                              class="inline-flex h-9 flex-1 touch-manipulation items-center justify-center rounded-md text-sm font-medium focus-visible:ring-2 focus-visible:ring-sky-500/60 focus-visible:ring-offset-1 focus-visible:outline-none"
                              :class="
                                !row.purchased
                                  ? 'bg-rose-50 text-rose-700 shadow-sm'
                                  : 'text-slate-500'
                              "
                              @click="setPurchaseState(row, false)"
                            >
                              未购
                            </button>
                          </div>
                        </div>
                        <label class="space-y-1.5">
                          <span class="text-[11px] font-medium text-slate-600">饰品名称</span>
                          <t-input
                            v-model="row.weaponName"
                            :disabled="!row.purchased"
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
                            :disabled="!row.purchased"
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
                            :disabled="!row.purchased"
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

                      <div class="grid grid-cols-2 gap-3">
                        <div class="rounded-[4px] border border-slate-200/80 bg-white p-3">
                          <div class="text-[11px] text-slate-500">实际价格</div>
                          <div class="font-numeric mt-2 text-base font-semibold text-[#303133]">
                            {{ formatPendingCurrency(metrics.purchaseCost) }}
                          </div>
                        </div>
                        <label class="space-y-1.5">
                          <span class="text-[11px] font-medium text-slate-500">平台参考卖价</span>
                          <t-input-number
                            v-model="row.c5Price"
                            :decimal-places="2"
                            :disabled="!row.purchased"
                            :min="0"
                            :step="0.1"
                            align="left"
                            :class="numberFieldMutedClass"
                            placeholder="仅用于预估"
                            theme="normal"
                          />
                          <div class="text-[11px] leading-4 text-slate-400">不影响最终净利润</div>
                        </label>
                      </div>

                      <div class="grid grid-cols-2 gap-3">
                        <label class="space-y-1.5">
                          <span class="text-[11px] font-semibold text-sky-700">平台卖出价</span>
                          <t-input-number
                            v-model="row.actualSellPrice"
                            :decimal-places="2"
                            :disabled="!row.purchased"
                            :min="0"
                            :step="0.1"
                            align="left"
                            :class="numberFieldPrimaryClass"
                            placeholder="优先录这里"
                            theme="normal"
                          />
                          <div class="text-[11px] leading-4 text-sky-600">
                            成交价会直接决定手续费和净利润
                          </div>
                        </label>
                        <div class="rounded-[4px] border border-slate-200/80 bg-white p-3">
                          <div class="text-[11px] text-slate-500">手续费</div>
                          <div class="font-numeric mt-2 text-base font-semibold text-amber-600">
                            {{ formatPendingCurrency(metrics.actualFee) }}
                          </div>
                          <div class="mt-1 text-xs text-slate-500">
                            {{ metrics.actualFee === null ? "等平台卖出价" : "上架 1% + 提现 1%" }}
                          </div>
                        </div>
                      </div>

                      <div class="grid grid-cols-2 gap-3">
                        <div class="rounded-[4px] border border-slate-200/80 bg-slate-50/70 p-3">
                          <div class="text-[11px] text-slate-500">预估净利润</div>
                          <div
                            class="font-numeric mt-2 text-base font-semibold"
                            :class="profitClass(metrics.marketNetProfit ?? 0)"
                          >
                            {{ formatActualProfit(metrics.marketNetProfit) }}
                          </div>
                          <div class="mt-1 text-xs text-slate-400">
                            {{
                              metrics.marketFee === null
                                ? "待参考价"
                                : `参考手续费 ${formatCurrency(metrics.marketFee)}`
                            }}
                          </div>
                        </div>
                        <div
                          class="rounded-[4px] border border-slate-900/90 bg-white p-3 shadow-sm"
                        >
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
import { computed, ref } from "vue";
import { useElementSize, useWindowSize } from "@vueuse/core";
import { MessagePlugin } from "tdesign-vue-next";
import type { AttachNode, PrimaryTableCol, Styles } from "tdesign-vue-next";
import PageFrame from "@/components/PageFrame.vue";

interface UnboxRow {
  id: string;
  purchased: boolean;
  weaponName: string;
  inGamePrice: number;
  discount: number;
  c5Price: number;
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

interface RowMetrics {
  purchaseCost: number | null;
  marketFee: number | null;
  marketNetProfit: number | null;
  actualFee: number | null;
  actualNetIncome: number | null;
  actualNetProfit: number | null;
}

interface BatchSummary {
  totalCount: number;
  purchasedCount: number;
  totalInGamePrice: number;
  totalPurchaseCost: number;
  totalMarketFee: number;
  totalMarketNetProfit: number;
  soldCount: number;
  unsoldCount: number;
  totalActualNetIncome: number;
  totalActualFee: number;
  totalActualNetProfit: number;
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

type DraftRowStage = "未购" | "待卖出" | "已结算";
type DraftRowStageTheme = "danger" | "warning" | "success";

interface DraftRowEntry {
  row: UnboxRow;
  metrics: RowMetrics;
  stage: DraftRowStage;
  stageTheme: DraftRowStageTheme;
  stageDescription: string;
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
const WITHDRAW_FEE_RATE = 0.01;
const TOTAL_FEE_RATE = LISTING_FEE_RATE + WITHDRAW_FEE_RATE;

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
  purchased: defaults?.purchased ?? true,
  weaponName: defaults?.weaponName ?? "",
  inGamePrice: defaults?.inGamePrice ?? 0,
  discount: defaults?.discount ?? batch?.defaultDiscount ?? 0.72,
  c5Price: defaults?.c5Price ?? 0,
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
  rows: [createRow(), createRow({ purchased: false }), createRow()],
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
    note: "待补充 C5 查询结果和批次备注。",
    rows: [
      createRow(
        {
          weaponName: "AK-47 | 血腥运动",
          inGamePrice: 85,
          discount: 0.72,
          c5Price: 69.8,
          actualSellPrice: 77,
          note: "首批挂单已成交",
        },
        { defaultDiscount: 0.72 }
      ),
      createRow(
        {
          purchased: false,
          note: "价差不够，直接放弃",
        },
        { defaultDiscount: 0.72 }
      ),
      createRow(
        {
          weaponName: "AWP | 二西莫夫",
          inGamePrice: 132,
          discount: 0.7,
          c5Price: 105.6,
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
          weaponName: "M4A1-S | 印花集",
          inGamePrice: 58,
          discount: 0.73,
          c5Price: 47.5,
          actualSellPrice: 50,
          note: "已小幅止盈",
        },
        { defaultDiscount: 0.71 }
      ),
      createRow(
        {
          purchased: false,
          note: "这一箱直接记损",
        },
        { defaultDiscount: 0.71 }
      ),
      createRow(
        {
          weaponName: "USP-S | 杀出重围",
          inGamePrice: 44,
          discount: 0.71,
          c5Price: 31.2,
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
          weaponName: "AK-47 | 霓虹骑士",
          inGamePrice: 102,
          discount: 0.69,
          c5Price: 83.8,
          actualSellPrice: 90,
          note: "当天卖出",
        },
        { defaultDiscount: 0.69 }
      ),
      createRow(
        {
          weaponName: "AWP | 渐变之色",
          inGamePrice: 136,
          discount: 0.68,
          c5Price: 109.4,
          note: "继续观察",
        },
        { defaultDiscount: 0.69 }
      ),
      createRow(
        {
          purchased: false,
          note: "未达到收益线",
        },
        { defaultDiscount: 0.69 }
      ),
    ],
  },
]);

const getRowMetrics = (row: UnboxRow): RowMetrics => {
  if (!row.purchased) {
    return {
      purchaseCost: null,
      marketFee: null,
      marketNetProfit: null,
      actualFee: null,
      actualNetIncome: null,
      actualNetProfit: null,
    };
  }

  const purchaseCost = round(row.inGamePrice * clampDiscount(row.discount));
  const hasMarketPrice = row.c5Price > 0;
  const marketFee = hasMarketPrice ? getFee(row.c5Price) : null;
  const marketNetProfit =
    hasMarketPrice && marketFee !== null ? round(row.c5Price - purchaseCost - marketFee) : null;

  const hasActualSellPrice = row.actualSellPrice > 0;
  const actualFee = hasActualSellPrice ? getFee(row.actualSellPrice) : null;
  const actualNetIncome =
    hasActualSellPrice && actualFee !== null ? round(row.actualSellPrice - actualFee) : null;
  const actualNetProfit =
    hasActualSellPrice && actualFee !== null
      ? round(row.actualSellPrice - purchaseCost - actualFee)
      : null;

  return {
    purchaseCost,
    marketFee,
    marketNetProfit,
    actualFee,
    actualNetIncome,
    actualNetProfit,
  };
};

const getBatchSummary = (batch: UnboxBatch): BatchSummary => {
  let purchasedCount = 0;
  let totalInGamePrice = 0;
  let totalPurchaseCost = 0;
  let totalMarketFee = 0;
  let totalMarketNetProfit = 0;
  let soldCount = 0;
  let totalActualNetIncome = 0;
  let totalActualFee = 0;
  let totalActualNetProfit = 0;

  batch.rows.forEach((item) => {
    const metrics = getRowMetrics(item);

    if (!item.purchased) return;

    purchasedCount += 1;
    totalInGamePrice += item.inGamePrice;
    totalPurchaseCost += metrics.purchaseCost ?? 0;
    totalMarketFee += metrics.marketFee ?? 0;
    totalMarketNetProfit += metrics.marketNetProfit ?? 0;

    if (item.actualSellPrice > 0) {
      soldCount += 1;
      totalActualNetIncome += metrics.actualNetIncome ?? 0;
      totalActualFee += metrics.actualFee ?? 0;
      totalActualNetProfit += metrics.actualNetProfit ?? 0;
    }
  });

  const totalCount = batch.rows.length;

  return {
    totalCount,
    purchasedCount,
    totalInGamePrice: round(totalInGamePrice),
    totalPurchaseCost: round(totalPurchaseCost),
    totalMarketFee: round(totalMarketFee),
    totalMarketNetProfit: round(totalMarketNetProfit),
    soldCount,
    unsoldCount: purchasedCount - soldCount,
    totalActualNetIncome: round(totalActualNetIncome),
    totalActualFee: round(totalActualFee),
    totalActualNetProfit: round(totalActualNetProfit),
  };
};

const getBatchStatus = (summary: BatchSummary): BatchStatus => {
  if (summary.purchasedCount === 0 || summary.soldCount === 0) return "未结算";
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

const summaryTableHeaderClass =
  "!bg-slate-50 !text-slate-500 !text-xs !font-medium !tracking-[0.08em] uppercase whitespace-nowrap";
const summaryTableBodyClass = "!py-3 text-slate-700 align-middle";
const fieldBaseClass =
  "w-full [&_.t-input__wrap]:rounded-[0.9rem] [&_.t-input__wrap]:border-slate-200 [&_.t-input__wrap]:shadow-none [&_.t-input__wrap:hover]:border-slate-400 [&_.t-is-focused]:border-sky-500 [&_.t-is-focused]:shadow-[0_0_0_3px_rgb(14_165_233_/_0.12)]";
const numberFieldBaseClass =
  "w-full [&_.t-input-number__wrap]:rounded-[0.9rem] [&_.t-input-number__wrap]:border-slate-200 [&_.t-input-number__wrap]:shadow-none [&_.t-input-number__wrap:hover]:border-slate-400 [&_.t-is-focused]:border-sky-500 [&_.t-is-focused]:shadow-[0_0_0_3px_rgb(14_165_233_/_0.12)]";
const numberFieldMutedClass = `${numberFieldBaseClass} [&_.t-input-number__wrap]:bg-slate-50 [&_.t-input-number__wrap:hover]:border-slate-300 [&_.t-is-focused]:border-slate-400 [&_.t-is-focused]:shadow-[0_0_0_3px_rgb(148_163_184_/_0.1)]`;
const numberFieldPrimaryClass = `${numberFieldBaseClass} [&_.t-input-number__wrap]:border-sky-300 [&_.t-input-number__wrap]:bg-sky-50 [&_.t-input-number__wrap:hover]:border-sky-400`;

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
    title: "批次名称",
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
    colKey: "marketNetProfit",
    title: "总预估净利润",
    width: 150,
    cell: "marketNetProfit",
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
      result.totalMarketNetProfit += item.summary.totalMarketNetProfit;
      result.totalActualNetProfit += item.summary.totalActualNetProfit;
      return result;
    },
    {
      totalBatches: 0,
      totalBoxes: 0,
      totalPurchaseCost: 0,
      totalFee: 0,
      totalMarketNetProfit: 0,
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
    hint: "按平台卖出价扣除上架 1% + 提现 1%",
    valueClass: "text-amber-600",
  },
  {
    label: "总实际净利润",
    value: formatSignedCurrency(pageSummary.value.totalActualNetProfit),
    hint: `预估净利润 ${formatSignedCurrency(pageSummary.value.totalMarketNetProfit)}`,
    valueClass: profitClass(pageSummary.value.totalActualNetProfit),
  },
]);

function getDraftRowStage(row: UnboxRow): DraftRowStage {
  if (!row.purchased) return "未购";
  if (row.actualSellPrice > 0) return "已结算";
  return "待卖出";
}

function getDraftRowStageTheme(stage: DraftRowStage): DraftRowStageTheme {
  if (stage === "已结算") return "success";
  if (stage === "待卖出") return "warning";
  return "danger";
}

function getDraftRowStageDescription(stage: DraftRowStage) {
  if (stage === "已结算") return "已录入平台卖出价，可直接看净利润";
  if (stage === "待卖出") return "已购，待录入平台卖出价";
  return "未购，不参与净利润统计";
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
const draftTableHeaderMutedClass =
  "!bg-slate-100 !text-slate-500 !text-xs !font-semibold !tracking-[0.08em] whitespace-nowrap";
const draftTableHeaderSubtleClass =
  "!bg-slate-100 !text-slate-400 !text-xs !font-semibold !tracking-[0.08em] whitespace-nowrap";
const draftTableHeaderPrimaryClass =
  "!bg-sky-50 !text-sky-700 !text-xs !font-semibold !tracking-[0.08em] whitespace-nowrap";
const draftTableHeaderStrongClass =
  "!bg-slate-900 !text-white !text-xs !font-semibold !tracking-[0.08em] whitespace-nowrap";
const draftTableBodyClass = "!py-2 text-slate-700 align-middle";
const draftTableFixedBodyClass = `${draftTableBodyClass} !bg-white`;

const draftTableColumns = computed<PrimaryTableCol[]>(() => [
  {
    colKey: "index",
    title: "#",
    width: 54,
    minWidth: 54,
    cell: "index",
    align: "left",
    className: `${draftTableBodyClass} whitespace-nowrap`,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "purchaseState",
    title: "阶段",
    width: 88,
    cell: "purchaseState",
    align: "left",
    className: draftTableBodyClass,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "weaponName",
    title: "饰品名称",
    minWidth: 192,
    cell: "weaponName",
    align: "left",
    className: draftTableBodyClass,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "inGamePrice",
    title: "游戏买入价",
    width: 112,
    cell: "inGamePrice",
    align: "left",
    className: draftTableBodyClass,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "discount",
    title: "折扣",
    width: 88,
    cell: "discount",
    align: "left",
    className: draftTableBodyClass,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "purchaseCost",
    title: "实际购入价",
    minWidth: 118,
    cell: "purchaseCost",
    align: "left",
    className: draftTableBodyClass,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "c5Price",
    title: "平台参考售价",
    minWidth: 122,
    cell: "c5Price",
    align: "left",
    className: draftTableBodyClass,
    thClassName: draftTableHeaderMutedClass,
  },
  {
    colKey: "actualSellPrice",
    title: "平台卖出价",
    minWidth: 130,
    cell: "actualSellPrice",
    align: "left",
    className: draftTableBodyClass,
    thClassName: draftTableHeaderPrimaryClass,
  },
  {
    colKey: "note",
    title: "备注",
    minWidth: 132,
    cell: "note",
    align: "left",
    className: draftTableBodyClass,
    thClassName: draftTableHeaderClass,
  },
  {
    colKey: "marketNetProfit",
    title: "参考净利润",
    width: 112,
    minWidth: 112,
    cell: "marketNetProfit",
    align: "left",
    fixed: "right",
    className: draftTableFixedBodyClass,
    thClassName: draftTableHeaderSubtleClass,
  },
  {
    colKey: "actualNetProfit",
    title: "净利润",
    width: 116,
    minWidth: 116,
    cell: "actualNetProfit",
    align: "left",
    fixed: "right",
    className: draftTableFixedBodyClass,
    thClassName: draftTableHeaderStrongClass,
  },
  {
    colKey: "operation",
    title: "操作",
    width: 72,
    minWidth: 72,
    cell: "operation",
    align: "left",
    fixed: "right",
    className: draftTableFixedBodyClass,
    thClassName: draftTableHeaderClass,
  },
]);

const draftTableHeight = computed(() => Math.max(Math.floor(draftTableViewportHeight.value), 320));

const draftTableScrollContainer = () => draftTableViewportRef.value ?? document.body;

const draftSummary = computed(() => getBatchSummary(draftBatch.value));

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
  if (!draftBatch.value.name.trim()) {
    MessagePlugin.warning("请填写批次名称");
    return;
  }
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

function setPurchaseState(row: UnboxRow, purchased: boolean) {
  row.purchased = purchased;
  if (!purchased) {
    row.actualSellPrice = 0;
  }
}

function handleAddRow(index?: number) {
  const nextRow = createRow(undefined, draftBatch.value);
  if (typeof index === "number") {
    draftBatch.value.rows.splice(index, 0, nextRow);
    return;
  }
  draftBatch.value.rows.push(nextRow);
}

function handleBulkAdd(count: number) {
  const normalizedCount = Math.max(1, Math.min(200, Math.floor(Number(count) || 0)));
  const newRows = Array.from({ length: normalizedCount }, () =>
    createRow(undefined, draftBatch.value)
  );
  draftBatch.value.rows.push(...newRows);
  MessagePlugin.success(`已新增 ${normalizedCount} 条明细`);
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

function mockQueryC5Price() {
  let updatedCount = 0;
  draftBatch.value.rows.forEach((row) => {
    if (!row.purchased || row.inGamePrice <= 0) return;
    const estimatedCost =
      row.inGamePrice * clampDiscount(row.discount || draftBatch.value.defaultDiscount);
    row.c5Price = round(Math.max(estimatedCost * 1.03, estimatedCost));
    updatedCount += 1;
  });

  if (!updatedCount) {
    MessagePlugin.info("没有可查询 C5 价格的已购明细");
    return;
  }
  MessagePlugin.success(`已更新 ${updatedCount} 条 C5 价格`);
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
