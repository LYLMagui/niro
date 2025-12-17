<template>
  <div class="mx-auto max-w-4xl">
    <!-- 配置表单卡片 -->
    <t-card title="爬虫配置" :bordered="false">
      <t-form ref="form" :data="formData" :rules="rules" :label-width="120" @submit="onSubmit">
        <!-- Cookie 输入框 -->
        <t-form-item label="Cookies" name="cookies">
          <t-textarea
            v-model="formData.cookies"
            placeholder="请输入Buff Cookies"
            :autosize="{ minRows: 3, maxRows: 6 }"
          />
        </t-form-item>

        <!-- 扫描间隔设置 -->
        <t-form-item label="扫描间隔(ms)" name="interval">
          <t-input-number v-model="formData.interval" theme="column" :min="100" :max="10000" />
        </t-form-item>

        <!-- 价格区间范围选择 -->
        <t-form-item label="价格区间" name="priceRange">
          <t-range-input v-model="formData.priceRange" />
        </t-form-item>

        <!-- 磨损区间范围选择 -->
        <t-form-item label="磨损区间" name="floatRange">
          <t-range-input v-model="formData.floatRange" :placeholder="['Min Float', 'Max Float']" />
        </t-form-item>

        <!-- 自动下单开关 -->
        <t-form-item label="自动下单" name="autoBuy">
          <t-switch v-model="formData.autoBuy" />
        </t-form-item>

        <!-- 通知方式多选 -->
        <t-form-item label="通知设置" name="notifications">
          <t-checkbox-group v-model="formData.notifications">
            <t-checkbox value="email">邮件通知</t-checkbox>
            <t-checkbox value="wechat">微信推送</t-checkbox>
            <t-checkbox value="desktop">桌面弹窗</t-checkbox>
          </t-checkbox-group>
        </t-form-item>

        <!-- 表单操作按钮 -->
        <t-form-item>
          <t-button theme="primary" type="submit">保存配置</t-button>
          <t-button theme="default" variant="base" type="reset" class="ml-4">重置</t-button>
        </t-form-item>
      </t-form>
    </t-card>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue';
import { MessagePlugin } from 'tdesign-vue-next';

// 表单响应式数据
const formData = reactive({
  cookies: '',
  interval: 2000,
  priceRange: [0, 1000],
  floatRange: [0, 1],
  autoBuy: false,
  notifications: ['desktop'],
});

// 表单校验规则
const rules = {
  cookies: [{ required: true, message: 'Cookies必填', type: 'error' }],
  interval: [{ required: true, message: '请设置扫描间隔', type: 'error' }],
};

// 提交处理函数
const onSubmit = ({ validateResult, firstError }: any) => {
  if (validateResult === true) {
    // 这里可以调用 API 保存配置
    MessagePlugin.success('配置已保存');
  } else {
    MessagePlugin.warning(firstError);
  }
};
</script>
