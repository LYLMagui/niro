<template>
  <div :class="{ 'p-6': !dialogOnly }">
    <t-card v-if="!dialogOnly" :bordered="false" class="shadow-sm embedded-card">
      <template #title>
        <div class="flex items-center">
          <t-icon name="task-1" class="mr-2 text-blue-600" />
          <span class="text-lg font-bold text-gray-800">扫货任务管理</span>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="p-6 border-b border-gray-100">
        <t-row :gutter="16">
        <t-col :span="3">
          <t-input
            v-model="queryParams.name"
            placeholder="搜索商品名称"
            clearable
            @enter="fetchData"
            @blur="(v: any) => handleInputTrim(v, queryParams, 'name')"
          />
        </t-col>
        <t-col :span="2">
          <t-select
            v-model="queryParams.status"
            placeholder="任务状态"
            clearable
            @change="fetchData"
          >
            <t-option label="停止" :value="0" />
            <t-option label="运行中" :value="1" />
            <t-option label="已完成" :value="2" />
            <t-option label="异常" :value="3" />
          </t-select>
        </t-col>
        <t-col :span="2">
          <div class="flex gap-4">
            <t-button
              theme="primary"
              size="medium"
              class="rounded-lg transition-all duration-300 hover:shadow active:shadow-none"
              @click="fetchData"
            >
              查询
            </t-button>
            <t-button
              theme="default"
              variant="base"
              size="medium"
              class="rounded-lg transition-all duration-300 hover:shadow active:shadow-none"
              @click="resetQuery"
            >
              重置
            </t-button>
          </div>
        </t-col>
        <t-col :span="5">
          <div class="flex justify-end items-center gap-3 w-full h-full">
            <t-button
              v-if="isAdmin"
              theme="default"
              variant="outline"
              size="medium"
              class="rounded-lg transition-all duration-300 hover:shadow active:shadow-none"
              @click="handleAddSystem"
            >
              新增系统任务
            </t-button>
            <t-button
              theme="primary"
              size="medium"
              class="rounded-lg transition-all duration-300 hover:shadow active:shadow-none"
              @click="handleAdd"
            >
              新增任务
            </t-button>
          </div>
        </t-col>
        </t-row>
      </div>

      <!-- 数据表格 -->
      <t-table
        row-key="id"
        :data="dataList"
        :columns="columns"
        :loading="loading"
        :pagination="pagination"
        hover
        :header-affixed-top="true"
        class="embedded-table w-full"
        @page-change="onPageChange"
      >
        <template #empty>
          <t-empty icon="task-1" description="暂无扫货任务，点击右侧“新增任务”开启自动化扫货" />
        </template>
        <template #goods="{ row }">
          <div class="flex items-center">
            <t-image
              v-if="row.goodsIconUrl"
              :src="row.goodsIconUrl"
              class="mr-2 h-8 w-8 rounded"
            />
            <div v-else-if="row.taskType >= 2" class="mr-2 flex h-8 w-8 items-center justify-center rounded bg-blue-100 text-blue-600">
              <t-icon name="setting" />
            </div>
            <div>
              <div class="max-w-xs truncate font-bold" :title="row.name">
                {{ row.name }}
              </div>
              <div v-if="row.goodsId" class="text-xs text-gray-500">ID: {{ row.goodsId }}</div>
            </div>
          </div>
        </template>

        <template #taskType="{ row }">
          <t-tag v-if="row.taskType === 1" theme="warning" variant="light">站内倒卖</t-tag>
          <t-tag v-else-if="row.taskType === 2" theme="primary" variant="light">分类同步</t-tag>
          <t-tag v-else-if="row.taskType === 3" theme="primary" variant="light">商品同步</t-tag>
          <t-tag v-else-if="row.taskType === 4" theme="primary" variant="light">印花同步</t-tag>
          <t-tag v-else-if="row.taskType === 5" theme="primary" variant="light">分类商品同步</t-tag>
          <t-tag v-else theme="primary" variant="light">炼金扫货</t-tag>
        </template>

        <template #target="{ row }">
          <div v-if="row.taskType === 1">
            <div class="text-xs text-gray-500">最小利润:</div>
            <div class="font-bold text-orange-600">¥{{ row.minProfit }}</div>
          </div>
          <div v-else-if="row.taskType >= 2">
            <div class="text-xs text-gray-500">系统自动执行</div>
          </div>
          <div v-else>
            <div class="text-xs text-gray-500">最高价格: ¥{{ row.maxPrice }}</div>
            <div class="text-xs text-gray-500">磨损: {{ row.minPaintwear }}-{{ row.maxPaintwear }}</div>
          </div>
        </template>

        <template #progress="{ row }">
          <span v-if="row.taskType < 2">{{ row.successCount }} / {{ row.buyCount }}</span>
          <span v-else>-</span>
        </template>

        <template #accounts="{ row }">
          <div v-if="row.accountNames && row.accountNames.length > 0" class="flex flex-wrap gap-1">
            <t-tag
              v-for="name in row.accountNames"
              :key="name"
              theme="primary"
              variant="light"
              size="small"
              class="rounded"
            >
              {{ name }}
            </t-tag>
          </div>
          <t-tag v-else theme="warning" variant="light" size="small" class="rounded">
            <template #icon><t-icon name="view-module" /></template>
            仅监控
          </t-tag>
        </template>

        <template #status="{ row }">
          <t-tag v-if="row.status === 0" theme="default">停止</t-tag>
          <t-tag v-else-if="row.status === 1" theme="success">待运行</t-tag>
          <t-tag v-else-if="row.status === 2" theme="primary">已完成</t-tag>
          <t-tag v-else-if="row.status === 4" theme="warning">
            {{ (!row.accountNames || row.accountNames.length === 0) ? '监控中' : '执行中' }}
          </t-tag>
          <t-tag v-else theme="danger">异常</t-tag>
        </template>

        <template #op="{ row }">
          <div class="flex items-center justify-center space-x-2">
            <t-tooltip v-if="[1, 4].includes(row.status)" content="任务正在运行中，无法修改配置，请先停止任务">
              <t-link theme="primary" disabled class="disabled:opacity-50 disabled:cursor-not-allowed">编辑</t-link>
            </t-tooltip>
            <t-link v-else theme="primary" @click="handleEdit(row)">编辑</t-link>
            
            <t-popconfirm
              v-if="[0, 2, 3].includes(row.status)"
              :content="(!row.accountNames || row.accountNames.length === 0) ? '当前任务未配置下单账号，将以“仅监控”模式启动，确定吗？' : '确定要启动任务吗？'"
              @confirm="handleStatus(row, 1)"
            >
              <t-link :theme="(!row.accountNames || row.accountNames.length === 0) ? 'warning' : 'success'">
                {{ (!row.accountNames || row.accountNames.length === 0) ? '监控' : '启动' }}
              </t-link>
            </t-popconfirm>
            <t-popconfirm
              v-if="[1, 4].includes(row.status)"
              content="确定要停止任务吗？"
              @confirm="handleStatus(row, 0)"
            >
              <t-link theme="warning">停止</t-link>
            </t-popconfirm>
            <t-popconfirm 
              v-if="![2, 3, 4].includes(row.taskType)" 
              content="确定要删除任务吗？" 
              @confirm="handleDelete(row)"
            >
              <t-link theme="danger">删除</t-link>
            </t-popconfirm>
          </div>
        </template>
      </t-table>
    </t-card>

    <!-- 扫货任务对话框 -->
    <t-dialog
      v-model:visible="dialogVisible"
      :header="dialogTitle"
      :confirm-btn="{ content: '提交', loading: submitLoading }"
      :width="uiState.isCycleMode ? '720px' : '640px'"
      class="task-edit-dialog"
      :footer="false"
    >
      <div class="form-container py-2">
        <t-form
          ref="formRef"
          :data="formData"
          :rules="rules"
          :label-width="110"
          class="compact-form"
          label-align="right"
          scroll-to-first-error="smooth"
          validation-trigger="blur"
          prevent-submit-default
          @submit="handleSubmit"
        >
          <t-form-item label="任务类型" name="taskType" class="mb-2">
            <t-radio-group v-model="formData.taskType">
              <t-radio :value="0">炼金扫货</t-radio>
              <t-radio :value="1">站内倒卖</t-radio>
            </t-radio-group>
          </t-form-item>

          <t-form-item label="选择商品" name="goodsId" class="mb-2">
            <t-select
              v-model="formData.goodsId"
              filterable
              placeholder="输入商品名称搜索"
              :loading="goodsLoading"
              :on-search="remoteSearchGoods"
              :disabled="!!formData.id"
              style="width: 320px"
            >
              <t-option
                v-for="item in goodsOptions"
                :key="item.goodsId"
                :value="item.goodsId"
                :label="item.name"
              >
                {{ item.name }}
              </t-option>
            </t-select>
          </t-form-item>

          <t-form-item v-if="formData.taskType === 1" label="预期利润" name="minProfit" class="mb-1.5">
            <t-input-number
              v-model="formData.minProfit"
              :min="0"
              :step="1"
              :decimal-places="2"
              suffix="元"
              theme="column"
              style="width: 160px"
            />
            <span class="ml-2 text-xs text-gray-400">
              低于此利润将不购买 (已扣除手续费)
            </span>
          </t-form-item>

          <t-form-item v-if="formData.taskType === 0" label="最高价格" name="maxPrice" class="mb-1.5">
            <t-input-number
              v-model="formData.maxPrice"
              :min="0.01"
              :step="0.1"
              :decimal-places="2"
              suffix="元"
              theme="column"
              style="width: 140px"
            />
          </t-form-item>

          <t-form-item v-if="formData.taskType === 0" label="磨损范围" name="wear" class="mb-1.5">
            <div class="flex items-center gap-1">
              <t-input-number
                v-model="formData.minPaintwear"
                :min="0"
                :max="1"
                :step="0.001"
                :decimal-places="3"
                placeholder="最小"
                theme="column"
                style="width: 110px"
              />
              <span class="text-gray-400">-</span>
              <t-input-number
                v-model="formData.maxPaintwear"
                :min="0"
                :max="1"
                :step="0.001"
                :decimal-places="3"
                placeholder="最大"
                theme="column"
                style="width: 110px"
              />
            </div>
          </t-form-item>

          <t-form-item label="购买数量" name="buyCount" class="mb-1.5">
            <t-input-number
              v-model="formData.buyCount"
              :min="1"
              :step="1"
              theme="column"
              style="width: 120px"
            />
          </t-form-item>

          <t-form-item label="执行账号" name="accountIds" class="mb-1.5">
            <t-select
              v-model="formData.accountIds"
              multiple
              filterable
              placeholder="请选择执行账号"
              :loading="accountsLoading"
              style="width: 320px"
              :min-collapsed-num="2"
              @focus="fetchAccounts"
            >
              <t-option
                v-for="item in accounts"
                :key="item.id"
                :value="item.id"
                :label="item.accountName"
                :disabled="!!item.boundTaskId && item.boundTaskId !== formData.id"
              >
                <div class="flex items-center justify-between w-full overflow-hidden">
                  <div class="flex items-center gap-1.5 overflow-hidden flex-1 mr-2">
                    <span class="font-medium shrink-0">{{ item.accountName }}</span>
                    <t-tooltip
                      v-if="item.boundTaskId && item.boundTaskId !== formData.id"
                      :content="'已绑定任务: ' + item.boundTaskName"
                      placement="top"
                    >
                      <span class="text-xs text-gray-400 truncate">
                        (已绑定: {{ item.boundTaskName }})
                      </span>
                    </t-tooltip>
                  </div>
                  <t-tag v-if="item.status === 'NORMAL'" theme="success" variant="light" size="small" class="shrink-0">在线</t-tag>
                  <t-tag v-else theme="danger" variant="light" size="small" class="shrink-0">异常</t-tag>
                </div>
              </t-option>
            </t-select>
            <template #tips>多选账号可实现多并发扫货，提高抢购成功率</template>
          </t-form-item>

          <t-form-item
            class="mb-2"
            label-width="0"
          >
            <div class="schedule-group w-full rounded-md border border-blue-100 bg-blue-50/50 p-3">
              <!-- 顶部模式切换 -->
              <div class="mb-3 flex items-center justify-between">
                <div class="flex items-center gap-2 text-sm font-medium text-blue-700">
                  <t-icon name="time-filled" />
                  <span>计划配置</span>
                </div>
                <t-radio-group v-model="uiState.isCycleMode" variant="default-filled" size="small">
                  <t-radio-button :value="false">持续执行</t-radio-button>
                  <t-radio-button :value="true">周期循环</t-radio-button>
                </t-radio-group>
              </div>

              <!-- Cron 配置 -->
              <t-form-item
                label="启动时间"
                name="cronExpression"
                label-width="70px"
                class="mb-3"
              >
                <div class="flex items-center gap-2">
                  <t-input
                    v-model="formData.cronExpression"
                    :disabled="uiState.isCronImmediate"
                    placeholder="未配置"
                    clearable
                    style="width: 180px"
                    @blur="(v: any) => handleInputTrim(v, formData, 'cronExpression')"
                  >
                    <template #suffix>
                      <t-popup
                        v-model:visible="cronVisible"
                        placement="bottom-right"
                        trigger="click"
                        :overlay-inner-style="{ padding: 0 }"
                      >
                        <t-link theme="primary" variant="underline" :disabled="uiState.isCronImmediate">
                          <t-icon name="calendar" />
                        </t-link>
                        <template #content>
                          <div class="cron-popup-container">
                            <cron-editor
                              :model-value="formData.cronExpression || ''"
                              @update:model-value="formData.cronExpression = $event"
                              @confirm="cronVisible = false"
                              @cancel="cronVisible = false"
                            />
                          </div>
                        </template>
                      </t-popup>
                    </template>
                  </t-input>
                  <t-checkbox v-model="uiState.isCronImmediate">立即启动</t-checkbox>
                </div>
              </t-form-item>

              <!-- 运行时长 / 循环配置 -->
              <t-form-item
                :label="uiState.isCycleMode ? '运行时长' : '持续时间'"
                name="durationMinutes"
                label-width="70px"
                class="mb-3"
              >
                <div class="flex items-center gap-2">
                  <t-input-number
                    v-model="uiState.durationValue"
                    :disabled="uiState.isDurationUnlimited && !uiState.isCycleMode"
                    :min="0"
                    :step="1"
                    theme="column"
                    style="width: 90px"
                  />
                  <t-select 
                    v-model="uiState.durationUnit" 
                    :disabled="uiState.isDurationUnlimited && !uiState.isCycleMode" 
                    style="width: 80px"
                  >
                    <t-option label="分钟" value="m" />
                    <t-option label="小时" value="h" />
                    <t-option label="天" value="d" />
                  </t-select>
                  <t-checkbox v-if="!uiState.isCycleMode" v-model="uiState.isDurationUnlimited">不限时间</t-checkbox>
                  
                  <template v-if="uiState.isCycleMode">
                    <span class="mx-1 text-orange-500 font-bold">/</span>
                    <t-tag theme="warning" variant="light" size="small" class="mr-1">暂停</t-tag>
                    <t-input-number
                      v-model="uiState.restValue"
                      :min="1"
                      :step="1"
                      theme="column"
                      style="width: 90px"
                    />
                    <t-select v-model="uiState.restUnit" style="width: 80px">
                      <t-option label="分钟" value="m" />
                      <t-option label="小时" value="h" />
                    </t-select>
                  </template>
                </div>
              </t-form-item>

              <!-- 扫描间隔 -->
              <t-form-item
                label="扫描间隔"
                name="scanInterval"
                label-width="70px"
                class="mb-3"
              >
                <div class="flex items-center gap-2">
                  <t-input-number
                    v-model="uiState.intervalMinValue"
                    :min="uiState.intervalUnit === 's' ? 15 : 1"
                    :step="1"
                    theme="column"
                    style="width: 90px"
                    @blur="handleIntervalMinBlur"
                  />
                  <span class="text-gray-400">-</span>
                  <t-input-number
                    v-model="uiState.intervalMaxValue"
                    :min="uiState.intervalUnit === 's' ? 15 : 1"
                    :step="1"
                    theme="column"
                    style="width: 90px"
                    @blur="handleIntervalMaxBlur"
                  />
                  <t-select
                    v-model="uiState.intervalUnit"
                    style="width: 80px"
                    @change="handleIntervalUnitChange"
                  >
                    <t-option label="秒" value="s" />
                    <t-option label="分钟" value="m" />
                    <t-option label="小时" value="h" />
                  </t-select>
                  <t-tooltip content="扫描间隔建议在 15-30 秒之间，过短容易触发风控。">
                    <t-icon name="help-circle" class="cursor-help text-gray-400" />
                  </t-tooltip>
                </div>
              </t-form-item>

              <!-- 动态逻辑预览 -->
              <div
                class="mt-2 rounded border border-blue-50 bg-white/80 p-2 text-[12px] leading-relaxed text-blue-600 shadow-sm"
              >
                <div class="flex items-start gap-1.5">
                  <t-icon name="info-circle" class="mt-0.5 shrink-0" />
                  <div class="font-medium">{{ executionSummary }}</div>
                </div>
              </div>
            </div>
          </t-form-item>

          <!-- 自定义底部按钮，用于触发表单提交 -->
          <div class="flex justify-end gap-3 border-t border-gray-100 pt-2.5 mt-2.5">
            <t-button variant="outline" theme="default" @click="dialogVisible = false">
              取消
            </t-button>
            <t-button theme="primary" type="submit" :loading="submitLoading">提交</t-button>
          </div>
        </t-form>
      </div>
    </t-dialog>

    <!-- 系统任务对话框 -->
    <t-dialog
      v-model:visible="systemDialogVisible"
      :header="dialogTitle"
      :confirm-btn="{ content: '提交', loading: submitLoading }"
      width="600px"
      class="task-edit-dialog"
      :footer="false"
    >
      <div class="form-container py-2">
        <t-form
          ref="systemFormRef"
          :data="formData"
          :rules="rules"
          :label-width="110"
          class="compact-form"
          label-align="right"
          scroll-to-first-error="smooth"
          validation-trigger="blur"
          prevent-submit-default
          @submit="handleSubmit"
        >
          <t-form-item label="任务类型" name="taskType" class="mb-2">
            <t-radio-group v-model="formData.taskType">
              <t-radio :value="2">系统-分类同步</t-radio>
              <t-radio :value="3">系统-商品同步</t-radio>
              <t-radio :value="4">系统-印花同步</t-radio>
              <t-radio :value="5">系统-分类商品同步</t-radio>
            </t-radio-group>
          </t-form-item>

          <t-form-item label="执行账号" name="accountIds" class="mb-1.5">
            <t-select
              v-model="formData.accountIds"
              multiple
              filterable
              placeholder="请选择执行账号"
              :loading="accountsLoading"
              style="width: 320px"
              :min-collapsed-num="2"
              @focus="fetchAccounts"
            >
              <t-option
                v-for="item in accounts"
                :key="item.id"
                :value="item.id"
                :label="item.accountName"
                :disabled="!!item.boundTaskId && item.boundTaskId !== formData.id"
              >
                <div class="flex items-center justify-between w-full overflow-hidden">
                  <div class="flex items-center gap-1.5 overflow-hidden flex-1 mr-2">
                    <span class="font-medium shrink-0">{{ item.accountName }}</span>
                    <t-tooltip
                      v-if="item.boundTaskId && item.boundTaskId !== formData.id"
                      :content="'已绑定任务: ' + item.boundTaskName"
                      placement="top"
                    >
                      <span class="text-xs text-gray-400 truncate">
                        (已绑定: {{ item.boundTaskName }})
                      </span>
                    </t-tooltip>
                  </div>
                  <t-tag v-if="item.status === 'NORMAL'" theme="success" variant="light" size="small" class="shrink-0">在线</t-tag>
                  <t-tag v-else theme="danger" variant="light" size="small" class="shrink-0">异常</t-tag>
                </div>
              </t-option>
            </t-select>
            <template #tips>系统任务建议绑定多个扫描账号以平衡负载</template>
          </t-form-item>

          <!-- 调度配置分组 -->
          <div class="schedule-group mt-1 rounded-md border border-blue-100 bg-blue-50/50 p-2">
            <div class="mb-1 flex items-center gap-2 text-sm font-medium text-blue-700">
              <t-icon name="time-filled" />
              <span>运行计划</span>
            </div>

            <t-form-item
              label="执行计划"
              name="cronExpression"
              class="mb-1.5"
            >
              <div class="flex items-center gap-2">
                <t-input
                  v-model="formData.cronExpression"
                  :disabled="uiState.isCronImmediate"
                  placeholder="未配置"
                  clearable
                  style="width: 200px"
                  @blur="(v: any) => handleInputTrim(v, formData, 'cronExpression')"
                >
                  <template #suffix>
                    <t-popup
                      v-model:visible="systemCronVisible"
                      placement="bottom-right"
                      trigger="click"
                      :overlay-inner-style="{ padding: 0 }"
                    >
                      <t-link theme="primary" variant="underline" :disabled="uiState.isCronImmediate">
                        <t-icon name="calendar" class="mr-1" />
                        可视化
                      </t-link>
                      <template #content>
                        <div class="cron-popup-container">
                          <cron-editor
                            :model-value="formData.cronExpression || ''"
                            @update:model-value="formData.cronExpression = $event"
                            @confirm="systemCronVisible = false"
                            @cancel="systemCronVisible = false"
                          />
                        </div>
                      </template>
                    </t-popup>
                  </template>
                </t-input>
                <t-checkbox v-model="uiState.isCronImmediate">立即执行</t-checkbox>
              </div>
            </t-form-item>

            <!-- 动态逻辑预览 -->
            <div
              class="mt-1 ml-[110px] rounded border border-blue-50 bg-white/60 p-1.5 text-[13px] leading-relaxed text-blue-600"
            >
              <div class="flex items-start gap-1.5">
                <t-icon name="info-circle" class="mt-0.5" />
                <div>{{ executionSummary }}</div>
              </div>
            </div>
          </div>

          <!-- 自定义底部按钮，用于触发表单提交 -->
          <div class="flex justify-end gap-3 border-t border-gray-100 pt-2.5 mt-2.5">
            <t-button variant="outline" theme="default" @click="systemDialogVisible = false">
              取消
            </t-button>
            <t-button theme="primary" type="submit" :loading="submitLoading">提交</t-button>
          </div>
        </t-form>
      </div>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import { goodsApi } from "@/api/goods";
import { taskApi } from "@/api/task";
import { settingsApi, type BuffAccount } from "@/api/settings";
import CronEditor from "@/components/CronEditor.vue";
import type { GoodsSimple } from "@/types/goods";
import type { BuffScanTask, TaskQueryParam, TaskSaveParam } from "@/types/task";
import cronParser from "cron-parser";
import { MessagePlugin, type FormRules, type SelectValue, type PrimaryTableCol } from "tdesign-vue-next";
import { computed, onMounted, reactive, ref, watch, nextTick } from "vue";

// 获取当前用户信息
const userInfo = computed(() => {
  const info = localStorage.getItem("niro-user-info");
  return info ? JSON.parse(info) : null;
});

// 是否是管理员 (BuffConstant.ADMIN_USER_ID = 1)
const isAdmin = computed(() => userInfo.value?.id === 1);

const props = defineProps({
  dialogOnly: {
    type: Boolean,
    default: false,
  },
});

// --- 表格数据 ---
const loading = ref(false);
const dataList = ref<BuffScanTask[]>([]);
const queryParams = reactive<TaskQueryParam>({
  pageNo: 1,
  pageSize: 10,
  name: "",
  status: undefined,
});
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
});

const accounts = ref<BuffAccount[]>([]);
const accountsLoading = ref(false);

const fetchAccounts = async () => {
  accountsLoading.value = true;
  try {
    accounts.value = await settingsApi.getBuffAccounts();
  } finally {
    accountsLoading.value = false;
  }
};

const cronVisible = ref(false);
const systemCronVisible = ref(false);

/**
 * 自动清除换行符和首尾空格
 */
const handleInputTrim = (val: any, target: any, key: string) => {
  if (typeof val === 'string') {
    target[key] = val.replace(/[\r\n]/g, '').trim();
  }
};

// --- 表单 UI 状态 (用于单位换算) ---
const uiState = reactive({
  durationValue: 0,
  durationUnit: "m" as "m" | "h" | "d",
  intervalMinValue: 15,
  intervalMaxValue: 20,
  intervalUnit: "s" as "s" | "m" | "h" | "d",
  isCronImmediate: true,
  isDurationUnlimited: true,
  isCycleMode: false,
  restValue: 5,
  restUnit: "m" as "m" | "h" | "d",
});

// 单位换算系数 (基准: 分钟)
const DURATION_FACTORS = {
  m: 1,
  h: 60,
  d: 1440,
};

// 单位换算系数 (基准: 秒)
const INTERVAL_FACTORS = {
  s: 1,
  m: 60,
  h: 3600,
  d: 86400,
};

/**
 * 将存储值转换为最合适的 UI 显示值和单位
 */
const convertToUi = (value: number, factors: Record<string, number>) => {
  if (!value) return { value: 0, unit: Object.keys(factors)[0] };
  const units = (Object.keys(factors) as Array<keyof typeof factors>).reverse();
  for (const unit of units) {
    const factor = factors[unit];
    if (value % factor === 0) {
      return { value: value / factor, unit };
    }
  }
  return { value, unit: Object.keys(factors)[0] };
};

const executionSummary = computed(() => {
  const cron = formData.cronExpression?.trim();
  const duration = uiState.durationValue;
  const durationUnit = { m: "分钟", h: "小时", d: "天" }[uiState.durationUnit] || "分钟";
  const intervalMin = uiState.intervalMinValue;
  const intervalMax = uiState.intervalMaxValue;
  const intervalUnit = { s: "秒", m: "分钟", h: "小时", d: "天" }[uiState.intervalUnit] || "秒";

  let summary = "";

  // 判断是否为立即启动：空表达式或全通配符（每秒执行）
  const isImmediate = !cron || cron === "* * * * * ?" || cron === "* * * * * *";

  if (isImmediate) {
    summary += `任务将立即启动。`;
  } else {
    try {
      // 1. 计算下一次执行的具体时间
      const safeExpression = cron
        .replace(/\?/g, "*")
        .replace(/last\s+(\w+)/g, "$1L")
        .replace(/last/g, "L");

      const options: any = {
        currentDate: new Date(),
        tz: "Asia/Shanghai",
      };
      if (safeExpression.split(" ").length >= 6) {
        options.hasSeconds = true;
      }

      const cp = cronParser as any;
      const interval = cp.parse(safeExpression, options);
      const nextDate = interval.next().toDate();

      const pad = (n: number) => (n < 10 ? `0${n}` : n);
      const nextTimeStr =
        `${nextDate.getFullYear()}-${pad(nextDate.getMonth() + 1)}-${pad(
          nextDate.getDate()
        )} ` +
        `${pad(nextDate.getHours())}:${pad(nextDate.getMinutes())}:${pad(
          nextDate.getSeconds()
        )}`;

      summary += `任务预计于 [${nextTimeStr}] 启动。`;
    } catch (e) {
      // 翻译失败则回退到原始表达式显示
      summary += `任务将在 Cron [${cron}] 触发时启动。`;
    }
  }

  const actionDesc = formData.taskType < 2 ? "采集价格" : "同步数据";
  if (formData.taskType < 2) {
    if (uiState.isCycleMode) {
      summary += `启动后将以 [工作 ${duration}${durationUnit} / 休息 ${uiState.restValue}${ { m: '分钟', h: '小时', d: '天' }[uiState.restUnit] }] 的周期循环运行。`;
      summary += `运行期间每隔 ${intervalMin}-${intervalMax} ${intervalUnit} 进行一次${actionDesc}。`;
    } else {
      summary += `启动后将持续运行 ${duration} ${durationUnit}，期间每隔 ${intervalMin}-${intervalMax} ${intervalUnit} 进行一次${actionDesc}。`;
    }
  } else {
    summary += `启动后将执行一次${actionDesc}。`;
  }

  return summary;
});

const columns: PrimaryTableCol[] = [
  { colKey: "id", title: "ID", width: 80, align: "left" },
  { colKey: "goods", title: "商品信息", width: 220, cell: "goods", align: "left" },
  { colKey: "taskType", title: "模式", width: 100, cell: "taskType", align: "left" },
  { colKey: "target", title: "目标配置", width: 150, cell: "target", align: "left" },
  { colKey: "accounts", title: "执行账号", width: 150, cell: "accounts", align: "left" },
  { colKey: "progress", title: "进度", width: 100, cell: "progress", align: "left" },
  { colKey: "status", title: "状态", width: 100, cell: "status", align: "left" },
  { colKey: "op", title: "操作", width: 180, cell: "op", fixed: "right", align: "center" },
];

// --- 表单数据 ---
const dialogVisible = ref(false);
const systemDialogVisible = ref(false);
const dialogTitle = ref("新增任务");
const lastModifiedTime = ref<string>(""); // 记录 Cron 修改/初始化的现实时间
const submitLoading = ref(false);
const formRef = ref();
const systemFormRef = ref();
const formData = reactive<TaskSaveParam>({
  goodsId: undefined as unknown as number,
  maxPrice: 0,
  minPaintwear: 0,
  maxPaintwear: 1,
  buyCount: 1,
  cronExpression: "",
  durationMinutes: 0,
  restPeriod: 0,
  scanInterval: 15,
  scanIntervalMin: 15,
  scanIntervalMax: 20,
  taskType: 0,
  minProfit: 0,
  accountIds: [],
});

const rules: FormRules<TaskSaveParam> = {
  accountIds: [],
  goodsId: [
    {
      required: true,
      validator: (val: any) => {
        if (formData.taskType < 2) return !!val;
        return true;
      },
      message: "请选择商品",
      type: "error",
      trigger: "blur",
    },
  ],
  maxPrice: [
    {
      required: true,
      validator: (val: any) => {
        if (formData.taskType === 0) return !!val;
        return true;
      },
      message: "请输入最高价格",
      type: "error",
      trigger: "blur",
    },
  ],
  minProfit: [
    {
      required: true,
      validator: (val: any) => {
        if (formData.taskType === 1) return val !== undefined && val !== null;
        return true;
      },
      message: "请输入最小预期利润",
      type: "error",
      trigger: "blur",
    },
  ],
  buyCount: [
    {
      required: true,
      validator: (val: any) => {
        if (formData.taskType < 2) return !!val;
        return true;
      },
      message: "请输入购买数量",
      type: "error",
      trigger: "blur",
    },
  ],
  scanInterval: [
    {
      required: true,
      validator: (val: any) => {
        if (formData.taskType >= 2) return true;
        return !!val;
      },
      message: "请输入扫描间隔",
      type: "error",
      trigger: "blur",
    },
    {
      validator: (val: any) => {
        if (formData.taskType >= 2) return true;
        return val >= 15;
      },
      message: "扫描间隔不得低于 15 秒",
      type: "error",
      trigger: "blur",
    },
  ],
};

// --- 商品搜索 ---
const goodsLoading = ref(false);
const goodsOptions = ref<GoodsSimple[]>([]);

const remoteSearchGoods = async (keyword: string) => {
  if (!keyword) return;
  goodsLoading.value = true;
  try {
    const res = await goodsApi.getSimpleList(keyword);
    goodsOptions.value = res;
  } finally {
    goodsLoading.value = false;
  }
};

// --- 方法 ---

const handleIntervalUnitChange = (unit: SelectValue) => {
  const unitStr = unit as string;
  const min = formData.taskType < 2 && unitStr === "s" ? 15 : 1;
  if (uiState.intervalMinValue < min) {
    uiState.intervalMinValue = min;
  }
  if (uiState.intervalMaxValue < min) {
    uiState.intervalMaxValue = min;
  }
};

const handleIntervalMinBlur = () => {
  const min = formData.taskType < 2 && uiState.intervalUnit === "s" ? 15 : 1;
  if (uiState.intervalMinValue < min) {
    uiState.intervalMinValue = min;
  }
  if (uiState.intervalMinValue > uiState.intervalMaxValue) {
    uiState.intervalMaxValue = uiState.intervalMinValue;
  }
};

const handleIntervalMaxBlur = () => {
  const min = formData.taskType < 2 && uiState.intervalUnit === "s" ? 15 : 1;
  if (uiState.intervalMaxValue < min) {
    uiState.intervalMaxValue = min;
  }
  if (uiState.intervalMaxValue < uiState.intervalMinValue) {
    uiState.intervalMinValue = uiState.intervalMaxValue;
  }
};

// 监听 UI 状态变化，实时同步到 formData 供校验和预览使用
watch(
  [() => uiState.intervalMinValue, () => uiState.intervalMaxValue, () => uiState.intervalUnit, () => uiState.durationValue, () => uiState.durationUnit],
  () => {
    if (formData.taskType < 2) {
    formData.scanIntervalMin = uiState.intervalMinValue * INTERVAL_FACTORS[uiState.intervalUnit];
    formData.scanIntervalMax = uiState.intervalMaxValue * INTERVAL_FACTORS[uiState.intervalUnit];
    formData.durationMinutes = uiState.durationValue * DURATION_FACTORS[uiState.durationUnit];
    formData.restPeriod = uiState.isCycleMode ? (uiState.restValue * DURATION_FACTORS[uiState.restUnit]) : 0;
  }
  },
  { immediate: true }
);

const fetchData = async () => {
  loading.value = true;
  try {
    const res = await taskApi.getPage({
      pageNo: pagination.current,
      pageSize: pagination.pageSize,
      name: queryParams.name,
      status: queryParams.status,
    });
    dataList.value = res.records;
    pagination.total = res.total;
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false;
  }
};

const onPageChange = (pageInfo: { current: number; pageSize: number }) => {
  pagination.current = pageInfo.current;
  pagination.pageSize = pageInfo.pageSize;
  fetchData();
};

const resetQuery = () => {
  queryParams.name = "";
  queryParams.status = undefined;
  fetchData();
};

const handleAdd = () => {
  dialogTitle.value = "新增任务";
  formData.id = undefined;
  formData.goodsId = undefined as unknown as number;
  formData.maxPrice = 0;
  formData.minPaintwear = 0;
  formData.maxPaintwear = 1;
  formData.buyCount = 1;
  formData.cronExpression = "";
  formData.durationMinutes = 0;
  formData.restPeriod = 0;
  formData.scanInterval = 15;
  formData.scanIntervalMin = 15;
  formData.scanIntervalMax = 20;
  formData.taskType = 0; // 默认炼金扫货
  formData.minProfit = 0;
  formData.accountIds = [];

  // 重置 UI 状态
  uiState.durationValue = 0;
  uiState.durationUnit = "m";
  uiState.isCronImmediate = true;
  uiState.isDurationUnlimited = true;
  uiState.isCycleMode = false;
  uiState.restValue = 5;
  uiState.restUnit = "m";
  uiState.intervalMinValue = 15;
  uiState.intervalMaxValue = 20;
  uiState.intervalUnit = "s";

  lastModifiedTime.value = new Date().toLocaleString(); // 初始化为当前时间
  fetchAccounts(); // 提前加载账号列表，防止显示ID
  dialogVisible.value = true;
  goodsOptions.value = []; // reset options
  nextTick(() => {
    formRef.value?.clearValidate();
  });
};

const handleAddSystem = () => {
  dialogTitle.value = "新增系统任务";
  formData.id = undefined;
  formData.goodsId = undefined as unknown as number;
  formData.maxPrice = 0;
  formData.minPaintwear = 0;
  formData.maxPaintwear = 1;
  formData.buyCount = 1;
  formData.cronExpression = "0 0 3 * * SUN"; // 默认周日凌晨3点
  formData.durationMinutes = 0;
  formData.scanInterval = 15;
  formData.scanIntervalMin = 15;
  formData.scanIntervalMax = 20;
  formData.taskType = 2; // 默认系统-分类同步
  formData.minProfit = 0;
  formData.accountIds = [];

  uiState.isCronImmediate = false;
  uiState.isDurationUnlimited = true;

  lastModifiedTime.value = new Date().toLocaleString();
  fetchAccounts(); // 提前加载账号列表，防止显示ID
  systemDialogVisible.value = true;
  nextTick(() => {
    systemFormRef.value?.clearValidate();
  });
};

const handleEdit = (row: BuffScanTask) => {
  dialogTitle.value = "编辑任务";
  Object.assign(formData, {
    id: row.id,
    goodsId: row.goodsId,
    maxPrice: row.maxPrice,
    minPaintwear: row.minPaintwear,
    maxPaintwear: row.maxPaintwear,
    buyCount: row.buyCount,
    cronExpression: row.cronExpression || "", // 确保默认为空字符串
    durationMinutes: row.durationMinutes || 0,
    restPeriod: row.restPeriod || 0,
    scanInterval: row.scanInterval || 15,
    scanIntervalMin: row.scanIntervalMin || 15,
    scanIntervalMax: row.scanIntervalMax || 20,
    taskType: row.taskType || 0,
    minProfit: row.minProfit || 0,
    accountIds: row.accountIds || [],
  });

  // 初始化 UI 状态
  const durationUi = convertToUi(formData.durationMinutes || 0, DURATION_FACTORS);
  uiState.durationValue = durationUi.value;
  uiState.durationUnit = durationUi.unit as any;
  uiState.isDurationUnlimited = !formData.durationMinutes && !formData.restPeriod;
  uiState.isCycleMode = !!formData.restPeriod;

  const restUi = convertToUi(formData.restPeriod || 0, DURATION_FACTORS);
  uiState.restValue = restUi.value || 5;
  uiState.restUnit = restUi.unit as any;

  uiState.isCronImmediate = !formData.cronExpression;

  const intervalMinUi = convertToUi(formData.scanIntervalMin || 15, INTERVAL_FACTORS);
  const intervalMaxUi = convertToUi(formData.scanIntervalMax || 20, INTERVAL_FACTORS);
  uiState.intervalMinValue = intervalMinUi.value;
  uiState.intervalMaxValue = intervalMaxUi.value;
  uiState.intervalUnit = intervalMinUi.unit as any;

  // 预填充当前商品到选项中，否则显示ID
  if (row.goodsId && row.name) {
    goodsOptions.value = [{ goodsId: row.goodsId, name: row.name }];
  }

  fetchAccounts(); // 提前加载账号列表，防止显示ID

  if (formData.taskType < 2) {
    dialogVisible.value = true;
    nextTick(() => {
      formRef.value?.clearValidate();
    });
  } else {
    systemDialogVisible.value = true;
    nextTick(() => {
      systemFormRef.value?.clearValidate();
    });
  }
};

// 暴露给外部调用的方法，用于从商品列表页打开
defineExpose({
  handleAdd,
  handleEdit,
  openWithGoods: (goods: GoodsSimple) => {
    dialogTitle.value = "新增任务";
    Object.assign(formData, {
      id: undefined,
      goodsId: goods.goodsId,
      maxPrice: 0,
      minPaintwear: 0,
      maxPaintwear: 1,
      buyCount: 1,
      cronExpression: "",
      durationMinutes: 0,
      scanInterval: 15,
      scanIntervalMin: 15,
      scanIntervalMax: 20,
      taskType: 0,
      minProfit: 0,
    });
    uiState.durationValue = 0;
    uiState.durationUnit = "m";
    uiState.isCronImmediate = true;
    uiState.isDurationUnlimited = true;
    uiState.intervalMinValue = 15;
    uiState.intervalMaxValue = 20;
    uiState.intervalUnit = "s";
    goodsOptions.value = [{ goodsId: goods.goodsId, name: goods.name }];
    fetchAccounts(); // 提前加载账号列表，防止显示ID
    dialogVisible.value = true;
    nextTick(() => {
      formRef.value?.clearValidate();
    });
  },
});

// 监听任务类型变化
watch(
  () => formData.taskType,
  (newVal) => {
    // 仅在新增任务或 Cron 为空时填充默认推荐值
    if (!formData.id && !formData.cronExpression) {
      if (newVal === 2) {
        // 全量分类同步：每周日凌晨 3 点
        formData.cronExpression = "0 0 3 * * SUN";
      } else if (newVal === 3) {
        // 全量商品同步：每天凌晨 4 点
        formData.cronExpression = "0 0 4 * * ?";
      }
    }
  }
);

// 监听 Cron 表达式变化，记录现实时间
watch(
  () => formData.cronExpression,
  (newVal, oldVal) => {
    if (newVal !== oldVal) {
      lastModifiedTime.value = new Date().toLocaleString();
      // 如果手动输入了内容，自动取消“立即执行”勾选
      if (newVal) {
        uiState.isCronImmediate = false;
      }
    }
  }
);

// 监听“立即执行”复选框
watch(
  () => uiState.isCronImmediate,
  (newVal) => {
    if (newVal) {
      formData.cronExpression = "";
    }
  }
);

// 监听“不限时间”复选框
watch(
  () => uiState.isDurationUnlimited,
  (newVal) => {
    if (newVal) {
      uiState.durationValue = 0;
    }
  }
);

const handleSubmit = async ({ validateResult, firstError }: any) => {
  // 提交前进行单位换算
  if (formData.taskType < 2) {
    formData.durationMinutes = uiState.durationValue * DURATION_FACTORS[uiState.durationUnit];
    formData.restPeriod = uiState.isCycleMode ? (uiState.restValue * DURATION_FACTORS[uiState.restUnit]) : 0;
    formData.scanIntervalMin = uiState.intervalMinValue * INTERVAL_FACTORS[uiState.intervalUnit];
    formData.scanIntervalMax = uiState.intervalMaxValue * INTERVAL_FACTORS[uiState.intervalUnit];
    // 保持scanInterval字段用于向后兼容
    formData.scanInterval = formData.scanIntervalMin;
  } else {
    // 系统任务默认值 (设置 5s 以绕过后端 @Min(5) 校验，虽然系统任务不使用该字段)
    formData.durationMinutes = 0;
    formData.scanInterval = 5;
    formData.scanIntervalMin = 5;
    formData.scanIntervalMax = 5;
  }

  if (validateResult !== true) {
    console.log("表单校验失败:", firstError);
    return;
  }

  submitLoading.value = true;
  try {
    if (formData.id) {
      await taskApi.update(formData);
      MessagePlugin.success("更新成功");
    } else {
      await taskApi.add(formData);
      MessagePlugin.success("创建成功");
    }
    dialogVisible.value = false;
    systemDialogVisible.value = false;
    if (!props.dialogOnly) {
      fetchData();
    }
  } finally {
    submitLoading.value = false;
  }
};

const handleDelete = async (row: BuffScanTask) => {
  await taskApi.delete(row.id);
  MessagePlugin.success("删除成功");
  fetchData();
};

const handleStatus = async (row: BuffScanTask, status: number) => {
  if (status === 1 && (!row.accountIds || row.accountIds.length === 0)) {
    MessagePlugin.error(`${row.name}未绑定执行账号`);
    return;
  }
  try {
    await taskApi.updateStatus(row.id, status);
    MessagePlugin.success(status === 1 ? "任务已启动" : "任务已停止");
    fetchData();
  } catch (err) {
    // 错误已由拦截器处理
  }
};

onMounted(() => {
  if (!props.dialogOnly) {
    fetchData();
  }
});
</script>

<style scoped>
.task-edit-dialog :deep(.t-dialog) {
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.12); /* 弹窗添加阴影 */
  border-radius: 8px;
}

.task-edit-dialog :deep(.t-dialog__body) {
  padding: 8px 16px; /* 减小对话框内容区域内边距 */
}

.task-edit-dialog :deep(.t-dialog__header) {
  padding: 12px 16px; /* 减小对话框头部内边距 */
}

.form-container {
  padding: 8px 12px;
  border: 1px solid #ebeef5; /* 表单范围添加边框 */
  border-radius: 6px;
  background-color: #fafafa;
}

.compact-form :deep(.t-form__item) {
  /* 进一步压缩行高 */
  min-height: auto;
  margin-bottom: 12px !important; /* 增加一点间距，防止过于拥挤 */
  align-items: flex-start; /* 标签置顶对齐 */
}

.compact-form :deep(.t-form__label) {
  /* 缩小标签高度，但不固定死，防止折行重叠 */
  min-height: 30px;
  line-height: 30px;
  padding-right: 8px;
}

.compact-form :deep(.t-form__controls) {
  min-height: auto;
  display: flex;
  flex-direction: column;
}

.compact-form :deep(.t-input),
.compact-form :deep(.t-input-number),
.compact-form :deep(.t-select__wrap) {
  /* 统一组件尺寸感，但允许高度自适应 */
  min-height: 30px;
}

.compact-form :deep(.t-input__inner),
.compact-form :deep(.t-input-number__input) {
  height: 28px;
}

.compact-form :deep(.t-radio-group) {
  /* 单选框组允许自然换行 */
  min-height: 30px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}

.compact-form :deep(.t-form__controls-content) {
  /* 确保内容区域不被压缩，且能够容纳报错信息 */
  width: 100%;
  min-height: 30px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}

.compact-form :deep(.t-form__error-msg) {
  /* 报错信息显示时，确保它占据空间并把下方元素顶开 */
  position: relative !important;
  top: 0 !important;
  left: 0 !important;
  margin-top: 4px;
  margin-bottom: 0;
  font-size: 12px;
  line-height: 1.2;
  display: block;
  width: 100%;
}

.compact-form :deep(.t-form__tips) {
  font-size: 12px;
  line-height: 1.4;
  margin-top: 2px;
  color: #999;
}

.cron-popup-container {
  width: 520px;
  max-height: 550px;
  overflow: hidden;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

</style>
