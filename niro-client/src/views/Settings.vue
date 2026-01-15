<template>
  <div class="p-6">
    <div class="grid grid-cols-1 lg:grid-cols-[72%_1fr] gap-6 items-start">
      <!-- 左侧：Content (72%) -->
      <div class="min-w-0">
        <!-- 账号列表 -->
        <t-card :bordered="false" class="shadow-sm embedded-card h-full">
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
                @click="onCheckAll"
                :loading="checkingAll"
                class="rounded transition-all duration-300 !text-gray-600 !border-gray-200"
              >
                <template #icon><t-icon name="refresh" /></template>
                一键检测
              </t-button>
              <t-button
                theme="primary"
                size="small"
                @click="onAddAccount"
                class="rounded transition-all duration-300"
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
              <t-empty icon="component-breadcrumb" description="暂无账号配置，请点击上方“新增账号”开始使用" />
            </template>

            <!-- 账号名称列增加图标标识 (保持原样) -->
            <template #accountName="{ row }">
              <div class="flex items-center space-x-2">
                <t-icon name="user-circle" class="text-blue-500" size="18px" />
                <span class="font-semibold text-[#1d2129]">{{ row.accountName }}</span>
              </div>
            </template>

            <!-- 角色标签美化 (保持原样) -->
            <template #role="{ row }">
              <t-tag 
                variant="outline" 
                shape="round" 
                size="small"
                :class="['px-2 font-bold compact-tag transition-all duration-300', getGhostTagClass(row.role)]"
              >
                <template #icon>
                  <t-icon :name="row.role === 'SCAN' ? 'search' : row.role === 'TRADE' ? 'cart' : 'view-module'" />
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
                <span :class="['w-2 h-2 rounded-full inline-block relative', getStatusTheme(row.status), (row.status === 'SCANNING' || row.status === 'COOLING_DOWN') ? 'breathing' : '', row.status === 'NORMAL' ? 'status-dot-online' : '']"></span>
                <div class="ml-2 flex flex-col">
                  <div class="flex items-center">
                    <span :class="['text-[13px] font-semibold', getStatusTextColor(row.status)]">{{ getStatusLabel(row.status) }}</span>
                    <t-tooltip :content="row.warningMsg" v-if="row.warningMsg">
                      <t-icon name="error-circle" class="ml-1 text-red-500" />
                    </t-tooltip>
                  </div>
                </div>
              </div>
            </template>

            <!-- 统计列美化 (保持原样) -->
            <template #stats="{ row }">
              <div class="flex flex-col justify-center gap-1 py-1 h-full min-h-[44px]">
                <div class="flex items-center leading-tight">
                  <span class="text-[12px] text-[#86909c] mr-2 shrink-0">扫描:</span>
                  <span class="text-[13px] font-bold text-blue-600 font-numeric">
                    {{ (row.todayScanCount || 0).toLocaleString() }}
                  </span>
                </div>
                <div class="flex items-center leading-tight">
                  <span class="text-[12px] text-[#86909c] mr-2 shrink-0">成功:</span>
                  <span class="text-[13px] font-bold text-green-600 font-numeric">
                    {{ ((row.tradeSuccessRate || 0) * 100).toFixed(1) }}%
                  </span>
                </div>
              </div>
            </template>

            <!-- 余额显示/隐藏 (保持原样) -->
            <template #balance="{ row }">
              <div 
                class="flex items-center justify-end cursor-pointer select-none group"
                @click="balanceVisible = !balanceVisible"
              >
                <div :class="['font-numeric font-bold text-sm transition-all duration-300', getBalanceClass(row), (row.balance || 0) > 1000 ? 'high-value-shadow' : '']">
                  <span class="text-[10px] mr-0.5 opacity-60">¥</span>
                  <span v-if="balanceVisible">{{ row.balance?.toFixed(2) }}</span>
                  <span v-else>****</span>
                </div>
              </div>
            </template>

            <!-- 最后检测时间 (保持原样) -->
            <template #lastCheckTime="{ row }">
              <div class="flex items-center space-x-2">
                <div class="text-[12px] text-[#86909c] font-numeric font-medium">
                  {{ row.lastCheckTime ? row.lastCheckTime.replace('T', ' ').substring(5, 16) : '等待检测' }}
                </div>
              </div>
            </template>

            <!-- 操作列 (保持原样) -->
            <template #operation="{ row }">
              <div class="flex items-center justify-center space-x-3">
                <t-tooltip content="编辑">
                  <t-link theme="default" @click="onEditAccount(row)" :disabled="row.checking" class="!text-gray-400 hover:!text-blue-600 transition-colors">
                    <t-icon name="edit" />
                  </t-link>
                </t-tooltip>
                <t-tooltip content="检测">
                  <t-link theme="default" @click="onCheckAccount(row)" :disabled="row.checking" class="!text-gray-400 hover:!text-blue-600 transition-colors">
                    <t-icon name="refresh" :class="{ 'checking-rotate': row.checking }" />
                  </t-link>
                </t-tooltip>
                <t-popconfirm content="确定删除该账号吗？" @confirm="onDeleteAccount(row)">
                  <t-link theme="default" :disabled="row.checking" class="!text-gray-400 hover:!text-red-500 transition-colors">
                    <t-icon name="delete" />
                  </t-link>
                </t-popconfirm>
              </div>
            </template>
          </t-table>
        </t-card>
      </div>

      <!-- 右侧：Aside 整体化 (30%) -->
      <div class="lg:sticky lg:top-6 flex flex-col h-fit">
        <t-card :bordered="false" class="shadow-sm overflow-hidden">
          <template #title>
            <div class="flex items-center">
              <t-icon name="setting" class="mr-2 text-gray-500" size="20px" />
              <span class="text-base font-bold text-gray-800">控制面板</span>
            </div>
          </template>
          
          <div class="px-4 py-2 space-y-6">
            <!-- 统计板块 -->
            <section>
              <div class="text-[13px] font-bold text-gray-700 uppercase tracking-wider mb-3 flex items-center">
                <span class="mr-2">实时统计</span>
                <div class="h-[1px] bg-gray-100 flex-1"></div>
              </div>
              <div class="grid grid-cols-2 gap-3">
                <div class="bg-blue-50/50 border border-blue-100 rounded-lg p-3 transition-all hover:bg-blue-50">
                  <div class="text-[12px] text-[#86909c] mb-1">活跃账号</div>
                  <div class="flex items-baseline gap-1">
                    <span class="text-xl font-bold text-[#0052d9] font-numeric">{{ accounts.filter(a => a.status === 'NORMAL').length }}</span>
                    <span class="text-[11px] text-[#86909c]">/ {{ accounts.length }}</span>
                  </div>
                </div>
                <div class="bg-orange-50/50 border border-orange-100 rounded-lg p-3 transition-all hover:bg-orange-50">
                  <div class="text-[12px] text-[#86909c] mb-1">总余额</div>
                  <div class="flex items-baseline gap-0.5">
                    <span v-if="balanceVisible" class="text-[12px] font-bold text-[#d97706]">¥</span>
                    <span class="text-xl font-bold text-[#d97706] font-numeric">
                      {{ balanceVisible ? totalBalance.toFixed(2) : '****' }}
                    </span>
                  </div>
                </div>
              </div>
            </section>

            <!-- 交易配置 -->
            <section>
              <div class="text-[13px] font-bold text-gray-700 uppercase tracking-wider mb-3 flex items-center">
                <span class="mr-2">交易配置</span>
                <div class="h-[1px] bg-gray-100 flex-1"></div>
              </div>
              <t-form :data="formData" label-align="top" size="small" @submit="onSubmit">
                <t-form-item label="默认支付方式" name="paymentMethod">
                  <template #label>
                    <span class="text-[#86909c]">默认支付方式</span>
                  </template>
                  <t-radio-group v-model="formData.paymentMethod" variant="default-filled" size="small" class="w-full">
                    <t-radio-button value="BALANCE">余额</t-radio-button>
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
              <div class="text-[13px] font-bold text-gray-700 uppercase tracking-wider mb-3 flex items-center">
                <span class="mr-2">通知参数</span>
                <div class="h-[1px] bg-gray-100 flex-1"></div>
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
                        <t-input v-model="formData.wecomCorpid" placeholder="ww..." @blur="(v: string) => handleInputTrim(v, formData, 'wecomCorpid')" />
                      </t-form-item>
                      <t-form-item label="CorpSecret" name="wecomCorpsecret">
                        <template #label><span class="text-[#86909c]">CorpSecret</span></template>
                        <t-input v-model="formData.wecomCorpsecret" type="password" placeholder="******" @blur="(v: string) => handleInputTrim(v, formData, 'wecomCorpsecret')" />
                      </t-form-item>
                      <div class="grid grid-cols-2 gap-3">
                        <t-form-item label="AgentID" name="wecomAgentid">
                          <template #label><span class="text-[#86909c]">AgentID</span></template>
                          <t-input v-model="formData.wecomAgentid" placeholder="1000..." @blur="(v: string) => handleInputTrim(v, formData, 'wecomAgentid')" />
                        </t-form-item>
                        <t-form-item label="接收人" name="wecomTouser">
                          <template #label><span class="text-[#86909c]">接收人</span></template>
                          <t-input v-model="formData.wecomTouser" placeholder="@all" @blur="(v: string) => handleInputTrim(v, formData, 'wecomTouser')" />
                        </t-form-item>
                      </div>
                    </div>
                  </t-collapse-panel>
                </t-collapse>
              </t-form>
            </section>
          </div>

          <!-- 底部固定保存按钮 -->
          <div class="p-4 bg-white border-t border-gray-50 mt-4">
            <t-button
              theme="primary"
              type="submit"
              block
              :loading="loading"
              size="small"
              class="rounded shadow-sm h-9"
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
        @submit="onAccountSubmit"
        class="overflow-x-hidden p-1"
      >
        <div class="flex gap-6">
          <t-form-item label="账号名称" name="accountName" class="flex-[1.5] min-w-0">
            <t-input v-model="accountFormData.accountName" placeholder="如：扫描账号01" @blur="(v: string) => handleInputTrim(v, accountFormData, 'accountName')" />
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
            @blur="(v: string) => handleInputTrim(v, accountFormData, 'buffCookie')"
            class="custom-textarea"
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
          <t-input v-model="accountFormData.remark" placeholder="可选备注信息" @blur="(v: string) => handleInputTrim(v, accountFormData, 'remark')" />
        </t-form-item>

        <div class="mt-8 flex justify-end gap-3">
          <t-button
            variant="outline"
            @click="accountDialogVisible = false"
            class="rounded-md transition-all duration-300"
          >
            取消
          </t-button>
          <t-button
            theme="primary"
            type="submit"
            :loading="accountSubmitLoading"
            class="rounded-md transition-all duration-300 px-8"
          >
            确定
          </t-button>
        </div>
      </t-form>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import { settingsApi, UserBuffSettings, BuffAccount, BuffAccountRole, BuffAccountStatus } from "@/api/settings";
import { FormRule, MessagePlugin, SubmitContext, PrimaryTableCol, TableRowData } from "tdesign-vue-next";
import { computed, onMounted, reactive, ref } from "vue";

// --- 通用配置部分 ---
const loading = ref(false);
const wecomEnabled = ref(true);
const balanceVisible = ref(true);

// 模拟 SSE 日志数据
const scanLogs = ref([
  { time: '10:24:05', type: 'info', content: '系统初始化完成，等待任务调度...' },
  { time: '10:24:10', type: 'success', content: '账号 [扫描号01] 登录状态校验成功' },
  { time: '10:24:12', type: 'info', content: '正在扫描 [AK-47 | 二西莫夫] 市场数据...' },
  { time: '10:24:15', type: 'info', content: '发现 5 个符合条件的饰品，正在进行磨损比对...' },
]);

/**
 * 自动清除换行符和首尾空格
 */
const handleInputTrim = (val: string, target: any, key: string) => {
  if (typeof val === 'string') {
    target[key] = val.replace(/[\r\n]/g, '').trim();
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

// --- BUFF账号部分 ---
const accounts = ref<BuffAccount[]>([]);
const accountsLoading = ref(false);
const checkingAll = ref(false);
const accountDialogVisible = ref(false);
const accountDialogTitle = ref("新增账号");
const accountSubmitLoading = ref(false);
const accountFormRef = ref();

const totalBalance = computed(() => accounts.value.reduce((sum, a) => sum + (a.balance || 0), 0));

const accountFormData = reactive<BuffAccount>({
  accountName: "",
  buffCookie: "",
  role: "SCAN",
  weight: 1,
  status: "NORMAL",
  balance: 0,
  failCount: 0,
});

const accountRules: Record<string, FormRule[]> = {
  accountName: [{ required: true, message: "账号名称不能为空", type: "error" }],
  buffCookie: [{ required: true, message: "Cookie 不能为空", type: "error" }],
  role: [{ required: true, message: "请选择角色", type: "error" }],
};

const accountColumns: PrimaryTableCol<TableRowData>[] = [
  { colKey: "accountName", title: "账号", width: 140, ellipsis: true, cell: "accountName", align: "left" },
  { colKey: "role", title: "角色", width: 100, cell: "role", align: "left" },
  { colKey: "status", title: "状态", width: 120, cell: "status", align: "left" },
  { colKey: "stats", title: "实时统计", width: 150, cell: "stats", align: "left" },
  { colKey: "balance", title: "余额", width: 110, cell: "balance", align: "right" },
  { colKey: "lastCheckTime", title: "最后检测", width: 140, cell: "lastCheckTime", align: "left" },
  { colKey: "operation", title: "操作", width: 130, fixed: "right", cell: "operation", align: "center" },
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
  accounts.value.forEach(a => a.checking = true);
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

const getRoleTheme = (role: BuffAccountRole) => {
  const map: Record<BuffAccountRole, string> = {
    SCAN: "primary", // 侦察蓝
    TRADE: "warning", // 击杀橙
    BOTH: "success",
  };
  return map[role] || "default";
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

const getRateColor = (rate?: number) => {
  if (!rate) return "text-gray-400";
  if (rate >= 0.8) return "text-green-600";
  if (rate >= 0.5) return "text-orange-500";
  return "text-red-500";
};

const getBalanceClass = (row: BuffAccount) => {
  // “射手”账号（下单/全能）使用金库金，普通账号使用精致橙
  if (row.role === "TRADE" || row.role === "BOTH") {
    return "text-vault-gold";
  }
  return "text-refined-orange";
};

onMounted(() => {
  fetchSettings();
  fetchAccounts();
});
</script>

<style scoped>
/* 状态呼吸灯动画 (Tailwind 无法直接实现 keyframes 扩展动画) */
.breathing::after {
  content: "";
  position: absolute;
  width: 100%;
  height: 100%;
  top: 0;
  left: 0;
  border-radius: 50%;
  background-color: inherit;
  animation: breathe 2s infinite ease-in-out;
  opacity: 0.6;
}

@keyframes breathe {
  0% { transform: scale(1); opacity: 0.6; }
  50% { transform: scale(2.5); opacity: 0; }
  100% { transform: scale(1); opacity: 0.6; }
}

/* 弹窗样式优化 */
:deep(.t-dialog__body) {
  overflow-x: hidden;
  padding: 16px 24px !important;
}

:deep(.custom-textarea) {
  background-color: #f9fafb !important;
  border: 1px solid #dcdfe6 !important;
  border-radius: 4px !important;
  transition: all 0.2s cubic-bezier(0.38, 0, 0.24, 1);
}

:deep(.custom-textarea:focus) {
  border-color: #0052d9 !important;
  box-shadow: 0 0 0 2px rgba(0, 82, 217, 0.1);
  background-color: #fff !important;
}
</style>
