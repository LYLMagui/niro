<template>
  <div class="p-6">
    <div class="grid grid-cols-1 items-start gap-6 lg:grid-cols-[72%_1fr]">
      <!-- 左侧：Content (72%) -->
      <div class="min-w-0">
        <!-- 账号列表 -->
        <t-card :bordered="false" class="embedded-card h-full shadow-sm">
          <template #title>
            <div class="flex items-center">
              <t-icon name="user-setting" class="mr-2 text-blue-600" />
              <span class="text-lg font-bold text-gray-800">BUFF 账号管理</span>
            </div>
          </template>
          <template #actions>
            <t-space size="8px">
              <t-button
                variant="outline"
                theme="default"
                size="small"
                :loading="checkingAll"
                class="rounded !border-gray-200 !text-gray-600 transition-all duration-300"
                @click="onCheckAll"
              >
                <template #icon><t-icon name="refresh" /></template>
                一键检测
              </t-button>
              <t-button
                theme="primary"
                size="small"
                class="rounded transition-all duration-300"
                @click="onAddAccount"
              >
                <template #icon><t-icon name="add" /></template>
                新增账号
              </t-button>
            </t-space>
          </template>

          <t-table
            :data="accounts"
            :columns="accountColumns"
            row-key="id"
            :loading="accountsLoading"
            hover
            :header-affixed-top="true"
            class="embedded-table w-full"
            :bordered="false"
          >
            <!-- 空状态 (保持原样) -->
            <template #empty>
              <t-empty
                icon="component-breadcrumb"
                description="暂无账号配置，请点击上方“新增账号”开始使用"
              />
            </template>

            <!-- 账号名称列增加图标标识 (保持原样) -->
            <template #accountName="{ row }">
              <div class="flex items-center space-x-2">
                <t-icon name="user-circle" class="text-blue-500" size="18px" />
                <span class="font-semibold text-[#1d2129]">{{ row.accountName }}</span>
              </div>
            </template>

            <!-- 备注列 -->
            <template #remark="{ row }">
              <t-tooltip v-if="row.remark" :content="row.remark">
                <span class="text-[13px] text-[#5e6d82]">{{ row.remark }}</span>
              </t-tooltip>
              <span v-else class="text-[12px] text-gray-400 italic">无</span>
            </template>

            <!-- 角色标签美化 (保持原样) -->
            <template #role="{ row }">
              <t-tag
                variant="outline"
                shape="round"
                size="small"
                :class="[
                  'compact-tag px-2 font-bold transition-all duration-300',
                  getGhostTagClass(row.role),
                ]"
              >
                <template #icon>
                  <t-icon
                    :name="
                      row.role === 'SCAN' ? 'search' : row.role === 'TRADE' ? 'cart' : 'view-module'
                    "
                  />
                </template>
                {{ getRoleLabel(row.role) }}
              </t-tag>
            </template>

            <!-- 状态呼吸灯与动效 (保持原样) -->
            <template #status="{ row }">
              <div v-if="row.checking" class="flex items-center text-blue-600">
                <t-loading size="small" text="检测中" inherit-color />
              </div>
              <div v-else class="flex items-center">
                <span
                  :class="[
                    'relative inline-block h-2 w-2 rounded-full',
                    getStatusTheme(row.status),
                    row.status === 'SCANNING' || row.status === 'COOLING_DOWN' ? 'breathing' : '',
                    row.status === 'NORMAL' ? 'status-dot-online' : '',
                  ]"
                ></span>
                <div class="ml-2 flex flex-col">
                  <div class="flex items-center">
                    <span :class="['text-[13px] font-semibold', getStatusTextColor(row.status)]">
                      {{ getStatusLabel(row.status) }}
                    </span>
                    <t-tooltip v-if="row.warningMsg" :content="row.warningMsg">
                      <t-icon name="error-circle" class="ml-1 text-red-500" />
                    </t-tooltip>
                  </div>
                </div>
              </div>
            </template>

            <!-- 统计列美化 (保持原样) -->
            <template #stats="{ row }">
              <div class="flex h-full min-h-[44px] flex-col justify-center gap-1 py-1">
                <div class="flex items-center leading-tight">
                  <span class="mr-2 shrink-0 text-[12px] text-[#86909c]">扫描:</span>
                  <span class="font-numeric text-[13px] font-bold text-blue-600">
                    {{ (row.todayScanCount || 0).toLocaleString() }}
                  </span>
                </div>
                <div class="flex items-center leading-tight">
                  <span class="mr-2 shrink-0 text-[12px] text-[#86909c]">成功:</span>
                  <span class="font-numeric text-[13px] font-bold text-green-600">
                    {{ ((row.tradeSuccessRate || 0) * 100).toFixed(1) }}%
                  </span>
                </div>
              </div>
            </template>

            <!-- 余额显示/隐藏 (保持原样) -->
            <template #balance="{ row }">
              <div
                class="group flex cursor-pointer flex-col items-end justify-center py-1 select-none"
                @click="balanceVisible = !balanceVisible"
              >
                <div
                  :class="[
                    'font-numeric text-sm font-bold transition-all duration-300',
                    getBalanceClass(row),
                    (row.balance || 0) > 1000 ? 'high-value-shadow' : '',
                  ]"
                >
                  <span class="mr-0.5 text-[10px] opacity-60">¥</span>
                  <span v-if="balanceVisible">{{ row.balance?.toFixed(2) }}</span>
                  <span v-else>****</span>
                </div>
                <div
                  v-if="row.pendingBalance > 0"
                  class="font-numeric mt-1 text-[12px] leading-tight font-medium text-orange-500 antialiased"
                >
                  <span v-if="balanceVisible">待结算: ¥{{ row.pendingBalance?.toFixed(2) }}</span>
                  <span v-else>****</span>
                </div>
              </div>
            </template>

            <!-- 最后检测时间 (保持原样) -->
            <template #lastCheckTime="{ row }">
              <div class="flex items-center space-x-2">
                <div class="font-numeric text-[12px] font-medium text-[#86909c]">
                  {{
                    row.lastCheckTime
                      ? row.lastCheckTime.replace("T", " ").substring(5, 16)
                      : "等待检测"
                  }}
                </div>
              </div>
            </template>

            <!-- 操作列 (保持原样) -->
            <template #operation="{ row }">
              <div class="flex items-center justify-center space-x-3">
                <t-tooltip content="编辑">
                  <t-link
                    theme="default"
                    :disabled="row.checking"
                    class="!text-gray-400 transition-colors hover:!text-blue-600"
                    @click="onEditAccount(row)"
                  >
                    <t-icon name="edit" />
                  </t-link>
                </t-tooltip>
                <t-tooltip content="检测">
                  <t-link
                    theme="default"
                    :disabled="row.checking"
                    class="!text-gray-400 transition-colors hover:!text-blue-600"
                    @click="onCheckAccount(row)"
                  >
                    <t-icon name="refresh" :class="{ 'checking-rotate': row.checking }" />
                  </t-link>
                </t-tooltip>
                <t-popconfirm
                  :content="
                    row.boundTaskId
                      ? `账号已绑定任务【${row.boundTaskName}】，无法删除`
                      : '确定删除该账号吗？'
                  "
                  :disabled="!!row.boundTaskId"
                  @confirm="onDeleteAccount(row)"
                >
                  <t-tooltip
                    :content="
                      row.boundTaskId ? `账号已绑定任务【${row.boundTaskName}】，无法删除` : '删除'
                    "
                  >
                    <t-link
                      theme="default"
                      :disabled="row.checking || !!row.boundTaskId"
                      class="!text-gray-400 transition-colors hover:!text-red-500 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      <t-icon name="delete" />
                    </t-link>
                  </t-tooltip>
                </t-popconfirm>
              </div>
            </template>
          </t-table>
        </t-card>
      </div>

      <!-- 右侧：Aside 整体化 (30%) -->
      <div class="flex h-fit flex-col lg:sticky lg:top-6">
        <t-card :bordered="false" class="overflow-hidden shadow-sm">
          <template #title>
            <div class="flex items-center">
              <t-icon name="setting" class="mr-2 text-gray-500" size="20px" />
              <span class="text-base font-bold text-gray-800">控制面板</span>
            </div>
          </template>

          <div class="space-y-6 px-4 py-2">
            <!-- 统计板块 -->
            <section>
              <div
                class="mb-3 flex items-center text-[13px] font-bold tracking-wider text-gray-700 uppercase"
              >
                <span class="mr-2">实时统计</span>
                <div class="h-[1px] flex-1 bg-gray-100"></div>
              </div>
              <div class="grid grid-cols-2 gap-3">
                <div
                  class="rounded-lg border border-blue-100 bg-blue-50/50 p-3 transition-all hover:bg-blue-50"
                >
                  <div class="mb-1 text-[12px] text-[#86909c]">活跃账号</div>
                  <div class="flex items-baseline gap-1">
                    <span class="font-numeric text-xl font-bold text-[#0052d9]">
                      {{ accounts.filter((a) => a.status === "NORMAL").length }}
                    </span>
                    <span class="text-[11px] text-[#86909c]">/ {{ accounts.length }}</span>
                  </div>
                </div>
                <div
                  class="rounded-lg border border-orange-100 bg-orange-50/50 p-3 transition-all hover:bg-orange-50"
                >
                  <div class="mb-1 flex items-center justify-between text-[12px] text-[#86909c]">
                    <span>总资产</span>
                    <t-tooltip
                      v-if="totalPendingBalance > 0"
                      :content="`可用: ¥${totalBalance.toFixed(2)} | 待结: ¥${totalPendingBalance.toFixed(2)}`"
                    >
                      <span class="cursor-help text-[12px] font-medium text-orange-500 antialiased">
                        待结: ¥{{ balanceVisible ? totalPendingBalance.toFixed(2) : "***" }}
                      </span>
                    </t-tooltip>
                  </div>
                  <div class="flex items-baseline gap-0.5">
                    <span v-if="balanceVisible" class="text-[12px] font-bold text-[#d97706]">
                      ¥
                    </span>
                    <span class="font-numeric text-xl font-bold text-[#d97706]">
                      {{ balanceVisible ? totalAssets.toFixed(2) : "****" }}
                    </span>
                  </div>
                </div>
              </div>
            </section>

            <!-- 交易配置 -->
            <section>
              <div
                class="mb-3 flex items-center text-[13px] font-bold tracking-wider text-gray-700 uppercase"
              >
                <span class="mr-2">交易配置</span>
                <div class="h-[1px] flex-1 bg-gray-100"></div>
              </div>
              <t-form :data="formData" label-align="top" size="small" @submit="onSubmit">
                <t-form-item label="默认支付方式" name="paymentMethod">
                  <template #label>
                    <span class="text-[#86909c]">默认支付方式</span>
                  </template>
                  <t-radio-group
                    v-model="formData.paymentMethod"
                    variant="default-filled"
                    size="small"
                    class="w-full"
                  >
                    <t-radio-button value="BALANCE">网易支付</t-radio-button>
                    <t-radio-button value="BUFF_BALANCE">BUFF余额</t-radio-button>
                    <t-radio-button value="ALIPAY">支付宝</t-radio-button>
                    <t-radio-button value="WECHAT">微信</t-radio-button>
                  </t-radio-group>
                </t-form-item>

                <div class="mt-4 flex items-center justify-between">
                  <span class="text-[13px] text-[#86909c]">企业微信通知</span>
                  <t-switch v-model="wecomEnabled" size="small" />
                </div>
              </t-form>
            </section>

            <!-- 参数详情 (折叠) -->
            <section v-if="wecomEnabled">
              <div
                class="mb-3 flex items-center text-[13px] font-bold tracking-wider text-gray-700 uppercase"
              >
                <span class="mr-2">通知参数</span>
                <div class="h-[1px] flex-1 bg-gray-100"></div>
              </div>
              <t-form :data="formData" label-align="top" size="small" @submit="onSubmit">
                <t-collapse :borderless="true" class="bg-transparent !p-0" :default-value="[]">
                  <t-collapse-panel value="wecom" class="!bg-transparent">
                    <template #header>
                      <span class="text-[13px] text-[#86909c]">企业微信凭据</span>
                    </template>
                    <div class="space-y-3 pt-2">
                      <t-form-item label="CorpID" name="wecomCorpid">
                        <template #label><span class="text-[#86909c]">CorpID</span></template>
                        <t-input
                          v-model="formData.wecomCorpid"
                          placeholder="ww..."
                          @blur="(v: any) => handleInputTrim(v, formData, 'wecomCorpid')"
                        />
                      </t-form-item>
                      <t-form-item label="CorpSecret" name="wecomCorpsecret">
                        <template #label><span class="text-[#86909c]">CorpSecret</span></template>
                        <t-input
                          v-model="formData.wecomCorpsecret"
                          type="password"
                          placeholder="******"
                          @blur="(v: any) => handleInputTrim(v, formData, 'wecomCorpsecret')"
                        />
                      </t-form-item>
                      <div class="grid grid-cols-2 gap-3">
                        <t-form-item label="AgentID" name="wecomAgentid">
                          <template #label><span class="text-[#86909c]">AgentID</span></template>
                          <t-input
                            v-model="formData.wecomAgentid"
                            placeholder="1000..."
                            @blur="(v: any) => handleInputTrim(v, formData, 'wecomAgentid')"
                          />
                        </t-form-item>
                        <t-form-item label="接收人" name="wecomTouser">
                          <template #label><span class="text-[#86909c]">接收人</span></template>
                          <t-input
                            v-model="formData.wecomTouser"
                            placeholder="@all"
                            @blur="(v: any) => handleInputTrim(v, formData, 'wecomTouser')"
                          />
                        </t-form-item>
                      </div>
                    </div>
                  </t-collapse-panel>
                </t-collapse>
              </t-form>
            </section>
          </div>

          <!-- 底部固定保存按钮 -->
          <div class="relative mt-4 border-t border-gray-50 bg-white p-4">
            <!-- 测试通知按钮 -->
            <div class="absolute top-[-28px] right-4">
              <t-button
                v-if="wecomEnabled"
                variant="text"
                theme="primary"
                size="small"
                :loading="testNotifyLoading"
                class="!px-2"
                @click="onTestNotify"
              >
                <template #icon><t-icon name="chat" /></template>
                发送测试消息
              </t-button>
            </div>
            <t-button
              theme="primary"
              type="submit"
              block
              :loading="loading"
              size="small"
              class="h-9 rounded shadow-sm"
              @click="onSubmit({ validateResult: true } as any)"
            >
              保存全局配置
            </t-button>
          </div>
        </t-card>
      </div>
    </div>
    <!-- 账号编辑弹窗 (保持原样) -->
    <t-dialog
      v-model:visible="accountDialogVisible"
      :header="accountDialogTitle"
      :footer="false"
      width="520px"
    >
      <t-form
        ref="accountFormRef"
        :data="accountFormData"
        :rules="accountRules"
        label-align="top"
        class="overflow-x-hidden p-1"
        @submit="onAccountSubmit"
      >
        <div class="flex gap-6">
          <t-form-item label="账号名称" name="accountName" class="min-w-0 flex-[1.5]">
            <t-input
              v-model="accountFormData.accountName"
              placeholder="如：扫描账号01"
              @blur="(v: any) => handleInputTrim(v, accountFormData, 'accountName')"
            />
          </t-form-item>
          <t-form-item label="权重 (1-10)" name="weight" class="w-[140px] shrink-0">
            <t-input-number
              v-model="accountFormData.weight"
              :min="1"
              :max="10"
              class="w-full"
              auto-width
            />
          </t-form-item>
        </div>

        <t-form-item label="Cookie (buff_cookie)" name="buffCookie">
          <t-textarea
            v-model="accountFormData.buffCookie"
            placeholder="请粘贴 Cookie 字符串..."
            :autosize="{ minRows: 3, maxRows: 5 }"
            class="custom-textarea"
            @blur="(v: any) => handleInputTrim(v, accountFormData, 'buffCookie')"
          />
        </t-form-item>

        <t-form-item label="角色" name="role">
          <t-select v-model="accountFormData.role">
            <t-option label="扫描 (仅扫描)" value="SCAN" />
            <t-option label="下单 (仅下单)" value="TRADE" />
            <t-option label="全能 (扫描+下单)" value="BOTH" />
          </t-select>
        </t-form-item>

        <t-form-item label="备注" name="remark">
          <t-input
            v-model="accountFormData.remark"
            placeholder="可选备注信息"
            @blur="(v: any) => handleInputTrim(v, accountFormData, 'remark')"
          />
        </t-form-item>

        <div class="mt-8 flex justify-end gap-3">
          <t-button
            variant="outline"
            class="rounded-md transition-all duration-300"
            @click="accountDialogVisible = false"
          >
            取消
          </t-button>
          <t-button
            theme="primary"
            type="submit"
            :loading="accountSubmitLoading"
            class="rounded-md px-8 transition-all duration-300"
          >
            确定
          </t-button>
        </div>
      </t-form>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import {
  settingsApi,
  UserBuffSettings,
  BuffAccount,
  BuffAccountRole,
  BuffAccountStatus,
} from "@/api/settings";
import {
  FormRule,
  MessagePlugin,
  SubmitContext,
  PrimaryTableCol,
  TableRowData,
} from "tdesign-vue-next";
import { computed, onMounted, reactive, ref } from "vue";

// --- 通用配置部分 ---
const loading = ref(false);
const testNotifyLoading = ref(false);
const wecomEnabled = ref(false);
const balanceVisible = ref(true);

/**
 * 自动清除换行符和首尾空格
 */
const handleInputTrim = (val: any, target: any, key: string) => {
  if (typeof val === "string") {
    target[key] = val.replace(/[\r\n]/g, "").trim();
  }
};

const formData = reactive<UserBuffSettings>({
  paymentMethod: "BALANCE",
  wecomCorpid: "",
  wecomCorpsecret: "",
  wecomAgentid: "",
  wecomTouser: "@all",
});

const fetchSettings = async () => {
  try {
    const res = await settingsApi.getSettings();
    if (res) {
      Object.assign(formData, res);
      // 如果企业ID为空，默认收起通知配置
      wecomEnabled.value = !!res.wecomCorpid;
    }
  } catch (e) {
    console.error(e);
  }
};

const onSubmit = async (context: SubmitContext) => {
  if (context.validateResult === true) {
    loading.value = true;
    try {
      await settingsApi.saveSettings(formData);
      MessagePlugin.success("全局配置已同步");
    } finally {
      loading.value = false;
    }
  }
};

const onTestNotify = async () => {
  testNotifyLoading.value = true;
  try {
    await settingsApi.sendTestNotify();
    MessagePlugin.success("测试通知已发送，请检查企业微信");
  } finally {
    testNotifyLoading.value = false;
  }
};

// --- BUFF账号部分 ---
const accounts = ref<BuffAccount[]>([]);
const accountsLoading = ref(false);
const checkingAll = ref(false);
const accountDialogVisible = ref(false);
const accountDialogTitle = ref("新增账号");
const accountSubmitLoading = ref(false);
const accountFormRef = ref();

const totalBalance = computed(() => accounts.value.reduce((sum, a) => sum + (a.balance || 0), 0));
const totalPendingBalance = computed(() =>
  accounts.value.reduce((sum, a) => sum + (a.pendingBalance || 0), 0)
);
const totalAssets = computed(() => totalBalance.value + totalPendingBalance.value);

const accountFormData = reactive<BuffAccount>({
  accountName: "",
  buffCookie: "",
  role: "SCAN",
  weight: 1,
  status: "NORMAL",
  balance: 0,
  pendingBalance: 0,
  failCount: 0,
});

const accountRules: Record<string, FormRule[]> = {
  accountName: [{ required: true, message: "账号名称不能为空", type: "error" }],
  buffCookie: [{ required: true, message: "Cookie 不能为空", type: "error" }],
  role: [{ required: true, message: "请选择角色", type: "error" }],
};

const accountColumns: PrimaryTableCol<TableRowData>[] = [
  {
    colKey: "accountName",
    title: "账号",
    width: 140,
    ellipsis: true,
    cell: "accountName",
    align: "left",
  },
  { colKey: "remark", title: "备注", width: 120, ellipsis: true, cell: "remark", align: "left" },
  { colKey: "role", title: "角色", width: 100, cell: "role", align: "left" },
  { colKey: "status", title: "状态", width: 120, cell: "status", align: "left" },
  { colKey: "stats", title: "实时统计", width: 150, cell: "stats", align: "left" },
  { colKey: "balance", title: "余额", width: 110, cell: "balance", align: "right" },
  { colKey: "lastCheckTime", title: "最后检测", width: 140, cell: "lastCheckTime", align: "left" },
  {
    colKey: "operation",
    title: "操作",
    width: 130,
    fixed: "right",
    cell: "operation",
    align: "center",
  },
];

const fetchAccounts = async () => {
  accountsLoading.value = true;
  try {
    const res = await settingsApi.getBuffAccounts();
    accounts.value = res || [];
  } finally {
    accountsLoading.value = false;
  }
};

const onAddAccount = () => {
  accountDialogTitle.value = "新增账号";
  Object.assign(accountFormData, {
    id: undefined,
    accountName: "",
    buffCookie: "",
    role: "SCAN",
    weight: 1,
    status: "NORMAL",
    balance: 0,
    pendingBalance: 0,
    failCount: 0,
    remark: "",
  });
  accountDialogVisible.value = true;
};

const onEditAccount = (row: BuffAccount) => {
  accountDialogTitle.value = "编辑账号";
  Object.assign(accountFormData, row);
  accountDialogVisible.value = true;
};

const onAccountSubmit = async (context: SubmitContext) => {
  if (context.validateResult === true) {
    accountSubmitLoading.value = true;
    try {
      await settingsApi.saveBuffAccount(accountFormData);
      MessagePlugin.success("账号已保存");
      accountDialogVisible.value = false;
      fetchAccounts();
    } finally {
      accountSubmitLoading.value = false;
    }
  }
};

const onDeleteAccount = async (row: BuffAccount) => {
  if (!row.id) return;
  try {
    await settingsApi.deleteBuffAccount(row.id);
    MessagePlugin.success("账号已移除");
    fetchAccounts();
  } catch (e) {
    console.error(e);
  }
};

const onCheckAccount = async (row: BuffAccount) => {
  if (!row.id) return;
  row.checking = true;
  try {
    await settingsApi.checkBuffAccount(row.id);
    MessagePlugin.success(`账号 [${row.accountName}] 检测完成`);
    fetchAccounts();
  } catch (e) {
    console.error(e);
  } finally {
    row.checking = false;
  }
};

const onCheckAll = async () => {
  if (accounts.value.length === 0) return;
  checkingAll.value = true;
  // 给所有账号设置检测状态
  accounts.value.forEach((a) => (a.checking = true));
  try {
    await settingsApi.checkAllBuffAccounts();
    MessagePlugin.success("全局账号检测完成");
    fetchAccounts();
  } catch (e) {
    console.error(e);
  } finally {
    checkingAll.value = false;
  }
};

const getRoleLabel = (role: BuffAccountRole) => {
  const map: Record<BuffAccountRole, string> = { SCAN: "扫描号", TRADE: "下单号", BOTH: "全能号" };
  return map[role] || role;
};

const getGhostTagClass = (role: BuffAccountRole) => {
  const map: Record<BuffAccountRole, string> = {
    SCAN: "tag-ghost-blue",
    TRADE: "tag-ghost-purple",
    BOTH: "tag-ghost-green",
  };
  return map[role] || "";
};

const getStatusLabel = (status: BuffAccountStatus) => {
  const map: Record<BuffAccountStatus, string> = {
    NORMAL: "在线",
    BANNED: "封禁",
    MARKET_RESTRICTED: "市场限频",
    TRADE_RESTRICTED: "交易限制",
    INVALID: "Cookie失效",
    SCANNING: "正在扫描",
    COOLING_DOWN: "冷却中",
  };
  return map[status] || status;
};

const getStatusTheme = (status: BuffAccountStatus) => {
  const map: Record<BuffAccountStatus, string> = {
    NORMAL: "bg-green-500",
    BANNED: "bg-red-600",
    MARKET_RESTRICTED: "bg-orange-400",
    TRADE_RESTRICTED: "bg-orange-500",
    INVALID: "bg-red-400",
    SCANNING: "bg-blue-500",
    COOLING_DOWN: "bg-purple-400",
  };
  return map[status] || "bg-gray-400";
};

const getStatusTextColor = (status: BuffAccountStatus) => {
  const map: Record<BuffAccountStatus, string> = {
    NORMAL: "text-green-600",
    BANNED: "text-red-600",
    MARKET_RESTRICTED: "text-orange-500",
    TRADE_RESTRICTED: "text-orange-600",
    INVALID: "text-red-500",
    SCANNING: "text-blue-600",
    COOLING_DOWN: "text-purple-600",
  };
  return map[status] || "text-gray-500";
};

const getBalanceClass = (row: BuffAccount) => {
  // “射手”账号（下单/全能）使用金库金，普通账号使用精致橙
  if (row.role === "TRADE" || row.role === "BOTH") {
    return "text-vault-gold";
  }
  return "text-refined-orange";
};

onMounted(async () => {
  fetchSettings();
  await fetchAccounts();
  // 进入页面后自动触发一次一键检测
  if (accounts.value.length > 0) {
    onCheckAll();
  }
});
</script>

<style scoped>
/* 状态呼吸灯动画 (Tailwind 无法直接实现 keyframes 扩展动画) */
.breathing::after {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  content: "";
  background-color: inherit;
  border-radius: 50%;
  opacity: 0.6;
  animation: breathe 2s infinite ease-in-out;
}

@keyframes breathe {
  0% {
    opacity: 0.6;
    transform: scale(1);
  }
  50% {
    opacity: 0;
    transform: scale(2.5);
  }
  100% {
    opacity: 0.6;
    transform: scale(1);
  }
}

/* 弹窗样式优化 */
:deep(.t-dialog__body) {
  padding: 16px 24px !important;
  overflow-x: hidden;
}

:deep(.custom-textarea) {
  background-color: #f9fafb !important;
  border: 1px solid #dcdfe6 !important;
  border-radius: 4px !important;
  transition: all 0.2s cubic-bezier(0.38, 0, 0.24, 1);
}

:deep(.custom-textarea:focus) {
  background-color: #fff !important;
  border-color: #0052d9 !important;
  box-shadow: 0 0 0 2px rgba(0, 82, 217, 0.1);
}
</style>
