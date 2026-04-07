<template>
  <PageFrame
    :is-mobile="isMobile"
    desktop-body-class="overflow-y-auto"
    desktop-content-class="px-4 pt-3 pb-4"
    mobile-content-class="px-3 pt-3 pb-3"
  >
    <div ref="editorHostRef" class="relative flex min-h-full flex-col gap-4 bg-slate-50">
      <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div class="min-w-0 space-y-3">
            <div class="flex flex-wrap items-center gap-2 text-[11px] text-slate-500">
              <span
                class="rounded-full border border-sky-200 bg-sky-50 px-3 py-1 font-medium text-sky-700"
              >
                开箱记录原型
              </span>
              <span>主表看批次，弹窗编辑明细</span>
            </div>
            <div class="space-y-1">
              <h1 class="text-xl font-semibold tracking-tight text-slate-900">开箱记录</h1>
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
          <div class="mt-3 font-numeric text-2xl font-semibold" :class="card.valueClass">
            {{ card.value }}
          </div>
          <div class="mt-2 text-xs leading-5 text-slate-400">{{ card.hint }}</div>
        </article>
      </section>

      <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div class="flex items-center justify-between gap-3">
          <div>
            <h2 class="text-base font-semibold text-slate-900">历史批次</h2>
            <p class="mt-1 text-sm text-slate-500">
              主页面只看批次汇总。新增批次或编辑批次时，再进入弹窗维护明细。
            </p>
          </div>
          <t-tag theme="primary" variant="light-outline">共 {{ batches.length }} 批</t-tag>
        </div>

        <div class="mt-4 overflow-x-auto rounded-2xl border border-slate-200">
          <table class="min-w-full border-collapse text-sm">
            <thead class="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th class="px-4 py-3 font-medium">日期</th>
                <th class="px-4 py-3 font-medium">批次名称</th>
                <th class="px-4 py-3 font-medium">开箱数</th>
                <th class="px-4 py-3 font-medium">已购买</th>
                <th class="px-4 py-3 font-medium">总预估利润</th>
                <th class="px-4 py-3 font-medium">总实际利润</th>
                <th class="px-4 py-3 font-medium">状态</th>
                <th class="px-4 py-3 font-medium">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in batchSummaryRows"
                :key="item.batch.id"
                class="border-t border-slate-100"
              >
                <td class="px-4 py-3 text-slate-600">{{ formatDateText(item.batch.date) }}</td>
                <td class="px-4 py-3">
                  <div class="font-medium text-slate-900">{{ item.batch.name }}</div>
                  <div class="mt-1 text-xs text-slate-400">{{ item.batch.boxType || '未设置箱子类型' }}</div>
                </td>
                <td class="px-4 py-3 font-numeric text-slate-700">
                  {{ item.summary.totalCount }}
                </td>
                <td class="px-4 py-3 font-numeric text-slate-700">
                  {{ item.summary.purchasedCount }}
                </td>
                <td
                  class="px-4 py-3 font-numeric"
                  :class="profitClass(item.summary.totalEstimatedProfit)"
                >
                  {{ formatSignedCurrency(item.summary.totalEstimatedProfit) }}
                </td>
                <td
                  class="px-4 py-3 font-numeric"
                  :class="profitClass(item.summary.totalActualProfit)"
                >
                  {{ formatSignedCurrency(item.summary.totalActualProfit) }}
                </td>
                <td class="px-4 py-3">
                  <t-tag
                    :theme="historyStatusTheme(item.status)"
                    variant="light-outline"
                  >
                    {{ item.status }}
                  </t-tag>
                </td>
                <td class="px-4 py-3">
                  <div class="flex flex-wrap gap-2">
                    <t-button size="small" variant="outline" @click="openEditEditor(item.batch.id)">
                      编辑
                    </t-button>
                    <t-popconfirm
                      content="确认删除该批次吗？"
                      theme="danger"
                      :popup-props="{ attach: 'body' }"
                      @confirm="removeBatch(item.batch.id)"
                    >
                      <t-button
                        size="small"
                        theme="danger"
                        variant="outline"
                      >
                        删除
                      </t-button>
                    </t-popconfirm>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>

    <t-dialog
      v-model:visible="editorVisible"
      :close-btn="true"
      :close-on-overlay-click="false"
      :confirm-btn="null"
      :cancel-btn="null"
      :destroy-on-close="true"
      :attach="editorDialogAttach"
      :show-in-attached-element="!isMobile && !isEditorFullscreen"
      :footer="false"
      :header="editingBatchId ? (draftBatch.name || '编辑开箱批次') : '新增开箱批次'"
      :mode="isMobile || isEditorFullscreen ? 'full-screen' : 'modal'"
      :show-overlay="isEditorFullscreen || isMobile"
      :dialog-style="editorDialogStyle"
      :dialog-class-name="editorDialogClassName"
      placement="center"
    >
      <div class="flex min-h-0 flex-col overflow-hidden bg-slate-50" :class="editorBodyClass">
        <div class="border-b border-slate-200 bg-white px-2.5 py-2.5 sm:px-3 sm:py-2.5">
          <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <div class="flex flex-wrap items-center gap-1.5 text-[11px] text-slate-500">
              <span class="rounded-full bg-slate-100 px-2.5 py-1 text-slate-600">
                {{ editingBatchId ? '编辑批次' : '新增批次' }}
              </span>
              <span class="rounded-full bg-slate-100 px-2.5 py-1 text-slate-500">
                当前 {{ draftSummary.totalCount }} 条明细
              </span>
            </div>
            <div class="flex shrink-0 flex-wrap items-center justify-end gap-1.5">
              <t-button size="small" variant="outline" @click="toggleEditorFullscreen">
                {{ isEditorFullscreen ? '缩小' : '全屏' }}
              </t-button>
              <t-button size="small" variant="outline" @click="editorVisible = false">取消</t-button>
              <t-button size="small" theme="primary" @click="saveDraftBatch">保存批次</t-button>
            </div>
          </div>
        </div>

        <div class="min-h-0 flex-1 overflow-y-auto px-2.5 py-2.5 sm:px-3 sm:py-2.5">
          <div class="flex min-h-0 flex-col space-y-2.5">
            <section class="rounded-[4px] border border-slate-200 bg-white px-2.5 py-2.5 shadow-sm sm:px-3">
              <div class="flex flex-col gap-2.5">
                <div class="flex flex-col gap-2.5 lg:flex-row lg:items-end lg:justify-between">
                  <div class="min-w-0">
                    <div class="text-[11px] font-medium uppercase tracking-[0.14em] text-slate-400">批次信息</div>
                    <h3 class="mt-0.5 text-[13px] font-semibold text-slate-900 sm:text-sm">先录单据头，再进入明细录入</h3>
                    <p class="mt-0.5 text-[11px] leading-4.5 text-slate-500 sm:text-xs">
                      头信息尽量压在同一行，录完即可继续处理明细。
                    </p>
                  </div>
                  <div class="flex flex-wrap items-center gap-2 text-[11px] text-slate-500">
                    <span class="rounded-full border border-slate-200 bg-slate-50 px-3 py-1">头信息</span>
                    <span class="rounded-full border border-slate-200 bg-slate-50 px-3 py-1">
                      默认值会带入新明细
                    </span>
                  </div>
                </div>

                <div class="grid grid-cols-1 gap-2.5 md:grid-cols-2 xl:grid-cols-10 xl:gap-2.5">
                  <label class="space-y-1.5 xl:col-span-2">
                    <span class="text-[11px] font-medium text-slate-700 sm:text-xs">批次名称</span>
                    <t-input
                      v-model="draftBatch.name"
                      class="prototype-input"
                      clearable
                      maxlength="40"
                      placeholder="例如：2026-04-06 晚场开箱"
                      size="small"
                    />
                  </label>

                  <label class="space-y-1.5 sm:max-w-xs md:max-w-none xl:col-span-2">
                    <span class="text-[11px] font-medium text-slate-700 sm:text-xs">开箱日期</span>
                    <t-date-picker
                      v-model="draftBatch.date"
                      allow-input
                      clearable
                      class="prototype-date"
                      format="YYYY-MM-DD"
                      value-type="YYYY-MM-DD"
                      placeholder="选择日期"
                      size="small"
                    />
                  </label>

                  <label class="space-y-1.5 xl:col-span-2">
                    <span class="text-[11px] font-medium text-slate-700 sm:text-xs">箱子类型</span>
                    <t-input
                      v-model="draftBatch.boxType"
                      class="prototype-input"
                      clearable
                      maxlength="30"
                      placeholder="例如：创世终端机"
                      size="small"
                    />
                  </label>

                  <label class="space-y-1.5 sm:max-w-xs md:max-w-none xl:col-span-1">
                    <span class="text-[11px] font-medium text-slate-700 sm:text-xs">默认箱子成本</span>
                    <t-input-number
                      v-model="draftBatch.defaultBoxCost"
                      :decimal-places="2"
                      :min="0"
                      :step="0.1"
                      align="left"
                      class="prototype-number"
                      placeholder="0.00"
                      size="small"
                      suffix="¥"
                      theme="normal"
                    />
                  </label>

                  <label class="space-y-1.5 sm:max-w-xs md:max-w-none xl:col-span-1">
                    <span class="text-[11px] font-medium text-slate-700 sm:text-xs">默认折扣</span>
                    <t-input-number
                      v-model="draftBatch.defaultDiscount"
                      :decimal-places="2"
                      :max="1"
                      :min="0"
                      :step="0.01"
                      align="left"
                      class="prototype-number"
                      placeholder="0.72"
                      size="small"
                      theme="normal"
                    />
                  </label>

                  <label class="space-y-1.5 xl:col-span-2">
                    <span class="text-[11px] font-medium text-slate-700 sm:text-xs">备注</span>
                    <t-input
                      v-model="draftBatch.note"
                      class="prototype-input"
                      maxlength="120"
                      placeholder="记录这一批的来源、玩法、特别说明"
                      size="small"
                    />
                  </label>
                </div>
              </div>
            </section>

            <section class="flex min-h-0 flex-1 flex-col rounded-[4px] border border-slate-200 bg-white p-2.5 shadow-sm">
              <div class="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
                <div class="min-w-0">
                  <div class="text-[11px] font-medium uppercase tracking-[0.14em] text-slate-400">批次明细</div>
                  <h3 class="mt-0.5 text-[13px] font-semibold text-slate-900 sm:text-sm">录入动作、辅助动作和结果判断放在同一工作带</h3>
                  <p class="mt-0.5 text-[11px] leading-4.5 text-slate-500 sm:text-xs">
                    未购买时直接记箱损；已购买时按游戏售价和折扣计算实际花费，再结合 C5 现价和卖出到账算利润。
                  </p>
                </div>
                <div class="flex flex-wrap items-center gap-2 text-[11px] text-slate-500">
                  <span class="rounded-full border border-slate-200 bg-slate-50 px-3 py-1">桌面端表格</span>
                  <span class="rounded-full border border-slate-200 bg-slate-50 px-3 py-1">移动端卡片</span>
                </div>
              </div>

              <div class="mt-2.5 rounded-xl border border-slate-200 bg-slate-50/80 p-2 sm:p-2.5">
                <div class="grid gap-2 xl:grid-cols-[minmax(0,1.55fr)_minmax(13rem,0.85fr)]">
                  <div class="min-w-0">
                    <div class="text-[11px] font-medium uppercase tracking-[0.14em] text-slate-400">录入动作</div>
                    <div class="mt-1.5 flex flex-wrap items-center gap-1.5">
                      <t-button size="small" variant="outline" class="touch-manipulation" @click="handleAddRow()">
                        新增一行
                      </t-button>
                      <t-button size="small" variant="outline" class="touch-manipulation" @click="handleBulkAdd(10)">
                        +10
                      </t-button>
                      <t-button size="small" variant="outline" class="touch-manipulation" @click="handleBulkAdd(50)">
                        +50
                      </t-button>
                      <div class="flex flex-wrap items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-2 py-1.5">
                        <label class="flex items-center gap-2">
                          <span class="text-[11px] text-slate-500 sm:text-sm">自定义新增</span>
                          <t-input-number
                            v-model="bulkAddCount"
                            :decimal-places="0"
                            :min="1"
                            :step="1"
                            align="center"
                            class="prototype-number w-20"
                            placeholder="数量"
                            theme="normal"
                          />
                        </label>
                        <t-button size="small" theme="primary" @click="handleBulkAdd()">添加</t-button>
                      </div>
                    </div>
                  </div>

                  <div class="min-w-0">
                    <div class="text-[11px] font-medium uppercase tracking-[0.14em] text-slate-400">辅助动作</div>
                    <div class="mt-1.5 flex flex-wrap items-center gap-1.5 xl:justify-end">
                      <t-button size="small" variant="outline" @click="applyDefaultsToEmptyRows">
                        应用默认值
                      </t-button>
                      <t-button size="small" variant="outline" @click="mockQueryC5Price">查询 C5 价格</t-button>
                    </div>
                  </div>
                </div>

                <div class="mt-2 grid gap-1.5 sm:grid-cols-2 xl:grid-cols-4">
                  <article
                    v-for="card in draftSummaryCards"
                    :key="card.label"
                    class="rounded-lg bg-white/90 px-2.5 py-2 ring-1 ring-inset ring-slate-200/80"
                  >
                    <div class="text-[11px] font-medium text-slate-500">{{ card.label }}</div>
                    <div class="mt-1 font-numeric text-[13px] font-semibold sm:text-[15px]" :class="card.valueClass">
                      {{ card.value }}
                    </div>
                    <div class="mt-1 text-[11px] leading-5 text-slate-400">{{ card.hint }}</div>
                  </article>
                </div>
              </div>

              <div
                v-if="!isMobile"
                class="mt-3 min-h-0 flex-1 overflow-hidden rounded-xl border border-slate-200"
              >
                <div class="h-full overflow-auto scrollbar-stable">
                  <table class="min-w-[1260px] table-fixed border-collapse bg-white text-xs">
                    <colgroup>
                      <col style="width: 3rem" />
                      <col style="width: 6rem" />
                      <col style="width: 6.5rem" />
                      <col style="width: 12rem" />
                      <col style="width: 6rem" />
                      <col style="width: 5rem" />
                      <col style="width: 6rem" />
                      <col style="width: 7rem" />
                      <col style="width: 6rem" />
                      <col style="width: 7rem" />
                      <col style="width: 10rem" />
                      <col style="width: 5rem" />
                    </colgroup>
                    <thead class="bg-slate-50 text-[11px] uppercase tracking-[0.12em] text-slate-500">
                      <tr>
                        <th class="w-12 px-2 py-2 text-left font-medium whitespace-nowrap">#</th>
                        <th class="w-24 px-2 py-2 text-left font-medium whitespace-nowrap">箱子成本</th>
                        <th class="w-[6.5rem] px-2 py-2 text-left font-medium whitespace-nowrap">购买状态</th>
                        <th class="w-48 px-2 py-2 text-left font-medium whitespace-nowrap">枪名称</th>
                        <th class="w-24 px-2 py-2 text-left font-medium whitespace-nowrap">游戏售价</th>
                        <th class="w-20 px-2 py-2 text-left font-medium whitespace-nowrap">折扣</th>
                        <th class="w-24 px-2 py-2 text-left font-medium whitespace-nowrap">C5 现价</th>
                        <th class="w-28 px-2 py-2 text-left font-medium whitespace-nowrap text-slate-400">预估结果</th>
                        <th class="w-24 px-2 py-2 text-left font-medium whitespace-nowrap">卖出价</th>
                        <th class="w-28 px-2 py-2 text-left font-medium whitespace-nowrap text-slate-400">实际结果</th>
                        <th class="w-40 px-2 py-2 text-left font-medium whitespace-nowrap">备注</th>
                        <th class="w-20 px-2 py-2 text-left font-medium whitespace-nowrap">操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr
                        v-for="({ row, metrics }, index) in draftRowEntries"
                        :key="row.id"
                        class="border-t border-slate-100 align-top transition-colors hover:bg-slate-50/40"
                      >
                        <td class="px-2 py-2">
                          <div class="font-numeric text-[11px] font-semibold leading-4 text-slate-600">{{ index + 1 }}</div>
                        </td>
                        <td class="px-2 py-2">
                          <t-input-number
                            v-model="row.boxCost"
                            :decimal-places="2"
                            :min="0"
                            :step="0.1"
                            align="right"
                            class="prototype-number"
                            placeholder="0.00"
                            size="small"
                            theme="normal"
                          />
                        </td>
                        <td class="px-2 py-2">
                          <div class="grid grid-cols-2 gap-1">
                            <button
                              type="button"
                              class="inline-flex h-7 items-center justify-center rounded-md border px-1.5 text-[11px] font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-500/60 focus-visible:ring-offset-1"
                              :class="row.purchased ? 'border-emerald-200 bg-emerald-50 text-emerald-700' : 'border-slate-200 bg-white text-slate-500 hover:border-slate-300'"
                              @click="setPurchaseState(row, true)"
                            >
                              买了
                            </button>
                            <button
                              type="button"
                              class="inline-flex h-7 items-center justify-center rounded-md border px-1.5 text-[11px] font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-500/60 focus-visible:ring-offset-1"
                              :class="!row.purchased ? 'border-rose-200 bg-rose-50 text-rose-700' : 'border-slate-200 bg-white text-slate-500 hover:border-slate-300'"
                              @click="setPurchaseState(row, false)"
                            >
                              没买
                            </button>
                          </div>
                        </td>
                        <td class="px-2 py-2">
                          <t-input
                            v-model="row.weaponName"
                            :disabled="!row.purchased"
                            class="prototype-input"
                            clearable
                            maxlength="40"
                            placeholder="例如：AK-47 | 血腥运动"
                            size="small"
                          />
                        </td>
                        <td class="px-2 py-2">
                          <t-input-number
                            v-model="row.inGamePrice"
                            :decimal-places="2"
                            :disabled="!row.purchased"
                            :min="0"
                            :step="0.1"
                            align="right"
                            class="prototype-number"
                            placeholder="0.00"
                            size="small"
                            theme="normal"
                          />
                        </td>
                        <td class="px-2 py-2">
                          <t-input-number
                            v-model="row.discount"
                            :decimal-places="2"
                            :disabled="!row.purchased"
                            :max="1"
                            :min="0"
                            :step="0.01"
                            align="right"
                            class="prototype-number"
                            placeholder="0.72"
                            size="small"
                            theme="normal"
                          />
                        </td>
                        <td class="px-2 py-2">
                          <t-input-number
                            v-model="row.c5Price"
                            :decimal-places="2"
                            :disabled="!row.purchased"
                            :min="0"
                            :step="0.1"
                            align="right"
                            class="prototype-number"
                            placeholder="0.00"
                            size="small"
                            theme="normal"
                          />
                        </td>
                        <td class="px-2 py-2">
                          <div class="space-y-0.5 leading-4">
                            <div class="flex items-center justify-between gap-2 text-[10px] text-slate-400">
                              <span>花费</span>
                              <span class="font-numeric text-[11px] text-slate-500">
                                {{ formatCurrency(metrics.actualCost) }}
                              </span>
                            </div>
                            <div
                              class="font-numeric text-sm font-semibold leading-5"
                              :class="profitClass(metrics.estimatedProfit)"
                            >
                              {{ formatSignedCurrency(metrics.estimatedProfit) }}
                            </div>
                            <div class="font-numeric text-[10px] text-slate-400">
                              利润率 {{ formatPercent(metrics.estimatedProfitRate) }}
                            </div>
                          </div>
                        </td>
                        <td class="px-2 py-2">
                          <t-input-number
                            v-model="row.actualSellPrice"
                            :decimal-places="2"
                            :disabled="!row.purchased"
                            :min="0"
                            :step="0.1"
                            align="right"
                            class="prototype-number"
                            placeholder="0.00"
                            size="small"
                            theme="normal"
                          />
                        </td>
                        <td class="px-2 py-2">
                          <div class="space-y-0.5 leading-4">
                            <div class="flex items-center justify-between gap-2 text-[10px] text-slate-400">
                              <span>到账</span>
                              <span class="font-numeric text-[11px] text-slate-500">
                                {{ formatCurrency(row.actualNetIncome) }}
                              </span>
                            </div>
                            <div
                              class="font-numeric text-sm font-semibold leading-5"
                              :class="profitClass(metrics.actualProfit ?? 0)"
                            >
                              {{ formatActualProfit(metrics.actualProfit) }}
                            </div>
                            <div class="font-numeric text-[10px] text-slate-400">
                              利润率 {{ formatPercent(metrics.actualProfitRate, true) }}
                            </div>
                          </div>
                        </td>
                        <td class="px-2 py-2">
                          <t-input
                            v-model="row.note"
                            class="prototype-input"
                            clearable
                            maxlength="50"
                            placeholder="补充判断或出售说明"
                            size="small"
                          />
                        </td>
                        <td class="px-2 py-2">
                          <div class="flex flex-col items-start gap-0.5">
                            <t-button
                              size="small"
                              variant="text"
                              class="!h-7 !px-1 text-slate-600"
                              @click="handleAddRow(index + 1)"
                            >
                              插入
                            </t-button>
                            <t-popconfirm
                              content="确认删除该条明细吗？"
                              theme="danger"
                              :popup-props="{ attach: 'body' }"
                              @confirm="handleRemoveRow(row.id)"
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
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              <div v-else class="mt-4 space-y-3">
                <article
                  v-for="({ row, metrics }, index) in draftRowEntries"
                  :key="row.id"
                  class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm"
                >
                  <div class="flex items-start justify-between gap-3 border-b border-slate-100 bg-slate-50/80 px-4 py-3">
                    <div class="min-w-0">
                      <div class="text-[11px] font-medium uppercase tracking-[0.14em] text-slate-400">明细 {{ index + 1 }}</div>
                      <div class="mt-1 text-sm font-semibold text-slate-900">{{ row.weaponName || '未填写枪名称' }}</div>
                      <div class="mt-1 text-[11px] text-slate-500">
                        {{ row.purchased ? '已购买，跟踪预估与实际收益' : '未购买，直接按箱子成本亏损' }}
                      </div>
                    </div>
                    <t-tag :theme="row.purchased ? 'success' : 'danger'" variant="light-outline">
                      {{ row.purchased ? '已购买' : '未购买' }}
                    </t-tag>
                  </div>

                  <div class="p-4">
                    <div class="grid grid-cols-1 gap-3">
                      <div class="grid grid-cols-2 gap-3">
                        <label class="space-y-1.5">
                          <span class="text-[11px] font-medium text-slate-600">箱子成本</span>
                          <t-input-number
                            v-model="row.boxCost"
                            :decimal-places="2"
                            :min="0"
                            :step="0.1"
                            align="left"
                            class="prototype-number"
                            placeholder="0.00"
                            theme="normal"
                          />
                        </label>
                        <div class="space-y-1.5">
                          <span class="text-[11px] font-medium text-slate-600">购买状态</span>
                          <div class="flex gap-2">
                            <button
                              type="button"
                              class="inline-flex h-10 flex-1 touch-manipulation items-center justify-center rounded-[4px] border text-sm font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-500/60 focus-visible:ring-offset-1"
                              :class="row.purchased ? 'border-emerald-200 bg-emerald-50 text-emerald-700' : 'border-slate-200 bg-white text-slate-500'"
                              @click="setPurchaseState(row, true)"
                            >
                              买了
                            </button>
                            <button
                              type="button"
                              class="inline-flex h-10 flex-1 touch-manipulation items-center justify-center rounded-[4px] border text-sm font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-500/60 focus-visible:ring-offset-1"
                              :class="!row.purchased ? 'border-rose-200 bg-rose-50 text-rose-700' : 'border-slate-200 bg-white text-slate-500'"
                              @click="setPurchaseState(row, false)"
                            >
                              没买
                            </button>
                          </div>
                        </div>
                      </div>

                      <label class="space-y-1.5">
                        <span class="text-[11px] font-medium text-slate-600">枪名称</span>
                        <t-input
                          v-model="row.weaponName"
                          :disabled="!row.purchased"
                          class="prototype-input"
                          clearable
                          maxlength="40"
                          placeholder="例如：M4A1-S | 印花集"
                        />
                      </label>

                      <div class="grid grid-cols-2 gap-3">
                        <label class="space-y-1.5">
                          <span class="text-[11px] font-medium text-slate-600">游戏售价</span>
                          <t-input-number
                            v-model="row.inGamePrice"
                            :decimal-places="2"
                            :disabled="!row.purchased"
                            :min="0"
                            :step="0.1"
                            align="left"
                            class="prototype-number"
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
                            class="prototype-number"
                            placeholder="0.72"
                            theme="normal"
                          />
                        </label>
                      </div>

                      <div class="grid grid-cols-2 gap-3">
                        <label class="space-y-1.5">
                          <span class="text-[11px] font-medium text-slate-600">C5 现价</span>
                          <t-input-number
                            v-model="row.c5Price"
                            :decimal-places="2"
                            :disabled="!row.purchased"
                            :min="0"
                            :step="0.1"
                            align="left"
                            class="prototype-number"
                            placeholder="0.00"
                            theme="normal"
                          />
                        </label>
                        <div class="rounded-[4px] border border-slate-200/80 bg-slate-50 p-3">
                          <div class="text-[11px] text-slate-500">实际花费</div>
                          <div class="font-numeric mt-2 text-base font-semibold text-slate-900">
                            {{ formatCurrency(metrics.actualCost) }}
                          </div>
                        </div>
                      </div>

                      <div class="grid grid-cols-2 gap-3">
                        <label class="space-y-1.5">
                          <span class="text-[11px] font-medium text-slate-600">卖出价</span>
                          <t-input-number
                            v-model="row.actualSellPrice"
                            :decimal-places="2"
                            :disabled="!row.purchased"
                            :min="0"
                            :step="0.1"
                            align="left"
                            class="prototype-number"
                            placeholder="0.00"
                            theme="normal"
                          />
                        </label>
                        <label class="space-y-1.5">
                          <span class="text-[11px] font-medium text-slate-600">实际到账</span>
                          <t-input-number
                            v-model="row.actualNetIncome"
                            :decimal-places="2"
                            :disabled="!row.purchased"
                            :min="0"
                            :step="0.1"
                            align="left"
                            class="prototype-number"
                            placeholder="0.00"
                            theme="normal"
                          />
                        </label>
                      </div>

                      <div class="grid grid-cols-2 gap-3">
                        <div class="rounded-[4px] border border-slate-200/80 bg-slate-50 p-3">
                          <div class="text-[11px] text-slate-500">预估结果</div>
                          <div
                            class="font-numeric mt-2 text-base font-semibold"
                            :class="profitClass(metrics.estimatedProfit)"
                          >
                            {{ formatSignedCurrency(metrics.estimatedProfit) }}
                          </div>
                          <div class="font-numeric mt-1 text-xs text-slate-400">
                            利润率 {{ formatPercent(metrics.estimatedProfitRate) }}
                          </div>
                        </div>
                        <div class="rounded-[4px] border border-slate-200/80 bg-slate-50 p-3">
                          <div class="text-[11px] text-slate-500">实际结果</div>
                          <div
                            class="font-numeric mt-2 text-base font-semibold"
                            :class="profitClass(metrics.actualProfit ?? 0)"
                          >
                            {{ formatActualProfit(metrics.actualProfit) }}
                          </div>
                          <div class="font-numeric mt-1 text-xs text-slate-400">
                            利润率 {{ formatPercent(metrics.actualProfitRate, true) }}
                          </div>
                        </div>
                      </div>

                      <label class="space-y-1.5">
                        <span class="text-[11px] font-medium text-slate-600">备注</span>
                        <t-input
                          v-model="row.note"
                          class="prototype-input"
                          clearable
                          maxlength="50"
                          placeholder="补充理由、出售节奏、挂单情况"
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
import type { ComponentPublicInstance } from "vue";
import { useWindowSize } from "@vueuse/core";
import { MessagePlugin } from "tdesign-vue-next";
import type { AttachNode, Styles } from "tdesign-vue-next";
import PageFrame from "@/components/PageFrame.vue";

interface UnboxRow {
  id: string;
  boxCost: number;
  purchased: boolean;
  weaponName: string;
  inGamePrice: number;
  discount: number;
  c5Price: number;
  actualSellPrice: number;
  actualNetIncome: number;
  note: string;
}

interface UnboxBatch {
  id: string;
  name: string;
  date: string;
  boxType: string;
  defaultBoxCost: number;
  defaultDiscount: number;
  note: string;
  rows: UnboxRow[];
}

type BatchStatus = "未结算" | "部分结算" | "已结算";

interface RowMetrics {
  actualCost: number;
  estimatedProfit: number;
  estimatedProfitRate: number;
  actualProfit: number | null;
  actualProfitRate: number | null;
}

interface BatchSummary {
  totalCount: number;
  purchasedCount: number;
  notPurchasedCount: number;
  purchaseRate: number;
  totalBoxCost: number;
  totalInGamePrice: number;
  totalActualCost: number;
  totalC5Price: number;
  totalEstimatedProfit: number;
  estimatedProfitRate: number;
  settledCount: number;
  unsettledCount: number;
  totalActualProfit: number;
  actualProfitRate: number;
}

interface SummaryCard {
  label: string;
  value: string;
  hint: string;
  valueClass: string;
}

interface BatchSummaryRow {
  batch: UnboxBatch;
  summary: BatchSummary;
  status: BatchStatus;
}

interface DraftRowEntry {
  row: UnboxRow;
  metrics: RowMetrics;
}

const currencyFormatter = new Intl.NumberFormat("zh-CN", {
  style: "currency",
  currency: "CNY",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const percentFormatter = new Intl.NumberFormat("zh-CN", {
  style: "percent",
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

type EditorHostElement = HTMLElement | ComponentPublicInstance | null;

const editorHostRef = ref<EditorHostElement>(null);
const resolveEditorHost = (): HTMLElement => {
  const rawHost = editorHostRef.value;
  if (rawHost instanceof HTMLElement) return rawHost;
  const componentRoot = rawHost?.$el;
  return componentRoot instanceof HTMLElement ? componentRoot : document.body;
};
const editorDialogAttach = computed<AttachNode>(() => {
  if (isMobile.value || isEditorFullscreen.value) return "body";
  return resolveEditorHost;
});

const editorVisible = ref(false);
const isEditorFullscreen = ref(false);
const editingBatchId = ref<string | null>(null);
const bulkAddCount = ref<number | string>(20);

const editorDialogClassName = computed(() => {
  const modeClass = isEditorFullscreen.value
    ? "unbox-editor-dialog--fullscreen h-full w-full max-w-none"
    : "unbox-editor-dialog--contained !absolute !inset-x-0 !top-0 !bottom-0 !m-0 w-full max-w-none";
  return `unbox-editor-dialog ${modeClass}`;
});

const editorDialogStyle = computed((): Styles | undefined => {
  if (isMobile.value) return undefined;
  if (isEditorFullscreen.value) {
    return {
      top: 0,
      width: "100%",
      maxWidth: "none",
      height: "100%",
      minHeight: "100%",
      padding: 0,
    } as Styles;
  }
  return {
    top: 0,
    width: "100%",
    maxWidth: "none",
    height: "100%",
    minHeight: "100%",
    padding: 0,
    boxShadow: "0 10px 30px rgba(15, 23, 42, 0.12)",
  } as Styles;
});

const editorBodyClass = computed(() => {
  if (isMobile.value || isEditorFullscreen.value) {
    return "h-full min-h-0 overflow-hidden overscroll-contain [scrollbar-gutter:stable]";
  }
  return "min-h-full overflow-visible overscroll-contain [scrollbar-gutter:stable]";
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

const clampDiscount = (value: number) => {
  const safeValue = Number.isFinite(value) ? value : 0;
  return round(Math.min(Math.max(safeValue, 0), 1), 2);
};

const createRow = (defaults?: Partial<UnboxRow>, batch?: Pick<UnboxBatch, "defaultBoxCost" | "defaultDiscount">): UnboxRow => ({
  id: createId(),
  boxCost: defaults?.boxCost ?? batch?.defaultBoxCost ?? 13.5,
  purchased: defaults?.purchased ?? true,
  weaponName: defaults?.weaponName ?? "",
  inGamePrice: defaults?.inGamePrice ?? 0,
  discount: defaults?.discount ?? batch?.defaultDiscount ?? 0.72,
  c5Price: defaults?.c5Price ?? 0,
  actualSellPrice: defaults?.actualSellPrice ?? 0,
  actualNetIncome: defaults?.actualNetIncome ?? 0,
  note: defaults?.note ?? "",
});

const createBlankBatch = (): UnboxBatch => ({
  id: createId(),
  name: "新的开箱批次",
  date: "2026-04-06",
  boxType: "创世终端机",
  defaultBoxCost: 13.5,
  defaultDiscount: 0.72,
  note: "",
  rows: [createRow(), createRow({ purchased: false }), createRow()],
});

const cloneBatch = (batch: UnboxBatch): UnboxBatch => JSON.parse(JSON.stringify(batch)) as UnboxBatch;

const draftBatch = ref<UnboxBatch>(createBlankBatch());

const batches = ref<UnboxBatch[]>([
  {
    id: createId(),
    name: "2026-04-06 晚场开箱",
    date: "2026-04-06",
    boxType: "创世终端机",
    defaultBoxCost: 13.5,
    defaultDiscount: 0.72,
    note: "当前为纯前端原型，后续可接 C5 查询与批次保存。",
    rows: [
      createRow(
        {
          weaponName: "AK-47 | 血腥运动",
          inGamePrice: 85,
          discount: 0.72,
          c5Price: 69.8,
          actualSellPrice: 77,
          actualNetIncome: 74.5,
          note: "首批挂单已成交",
        },
        { defaultBoxCost: 13.5, defaultDiscount: 0.72 }
      ),
      createRow(
        {
          purchased: false,
          note: "价差不够，直接放弃",
        },
        { defaultBoxCost: 13.5, defaultDiscount: 0.72 }
      ),
      createRow(
        {
          weaponName: "AWP | 二西莫夫",
          inGamePrice: 132,
          discount: 0.7,
          c5Price: 105.6,
          note: "待观察两天后再卖",
        },
        { defaultBoxCost: 13.5, defaultDiscount: 0.72 }
      ),
    ],
  },
  {
    id: createId(),
    name: "2026-04-03 下午开箱",
    date: "2026-04-03",
    boxType: "创世终端机",
    defaultBoxCost: 12.8,
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
          actualNetIncome: 48.2,
          note: "已小幅止盈",
        },
        { defaultBoxCost: 12.8, defaultDiscount: 0.71 }
      ),
      createRow(
        {
          purchased: false,
          note: "这一箱直接记损",
        },
        { defaultBoxCost: 12.8, defaultDiscount: 0.71 }
      ),
      createRow(
        {
          weaponName: "USP-S | 杀出重围",
          inGamePrice: 44,
          discount: 0.71,
          c5Price: 31.2,
          actualSellPrice: 0,
          actualNetIncome: 0,
          note: "还没卖",
        },
        { defaultBoxCost: 12.8, defaultDiscount: 0.71 }
      ),
    ],
  },
  {
    id: createId(),
    name: "2026-04-01 深夜冲刺",
    date: "2026-04-01",
    boxType: "创世终端机",
    defaultBoxCost: 12.2,
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
          actualNetIncome: 86.4,
          note: "当天卖出",
        },
        { defaultBoxCost: 12.2, defaultDiscount: 0.69 }
      ),
      createRow(
        {
          weaponName: "AWP | 渐变之色",
          inGamePrice: 136,
          discount: 0.68,
          c5Price: 109.4,
          note: "继续观察",
        },
        { defaultBoxCost: 12.2, defaultDiscount: 0.69 }
      ),
      createRow(
        {
          purchased: false,
          note: "未达到收益线",
        },
        { defaultBoxCost: 12.2, defaultDiscount: 0.69 }
      ),
    ],
  },
]);

const getRowMetrics = (row: UnboxRow): RowMetrics => {
  const actualCost = row.purchased ? round(row.inGamePrice * clampDiscount(row.discount)) : round(row.boxCost);

  if (!row.purchased) {
    return {
      actualCost,
      estimatedProfit: round(-row.boxCost),
      estimatedProfitRate: -1,
      actualProfit: round(-row.boxCost),
      actualProfitRate: -1,
    };
  }

  const estimatedProfit = round(row.c5Price - actualCost);
  const estimatedProfitRate = actualCost > 0 ? estimatedProfit / actualCost : 0;
  const hasActualIncome = row.actualNetIncome > 0;
  const actualProfit = hasActualIncome ? round(row.actualNetIncome - actualCost) : null;
  const actualProfitRate = hasActualIncome && actualCost > 0 && actualProfit !== null ? actualProfit / actualCost : null;

  return {
    actualCost,
    estimatedProfit,
    estimatedProfitRate,
    actualProfit,
    actualProfitRate,
  };
};

const getBatchSummary = (batch: UnboxBatch): BatchSummary => {
  let purchasedCount = 0;
  let totalBoxCost = 0;
  let totalInGamePrice = 0;
  let totalActualCost = 0;
  let totalC5Price = 0;
  let totalEstimatedProfit = 0;
  let totalActualProfit = 0;
  let settledCount = 0;

  batch.rows.forEach((item) => {
    const metrics = getRowMetrics(item);
    totalBoxCost += item.boxCost;
    totalActualCost += metrics.actualCost;
    totalEstimatedProfit += metrics.estimatedProfit;
    totalActualProfit += metrics.actualProfit ?? 0;

    if (!item.purchased) return;
    purchasedCount += 1;
    totalInGamePrice += item.inGamePrice;
    totalC5Price += item.c5Price;
    if (item.actualNetIncome > 0) settledCount += 1;
  });

  const totalCount = batch.rows.length;
  const notPurchasedCount = totalCount - purchasedCount;
  const roundedTotalActualCost = round(totalActualCost);
  const roundedTotalEstimatedProfit = round(totalEstimatedProfit);
  const roundedTotalActualProfit = round(totalActualProfit);

  return {
    totalCount,
    purchasedCount,
    notPurchasedCount,
    purchaseRate: totalCount > 0 ? purchasedCount / totalCount : 0,
    totalBoxCost: round(totalBoxCost),
    totalInGamePrice: round(totalInGamePrice),
    totalActualCost: roundedTotalActualCost,
    totalC5Price: round(totalC5Price),
    totalEstimatedProfit: roundedTotalEstimatedProfit,
    estimatedProfitRate: roundedTotalActualCost > 0 ? roundedTotalEstimatedProfit / roundedTotalActualCost : 0,
    settledCount,
    unsettledCount: purchasedCount - settledCount,
    totalActualProfit: roundedTotalActualProfit,
    actualProfitRate: roundedTotalActualCost > 0 ? roundedTotalActualProfit / roundedTotalActualCost : 0,
  };
};

const getBatchStatus = (summary: BatchSummary): BatchStatus => {
  if (summary.unsettledCount === 0) return "已结算";
  if (summary.settledCount === 0) return "未结算";
  return "部分结算";
};

const batchSummaryRows = computed<BatchSummaryRow[]>(() =>
  batches.value.map((batch) => {
    const summary = getBatchSummary(batch);
    return {
      batch,
      summary,
      status: getBatchStatus(summary),
    };
  })
);

const pageSummary = computed(() => {
  return batchSummaryRows.value.reduce(
    (result, item) => {
      result.totalBatches += 1;
      result.totalBoxes += item.summary.totalCount;
      result.totalEstimatedProfit += item.summary.totalEstimatedProfit;
      result.totalActualProfit += item.summary.totalActualProfit;
      return result;
    },
    {
      totalBatches: 0,
      totalBoxes: 0,
      totalEstimatedProfit: 0,
      totalActualProfit: 0,
    }
  );
});

const pageSummaryCards = computed<SummaryCard[]>(() => [
  {
    label: "批次数量",
    value: `${pageSummary.value.totalBatches}`,
    hint: `累计开箱 ${pageSummary.value.totalBoxes} 个`,
    valueClass: "text-slate-900",
  },
  {
    label: "当前总预估利润",
    value: formatSignedCurrency(pageSummary.value.totalEstimatedProfit),
    hint: "主表所有批次按当前 C5 预估汇总",
    valueClass: profitClass(pageSummary.value.totalEstimatedProfit),
  },
  {
    label: "当前总实际利润",
    value: formatSignedCurrency(pageSummary.value.totalActualProfit),
    hint: "主表所有批次按实际到账汇总",
    valueClass: profitClass(pageSummary.value.totalActualProfit),
  },
  {
    label: "操作方式",
    value: "主表 + 弹窗",
    hint: "主页面看结果，弹窗内维护整批明细",
    valueClass: "text-slate-900",
  },
]);

const draftRowEntries = computed<DraftRowEntry[]>(() =>
  draftBatch.value.rows.map((row) => ({
    row,
    metrics: getRowMetrics(row),
  }))
);

const draftSummary = computed(() => getBatchSummary(draftBatch.value));

const draftSummaryCards = computed<SummaryCard[]>(() => [
  {
    label: "开箱总数 / 已购买",
    value: `${draftSummary.value.totalCount} / ${draftSummary.value.purchasedCount}`,
    hint: `未购买 ${draftSummary.value.notPurchasedCount} 个 · 购买率 ${formatPercent(draftSummary.value.purchaseRate)}`,
    valueClass: "text-slate-900",
  },
  {
    label: "箱子总成本 / 实际花费",
    value: `${formatCurrency(draftSummary.value.totalBoxCost)} / ${formatCurrency(draftSummary.value.totalActualCost)}`,
    hint: `游戏内总价 ${formatCurrency(draftSummary.value.totalInGamePrice)}`,
    valueClass: "text-slate-900",
  },
  {
    label: "总预估利润",
    value: formatSignedCurrency(draftSummary.value.totalEstimatedProfit),
    hint: `C5 回收 ${formatCurrency(draftSummary.value.totalC5Price)} · 利润率 ${formatPercent(draftSummary.value.estimatedProfitRate)}`,
    valueClass: profitClass(draftSummary.value.totalEstimatedProfit),
  },
  {
    label: "总实际利润",
    value: formatSignedCurrency(draftSummary.value.totalActualProfit),
    hint: `已结算 ${draftSummary.value.settledCount} 条 · 未结算 ${draftSummary.value.unsettledCount} 条 · 利润率 ${formatPercent(draftSummary.value.actualProfitRate)}`,
    valueClass: profitClass(draftSummary.value.totalActualProfit),
  },
]);

function formatCurrency(value: number) {
  return currencyFormatter.format(round(value));
}

function formatSignedCurrency(value: number) {
  const normalized = round(value);
  const prefix = normalized > 0 ? "+" : "";
  return `${prefix}${currencyFormatter.format(normalized)}`;
}

function formatActualProfit(value: number | null) {
  if (value === null) return "待补录";
  return formatSignedCurrency(value);
}

function formatPercent(value: number | null, allowPending = false) {
  if (value === null) return allowPending ? "待补录" : "0.00%";
  return percentFormatter.format(value);
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
  return "text-slate-900";
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

function openCreateEditor() {
  editingBatchId.value = null;
  draftBatch.value = createBlankBatch();
  bulkAddCount.value = 20;
  isEditorFullscreen.value = false;
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
  bulkAddCount.value = 20;
  isEditorFullscreen.value = false;
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
    row.weaponName = "";
    row.inGamePrice = 0;
    row.discount = draftBatch.value.defaultDiscount;
    row.c5Price = 0;
    row.actualSellPrice = 0;
    row.actualNetIncome = 0;
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

function handleRemoveRow(id: string) {
  if (draftBatch.value.rows.length === 1) {
    MessagePlugin.warning("至少保留一条明细，避免批次编辑区为空");
    return;
  }
  draftBatch.value.rows = draftBatch.value.rows.filter((item) => item.id !== id);
}

function parseBulkCount() {
  const numericValue = Number(bulkAddCount.value);
  if (!Number.isFinite(numericValue) || numericValue <= 0) return 0;
  return Math.min(Math.floor(numericValue), 300);
}

function handleBulkAdd(count?: number) {
  const target = count ?? parseBulkCount();
  if (!target) {
    MessagePlugin.warning("请输入有效的批量新增数量");
    return;
  }
  draftBatch.value.rows.push(...Array.from({ length: target }, () => createRow(undefined, draftBatch.value)));
  MessagePlugin.success(`已新增 ${target} 条明细`);
}

function applyDefaultsToEmptyRows() {
  let updated = 0;
  draftBatch.value.rows.forEach((row) => {
    if (!row.boxCost) {
      row.boxCost = draftBatch.value.defaultBoxCost;
      updated += 1;
    }
    if (row.purchased && !row.discount) {
      row.discount = draftBatch.value.defaultDiscount;
      updated += 1;
    }
  });
  MessagePlugin.success(updated ? `已更新 ${updated} 处默认值` : "空行已是最新默认值");
}

function mockQueryC5Price() {
  let updated = 0;
  draftBatch.value.rows.forEach((row, index) => {
    if (!row.purchased || !row.inGamePrice) return;
    const ratio = 0.72 + ((index % 5) * 0.04);
    row.c5Price = round(row.inGamePrice * ratio);
    updated += 1;
  });
  MessagePlugin.success(updated ? `已为 ${updated} 条已购买记录填入模拟 C5 价格` : "暂无可查询的已购买记录");
}
</script>

<style scoped>
:deep(.unbox-editor-dialog) {
  overflow: visible;
}

:deep(.unbox-editor-dialog .t-dialog__body) {
  height: auto;
  min-height: 0;
  padding: 0;
  overflow: visible;
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

:deep(.unbox-editor-dialog--fullscreen) {
  height: 100%;
  border-radius: 0;
}

:deep(.unbox-editor-dialog--contained) {
  height: 100%;
  min-height: 100%;
  border-radius: 1.25rem;
}

:deep(.prototype-input),
:deep(.prototype-date),
:deep(.prototype-number),
:deep(.prototype-textarea) {
  width: 100%;
}

:deep(.prototype-input .t-input__wrap),
:deep(.prototype-date .t-input__wrap),
:deep(.prototype-number .t-input-number__wrap),
:deep(.prototype-textarea .t-textarea__inner) {
  border-radius: 0.9rem;
  border-color: rgb(226 232 240);
  box-shadow: none;
}

:deep(.prototype-input .t-input__wrap:hover),
:deep(.prototype-date .t-input__wrap:hover),
:deep(.prototype-number .t-input-number__wrap:hover),
:deep(.prototype-textarea .t-textarea__inner:hover) {
  border-color: rgb(148 163 184);
}

:deep(.prototype-input .t-is-focused),
:deep(.prototype-date .t-is-focused),
:deep(.prototype-number .t-is-focused),
:deep(.prototype-textarea .t-is-focused) {
  border-color: rgb(14 165 233);
  box-shadow: 0 0 0 3px rgb(14 165 233 / 0.12);
}

</style>
