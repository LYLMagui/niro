<template>
  <PageFrame
    :is-mobile="isMobile"
    desktop-outer-class="!p-0"
    desktop-body-class="overflow-y-auto"
    desktop-content-class="px-4 pt-0 pb-0"
    mobile-content-class="px-3 pt-3 pb-3"
  >
    <PageHeader title="订单统计看板">
      <template #icon>
        <svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
        </svg>
      </template>
      <template #extra>
        <div class="flex items-center gap-6">
          <div class="flex flex-col items-end">
            <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400">全部总额</span>
            <span class="font-numeric text-base font-bold text-slate-900">{{ formatCurrency(normalizedGlobalSummary.totalAmount) }}</span>
          </div>
          <div class="flex flex-col items-end">
            <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400">全部总量</span>
            <span class="font-numeric text-base font-bold text-slate-900">{{ formatInteger(normalizedGlobalSummary.totalQuantity) }}</span>
          </div>
        </div>
      </template>
    </PageHeader>

    <div
      class="inventory-board-page relative flex min-h-0 flex-1 flex-col overflow-y-auto overscroll-contain bg-slate-50/30 p-4"
    >

      <!-- 筛选区域 -->
      <section class="mb-4 space-y-3 border border-slate-100 bg-white p-4 shadow-sm">
        <div class="flex flex-col gap-3">
          <!-- 时间筛选 -->
          <div class="flex flex-col gap-2 xl:flex-row xl:items-center">
            <span class="w-24 shrink-0 text-[13px] font-bold text-slate-600">时间快捷筛选:</span>
            <div class="flex flex-wrap items-center gap-2">
              <button
                v-for="option in timeOptions"
                :key="option.value"
                type="button"
                class="inline-flex items-center px-3 py-1.5 text-[13px] transition-all"
                :class="getFilterButtonClass(selectedTimePresets.includes(option.value))"
                @click="toggleTimePreset(option.value)"
              >
                {{ option.label }}
              </button>
              <t-date-picker
                multiple
                clearable
                format="YYYY-MM-DD"
                value-type="YYYY-MM-DD"
                placeholder="自定义日期"
                class="inventory-board-date-picker !w-[180px]"
                :value="customDates"
                :input-props="{ readonly: true }"
                @change="handleCustomDatesChange"
              />
            </div>
          </div>
          <!-- 自定义日期展示 -->
          <div v-if="hasCustomDates" class="flex items-center gap-2 pl-24">
            <t-tag
              v-for="date in customDates"
              :key="date"
              variant="light"
              size="small"
              class="inventory-date-token"
              closable
            >
              {{ dayjs(date).format("MM-DD") }}
            </t-tag>
          </div>
          <!-- 商品筛选 -->
          <div class="flex flex-col gap-2 xl:flex-row xl:items-center">
            <span class="w-24 shrink-0 text-[13px] font-bold text-slate-600">商品快捷筛选:</span>
            <div class="flex flex-1 flex-wrap items-center justify-between gap-2">
              <div class="flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  class="inline-flex items-center px-3 py-1.5 text-[13px] transition-all"
                  :class="getFilterButtonClass(selectedGoods === 'all')"
                  @click="selectGoods('all')"
                >
                  全部商品
                </button>
                <button
                  v-for="goods in goodsOptions"
                  :key="goods"
                  type="button"
                  class="inline-flex items-center px-3 py-1.5 text-[13px] transition-all"
                  :class="getFilterButtonClass(selectedGoods === goods)"
                  @click="selectGoods(goods)"
                >
                  {{ goods }}
                </button>
              </div>

              <!-- Steam 折扣测算 -->
              <div v-if="selectedGoods !== 'all'" class="flex items-center">
                <t-popup
                  v-model:visible="isSteamDialogVisible"
                  trigger="click"
                  placement="bottom-right"
                  :overlay-inner-style="{ padding: 0, borderRadius: '12px', border: 'none', boxShadow: '0 20px 50px rgba(15,23,42,0.15)' }"
                  destroy-on-close
                >
                  <button
                    type="button"
                    class="inline-flex h-10 items-center gap-2.5 rounded-lg border border-slate-200 bg-white px-4 text-[13px] font-bold text-slate-600 shadow-sm transition-all duration-300 hover:scale-105 hover:border-blue-300 hover:bg-blue-50/50 hover:text-blue-600 hover:shadow-md active:scale-95"
                  >
                    <span
                      class="relative flex h-2.5 w-2.5"
                    >
                      <span
                        class="absolute inline-flex h-full w-full animate-ping rounded-full opacity-75"
                        :class="floatingTriggerToneClass"
                      ></span>
                      <span
                        class="relative inline-flex h-2.5 w-2.5 rounded-full"
                        :class="floatingTriggerToneClass"
                      ></span>
                    </span>
                    Steam 折扣
                  </button>
                  <template #content>
                    <section
                      class="w-[360px] overflow-hidden bg-white"
                      aria-labelledby="steam-discount-title"
                      @click.stop
                    >
                      <header class="flex items-start justify-between gap-3 border-b border-slate-50 px-5 py-4">
                        <div>
                          <h2 id="steam-discount-title" class="text-[16px] font-bold text-slate-900">
                            Steam 折扣测算
                          </h2>
                          <p class="mt-1 text-[13px] text-slate-400">
                            按 Steam 到手价与平均买入价计算
                          </p>
                        </div>
                        <button
                          type="button"
                          class="inline-flex h-8 w-8 items-center justify-center rounded-full text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
                          @click="isSteamDialogVisible = false"
                        >
                          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                          </svg>
                        </button>
                      </header>

                      <div class="px-5 py-5">
                        <div class="text-[12px] font-medium text-slate-400 uppercase tracking-wider">当前商品</div>
                        <div class="mt-1 text-[15px] font-bold text-slate-700">
                          {{ selectedGoods }}
                        </div>

                        <div class="mt-5">
                          <div class="mb-2 text-[13px] font-medium text-slate-500">
                            Steam 市场卖出单价 (含税)
                          </div>
                          <t-input
                            v-model="steamMarketPrice"
                            clearable
                            placeholder="输入 Steam 售价"
                            class="!rounded-lg"
                          >
                            <template #suffix>¥</template>
                          </t-input>
                        </div>

                        <div class="mt-5 grid grid-cols-2 gap-3">
                          <div class="rounded-xl bg-slate-50/80 px-4 py-3">
                            <div class="text-[12px] text-slate-400">库存总量</div>
                            <div class="font-numeric mt-1 text-[16px] font-bold text-slate-700">
                              {{ formatInteger(normalizedCurrentSummary.totalQuantity) }}
                            </div>
                          </div>
                          <div class="rounded-xl bg-slate-50/80 px-4 py-3">
                            <div class="text-[12px] text-slate-400">Steam 到手</div>
                            <div class="font-numeric mt-1 text-[16px] font-bold text-slate-700">
                              {{ steamTotalAmountText }}
                            </div>
                          </div>
                        </div>

                        <div class="mt-5 rounded-2xl border border-dashed border-slate-200 bg-slate-50/30 px-5 py-6 text-center">
                          <div class="text-[12px] font-medium text-slate-400">计算折扣</div>
                          <div
                            class="font-numeric mt-2 text-[40px] font-bold leading-none tracking-tight"
                            :class="discountToneClass"
                          >
                            {{ discountText }}
                          </div>
                          <div class="mt-3 text-[13px] text-slate-500">
                            {{ discountDescription }}
                          </div>
                        </div>
                      </div>
                    </section>
                  </template>
                </t-popup>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 核心指标统计 -->
      <section class="mb-4 grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
        <!-- 总购买数量 -->
        <div
          class="relative overflow-hidden rounded-xl border border-blue-100 bg-white p-3.5 shadow-sm transition-all hover:shadow-md"
        >
          <div class="absolute -top-3 -right-3 text-blue-50/30">
            <svg class="h-16 w-16" fill="currentColor" viewBox="0 0 24 24">
              <path
                d="M7 18c-1.1 0-1.99.9-1.99 2S5.9 22 7 22s2-.9 2-2-.9-2-2-2zM1 2v2h2l3.6 7.59-1.35 2.45c-.16.28-.25.61-.25.96 0 1.1.9 2 2 2h12v-2H7.42c-.14 0-.25-.11-.25-.25l.03-.12.9-1.63h7.45c.75 0 1.41-.41 1.75-1.03l3.58-6.49c.08-.14.12-.31.12-.48 0-.55-.45-1-1-1H5.21l-.94-2H1zm16 16c-1.1 0-1.99.9-1.99 2s.89 2 1.99 2 2-.9 2-2-.9-2-2-2z"
              />
            </svg>
          </div>
          <div class="relative z-10 flex flex-col gap-0.5">
            <div class="flex items-center gap-2">
              <div
                class="flex h-7 w-7 items-center justify-center rounded-md bg-blue-50 text-blue-600"
              >
                <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"
                  />
                </svg>
              </div>
              <span class="text-xs font-bold text-slate-500">总购买数量</span>
            </div>
            <div class="mt-1 flex items-baseline gap-1 text-slate-900">
              <span class="font-numeric text-2xl font-bold tracking-tight">
                {{ formatInteger(normalizedCurrentSummary.totalQuantity) }}
              </span>
              <span class="text-[10px] font-medium text-slate-400">件</span>
            </div>
          </div>
        </div>

        <!-- 总购买金额 -->
        <div
          class="relative overflow-hidden rounded-xl border border-indigo-100 bg-white p-3.5 shadow-sm transition-all hover:shadow-md"
        >
          <div class="absolute -top-3 -right-3 text-indigo-50/30">
            <svg class="h-16 w-16" fill="currentColor" viewBox="0 0 24 24">
              <path
                d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1.41 16.09V20h-2.82v-1.91c-.08-.05-.16-.09-.23-.14-1.25-.8-1.57-1.74-1.59-2.81h1.79c.02.63.2 1.05.74 1.4.38.25.9.43 1.54.43.52 0 1.03-.13 1.37-.39.43-.32.55-.83.33-1.27-.15-.31-.46-.57-1.1-.81l-.99-.37c-1.34-.51-2.43-1.22-2.78-2.6-.18-.71-.12-1.48.25-2.09.34-.57.94-1.03 1.74-1.33V6h2.82v1.89c.14.07.28.16.42.25 1.01.66 1.4 1.54 1.45 2.5h-1.8c-.02-.45-.11-.84-.5-1.12-.35-.25-.85-.43-1.44-.43-.46 0-.89.1-1.18.3-.39.27-.47.74-.32 1.14.12.33.43.58 1.04.81l.99.37c1.39.52 2.37 1.3 2.76 2.61.16.53.18 1.09.06 1.63-.2.91-.77 1.64-1.63 2.03z"
              />
            </svg>
          </div>
          <div class="relative z-10 flex flex-col gap-0.5">
            <div class="flex items-center gap-2">
              <div
                class="flex h-7 w-7 items-center justify-center rounded-md bg-indigo-50 text-indigo-600"
              >
                <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                  />
                </svg>
              </div>
              <span class="text-xs font-bold text-slate-500">总购买金额</span>
            </div>
            <div class="mt-1 flex items-baseline gap-0.5 text-slate-900">
              <span class="text-sm font-bold">¥</span>
              <span class="font-numeric text-2xl font-bold tracking-tight">
                {{ formatCurrency(normalizedCurrentSummary.totalAmount, { symbol: false }) }}
              </span>
            </div>
          </div>
        </div>

        <!-- 平均买入价 -->
        <div
          class="relative overflow-hidden rounded-xl border border-amber-100 bg-white p-3.5 shadow-sm transition-all hover:shadow-md"
        >
          <div class="absolute -top-3 -right-3 text-amber-50/30">
            <svg class="h-16 w-16" fill="currentColor" viewBox="0 0 24 24">
              <path
                d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM9 17H7v-7h2v7zm4 0h-2V7h2v10zm4 0h-2v-4h2v4z"
              />
            </svg>
          </div>
          <div class="relative z-10 flex flex-col gap-0.5">
            <div class="flex items-center gap-2">
              <div
                class="flex h-7 w-7 items-center justify-center rounded-md bg-amber-50 text-amber-600"
              >
                <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"
                  />
                </svg>
              </div>
              <span class="text-xs font-bold text-slate-500">平均买入价</span>
            </div>
            <div class="mt-1 flex items-baseline gap-0.5 text-amber-600">
              <span class="text-sm font-bold">¥</span>
              <span class="font-numeric text-2xl font-bold tracking-tight">
                {{ formatCurrency(normalizedCurrentSummary.averagePrice, { symbol: false }) }}
              </span>
            </div>
          </div>
        </div>

        <!-- 商品数 -->
        <div
          class="relative overflow-hidden rounded-xl border border-emerald-100 bg-white p-3.5 shadow-sm transition-all hover:shadow-md"
        >
          <div class="absolute -top-3 -right-3 text-emerald-50/30">
            <svg class="h-16 w-16" fill="currentColor" viewBox="0 0 24 24">
              <path
                d="M12 2l-5.5 9h11L12 2zm0 3.84L13.93 9h-3.87L12 5.84zM17.5 13c-2.49 0-4.5 2.01-4.5 4.5s2.01 4.5 4.5 4.5 4.5-2.01 4.5-4.5-2.01-4.5-4.5-4.5zm0 7c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5zM3 21.5h8v-8H3v8zm2-6h4v4H5v-4z"
              />
            </svg>
          </div>
          <div class="relative z-10 flex flex-col gap-0.5">
            <div class="flex items-center gap-2">
              <div
                class="flex h-7 w-7 items-center justify-center rounded-md bg-emerald-50 text-emerald-600"
              >
                <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M16 8v8m-4-5v5m-4-2v2m-2 4h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
                  />
                </svg>
              </div>
              <span class="text-xs font-bold text-slate-500">商品数</span>
            </div>
            <div class="mt-1 flex items-baseline gap-1 text-slate-900">
              <span class="font-numeric text-2xl font-bold tracking-tight">
                {{ formatInteger(normalizedCurrentSummary.goodsCount) }}
              </span>
              <span class="text-[10px] font-medium text-slate-400">种</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 统计汇总 (移除) -->

      <section class="border border-[#ebeef5] bg-white p-3">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <div class="text-[13px] font-medium text-[#303133]">结果列表</div>
          </div>
          <div class="flex items-center gap-2">
            <button
              type="button"
              class="inline-flex items-center px-2 py-1 text-[12px] leading-none transition-colors duration-200 focus-visible:ring-2 focus-visible:ring-[var(--td-brand-color)] focus-visible:ring-offset-2 focus-visible:outline-none"
              :class="getFilterButtonClass(viewMode === 'aggregate')"
              :aria-pressed="viewMode === 'aggregate'"
              @click="viewMode = 'aggregate'"
            >
              按商品聚合
            </button>
            <button
              type="button"
              class="inline-flex items-center px-2 py-1 text-[12px] leading-none transition-colors duration-200 focus-visible:ring-2 focus-visible:ring-[var(--td-brand-color)] focus-visible:ring-offset-2 focus-visible:outline-none"
              :class="getFilterButtonClass(viewMode === 'split')"
              :aria-pressed="viewMode === 'split'"
              @click="viewMode = 'split'"
            >
              按时间拆分
            </button>
          </div>
        </div>

        <div v-if="!isMobile" class="mt-3 overflow-hidden">
          <t-table
            row-key="key"
            size="small"
            hover
            table-layout="auto"
            :data="tableData"
            :columns="tableColumns"
            cell-empty-content="-"
            class="niro-unified-table bg-white"
          >
            <template #empty>
              <div class="flex min-h-[180px] items-center justify-center">
                <t-empty description="当前筛选条件下暂无数据" />
              </div>
            </template>

            <template #goodsName="{ row }">
              <div class="min-w-0">
              <t-tooltip :content="row.goodsName" placement="top-left">
                <div class="truncate font-medium text-[#303133]">
                  {{ row.goodsName }}
                </div>
              </t-tooltip>
              </div>
            </template>

            <template #dateLabel="{ row }">
              <span class="text-[13px] text-[#606266]">
                {{ "dateLabel" in row ? row.dateLabel : "-" }}
              </span>
            </template>

            <template #quantity="{ row }">
              <span class="font-numeric text-[13px] font-medium text-[#111827]">
                {{ formatInteger(row.quantity) }}
              </span>
            </template>

            <template #amount="{ row }">
              <span class="font-numeric text-[13px] font-medium text-[#111827]">
                {{ formatCurrency(row.amount) }}
              </span>
            </template>

            <template #avgPrice="{ row }">
              <span class="font-numeric text-[13px] font-medium text-[#111827]">
                {{ formatCurrency(row.avgPrice) }}
              </span>
            </template>
          </t-table>
        </div>

        <div v-else class="mt-3">
          <div v-if="tableData.length > 0" class="space-y-3">
            <article
              v-for="row in tableData"
              :key="row.key"
              class="bg-white px-3 py-3 shadow-[0_1px_2px_rgba(15,23,42,0.04)]"
            >
              <div class="flex items-start gap-3">
                <div
                  class="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-[10px] border border-[#e5e7eb] bg-[#f5f7fa]"
                >
                  <img
                    v-if="row.imageUrl"
                    :src="row.imageUrl"
                    :alt="row.goodsName"
                    referrerpolicy="no-referrer"
                    class="h-full w-full object-contain"
                  />
                  <div
                    v-else
                    class="h-9 w-9 rounded-[8px] border border-[#d5dbe5] bg-[#eef2f7]"
                  ></div>
                </div>

                <div class="min-w-0 flex-1">
                  <div class="flex items-start justify-between gap-3">
                    <div class="min-w-0 flex-1">
                      <t-tooltip :content="row.goodsName" placement="top-left">
                        <div class="truncate text-[14px] leading-6 font-medium text-[#303133]">
                          {{ row.goodsName }}
                        </div>
                      </t-tooltip>
                    </div>
                    <t-tag
                      v-if="'dateLabel' in row"
                      size="small"
                      variant="light"
                      color="var(--td-brand-color-light)"
                      class="shrink-0 rounded-[6px] border border-[var(--td-brand-color-2)] text-[var(--td-brand-color-7)]"
                    >
                      {{ row.dateLabel }}
                    </t-tag>
                  </div>

                  <div class="mt-3 grid grid-cols-2 gap-x-4 gap-y-3">
                    <div>
                      <div class="text-[11px] text-[#909399]">购买数量</div>
                      <div
                        class="font-numeric mt-1 text-[17px] leading-none font-semibold text-[#111827]"
                      >
                        {{ formatInteger(row.quantity) }}
                      </div>
                    </div>
                    <div>
                      <div class="text-[11px] text-[#909399]">总金额</div>
                      <div
                        class="font-numeric mt-1 text-[17px] leading-none font-semibold text-[#111827]"
                      >
                        {{ formatCurrency(row.amount) }}
                      </div>
                    </div>
                    <div class="col-span-2">
                      <div class="text-[11px] text-[#909399]">平均买入价</div>
                      <div
                        class="font-numeric mt-1 text-[15px] leading-none font-semibold text-[#111827]"
                      >
                        {{ formatCurrency(row.avgPrice) }}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </article>
          </div>

          <div v-else class="overflow-hidden border border-[#ebeef5] bg-white">
            <div class="flex min-h-[180px] items-center justify-center">
              <t-empty description="当前筛选条件下暂无数据" />
            </div>
          </div>
        </div>
      </section>
    </div>
  </PageFrame>
</template>

<script setup lang="ts">
import { computed, nextTick, onActivated, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useWindowSize } from "@vueuse/core";
import dayjs from "dayjs";
import { MessagePlugin } from "tdesign-vue-next";
import type { CSSProperties, Ref } from "vue";
import type { DateValue, PrimaryTableCol, TableRowData } from "tdesign-vue-next";
import { orderApi } from "@/api/order";
import PageFrame from "@/components/PageFrame.vue";
import PageHeader from "@/components/PageHeader.vue";
import type {
  PurchaseStatsGoodsItem,
  PurchaseStatsSplitItem,
  PurchaseStatsSummary,
} from "@/types/order";

type TimePreset = "today" | "yesterday" | "last3" | "last7" | "all";
type ViewMode = "aggregate" | "split";

interface TimeOption {
  value: TimePreset;
  label: string;
}

interface AggregateRow {
  key: string;
  goodsName: string;
  quantity: number;
  amount: number;
  avgPrice: number;
  imageUrl?: string;
}

interface SplitRow extends AggregateRow {
  dateLabel: string;
  dateSort: string;
}

type DisplayRow = AggregateRow | SplitRow;

const { width } = useWindowSize();
const isMobile = computed(() => width.value <= 640);

const timeOptions: TimeOption[] = [
  { value: "today", label: "今日" },
  { value: "yesterday", label: "昨日" },
  { value: "last3", label: "近3天" },
  { value: "last7", label: "近7天" },
  { value: "all", label: "全部" },
];

const selectedTimePresets = ref<TimePreset[]>(["today"]);
const customDates = ref<string[]>([]);
const selectedGoods = ref<string>("all");
const steamMarketPrice = ref("");
const viewMode = ref<ViewMode>("aggregate");
const isSteamDialogVisible = ref(false);
const summaryLoading = ref(false);
const itemsLoading = ref(false);
const globalSummary = ref<PurchaseStatsSummary>({
  totalAmount: 0,
  totalQuantity: 0,
  avgPrice: 0,
  goodsTypeCount: 0,
});
const currentSummary = ref<PurchaseStatsSummary>({
  totalAmount: 0,
  totalQuantity: 0,
  avgPrice: 0,
  goodsTypeCount: 0,
});
const aggregateItems = ref<PurchaseStatsGoodsItem[]>([]);
const splitItems = ref<PurchaseStatsSplitItem[]>([]);

const priceFormatter = new Intl.NumberFormat("zh-CN", {
  style: "currency",
  currency: "CNY",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const integerFormatter = new Intl.NumberFormat("zh-CN", {
  maximumFractionDigits: 0,
});

const hasCustomDates = computed(() => customDates.value.length > 0);

const activeTimePresets = computed<TimePreset[]>(() => {
  if (hasCustomDates.value) {
    return [];
  }

  if (selectedTimePresets.value.length === 0 || selectedTimePresets.value.includes("all")) {
    return ["all"];
  }
  return selectedTimePresets.value;
});

const aggregateColumns: PrimaryTableCol<TableRowData>[] = [
  { colKey: "goodsName", title: "商品", minWidth: 220, cell: "goodsName" },
  { colKey: "quantity", title: "购买数量", width: 140, align: "left", cell: "quantity" },
  { colKey: "amount", title: "总金额", width: 160, align: "left", cell: "amount" },
  { colKey: "avgPrice", title: "平均买入价", width: 160, align: "left", cell: "avgPrice" },
];

const splitColumns: PrimaryTableCol<TableRowData>[] = [
  { colKey: "goodsName", title: "商品", minWidth: 220, cell: "goodsName" },
  { colKey: "dateLabel", title: "日期", width: 160, align: "left", cell: "dateLabel" },
  { colKey: "quantity", title: "购买数量", width: 140, align: "left", cell: "quantity" },
  { colKey: "amount", title: "总金额", width: 160, align: "left", cell: "amount" },
  { colKey: "avgPrice", title: "平均买入价", width: 160, align: "left", cell: "avgPrice" },
];

const formatCurrency = (value: number) => priceFormatter.format(value);
const formatInteger = (value: number) => integerFormatter.format(value);

const buildQueryParams = (keyword?: string) => {
  const params: { keyword?: string; startDate?: string; endDate?: string } = {};

  if (keyword && keyword !== "all") {
    params.keyword = keyword;
  }

  if (hasCustomDates.value) {
    const sortedDates = [...customDates.value].sort((left, right) => left.localeCompare(right));
    params.startDate = sortedDates[0];
    params.endDate = sortedDates[sortedDates.length - 1];
    return params;
  }

  if (activeTimePresets.value.includes("all")) {
    return params;
  }

  const today = dayjs();
  let startDate = today;
  let endDate = today;

  if (activeTimePresets.value.includes("last7")) {
    startDate = today.subtract(6, "day");
  } else if (activeTimePresets.value.includes("last3")) {
    startDate = today.subtract(2, "day");
  } else {
    if (activeTimePresets.value.includes("yesterday")) {
      startDate = today.subtract(1, "day");
    }
    if (activeTimePresets.value.includes("today")) {
      endDate = today;
    } else if (activeTimePresets.value.includes("yesterday")) {
      endDate = today.subtract(1, "day");
    }
  }

  params.startDate = startDate.format("YYYY-MM-DD");
  params.endDate = endDate.format("YYYY-MM-DD");
  return params;
};

const isTimePresetActive = (preset: TimePreset) => activeTimePresets.value.includes(preset);

const toggleTimePreset = (preset: TimePreset) => {
  customDates.value = [];

  if (preset === "all") {
    selectedTimePresets.value = ["all"];
    return;
  }

  const nextSet = new Set(selectedTimePresets.value.filter((item) => item !== "all"));

  if (nextSet.has(preset)) {
    nextSet.delete(preset);
  } else {
    nextSet.add(preset);
  }

  selectedTimePresets.value = nextSet.size > 0 ? Array.from(nextSet) : ["all"];
};

const handleCustomDatesChange = (value: DateValue | DateValue[]) => {
  const nextDates = Array.isArray(value) ? value : value ? [value] : [];

  customDates.value = nextDates
    .map((item) => dayjs(item).format("YYYY-MM-DD"))
    .filter((item, index, array) => array.indexOf(item) === index)
    .sort((left, right) => left.localeCompare(right));
};

const removeCustomDate = (date: string) => {
  customDates.value = customDates.value.filter((item) => item !== date);
};

const closeSteamDialog = () => {
  isSteamDialogVisible.value = false;
};

const toggleSteamDialog = () => {
  if (selectedGoods.value === "all") {
    closeSteamDialog();
    return;
  }

  isSteamDialogVisible.value = !isSteamDialogVisible.value;
};

const selectGoods = (value: string) => {
  if (value === "all") {
    closeSteamDialog();
  }

  selectedGoods.value = value;
};

const normalizedCurrentSummary = computed(() => ({
  totalQuantity: currentSummary.value.totalQuantity ?? 0,
  totalAmount: currentSummary.value.totalAmount ?? 0,
  averagePrice: currentSummary.value.avgPrice ?? 0,
  goodsCount: currentSummary.value.goodsTypeCount ?? 0,
}));

const normalizedGlobalSummary = computed(() => ({
  totalAmount: globalSummary.value.totalAmount ?? 0,
  totalQuantity: globalSummary.value.totalQuantity ?? 0,
}));

const activeTimeLabels = computed(() => {
  if (hasCustomDates.value) {
    return customDates.value.map((item) => dayjs(item).format("MM-DD"));
  }

  if (activeTimePresets.value.includes("all")) {
    return ["全部"];
  }

  return timeOptions
    .filter((item) => activeTimePresets.value.includes(item.value))
    .map((item) => item.label);
});

const dateFilterHintText = computed(() => {
  if (hasCustomDates.value) {
    return "当前按自定义日期统计";
  }
  if (activeTimePresets.value.includes("all")) {
    return "当前统计全部日期";
  }
  return "支持多选，按选中日期汇总";
});

const scopeLabel = computed(() => {
  const goodsLabel = selectedGoods.value === "all" ? "全部商品" : selectedGoods.value;
  return `${goodsLabel} · ${activeTimeLabels.value.join(" + ")}`;
});

const STEAM_NET_RATE = 0.87;

const parsedSteamMarketPrice = computed(() => {
  const normalized = steamMarketPrice.value.trim();
  if (!normalized) {
    return null;
  }

  const value = Number(normalized);
  return Number.isFinite(value) && value > 0 ? value : null;
});

const steamNetUnitPrice = computed(() => {
  const marketPrice = parsedSteamMarketPrice.value;
  if (!marketPrice) {
    return null;
  }

  return marketPrice * STEAM_NET_RATE;
});

const steamTotalAmount = computed(() => {
  const netUnitPrice = steamNetUnitPrice.value;
  const quantity = normalizedCurrentSummary.value.totalQuantity;

  if (!netUnitPrice || quantity <= 0) {
    return null;
  }

  return netUnitPrice * quantity;
});

const steamTotalAmountText = computed(() => {
  if (steamTotalAmount.value === null) {
    return "--";
  }

  return formatCurrency(steamTotalAmount.value);
});

const discountRatio = computed(() => {
  const netUnitPrice = steamNetUnitPrice.value;
  const averagePrice = normalizedCurrentSummary.value.averagePrice;

  if (!netUnitPrice || averagePrice <= 0) {
    return null;
  }

  return averagePrice / netUnitPrice;
});

const discountText = computed(() => {
  if (discountRatio.value === null) {
    return "--";
  }

  return `${(discountRatio.value * 10).toFixed(1)} 折`;
});

const discountToneClass = computed(() => {
  const ratio = discountRatio.value;

  if (ratio === null) {
    return "text-[#909399]";
  }
  if (ratio <= 0.35) {
    return "text-[#16a34a]";
  }
  if (ratio <= 0.55) {
    return "text-[var(--td-brand-color)]";
  }
  if (ratio <= 0.75) {
    return "text-[#d97706]";
  }
  return "text-[#dc2626]";
});

const discountDescription = computed(() => {
  if (parsedSteamMarketPrice.value === null) {
    return "请输入有效的 Steam 单价";
  }
  if (discountRatio.value === null) {
    return "当前范围暂无可计算的平均买入价";
  }
  return "按 Steam 到手价与平均买入价计算";
});

const goodsOptions = computed(() => aggregateItems.value.map((item) => item.goodsName));

const floatingTriggerToneClass = computed(() => {
  if (discountRatio.value === null) {
    return "bg-slate-300";
  }
  if (discountRatio.value <= 0.35) {
    return "bg-emerald-500";
  }
  if (discountRatio.value <= 0.55) {
    return "bg-blue-500";
  }
  if (discountRatio.value <= 0.75) {
    return "bg-amber-500";
  }
  return "bg-red-500";
});

const aggregateRows = computed<AggregateRow[]>(() =>
  aggregateItems.value.map((item) => ({
    key: item.goodsName,
    goodsName: item.goodsName,
    quantity: item.totalQuantity,
    amount: item.totalAmount,
    avgPrice: item.avgPrice,
    imageUrl: item.goodsImg,
  }))
);

const splitRows = computed<SplitRow[]>(() =>
  splitItems.value.map((item) => ({
    key: `${item.goodsName}-${item.date}`,
    goodsName: item.goodsName,
    quantity: item.totalQuantity,
    amount: item.totalAmount,
    avgPrice: item.avgPrice,
    imageUrl: item.goodsImg,
    dateLabel: dayjs(item.date).format("MM-DD"),
    dateSort: item.date,
  }))
);

const tableColumns = computed(() =>
  viewMode.value === "aggregate" ? aggregateColumns : splitColumns
);

const tableData = computed<DisplayRow[]>(() =>
  viewMode.value === "aggregate" ? aggregateRows.value : splitRows.value
);

const fetchGlobalSummary = async () => {
  summaryLoading.value = true;
  try {
    globalSummary.value = await orderApi.getPurchaseStatsSummary();
  } finally {
    summaryLoading.value = false;
  }
};

const fetchCurrentStats = async () => {
  const params = buildQueryParams(selectedGoods.value);
  summaryLoading.value = true;
  itemsLoading.value = true;
  try {
    const [summary, items, splitItemList] = await Promise.all([
      orderApi.getPurchaseStatsSummary(params),
      orderApi.getPurchaseStatsItems(params),
      orderApi.getPurchaseStatsSplitItems(params),
    ]);
    currentSummary.value = summary;
    aggregateItems.value = items;
    splitItems.value = splitItemList;

    if (selectedGoods.value !== "all" && items.length === 0) {
      selectedGoods.value = "all";
    }
  } catch (error) {
    MessagePlugin.error("加载订单统计数据失败");
    throw error;
  } finally {
    summaryLoading.value = false;
    itemsLoading.value = false;
  }
};

const loadStatsData = async () => {
  await Promise.all([fetchGlobalSummary(), fetchCurrentStats()]);
};

const getFilterButtonClass = (active: boolean) => {
  if (active) {
    return "rounded-[4px] bg-[var(--td-brand-color-light)] font-medium text-[var(--td-brand-color-7)]";
  }

  return "rounded-[4px] bg-transparent text-[#5b6473] hover:bg-[var(--td-brand-color-light)] hover:text-[var(--td-brand-color-7)]";
};

watch(selectedGoods, () => {
  void fetchCurrentStats();
});

watch(
  [activeTimePresets, customDates],
  () => {
    void fetchCurrentStats();
  },
  { deep: true }
);

onMounted(() => {
  void loadStatsData();
});

onActivated(() => {
  void fetchCurrentStats();
});

onBeforeUnmount(() => {
  // Cleanup if needed
});
</script>

<style scoped>
:deep(.inventory-board-date-picker .t-input) {
  min-height: 28px;
  border: none;
  border-radius: 4px;
  box-shadow: none;
  background: transparent;
  padding: 4px 8px;
  transition:
    background-color 0.2s ease,
    color 0.2s ease;
}

:deep(.inventory-board-date-picker .t-input:hover) {
  background: rgba(24, 144, 255, 0.06);
}

:deep(.inventory-board-date-picker .t-input__inner),
:deep(.inventory-board-date-picker .t-input__prefix),
:deep(.inventory-board-date-picker .t-input__suffix) {
  color: rgb(91, 100, 115);
}

:deep(.inventory-board-date-picker .t-input--focused) {
  box-shadow: 0 0 0 2px var(--td-brand-color-focus);
}

:deep(.inventory-board-date-picker .t-input--focused .t-input__inner),
:deep(.inventory-board-date-picker .t-input--focused .t-input__prefix),
:deep(.inventory-board-date-picker .t-input--focused .t-input__suffix) {
  color: var(--td-brand-color-7);
}

:deep(.inventory-date-token.t-tag),
:deep(.inventory-scope-tag.t-tag) {
  background-color: var(--td-brand-color-light);
  color: var(--td-brand-color-7);
}

:deep(.inventory-date-token.t-tag .t-tag__suffix-icon) {
  color: var(--td-brand-color-7);
}
</style>
