<template>
  <div class="space-y-6">
    <!-- 页面标题 -->
    <div class="flex items-center justify-between">
      <h2 class="text-2xl font-bold text-gray-800">个人配置</h2>
    </div>

    <!-- 配置表单 -->
    <t-card :bordered="false" class="shadow-sm">
      <t-form
        ref="formRef"
        :data="formData"
        :rules="rules"
        label-align="top"
        @submit="onSubmit"
      >
        <!-- Buff Cookie 配置 -->
        <t-form-item label="Buff Cookie" name="buffCookie" help="请登录网页版 Buff 并在控制台获取 Cookie (包含 session 字段)">
          <t-textarea
            v-model="formData.buffCookie"
            placeholder="请粘贴完整的 Cookie 字符串..."
            :autosize="{ minRows: 3, maxRows: 6 }"
          />
        </t-form-item>

        <!-- 支付方式配置 -->
        <t-form-item label="支付方式" name="paymentMethod">
          <div class="grid w-full grid-cols-1 gap-4 sm:grid-cols-3">
            <div
              v-for="item in paymentOptions"
              :key="item.value"
              class="relative flex cursor-pointer flex-col rounded-lg border-2 p-4 transition-all hover:shadow-md"
              :class="
                formData.paymentMethod === item.value
                  ? 'border-blue-500 bg-blue-50 text-blue-700'
                  : 'border-gray-200 bg-white text-gray-600 hover:border-gray-300'
              "
              @click="formData.paymentMethod = item.value"
            >
              <div class="mb-2 flex items-center justify-between">
                <component :is="item.icon" class="text-xl" />
                <t-icon
                  v-if="formData.paymentMethod === item.value"
                  name="check-circle-filled"
                  class="text-blue-500"
                />
              </div>
              <div class="text-base font-bold">{{ item.label }}</div>
              <div class="mt-1 text-xs opacity-70">{{ item.desc }}</div>
            </div>
          </div>
        </t-form-item>

        <!-- 企业微信通知配置 -->
        <div class="mt-8 mb-4 border-t pt-6">
          <h3 class="text-lg font-medium text-gray-800 mb-4 flex items-center">
            <t-icon name="chat" class="mr-2" />
            企业微信通知配置
          </h3>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-4">
            <t-form-item label="企业ID (CorpID)" name="wecomCorpid">
              <t-input v-model="formData.wecomCorpid" placeholder="请输入企业微信企业ID" />
            </t-form-item>
            <t-form-item label="应用Secret (CorpSecret)" name="wecomCorpsecret">
              <t-input v-model="formData.wecomCorpsecret" type="password" placeholder="请输入应用Secret" />
            </t-form-item>
            <t-form-item label="应用AgentID" name="wecomAgentid">
              <t-input v-model="formData.wecomAgentid" placeholder="请输入应用AgentID" />
            </t-form-item>
            <t-form-item label="接收人账号 (ToUser)" name="wecomTouser" help="默认为 @all，指定多人用 | 分隔">
              <t-input v-model="formData.wecomTouser" placeholder="请输入接收人账号" />
            </t-form-item>
          </div>
        </div>

        <!-- 操作按钮 -->
        <t-form-item>
          <t-button theme="primary" type="submit" :loading="loading">保存配置</t-button>
          <t-button theme="default" variant="text" class="ml-4" @click="resetForm">重置</t-button>
        </t-form-item>
      </t-form>
    </t-card>
  </div>
</template>

<script setup lang="ts">
import { settingsApi, UserBuffSettings } from "@/api/settings";
import { LogoAlipayIcon, LogoWechatpayIcon, WalletIcon } from "tdesign-icons-vue-next";
import { FormRule, MessagePlugin, SubmitContext } from "tdesign-vue-next";
import { onMounted, reactive, ref } from "vue";

const loading = ref(false);
const formRef = ref();

// 支付方式选项配置
const paymentOptions = [
  { value: "BALANCE", label: "平台余额", desc: "优先扣除 Buff 账号余额", icon: WalletIcon },
  { value: "ALIPAY", label: "支付宝", desc: "扫码或跳转支付宝支付", icon: LogoAlipayIcon },
  { value: "WECHAT", label: "微信支付", desc: "微信扫码支付", icon: LogoWechatpayIcon },
];

// 表单数据
const formData = reactive<UserBuffSettings>({
  buffCookie: "",
  paymentMethod: "BALANCE", // 默认选中余额
  wecomCorpid: "",
  wecomCorpsecret: "",
  wecomAgentid: "",
  wecomTouser: "@all",
});

// 表单校验规则
const rules: Record<string, FormRule[]> = {
  buffCookie: [
    { required: true, message: "Cookie 不能为空", type: "error" },
    { min: 10, message: "Cookie 长度过短，请检查是否正确", type: "warning" }
  ],
  paymentMethod: [{ required: true, message: "请选择支付方式", type: "error" }],
};

// 加载配置
const fetchSettings = async () => {
  try {
    const res = await settingsApi.getSettings();
    if (res) {
      Object.assign(formData, res);
    }
  } catch (error) {
    // 忽略错误或显示提示
  }
};

// 提交表单
const onSubmit = async (context: SubmitContext) => {
  if (context.validateResult === true) {
    loading.value = true;
    try {
      await settingsApi.saveSettings(formData);
      MessagePlugin.success("配置已保存");
    } catch (error) {
      // 异常已由拦截器处理
    } finally {
      loading.value = false;
    }
  }
};

const resetForm = () => {
  formRef.value?.reset();
  formData.paymentMethod = "BALANCE"; // 重置为默认
};

onMounted(() => {
  fetchSettings();
});
</script>

<style scoped>
/* 增强支付方式选中态的背景对比度 */
:deep(.t-radio-group.t-radio-group--filled .t-radio-button.t-is-checked) {
  background-color: #0052d9; /* TDesign 品牌蓝 */
  color: #ffffff;
  border-color: #0052d9;
}

/* 增强未选中态的边框，使其更清晰 */
:deep(.t-radio-group.t-radio-group--filled .t-radio-button) {
  background-color: #f3f3f3;
  border-color: #dcdcdc;
}
</style>
