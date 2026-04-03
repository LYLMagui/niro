<template>
  <teleport :to="resolvedAttach || 'body'" :disabled="!resolvedAttach">
    <div v-if="rendered" ref="rootRef" :class="rootClass">
      <div class="page-overlay-dialog__mask" @click="handleMaskClick"></div>

      <div class="page-overlay-dialog__viewport">
        <section ref="panelRef" class="page-overlay-dialog__panel" :style="panelStyle" @click.stop>
          <header class="page-overlay-dialog__header">
            <div class="min-w-0 flex-1">
              <slot name="title">
                <h2 class="page-overlay-dialog__title">{{ title }}</h2>
              </slot>
            </div>

            <button
              v-if="showClose"
              type="button"
              class="page-overlay-dialog__close"
              aria-label="关闭弹窗"
              @click="close"
            >
              <close-icon />
            </button>
          </header>

          <div class="page-overlay-dialog__body">
            <slot />
          </div>

          <footer v-if="$slots.footer" class="page-overlay-dialog__footer">
            <slot name="footer" />
          </footer>
        </section>
      </div>
    </div>
  </teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import { CloseIcon } from "tdesign-icons-vue-next";

interface OverlayOriginRect {
  left: number;
  top: number;
  width: number;
  height: number;
}

interface Props {
  visible: boolean;
  title: string;
  width?: string | number;
  showClose?: boolean;
  maskClosable?: boolean;
  attach?: string;
  originRect?: OverlayOriginRect;
}

const LEAVE_DURATION = 320;

const props = withDefaults(defineProps<Props>(), {
  width: "1080px",
  showClose: true,
  maskClosable: true,
  attach: "",
  originRect: undefined,
});

const emit = defineEmits<{
  (e: "update:visible", value: boolean): void;
  (e: "close"): void;
}>();

const rootRef = ref<HTMLElement | null>(null);
const panelRef = ref<HTMLElement | null>(null);
const rendered = ref(false);
const active = ref(false);
const leaving = ref(false);
let leaveTimer: number | null = null;

const resolvedAttach = computed(() => props.attach || "");
const rootClass = computed(() => [
  "page-overlay-dialog inset-0 z-50",
  resolvedAttach.value ? "absolute" : "fixed",
  {
    "page-overlay-dialog--active": active.value,
    "page-overlay-dialog--leaving": leaving.value,
  },
]);

const panelStyle = computed(() => ({
  width: "100%",
  maxWidth: typeof props.width === "number" ? `${props.width}px` : props.width,
}));

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), max);

const clearLeaveTimer = () => {
  if (leaveTimer === null) return;
  window.clearTimeout(leaveTimer);
  leaveTimer = null;
};

const applyPanelMotion = () => {
  const panel = panelRef.value;
  if (!panel) return;

  const panelRect = panel.getBoundingClientRect();
  const originRect = props.originRect;

  if (!originRect || panelRect.width === 0 || panelRect.height === 0) {
    panel.style.setProperty("--overlay-origin-x", "0px");
    panel.style.setProperty("--overlay-origin-y", "-16px");
    panel.style.setProperty("--overlay-origin-scale", "0.96");
    panel.style.transformOrigin = "center top";
    return;
  }

  const triggerCenterX = originRect.left + originRect.width / 2;
  const triggerCenterY = originRect.top + originRect.height / 2;
  const panelCenterX = panelRect.left + panelRect.width / 2;
  const panelCenterY = panelRect.top + panelRect.height / 2;
  const scale = clamp(
    Math.max(originRect.width / panelRect.width, originRect.height / panelRect.height),
    0.12,
    0.3,
  );
  const originX = clamp(((triggerCenterX - panelRect.left) / panelRect.width) * 100, 0, 100);
  const originY = clamp(((triggerCenterY - panelRect.top) / panelRect.height) * 100, 0, 100);

  panel.style.setProperty("--overlay-origin-x", `${triggerCenterX - panelCenterX}px`);
  panel.style.setProperty("--overlay-origin-y", `${triggerCenterY - panelCenterY}px`);
  panel.style.setProperty("--overlay-origin-scale", `${scale}`);
  panel.style.transformOrigin = `${originX}% ${originY}%`;
};

const startEnter = async () => {
  clearLeaveTimer();
  leaving.value = false;
  active.value = false;
  rendered.value = true;

  await nextTick();
  applyPanelMotion();

  requestAnimationFrame(() => {
    active.value = true;
  });
};

const finishLeave = () => {
  clearLeaveTimer();
  leaving.value = false;
  active.value = false;
  rendered.value = false;
};

const startLeave = () => {
  if (!rendered.value) return;

  clearLeaveTimer();
  applyPanelMotion();
  leaving.value = true;
  active.value = false;
  leaveTimer = window.setTimeout(finishLeave, LEAVE_DURATION);
};

const close = () => {
  emit("update:visible", false);
  emit("close");
};

const handleMaskClick = () => {
  if (!props.maskClosable) return;
  close();
};

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      startEnter();
      return;
    }
    startLeave();
  },
  { immediate: true },
);

onBeforeUnmount(() => {
  clearLeaveTimer();
});
</script>

<style scoped>
.page-overlay-dialog {
  pointer-events: auto;
}

.page-overlay-dialog__mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.42);
  opacity: 0;
  transition: opacity 0.18s ease;
}

.page-overlay-dialog__viewport {
  position: relative;
  z-index: 1;
  display: flex;
  width: 100%;
  height: 100%;
  align-items: flex-start;
  justify-content: center;
  overflow: auto;
  padding: 16px;
  box-sizing: border-box;
}

.page-overlay-dialog__panel {
  display: flex;
  max-height: 100%;
  flex-direction: column;
  overflow: hidden;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.18);
  opacity: 0;
  transform: translate(var(--overlay-origin-x, 0px), var(--overlay-origin-y, -16px))
    scale(var(--overlay-origin-scale, 0.96));
  transition:
    transform 0.28s cubic-bezier(0.2, 0.8, 0.2, 1),
    opacity 0.22s ease;
  will-change: transform, opacity;
}

.page-overlay-dialog--active .page-overlay-dialog__mask {
  opacity: 1;
}

.page-overlay-dialog--active .page-overlay-dialog__panel {
  opacity: 1;
  transform: translate(0, 0) scale(1);
}

.page-overlay-dialog__header {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 24px 18px;
  border-bottom: 1px solid #efefef;
}

.page-overlay-dialog__title {
  margin: 0;
  color: #111827;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
}

.page-overlay-dialog__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  color: #6b7280;
  border: 0;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.page-overlay-dialog__close:hover {
  color: #111827;
  background: #f3f4f6;
}

.page-overlay-dialog__body {
  min-height: 0;
  flex: 1 1 auto;
  overflow: auto;
}

.page-overlay-dialog__footer {
  flex: 0 0 auto;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px 20px;
  border-top: 1px solid #f3f4f6;
  background: #fff;
}
</style>
