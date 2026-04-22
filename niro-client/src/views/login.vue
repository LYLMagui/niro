<template>
  <div class="flex min-h-screen w-full flex-col items-center justify-center bg-[#f0f2f5] px-4">
    <div
      class="w-full max-w-[480px] rounded-lg bg-white p-8 shadow-[0_8px_24px_rgba(0,0,0,0.16)] transition-all duration-500 ease-in-out hover:shadow-[0_16px_48px_rgba(0,0,0,0.22)] sm:p-10"
    >
      <div class="mb-8 flex flex-col items-center justify-center text-center">
        <h1 class="text-3xl font-bold text-gray-800">Niro</h1>
        <p class="mt-2 text-sm text-gray-500">
          {{ isRegisterMode ? "使用邀请码完成注册" : "追风赶月莫停留，平芜尽处是春山" }}
        </p>
      </div>

      <div
        v-if="!isRegisterMode && loginNotice"
        class="mb-6 rounded-md border border-green-100 bg-green-50 px-4 py-3 text-sm text-green-600"
      >
        {{ loginNotice }}
      </div>

      <div
        v-if="isRegisterMode && registerBanner"
        class="mb-6 rounded-md border border-blue-100 bg-blue-50 px-4 py-3 text-sm text-blue-600"
      >
        {{ registerBanner }}
      </div>

      <t-form
        v-if="!isRegisterMode"
        ref="accountFormRef"
        :data="accountFormData"
        :rules="accountRules"
        :label-width="0"
        @submit="handleAccountLogin"
      >
        <t-form-item name="username">
          <t-input v-model="accountFormData.username" placeholder="请输入用户名" size="large">
            <template #prefix-icon>
              <user-icon />
            </template>
          </t-input>
        </t-form-item>

        <t-form-item name="password">
          <t-input
            v-model="accountFormData.password"
            type="password"
            placeholder="请输入密码"
            size="large"
          >
            <template #prefix-icon>
              <lock-on-icon />
            </template>
          </t-input>
        </t-form-item>

        <div class="mb-4 flex items-center justify-between gap-4 text-sm">
          <t-checkbox v-model="rememberMe">记住我</t-checkbox>
          <button
            type="button"
            class="font-medium text-[var(--td-brand-color)] transition-colors hover:text-[var(--td-brand-color-hover)]"
            @click="switchMode('register')"
          >
            注册
          </button>
        </div>

        <t-form-item class="pt-2">
          <t-button theme="primary" type="submit" block :loading="loginLoading">
            登录
          </t-button>
        </t-form-item>
      </t-form>

      <t-form
        v-else
        ref="registerFormRef"
        :data="registerFormData"
        :rules="registerRules"
        :label-width="0"
        @submit="handleRegister"
      >
        <t-form-item name="email">
          <t-input
            v-model="registerFormData.email"
            clearable
            placeholder="请输入邮箱"
            size="large"
            @change="handleRegisterEmailChange"
          />
        </t-form-item>

        <t-form-item name="emailCode" :show-error-message="false">
          <div class="flex w-full items-start gap-3">
            <div class="min-w-0 flex-1">
              <t-input
                v-model="registerFormData.emailCode"
                :status="emailCodeInputStatus"
                :tips="emailCodeHint"
                clearable
                placeholder="请输入邮箱验证码"
                size="large"
              />
            </div>
            <t-button
              class="w-[132px] shrink-0"
              variant="outline"
              size="large"
              :disabled="sendEmailCodeDisabled"
              :loading="sendEmailCodeLoading"
              @click="handleSendRegisterEmailCode"
            >
              {{ sendEmailCodeButtonText }}
            </t-button>
          </div>
        </t-form-item>

        <t-form-item name="password">
          <t-input
            v-model="registerFormData.password"
            type="password"
            placeholder="请设置密码（8-20 位）"
            size="large"
          />
        </t-form-item>

        <t-form-item name="confirmPassword">
          <t-input
            v-model="registerFormData.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            size="large"
          />
        </t-form-item>

        <t-form-item name="inviteCode" :show-error-message="false">
          <t-input
            v-model="registerFormData.inviteCode"
            :status="inviteInputStatus"
            :tips="inviteInputTips"
            clearable
            placeholder="请输入邀请码"
            size="large"
            @blur="handleInviteCodeBlur"
            @change="handleInviteCodeChange"
          />
        </t-form-item>

        <t-form-item class="pt-2">
          <t-button theme="primary" type="submit" block :loading="registerLoading">
            注册并返回登录
          </t-button>
        </t-form-item>

        <div class="mt-4 text-center text-sm text-gray-500">
          已有账号？
          <button
            type="button"
            class="font-medium text-[var(--td-brand-color)] transition-colors hover:text-[var(--td-brand-color-hover)]"
            @click="switchMode('login')"
          >
            去登录
          </button>
        </div>
      </t-form>
    </div>

    <div class="mt-8 text-sm text-gray-400">Copyright @ 2024 Niro Control</div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { type FormRules, type SubmitContext } from "tdesign-vue-next";
import { UserIcon, LockOnIcon } from "tdesign-icons-vue-next";
import { userApi } from "@/api/user";
import { useRequest } from "@/composables/useRequest";
import { useUserStore } from "@/store/user";
import { usePermissionStore } from "@/store/permission";
import { encrypt, decrypt } from "@/utils/crypto";

type AuthMode = "login" | "register";
type InviteValidationState = "idle" | "validating" | "valid" | "invalid";
type InviteSource = "none" | "link" | "manual";
type InputStatus = "default" | "success" | "warning" | "error";

interface AccountFormData {
  username: string;
  password: string;
}

interface RegisterFormData {
  inviteCode: string;
  email: string;
  emailCode: string;
  password: string;
  confirmPassword: string;
}

interface FormRefLike {
  clearValidate: (fields?: string[]) => void;
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const EMAIL_CODE_COUNTDOWN_SECONDS = 60;

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const accountFormRef = ref<FormRefLike | null>(null);
const registerFormRef = ref<FormRefLike | null>(null);
const rememberMe = ref(false);
const authMode = ref<AuthMode>("login");
const loginNotice = ref("");
const inviteValidationState = ref<InviteValidationState>("idle");
const inviteValidationMessage = ref("");
const inviteSource = ref<InviteSource>("none");
const emailCodeHint = ref("");
const emailCodeFeedbackState = ref<InputStatus>("default");
const sendEmailCodeLoading = ref(false);
const sendEmailCodeCountdown = ref(0);
const registerLoading = ref(false);

let sendEmailCodeTimer: number | null = null;

const accountFormData = reactive<AccountFormData>({
  username: "",
  password: "",
});

const registerFormData = reactive<RegisterFormData>({
  inviteCode: "",
  email: "",
  emailCode: "",
  password: "",
  confirmPassword: "",
});

const isRegisterMode = computed(() => authMode.value === "register");
const registerBanner = computed(() => {
  if (inviteSource.value === "link" && registerFormData.inviteCode.trim()) {
    return "已从邀请链接带入邀请码，请完成注册。";
  }
  return "";
});
const inviteInputStatus = computed<InputStatus>(() => {
  if (inviteValidationState.value === "valid") {
    return "success";
  }
  if (inviteValidationState.value === "invalid") {
    return "error";
  }
  return "default";
});
const inviteInputTips = computed(() => {
  if (inviteValidationState.value === "validating") {
    return "正在校验邀请码...";
  }
  return inviteValidationMessage.value;
});
const emailCodeInputStatus = computed<InputStatus>(() => emailCodeFeedbackState.value);
const sendEmailCodeButtonText = computed(() => {
  if (sendEmailCodeCountdown.value > 0) {
    return `${sendEmailCodeCountdown.value}s 后重发`;
  }
  return "发送验证码";
});
const sendEmailCodeDisabled = computed(() => {
  return sendEmailCodeLoading.value || sendEmailCodeCountdown.value > 0 || !isEmailFormat(registerFormData.email);
});

const accountRules: FormRules<AccountFormData> = {
  username: [{ required: true, message: "请输入用户名", type: "error", trigger: "blur" }],
  password: [{ required: true, message: "请输入密码", type: "error", trigger: "blur" }],
};

const registerRules: FormRules<RegisterFormData> = {
  inviteCode: [{ required: true, message: "请输入邀请码", type: "error", trigger: "blur" }],
  email: [
    { required: true, message: "请输入邮箱", type: "error", trigger: "blur" },
    { email: true, message: "请输入正确的邮箱地址", type: "error", trigger: "blur" },
  ],
  emailCode: [{ required: true, message: "请输入邮箱验证码", type: "error", trigger: "blur" }],
  password: [
    { required: true, message: "请输入密码", type: "error", trigger: "blur" },
    { min: 8, message: "密码长度至少 8 位", type: "error", trigger: "blur" },
    { max: 20, message: "密码长度不能超过 20 位", type: "error", trigger: "blur" },
  ],
  confirmPassword: [
    { required: true, message: "请再次输入密码", type: "error", trigger: "blur" },
    {
      validator: (value: string) => value === registerFormData.password,
      message: "两次输入的密码不一致",
      type: "error",
      trigger: "blur",
    },
  ],
};

function normalizeQueryValue(value: unknown): string {
  if (Array.isArray(value)) {
    return typeof value[0] === "string" ? value[0] : "";
  }
  return typeof value === "string" ? value : "";
}

function isEmailFormat(value: string) {
  return EMAIL_PATTERN.test(value.trim());
}

function clearSendEmailCodeTimer() {
  if (sendEmailCodeTimer !== null) {
    window.clearInterval(sendEmailCodeTimer);
    sendEmailCodeTimer = null;
  }
}

function startSendEmailCodeCountdown() {
  clearSendEmailCodeTimer();
  sendEmailCodeCountdown.value = EMAIL_CODE_COUNTDOWN_SECONDS;
  sendEmailCodeTimer = window.setInterval(() => {
    if (sendEmailCodeCountdown.value <= 1) {
      clearSendEmailCodeTimer();
      sendEmailCodeCountdown.value = 0;
      return;
    }
    sendEmailCodeCountdown.value -= 1;
  }, 1000);
}

function resetEmailCodeState() {
  clearSendEmailCodeTimer();
  sendEmailCodeCountdown.value = 0;
  emailCodeHint.value = "";
  emailCodeFeedbackState.value = "default";
  registerFormData.emailCode = "";
}

function resetInviteValidation() {
  inviteValidationState.value = "idle";
  inviteValidationMessage.value = "";
}

function resetRegisterState() {
  registerFormData.inviteCode = "";
  registerFormData.email = "";
  registerFormData.emailCode = "";
  registerFormData.password = "";
  registerFormData.confirmPassword = "";
  inviteSource.value = "none";
  resetInviteValidation();
  resetEmailCodeState();
  registerFormRef.value?.clearValidate();
}

async function switchMode(mode: AuthMode) {
  authMode.value = mode;
  if (mode === "register") {
    loginNotice.value = "";
    syncRouteQuery("register", registerFormData.inviteCode.trim());
  } else {
    syncRouteQuery("login");
  }
  await nextTick();
  if (mode === "login") {
    registerFormRef.value?.clearValidate();
    return;
  }
  accountFormRef.value?.clearValidate();
}

function handleInviteCodeChange(value: string | number) {
  const nextInviteCode = String(value ?? "");
  inviteSource.value = "manual";
  registerFormData.inviteCode = nextInviteCode;
  resetInviteValidation();
  syncRouteQuery("register", nextInviteCode.trim());
}

function handleRegisterEmailChange() {
  resetEmailCodeState();
  registerFormRef.value?.clearValidate(["email", "emailCode"]);
}

function syncRouteQuery(mode: AuthMode, inviteCode = "") {
  const nextQuery: Record<string, string> = {};
  if (mode === "register") {
    nextQuery.mode = "register";
    if (inviteCode) {
      nextQuery.inviteCode = inviteCode;
    }
  }
  router.replace({ query: nextQuery });
}

async function validateInviteCode() {
  const inviteCode = registerFormData.inviteCode.trim();
  if (!inviteCode) {
    resetInviteValidation();
    return false;
  }

  inviteValidationState.value = "validating";
  try {
    const response = await userApi.validateInviteCode({ inviteCode });
    if (response.valid) {
      inviteValidationState.value = "valid";
      inviteValidationMessage.value = response.message || "邀请码可用";
      return true;
    }

    inviteValidationState.value = "invalid";
    inviteValidationMessage.value = response.message || "邀请码不可用";
    return false;
  } catch {
    inviteValidationState.value = "invalid";
    inviteValidationMessage.value = "邀请码校验失败，请稍后重试";
    return false;
  }
}

async function handleInviteCodeBlur() {
  if (!registerFormData.inviteCode.trim()) {
    return;
  }
  await validateInviteCode();
}

async function handleSendRegisterEmailCode() {
  const email = registerFormData.email.trim();
  if (!isEmailFormat(email)) {
    emailCodeHint.value = "请输入正确的邮箱地址。";
    emailCodeFeedbackState.value = "error";
    return;
  }

  sendEmailCodeLoading.value = true;
  try {
    await userApi.sendRegisterEmailCode({
      email,
    });
    emailCodeHint.value = "验证码已发送，请注意查收邮箱。";
    emailCodeFeedbackState.value = "success";
    startSendEmailCodeCountdown();
  } catch {
    emailCodeHint.value = "验证码发送失败，请稍后重试。";
    emailCodeFeedbackState.value = "error";
  } finally {
    sendEmailCodeLoading.value = false;
  }
}

const { loading: loginLoading, run: handleAccountLogin } = useRequest(
  async (context: SubmitContext) => {
    if (context.validateResult !== true) {
      return;
    }

    try {
      const loginSuccess = await userStore.login(accountFormData);
      if (!loginSuccess) {
        return;
      }

      sessionStorage.removeItem("niro-dynamic-routes-raw");

      const permissionStore = usePermissionStore();
      permissionStore.isRoutesLoaded = false;

      if (rememberMe.value) {
        const encryptedPassword = encrypt(accountFormData.password);
        localStorage.setItem(
          "niro-remember-me",
          JSON.stringify({
            username: accountFormData.username,
            password: encryptedPassword,
            isRemember: true,
          })
        );
      } else {
        localStorage.removeItem("niro-remember-me");
      }

      await userStore.getInfo();
      router.push("/");
    } catch {
      // 异常已由拦截器处理
    }
  }
);

async function handleRegister(context: SubmitContext) {
  if (context.validateResult !== true) {
    return;
  }

  const inviteValid = inviteValidationState.value === "valid" ? true : await validateInviteCode();
  if (!inviteValid) {
    return;
  }

  registerLoading.value = true;
  try {
    await userApi.register({
      inviteCode: registerFormData.inviteCode.trim(),
      email: registerFormData.email.trim(),
      emailCode: registerFormData.emailCode.trim(),
      password: registerFormData.password,
    });

    const registeredUsername = registerFormData.email.trim();
    resetRegisterState();
    authMode.value = "login";
    syncRouteQuery("login");
    accountFormData.username = registeredUsername;
    accountFormData.password = "";
    loginNotice.value = "注册成功，请使用新账号登录。";
    await nextTick();
    accountFormRef.value?.clearValidate();
  } catch {
    // 异常已由拦截器处理
  } finally {
    registerLoading.value = false;
  }
}

watch(
  () => ({
    mode: normalizeQueryValue(route.query.mode),
    inviteCode: normalizeQueryValue(route.query.inviteCode).trim(),
  }),
  async ({ mode, inviteCode }) => {
    if (inviteCode) {
      authMode.value = "register";
      if (registerFormData.inviteCode !== inviteCode) {
        registerFormData.inviteCode = inviteCode;
        resetEmailCodeState();
      }
      inviteSource.value = "link";
      inviteValidationState.value = "idle";
      inviteValidationMessage.value = "已从邀请链接带入邀请码";
      await nextTick();
      await validateInviteCode();
      return;
    }

    if (mode === "register") {
      authMode.value = "register";
      return;
    }

    authMode.value = "login";
    inviteSource.value = "none";
  },
  { immediate: true }
);

onMounted(() => {
  const remembered = localStorage.getItem("niro-remember-me");
  if (!remembered) {
    return;
  }

  try {
    const parsed = JSON.parse(remembered) as {
      username?: string;
      password?: string;
      isRemember?: boolean;
    };
    if (!parsed.isRemember || !parsed.username || !parsed.password) {
      return;
    }

    accountFormData.username = parsed.username;
    const decryptedPassword = decrypt(parsed.password);
    if (!decryptedPassword) {
      return;
    }

    accountFormData.password = decryptedPassword;
    rememberMe.value = true;
  } catch {
    localStorage.removeItem("niro-remember-me");
  }
});

onBeforeUnmount(() => {
  clearSendEmailCodeTimer();
});
</script>

<style scoped>
:deep(.t-input) {
  background-color: #f9fafb;
  border-color: transparent;
  transition: all 0.2s;
}

:deep(.t-input:hover),
:deep(.t-input.t-is-focused) {
  background-color: #ffffff;
  border-color: var(--td-brand-color);
}

:deep(.t-form__item) {
  margin-bottom: 24px;
}
</style>
