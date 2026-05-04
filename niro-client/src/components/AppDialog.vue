<script setup lang="ts">
import { computed } from "vue";

interface Props {
  visible: boolean;
  title?: string;
  width?: string;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  title: "",
  width: "520px",
});

const emit = defineEmits(["update:visible", "close"]);

const innerVisible = computed({
  get: () => props.visible,
  set: (val) => emit("update:visible", val),
});

const onClose = () => {
  emit("close");
};
</script>

<template>
  <t-dialog
    v-model:visible="innerVisible"
    v-bind="$attrs"
    :header="title"
    :footer="false"
    :width="width"
    placement="center"
    class="app-common-dialog"
    :dialog-style="{ padding: 0, boxShadow: '0 10px 30px rgba(15, 23, 42, 0.12)' }"
    @close="onClose"
  >
    <div class="dialog-shell">
      <slot></slot>
    </div>
  </t-dialog>
</template>

<style scoped>
/* 弹窗整体样式增强 */
:deep(.app-common-dialog .t-dialog__header) {
  padding: 16px 24px;
  border-bottom: 1px solid #f1f5f9;
}

:deep(.app-common-dialog .t-dialog__body) {
  padding: 0;
}

.dialog-shell {
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #fff;
}

/* 提供的通用子容器样式，通过 :deep 作用于插槽内容 */
:deep(.form-container) {
  max-height: min(640px, calc(100vh - 260px));
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 20px 24px;
  background: #fff;
}

:deep(.form-footer) {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  border-top: 1px solid #f1f5f9;
  background: #f8fafc; /* slate-50/50 */
  padding: 16px 24px;
}

/* 紧凑型表单样式增强 */
:deep(.compact-form .t-form__item) {
  margin-bottom: 16px;
}

:deep(.compact-form .t-form__item.t-is-error) {
  margin-bottom: 28px;
}

:deep(.compact-form .t-form__item .t-form__controls-content) {
  display: flex;
  flex-direction: column;
  align-items: stretch;
}

:deep(.compact-form .t-form__label) {
  padding-right: 12px !important;
}

:deep(.compact-form .t-form__item .t-form__verify-message) {
  position: relative !important;
  display: block !important;
  min-height: auto !important;
  margin-top: 6px;
  margin-bottom: 0;
  font-size: 12px;
  line-height: 1.5;
}

/* 响应式适配 */
@media (max-width: 768px) {
  :deep(.form-container) {
    padding: 16px;
    max-height: calc(100vh - 180px);
  }

  :deep(.compact-form .t-form__item) {
    margin-bottom: 14px;
  }

  :deep(.compact-form .t-form__label) {
    width: 88px !important;
    padding-right: 8px;
  }
}

@media (max-width: 640px) {
  :deep(.compact-form .t-form__item) {
    display: flex;
    flex-direction: column;
    align-items: stretch;
  }

  :deep(.compact-form .t-form__label) {
    width: 100% !important;
    min-width: 0 !important;
    padding-right: 0;
    margin-bottom: 8px;
    text-align: left;
    line-height: 1.5;
  }

  :deep(.compact-form .t-form__controls) {
    width: 100%;
    margin-left: 0 !important;
  }
}
</style>
