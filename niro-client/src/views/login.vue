<template>
  <div class="flex min-h-screen w-full flex-col items-center justify-center bg-[#f0f2f5]">
    <!-- 登录卡片 -->
    <div
      class="w-full max-w-[420px] rounded-lg bg-white p-10 shadow-[0_8px_24px_rgba(0,0,0,0.16)] transition-all duration-500 ease-in-out hover:shadow-[0_16px_48px_rgba(0,0,0,0.22)]"
    >
      <!-- 顶部 Logo 区域 -->
      <div class="mb-10 flex flex-col items-center justify-center">
        <h1 class="text-3xl font-bold text-gray-800">Niro</h1>
        <p class="mt-2 text-sm text-gray-500">追风赶月莫停留，平芜尽处是春山</p>
      </div>

      <t-form
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

        <div class="mb-4 flex items-center justify-between">
          <t-checkbox v-model="rememberMe">记住我</t-checkbox>
        </div>

        <t-form-item class="pt-2">
          <t-button theme="primary" type="submit" block size="large" :loading="loading">
            登录
          </t-button>
        </t-form-item>
      </t-form>
    </div>

    <!-- 底部版权信息 -->
    <div class="mt-8 text-sm text-gray-400">Copyright @ 2024 Niro Control</div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from "vue";
import { MessagePlugin, FormRules, SubmitContext } from "tdesign-vue-next";
import { useRouter } from "vue-router";
import { UserIcon, LockOnIcon } from "tdesign-icons-vue-next";
import { userApi } from "@/api/user";
import { useRequest } from "@/composables/useRequest";
import { encrypt, decrypt } from "@/utils/crypto";

// 路由
const router = useRouter();

// 记住我
const rememberMe = ref(false);

// 账号登录表单
const accountFormData = reactive({
  username: "",
  password: "",
});

// 初始化时读取记住的账号密码
onMounted(() => {
  const remembered = localStorage.getItem("niro-remember-me");
  if (remembered) {
    try {
      const { username, password, isRemember } = JSON.parse(remembered);
      if (isRemember) {
        accountFormData.username = username;
        // 解密密码
        const decryptedPassword = decrypt(password);
        if (decryptedPassword) {
          accountFormData.password = decryptedPassword;
          rememberMe.value = true;
        }
      }
    } catch {
      localStorage.removeItem("niro-remember-me");
    }
  }
});

const accountRules: FormRules = {
  username: [{ required: true, message: "请输入用户名", type: "error" }],
  password: [{ required: true, message: "请输入密码", type: "error" }],
};

const { loading, run: handleAccountLogin } = useRequest(async (context: SubmitContext) => {
  if (context.validateResult === true) {
    try {
      const res = await userApi.login(accountFormData);
      if (res) {
        // 登录成功后，将 token 存储到 localStorage
        if (res.token) {
          localStorage.setItem("niro-web-token", res.token);
        }

        // 存储用户信息
        localStorage.setItem("niro-user-info", JSON.stringify(res));

        MessagePlugin.success("登录成功");

        // 记住我逻辑
        if (rememberMe.value) {
          // 加密密码存储
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

        // 获取重定向地址，如果有则跳转到重定向地址，否则跳转到仪表盘
        const redirect = router.currentRoute.value.query.redirect as string;
        router.push(redirect || "/dashboard");
      }
    } catch {
      // 异常已由拦截器处理
    }
  }
});
</script>

<style scoped>
/* 强制覆盖 TDesign 输入框样式 */
:deep(.t-input) {
  background-color: #f9fafb; /* gray-50 */
  border-color: transparent;
  transition: all 0.2s;
}

:deep(.t-input:hover),
:deep(.t-input.t-is-focused) {
  background-color: #ffffff;
  border-color: var(--td-brand-color);
}

/* 增加表单项间距 */
:deep(.t-form__item) {
  margin-bottom: 24px;
}
</style>
