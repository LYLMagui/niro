<template>
  <PageFrame :is-mobile="false" desktop-outer-class="!p-0" desktop-content-class="px-4 pt-0 pb-0">
    <PageHeader title="系统设置">
      <template #icon>
        <svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"
          />
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
          />
        </svg>
      </template>
    </PageHeader>

    <div class="mx-auto max-w-3xl">
      <t-card :bordered="false" class="embedded-card overflow-hidden">
        <template #title>
          <div class="flex items-center">
            <t-icon name="setting" class="mr-2 text-gray-500" size="20px" />
            <span class="text-base font-bold text-gray-800">个人配置</span>
          </div>
        </template>

        <t-form :data="formData" :rules="formRules" label-align="top" @submit="onSubmit">
          <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
            <t-form-item label="C5 AppKey" name="c5AppKeyPlain" class="md:col-span-2">
              <t-input
                v-model="c5AppKeyPlain"
                :placeholder="formData.hasC5AppKey ? '留空则不修改当前 AppKey' : '请输入 C5 平台 AppKey'"
                type="password"
                clearable
                @blur="(value: string | number) => handlePlainAppKeyTrim(String(value))"
              />
              <div v-if="formData.c5AppKeyMasked" class="mt-1 text-xs text-slate-400">
                当前已配置：{{ formData.c5AppKeyMasked }}
              </div>
            </t-form-item>

            <t-form-item label="Steam 交易链接" name="steamTradeUrl" class="md:col-span-2">
              <t-input
                v-model="formData.steamTradeUrl"
                placeholder="请输入 Steam 交易链接"
                clearable
                @blur="(value: string | number) => handleInputTrim(String(value), 'steamTradeUrl')"
              />
            </t-form-item>

            <t-form-item label="默认支付方式" name="paymentMethod" class="md:col-span-2">
              <t-radio-group v-model="formData.paymentMethod" variant="default-filled">
                <t-radio-button value="BALANCE">网易支付</t-radio-button>
                <t-radio-button value="ALIPAY">支付宝</t-radio-button>
                <t-radio-button value="WECHAT">微信</t-radio-button>
              </t-radio-group>
            </t-form-item>
          </div>

          <div class="mt-6 flex justify-end">
            <t-button
              theme="primary"
              type="submit"
              :loading="loading"
              :disabled="!canSaveSettings"
              class="rounded px-8"
            >
              保存配置
            </t-button>
          </div>
        </t-form>
      </t-card>
    </div>
  </PageFrame>
</template>

<script setup lang="ts">
import PageFrame from "@/components/PageFrame.vue";
import PageHeader from "@/components/PageHeader.vue";
import { settingsApi, type UserPlatformSettings } from "@/api/settings";
import { PermissionConstant } from "@/constant/PermissionConstant";
import useNewPermission from "@/hooks/useNewPermission";
import { MessagePlugin, type FormRule, type SubmitContext } from "tdesign-vue-next";
import { computed, onMounted, reactive, ref } from "vue";

const { hasButtonPermission } = useNewPermission();
const canSaveSettings = computed(() => hasButtonPermission(PermissionConstant.SETTINGS_SAVE));

const loading = ref(false);
const c5AppKeyPlain = ref("");

const formData = reactive<UserPlatformSettings>({
  paymentMethod: "BALANCE",
  c5AppKeyMasked: "",
  hasC5AppKey: false,
  steamTradeUrl: "",
});

const formRules: Record<string, FormRule[]> = {};

const handleInputTrim = (value: string, key: keyof UserPlatformSettings) => {
  if (typeof formData[key] === "string") {
    formData[key] = value.replace(/[\r\n]/g, "").trim() as never;
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

const fetchSettings = async () => {
  const res = await settingsApi.getSettings();
  if (res) {
    Object.assign(formData, res);
    c5AppKeyPlain.value = "";
  }
};

const onSubmit = async (context: SubmitContext) => {
  if (context.validateResult !== true) {
    return;
  }
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
    MessagePlugin.success("配置已保存");
  } finally {
    loading.value = false;
  }
};

onMounted(fetchSettings);
</script>
