<template>
  <PageFrame :is-mobile="false" desktop-outer-class="!p-0" desktop-content-class="px-4 pt-0 pb-0">
    <PageHeader title="系统设置">
      <template #icon>
        <svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
      </template>
      <template #extra>
        <div v-if="accounts.length > 0" class="flex flex-col items-end">
          <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400">BUFF 全站总资产</span>
          <span class="font-numeric text-base font-bold text-slate-900">
            <span v-if="balanceVisible">¥{{ totalAssets.toFixed(2) }}</span>
            <span v-else>****</span>
          </span>
        </div>
      </template>
    </PageHeader>
    <div class="grid grid-cols-1 items-start gap-6 lg:grid-cols-[72%_1fr]">
      <!-- 左侧：Content (72%) -->
      <div class="min-w-0">
        <!-- 账号列表 -->
        <t-card :bordered="false" class="embedded-card h-full">
          <template #title>
            <div class="flex items-center">
              <t-icon name="user-circle" class="mr-2 text-blue-600" />
              <span class="text-lg font-bold text-gray-800">BUFF 账号管理</span>
            </div>
          </template>
          <template #actions>
            <t-space size="8px">
              <t-button
                v-if="canCheckAllBuffAccounts"
                variant="outline"
                theme="default"
                :loading="checkingAll"
                class="rounded !border-gray-200 !text-gray-600 transition-all duration-300"
                @click="onCheckAll"
              >
                <template #icon><t-icon name="refresh" /></template>
                一键检测
              </t-button>
              <t-button
                v-if="canSaveBuffAccount"
                theme="primary"
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
            class="niro-unified-table w-full bg-white"
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
              <div class="flex min-w-0 items-center space-x-2">
                <t-icon name="user-circle" class="shrink-0 text-blue-500" size="18px" />
                <t-tooltip :content="row.accountName" placement="top-left">
                  <span class="truncate text-[15px] font-bold text-[#1d2129]">{{ row.accountName }}</span>
                </t-tooltip>
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
                    <span :class="['text-[14px] font-bold', getStatusTextColor(row.status)]">
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
              <div class="flex h-full min-h-[44px] flex-col justify-center gap-1.5 py-1">
                <div class="flex items-center leading-tight">
                  <span class="mr-2 shrink-0 text-[13px] text-[#86909c]">扫描:</span>
                  <span class="font-numeric text-[14px] font-bold text-[#1d2129]">
                    {{ (row.todayScanCount || 0).toLocaleString() }}
                  </span>
                </div>
                <div class="flex items-center leading-tight">
                  <span class="mr-2 shrink-0 text-[13px] text-[#86909c]">成功:</span>
                  <span class="font-numeric text-[14px] font-bold text-green-600">
                    {{ ((row.tradeSuccessRate || 0) * 100).toFixed(1) }}%
                  </span>
                </div>
              </div>
            </template>

            <!-- 余额显示/隐藏 -->
            <template #balance="{ row }">
              <div
                class="group flex cursor-pointer flex-col gap-1 py-0.5 px-0.5 w-full select-none"
                @click="balanceVisible = !balanceVisible"
              >
                <!-- 资产统计 (置顶突出) -->
                <div class="flex items-center justify-between w-full pb-1 border-b border-orange-100/40">
                   <span class="text-[9px] font-bold text-orange-600 uppercase tracking-tight px-1 py-0 bg-orange-50 rounded-[2px]">资产统计</span>
                   <span class="text-orange-600 tabular-nums text-[13px] font-bold">
                     <span v-if="balanceVisible">¥{{ ((row.balance || 0) + (row.pendingBalance || 0)).toFixed(2) }}</span>
                     <span v-else>****</span>
                   </span>
                </div>

                <!-- 可用余额 & 待结算 (并排) -->
                <div class="flex items-center justify-between w-full px-0.5 text-[11px]">
                  <div class="flex items-center gap-1">
                    <span class="text-slate-400">可用</span>
                    <span :class="['font-bold tabular-nums', getBalanceClass(row)]">
                      <span v-if="balanceVisible">¥{{ row.balance?.toFixed(1) }}</span>
                      <span v-else>***</span>
                    </span>
                  </div>
                  <div class="flex items-center gap-1">
                    <span class="text-slate-400">待结</span>
                    <span class="font-bold text-slate-600 tabular-nums">
                      <span v-if="balanceVisible">¥{{ row.pendingBalance?.toFixed(1) }}</span>
                      <span v-else>***</span>
                    </span>
                  </div>
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
              <div class="niro-table-actions niro-table-actions--center">
                <t-tooltip content="编辑">
                  <t-button
                    v-if="canSaveBuffAccount"
                    variant="outline"
                    size="small"
                    :disabled="row.checking"
                    class="niro-table-action-btn niro-table-action-btn--compact"
                    @click="onEditAccount(row)"
                  >
                    <t-icon name="edit" />
                  </t-button>
                </t-tooltip>
                <t-tooltip content="检测">
                  <t-button
                    v-if="canCheckBuffAccount"
                    variant="outline"
                    size="small"
                    :disabled="row.checking"
                    class="niro-table-action-btn niro-table-action-btn--compact"
                    @click="onCheckAccount(row)"
                  >
                    <t-icon name="refresh" :class="{ 'checking-rotate': row.checking }" />
                  </t-button>
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
                    <t-button
                      v-if="canDeleteBuffAccount"
                      variant="outline"
                      size="small"
                      theme="danger"
                      :disabled="row.checking || !!row.boundTaskId"
                      class="niro-table-action-btn niro-table-action-btn--compact disabled:!opacity-50"
                    >
                      <t-icon name="delete" />
                    </t-button>
                  </t-tooltip>
                </t-popconfirm>
              </div>
            </template>
          </t-table>
        </t-card>
      </div>

      <!-- 右侧：Aside 整体化 (30%) -->
      <div class="flex h-fit flex-col lg:sticky lg:top-6">
        <t-card :bordered="false" class="overflow-hidden">
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
                      v-if="totalPendingBalance >= 0"
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

            <!-- 平台配置 -->
            <section>
              <div
                class="mb-3 flex items-center text-[13px] font-bold tracking-wider text-gray-700 uppercase"
              >
                <span class="mr-2">平台配置</span>
                <div class="h-[1px] flex-1 bg-gray-100"></div>
              </div>
              <t-form :data="formData" :rules="formRules" label-align="top" @submit="onSubmit">
                <t-form-item label="C5 AppKey" name="c5AppKeyPlain">
                  <template #label><span class="text-[#86909c]">C5 AppKey</span></template>
                  <t-input
                    v-model="c5AppKeyPlain"
                    :placeholder="formData.hasC5AppKey ? '留空则不修改当前 AppKey' : '请输入C5平台的AppKey'"
                    type="password"
                    clearable
                    @blur="(v: string | number) => handlePlainAppKeyTrim(String(v))"
                  />
                  <div v-if="formData.c5AppKeyMasked" class="mt-1 text-xs text-slate-400">
                    当前已配置：{{ formData.c5AppKeyMasked }}
                  </div>
                </t-form-item>
                <t-form-item label="Steam交易链接" name="steamTradeUrl">
                  <template #label><span class="text-[#86909c]">Steam交易链接</span></template>
                  <t-input
                    v-model="formData.steamTradeUrl"
                    placeholder="请输入Steam交易链接"
                    @blur="(v: any) => handleInputTrim(v, formData, 'steamTradeUrl')"
                  />
                </t-form-item>
              </t-form>
            </section>

            <!-- 交易配置 -->
            <section>
              <div
                class="mb-3 flex items-center text-[13px] font-bold tracking-wider text-gray-700 uppercase"
              >
                <span class="mr-2">交易配置</span>
                <div class="h-[1px] flex-1 bg-gray-100"></div>
              </div>
              <t-form :data="formData" :rules="formRules" label-align="top" @submit="onSubmit">
                <t-form-item label="默认支付方式" name="paymentMethod">
                  <template #label>
                    <span class="text-[#86909c]">默认支付方式</span>
                  </template>
                  <t-radio-group
                    v-model="formData.paymentMethod"
                    variant="default-filled"
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
                <div class="mt-3 flex items-center justify-between">
                  <span class="text-[13px] text-[#86909c]">邮件通知</span>
                  <t-switch v-model="formData.emailEnabled" size="small" />
                </div>
              </t-form>
            </section>

            <!-- 参数详情 (折叠) -->
            <section v-if="wecomEnabled || formData.emailEnabled">
              <div
                class="mb-3 flex items-center text-[13px] font-bold tracking-wider text-gray-700 uppercase"
              >
                <span class="mr-2">通知参数</span>
                <div class="h-[1px] flex-1 bg-gray-100"></div>
              </div>
              <t-form :data="formData" :rules="formRules" label-align="top" @submit="onSubmit">
                <t-collapse :borderless="true" class="bg-transparent !p-0" :default-value="[]">
                  <t-collapse-panel v-if="wecomEnabled" value="wecom" class="!bg-transparent">
                    <template #header>
                      <span class="text-[13px] text-[#86909c]">企业微信配置</span>
                    </template>
                    <div class="config-panel-bg compact-form mt-2 rounded p-3">
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

                  <t-collapse-panel
                    v-if="formData.emailEnabled"
                    value="email"
                    class="!bg-transparent"
                  >
                    <template #header>
                      <span class="text-[13px] text-[#86909c]">邮件通知配置</span>
                    </template>
                    <div class="config-panel-bg compact-form mt-2 rounded p-3">
                      <div class="grid grid-cols-3 gap-3">
                        <t-form-item label="SMTP服务器" name="emailHost" class="col-span-2">
                          <template #label><span class="text-[#86909c]">SMTP服务器</span></template>
                          <t-input
                            v-model="formData.emailHost"
                            placeholder="smtp.qq.com"
                            @blur="(v: any) => handleInputTrim(v, formData, 'emailHost')"
                          />
                        </t-form-item>
                        <t-form-item label="端口" name="emailPort">
                          <template #label><span class="text-[#86909c]">端口</span></template>
                          <t-input-number
                            v-model="formData.emailPort"
                            placeholder="465"
                            theme="column"
                            class="w-full"
                          />
                        </t-form-item>
                      </div>
                      <div class="grid grid-cols-2 gap-3">
                        <t-form-item label="发件账号" name="emailAccount">
                          <template #label><span class="text-[#86909c]">发件账号</span></template>
                          <t-input
                            v-model="formData.emailAccount"
                            placeholder="example@qq.com"
                            @blur="(v: any) => handleInputTrim(v, formData, 'emailAccount')"
                          />
                        </t-form-item>
                        <t-form-item label="授权码/密码" name="emailPassword">
                          <template #label>
                            <span class="text-[#86909c]">授权码/密码</span>
                          </template>
                          <t-input
                            v-model="formData.emailPassword"
                            type="password"
                            placeholder="******"
                            @blur="(v: any) => handleInputTrim(v, formData, 'emailPassword')"
                          />
                        </t-form-item>
                      </div>
                      <t-form-item label="收件人" name="emailReceiver">
                        <template #label><span class="text-[#86909c]">收件人</span></template>
                        <t-input
                          v-model="formData.emailReceiver"
                          placeholder="receiver@example.com"
                          @blur="(v: any) => handleInputTrim(v, formData, 'emailReceiver')"
                        />
                      </t-form-item>
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
                v-if="(wecomEnabled || formData.emailEnabled) && canTestNotify"
                variant="text"
                theme="primary"
                :loading="testNotifyLoading"
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
              :disabled="!canSaveSettings"
              class="h-9 rounded"
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
            class="rounded transition-all duration-300"
            @click="accountDialogVisible = false"
          >
            取消
          </t-button>
          <t-button
            v-if="canSaveBuffAccount"
            theme="primary"
            type="submit"
            :loading="accountSubmitLoading"
            class="rounded px-8 transition-all duration-300"
          >
            确定
          </t-button>
        </div>
      </t-form>
    </t-dialog>
  </PageFrame>
</template>

<script setup lang="ts">
import PageHeader from "@/components/PageHeader.vue";
import {
  settingsApi,
  UserPlatformSettings,
  BuffAccount,
  BuffAccountRole,
  BuffAccountStatus,
} from "@/api/settings";
import { PermissionConstant } from "@/constant/PermissionConstant";
import { usePermission } from "@/hooks/usePermission";
import useNewPermission from "@/hooks/useNewPermission";
import {
  FormRule,
  MessagePlugin,
  SubmitContext,
  PrimaryTableCol,
  TableRowData,
} from "tdesign-vue-next";
import { computed, onMounted, reactive, ref, watch } from "vue";

const { hasPermission } = usePermission();
const { hasButtonPermission } = useNewPermission();
const canViewAccountList = computed(() => hasPermission(PermissionConstant.ACCOUNT_LIST));
const canSaveBuffAccount = computed(() => hasButtonPermission(PermissionConstant.BUFF_ACCOUNT_SAVE));
const canDeleteBuffAccount = computed(() => hasButtonPermission(PermissionConstant.BUFF_ACCOUNT_DELETE));
const canCheckBuffAccount = computed(() => hasButtonPermission(PermissionConstant.BUFF_ACCOUNT_CHECK));
const canCheckAllBuffAccounts = computed(() => hasButtonPermission(PermissionConstant.BUFF_ACCOUNT_CHECK_ALL));
const canSaveSettings = computed(() => hasButtonPermission(PermissionConstant.SETTINGS_SAVE));
const canTestNotify = computed(() => hasButtonPermission(PermissionConstant.SETTINGS_TEST_NOTIFY));

// --- 通用配置部分 ---
const loading = ref(false);
const testNotifyLoading = ref(false);
const wecomEnabled = ref(false);
const balanceVisible = ref(true);
const c5AppKeyPlain = ref("");

/**
 * 自动清除换行符和首尾空格
 */
const handleInputTrim = (val: any, target: any, key: string) => {
  if (typeof val === "string") {
    target[key] = val.replace(/[\r\n]/g, "").trim();
  }
};

const handlePlainAppKeyTrim = (value: string) => {
  c5AppKeyPlain.value = value.replace(/[\r\n]/g, "").trim();
};

const base64ToBytes = (value: string) => {
  const binary = window.atob(value);
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
};

const bytesToBase64 = (value: ArrayBuffer) => {
  const bytes = new Uint8Array(value);
  let binary = "";
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return window.btoa(binary);
};

const encryptAppKeyWithPublicKey = async (appKey: string, publicKey: string) => {
  const key = await window.crypto.subtle.importKey(
    "spki",
    base64ToBytes(publicKey),
    { name: "RSA-OAEP", hash: "SHA-256" },
    false,
    ["encrypt"]
  );
  const encrypted = await window.crypto.subtle.encrypt(
    { name: "RSA-OAEP" },
    key,
    new TextEncoder().encode(appKey)
  );
  return bytesToBase64(encrypted);
};

const formData = reactive<UserPlatformSettings>({
  paymentMethod: "BALANCE",
  c5AppKeyMasked: "",
  hasC5AppKey: false,
  steamTradeUrl: "",
  wecomCorpid: "",
  wecomCorpsecret: "",
  wecomAgentid: "",
  wecomTouser: "@all",
  emailEnabled: false,
  emailHost: "",
  emailPort: 465,
  emailAccount: "",
  emailPassword: "",
  emailReceiver: "",
});

const fetchSettings = async () => {
  try {
    const res = await settingsApi.getSettings();
    if (res) {
      Object.assign(formData, res);
      c5AppKeyPlain.value = "";
      // 如果企业ID为空，默认收起通知配置
      wecomEnabled.value = !!res.wecomCorpid;
      // 邮件通知开关直接绑定 formData.emailEnabled，无需额外处理
    }
  } catch (e) {
    console.error(e);
  }
};

const onSubmit = async (context: SubmitContext) => {
  if (context.validateResult === true) {
    loading.value = true;
    try {
      const encryptedC5AppKey = c5AppKeyPlain.value
        ? await encryptAppKeyWithPublicKey(
            c5AppKeyPlain.value,
            (await settingsApi.getAppKeyPublicKey()).publicKey
          )
        : undefined;
      await settingsApi.saveSettings({
        ...formData,
        encryptedC5AppKey,
      });
      c5AppKeyPlain.value = "";
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
    MessagePlugin.success("测试通知已发送，请检查企业微信或邮件");
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

const formRules = computed<Record<string, FormRule[]>>(() => {
  const rules: Record<string, FormRule[]> = {};
  if (formData.emailEnabled) {
    rules.emailHost = [
      { required: true, message: "请输入SMTP服务器", type: "error", trigger: "blur" },
    ];
    rules.emailPort = [{ required: true, message: "请输入端口", type: "error", trigger: "blur" }];
    rules.emailAccount = [
      { required: true, message: "请输入发件账号", type: "error", trigger: "blur" },
    ];
    rules.emailPassword = [
      { required: true, message: "请输入授权码", type: "error", trigger: "blur" },
    ];
    rules.emailReceiver = [
      { required: true, message: "请输入收件人", type: "error", trigger: "blur" },
    ];
  }
  if (wecomEnabled.value) {
    rules.wecomCorpid = [
      { required: true, message: "请输入CorpID", type: "error", trigger: "blur" },
    ];
    rules.wecomCorpsecret = [
      { required: true, message: "请输入CorpSecret", type: "error", trigger: "blur" },
    ];
    rules.wecomAgentid = [
      { required: true, message: "请输入AgentID", type: "error", trigger: "blur" },
    ];
    rules.wecomTouser = [
      { required: true, message: "请输入接收人", type: "error", trigger: "blur" },
    ];
  }
  return rules;
});

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
  { colKey: "balance", title: "余额", width: 210, cell: "balance", align: "left" },
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
  if (!canViewAccountList.value) {
    accounts.value = [];
    return;
  }
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
});

watch(
  canViewAccountList,
  async (allowed) => {
    if (!allowed) {
      accounts.value = [];
      return;
    }
    await fetchAccounts();
    if (accounts.value.length > 0) {
      onCheckAll();
    }
  },
  { immediate: true }
);
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
/* 配置面板背景 */
.config-panel-bg {
  background-color: #f9fafb;
  border: 1px solid #dcdfe6;
}
/* 压缩表单间距 */
.compact-form :deep(.t-form__item) {
  margin-bottom: 12px;
}
.compact-form :deep(.t-form__item:last-child) {
  margin-bottom: 0;
}
/* 修复数字输入框宽度溢出 */
:deep(.t-input-number) {
  width: 100%;
}
</style>
