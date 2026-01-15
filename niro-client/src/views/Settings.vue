<template>
  <div class="settings-container p-6 bg-gray-50 min-h-screen">
    <!-- 顶部标题栏 -->
    <div class="mb-6 flex items-center justify-between">
      <div class="flex items-center space-x-3">
        <t-icon name="user-setting" size="24px" class="text-blue-600" />
        <h2 class="text-xl font-bold text-gray-800">个人配置中心</h2>
      </div>
      <t-breadcrumb>
        <t-breadcrumb-item>控制台</t-breadcrumb-item>
        <t-breadcrumb-item>个人配置</t-breadcrumb-item>
      </t-breadcrumb>
    </div>

    <t-row :gutter="[24, 24]">
      <!-- 账号管理 (占据全部宽度，侧边设置改为抽屉) -->
      <t-col :span="24">
        <t-card :bordered="false" class="shadow-sm h-full embedded-card">
          <template #title>
            <div class="flex items-center">
              <t-icon name="user-circle" class="mr-2 text-blue-600" />
              <span class="text-lg font-bold text-gray-800">账号列表</span>
            </div>
          </template>
          <template #actions>
            <t-space size="16px">
              <t-button
                variant="outline"
                theme="default"
                size="medium"
                @click="onCheckAll"
                :loading="checkingAll"
                class="rounded-lg transition-all duration-300 hover:shadow active:shadow-none"
              >
                <template #icon><t-icon name="refresh" /></template>
                一键检测
              </t-button>
              <t-button
                theme="primary"
                size="medium"
                @click="onAddAccount"
                class="rounded-lg transition-all duration-300 hover:shadow active:shadow-none"
              >
                <template #icon><t-icon name="add" /></template>
                新增账号
              </t-button>
              <t-button
                variant="outline"
                theme="default"
                size="medium"
                shape="square"
                @click="drawerVisible = true"
                class="rounded-lg transition-all duration-300 hover:shadow active:shadow-none text-gray-400"
              >
                <template #icon><t-icon name="setting" /></template>
              </t-button>
            </t-space>
          </template>

          <t-table
            :data="accounts"
            :columns="accountColumns"
            row-key="id"
            :loading="accountsLoading"
            size="medium"
            hover
            class="embedded-table w-full"
            :bordered="false"
          >
            <!-- 空状态 -->
            <template #empty>
              <t-empty icon="component-breadcrumb" description="暂无账号配置，请点击上方“新增账号”开始使用" />
            </template>

            <!-- 账号名称列增加图标标识 -->
            <template #accountName="{ row }">
              <div class="flex items-center space-x-2">
                <t-icon name="user-circle" class="text-blue-500" size="18px" />
                <span class="font-medium text-gray-800">{{ row.accountName }}</span>
              </div>
            </template>

            <!-- 角色标签美化 -->
            <template #role="{ row }">
              <t-tag :theme="getRoleTheme(row.role)" variant="light" shape="round" class="px-3 font-bold">
                <template #icon>
                  <t-icon :name="row.role === 'SCAN' ? 'search' : row.role === 'TRADE' ? 'target' : 'component-dropdown'" />
                </template>
                {{ getRoleLabel(row.role) }}
              </t-tag>
            </template>

            <!-- 状态呼吸灯与动效 -->
            <template #status="{ row }">
              <div class="flex items-center">
                <span :class="['w-2 h-2 rounded-full inline-block relative', getStatusTheme(row.status), (row.status === 'SCANNING' || row.status === 'COOLING_DOWN') ? 'breathing' : '']"></span>
                <div class="ml-2 flex flex-col">
                  <div class="flex items-center">
                    <span :class="['text-xs font-bold', getStatusTextColor(row.status)]">{{ getStatusLabel(row.status) }}</span>
                    <t-tooltip :content="row.warningMsg" v-if="row.warningMsg">
                      <t-icon name="error-circle" class="ml-1 text-red-500" />
                    </t-tooltip>
                  </div>
                  <!-- 风控倒计时预警 -->
                  <div v-if="row.status === 'MARKET_RESTRICTED'" class="text-[10px] text-orange-500 font-mono font-bold">
                    恢复中: 14:59
                  </div>
                </div>
              </div>
            </template>

            <!-- 统计列美化 -->
            <template #stats="{ row }">
              <div class="flex flex-col space-y-1 py-1">
                <div class="flex justify-between text-[11px] text-gray-500">
                  <span>今日扫描</span>
                  <span class="font-mono font-bold text-blue-600">{{ row.todayScanCount || 0 }}</span>
                </div>
                <div class="flex justify-between text-[11px] text-gray-500">
                  <span>下单成功率</span>
                  <span class="font-mono font-bold" :class="getRateColor(row.tradeSuccessRate)">
                    {{ ((row.tradeSuccessRate || 0) * 100).toFixed(1) }}%
                  </span>
                </div>
              </div>
            </template>

            <!-- 余额显示/隐藏 -->
            <template #balance="{ row }">
              <div class="flex items-center justify-between group">
                <div class="font-mono font-bold text-orange-600">
                  <span class="text-xs mr-0.5">¥</span>
                  <span v-if="balanceVisible">{{ row.balance?.toFixed(2) }}</span>
                  <span v-else>****</span>
                </div>
                <t-button variant="text" shape="square" size="small" class="opacity-0 group-hover:opacity-100 transition-opacity" @click="balanceVisible = !balanceVisible">
                  <template #icon><t-icon :name="balanceVisible ? 'browse' : 'browse-off'" size="14px" /></template>
                </t-button>
              </div>
            </template>

            <!-- 最后检测时间 -->
            <template #lastCheckTime="{ row }">
              <div class="text-[11px] text-gray-400">
                {{ row.lastCheckTime ? row.lastCheckTime.replace('T', ' ').substring(5, 16) : '-' }}
              </div>
            </template>

            <!-- 操作列 -->
            <template #operation="{ row }">
              <div class="flex items-center space-x-3">
                <t-tooltip content="编辑">
                  <t-link theme="primary" @click="onEditAccount(row)">
                    <t-icon name="edit" />
                  </t-link>
                </t-tooltip>
                <t-tooltip content="检测">
                  <t-link theme="primary" @click="onCheckAccount(row)" :loading="checkingIds.includes(row.id)">
                    <t-icon name="refresh" />
                  </t-link>
                </t-tooltip>
                <t-popconfirm content="确定删除该账号吗？" @confirm="onDeleteAccount(row)">
                  <t-link theme="danger">
                    <t-icon name="delete" />
                  </t-link>
                </t-popconfirm>
              </div>
            </template>
          </t-table>
        </t-card>
      </t-col>
    </t-row>

    <!-- 全局配置与监控抽屉 -->
    <t-drawer
      v-model:visible="drawerVisible"
      header="全局配置与监控"
      size="400px"
      :footer="false"
      destroy-on-close
    >
      <div class="drawer-content space-y-6">
        <!-- 系统监控卡片 (移动至抽屉顶部) -->
        <t-card title="系统监控" :bordered="false" class="bg-gray-50 border border-gray-100">
          <div class="grid grid-cols-2 gap-4">
            <div class="p-4 bg-white rounded-xl shadow-sm border border-blue-100">
              <div class="text-xs text-blue-500 font-bold mb-1 uppercase tracking-wider">活跃账号</div>
              <div class="text-2xl font-black text-blue-900">{{ accounts.filter(a => a.status === 'NORMAL').length }}<span class="text-sm text-gray-400 font-normal ml-1">/ {{ accounts.length }}</span></div>
            </div>
            <div class="p-4 bg-white rounded-xl shadow-sm border border-orange-100">
              <div class="text-xs text-orange-500 font-bold mb-1 uppercase tracking-wider">总余额</div>
              <div class="text-2xl font-black text-orange-900">
                <span v-if="balanceVisible">¥{{ totalBalance.toFixed(0) }}</span>
                <span v-else>****</span>
              </div>
            </div>
          </div>
        </t-card>

        <!-- 交易全局设置 -->
        <t-card title="交易全局设置" :bordered="false" class="bg-gray-50 border border-gray-100">
          <t-form :data="formData" label-align="top" @submit="onSubmit">
            <t-form-item label="默认支付方式" name="paymentMethod">
              <t-radio-group v-model="formData.paymentMethod" variant="default-filled" size="small" class="w-full">
                <t-radio-button value="BALANCE">
                  <template #default><t-icon name="wallet" class="mr-1" />余额</template>
                </t-radio-button>
                <t-radio-button value="ALIPAY">
                  <template #default><t-icon name="logo-alipay" class="mr-1" />支付宝</template>
                </t-radio-button>
                <t-radio-button value="WECHAT">
                  <template #default><t-icon name="logo-wechat" class="mr-1" />微信</template>
                </t-radio-button>
              </t-radio-group>
            </t-form-item>
            
            <div class="mt-6 pt-4 border-t border-gray-200">
              <div class="flex items-center justify-between mb-4">
                <h4 class="text-sm font-bold text-gray-700 flex items-center">
                  <t-icon name="chat" class="mr-2 text-green-600" />
                  企业微信通知
                </h4>
                <t-switch v-model="wecomEnabled" size="small" />
              </div>
              
              <div v-show="wecomEnabled" class="space-y-4 transition-all duration-300">
                <t-form-item label="企业ID (CorpID)" name="wecomCorpid">
                  <t-input v-model="formData.wecomCorpid" placeholder="ww..." />
                </t-form-item>
                <t-form-item label="应用Secret" name="wecomCorpsecret">
                  <t-input v-model="formData.wecomCorpsecret" type="password" placeholder="******" />
                </t-form-item>
                <div class="grid grid-cols-2 gap-4">
                  <t-form-item label="AgentID" name="wecomAgentid">
                    <t-input v-model="formData.wecomAgentid" placeholder="1000..." />
                  </t-form-item>
                  <t-form-item label="接收人" name="wecomTouser">
                    <t-input v-model="formData.wecomTouser" placeholder="@all" />
                  </t-form-item>
                </div>
              </div>
            </div>

            <div class="mt-8">
              <t-button
                theme="primary"
                type="submit"
                block
                :loading="loading"
                size="large"
                class="rounded-lg transition-all duration-300 hover:shadow active:shadow-none"
              >
                保存全局配置
              </t-button>
            </div>
          </t-form>
        </t-card>
      </div>
    </t-drawer>

    <!-- 账号编辑弹窗 (保持原样) -->
    <t-dialog
      v-model:visible="accountDialogVisible"
      :header="accountDialogTitle"
      :footer="false"
      width="600px"
    >
      <t-form
        ref="accountFormRef"
        :data="accountFormData"
        :rules="accountRules"
        label-align="top"
        @submit="onAccountSubmit"
      >
        <t-form-item label="账号名称" name="accountName">
          <t-input v-model="accountFormData.accountName" placeholder="如：扫描账号01" />
        </t-form-item>
        <t-form-item label="Cookie (buff_cookie)" name="buffCookie">
          <t-textarea
            v-model="accountFormData.buffCookie"
            placeholder="请粘贴 Cookie 字符串..."
            :autosize="{ minRows: 3, maxRows: 5 }"
          />
        </t-form-item>
        <div class="grid grid-cols-2 gap-4">
          <t-form-item label="角色" name="role">
            <t-select v-model="accountFormData.role">
              <t-option label="扫描 (仅扫描)" value="SCAN" />
              <t-option label="下单 (仅下单)" value="TRADE" />
              <t-option label="全能 (扫描+下单)" value="BOTH" />
            </t-select>
          </t-form-item>
          <t-form-item label="权重 (1-10)" name="weight">
            <t-input-number
              v-model="accountFormData.weight"
              :min="1"
              :max="10"
              class="w-full"
            />
          </t-form-item>
        </div>
        <t-form-item label="备注" name="remark">
          <t-input v-model="accountFormData.remark" placeholder="可选备注信息" />
        </t-form-item>
        <div class="mt-6 flex justify-end space-x-4">
          <t-button
            variant="outline"
            @click="accountDialogVisible = false"
            class="rounded-lg transition-all duration-300 hover:shadow active:shadow-none"
          >
            取消
          </t-button>
          <t-button
            theme="primary"
            type="submit"
            :loading="accountSubmitLoading"
            class="rounded-lg transition-all duration-300 hover:shadow active:shadow-none"
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
const drawerVisible = ref(false);
const balanceVisible = ref(true);

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
      drawerVisible.value = false;
    } finally {
      loading.value = false;
    }
  }
};

// --- BUFF账号部分 ---
const accounts = ref<BuffAccount[]>([]);
const accountsLoading = ref(false);
const checkingAll = ref(false);
const checkingIds = ref<number[]>([]);
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
  { colKey: "accountName", title: "账号", width: 140, ellipsis: true, cell: "accountName" },
  { colKey: "role", title: "角色", width: 110, cell: "role" },
  { colKey: "status", title: "状态", width: 120, cell: "status" },
  { colKey: "stats", title: "实时统计", width: 160, cell: "stats" },
  { colKey: "balance", title: "余额", width: 110, cell: "balance" },
  { colKey: "lastCheckTime", title: "最后检测", width: 110, cell: "lastCheckTime" },
  { colKey: "operation", title: "操作", width: 130, fixed: "right", cell: "operation" },
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
  checkingIds.value.push(row.id);
  try {
    await settingsApi.checkBuffAccount(row.id);
    MessagePlugin.success(`${row.accountName} 检测任务已启动`);
    // 模拟 SSE 更新延迟
    setTimeout(fetchAccounts, 1500);
  } finally {
    checkingIds.value = checkingIds.value.filter(id => id !== row.id);
  }
};

const onCheckAll = async () => {
  checkingAll.value = true;
  try {
    await settingsApi.checkAllBuffAccounts();
    MessagePlugin.success("全局检测指令已下发，请留意状态变化");
    setTimeout(fetchAccounts, 2000);
  } finally {
    checkingAll.value = false;
  }
};

const getRoleLabel = (role: BuffAccountRole) => {
  const map: Record<BuffAccountRole, string> = { SCAN: "扫描号", TRADE: "下单号", BOTH: "全能号" };
  return map[role] || role;
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

/* 嵌入式卡片布局优化 */
.embedded-card :deep(.t-card__body) {
  padding: 0 !important;
}

.embedded-card :deep(.t-card__header) {
  padding: 16px 24px !important;
}

/* 嵌入式表格深度定制 */
:deep(.embedded-table) {
  border: none !important;
}

/* 表头背景色与标题栏衔接 */
:deep(.embedded-table .t-table__header tr) {
  background-color: #f8fafc !important;
}

:deep(.embedded-table .t-table__header th) {
  font-weight: 700 !important;
  color: #334155 !important;
  background-color: transparent !important;
  border-bottom: 1px solid #f1f5f9 !important;
  padding: 12px 16px !important;
  height: 48px;
}

:deep(.embedded-table .t-table__body td) {
  padding: 16px 16px !important;
  border-bottom: 1px solid #f1f5f9 !important;
}

/* 第一列和最后一列的 24px 边距对齐 */
:deep(.embedded-table th:first-child),
:deep(.embedded-table td:first-child) {
  padding-left: 24px !important;
}

:deep(.embedded-table th:last-child),
:deep(.embedded-table td:last-child) {
  padding-right: 24px !important;
}

:deep(.embedded-table .t-table__row--hover) {
  background-color: #f8fafc !important;
}

/* 抽屉内表单优化 */
.drawer-content :deep(.t-form__item) {
  margin-bottom: 20px;
}

.drawer-content :deep(.t-card) {
  border-radius: 12px;
  overflow: hidden;
}
</style>
