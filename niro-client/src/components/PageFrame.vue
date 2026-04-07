<template>
  <div
    :class="[
      'flex h-full min-h-0 flex-col',
      isMobile ? mobileOuterClass : desktopOuterClass,
      outerClass,
    ]"
  >
    <section
      :class="[
        'flex min-h-0 flex-1 flex-col bg-white',
        isMobile ? mobileShellClass : desktopShellClass,
        shellClass,
      ]"
    >
      <div
        :ref="setBodyRef"
        :class="[
          'relative flex min-h-0 flex-1 flex-col overflow-x-hidden',
          isMobile ? mobileBodyClass : desktopBodyClass,
          bodyClass,
        ]"
      >
        <div
          :class="[
            'flex min-h-0 flex-1 flex-col',
            isMobile ? mobileContentClass : desktopContentClass,
            contentClass,
          ]"
        >
          <slot />
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import type { ComponentPublicInstance, HTMLAttributes } from "vue";

interface Props {
  isMobile: boolean;
  onBodyRefChange?: ((element: HTMLElement | null) => void) | null;
  outerClass?: HTMLAttributes["class"];
  shellClass?: HTMLAttributes["class"];
  bodyClass?: HTMLAttributes["class"];
  contentClass?: HTMLAttributes["class"];
  desktopOuterClass?: HTMLAttributes["class"];
  mobileOuterClass?: HTMLAttributes["class"];
  desktopShellClass?: HTMLAttributes["class"];
  mobileShellClass?: HTMLAttributes["class"];
  desktopBodyClass?: HTMLAttributes["class"];
  mobileBodyClass?: HTMLAttributes["class"];
  desktopContentClass?: HTMLAttributes["class"];
  mobileContentClass?: HTMLAttributes["class"];
}

const props = withDefaults(defineProps<Props>(), {
  onBodyRefChange: null,
  outerClass: undefined,
  shellClass: undefined,
  bodyClass: undefined,
  contentClass: undefined,
  desktopOuterClass: "px-1 pt-1 pb-2",
  mobileOuterClass: "px-0 pt-0 pb-0",
  desktopShellClass: "overflow-hidden rounded-[1px]",
  mobileShellClass: "overflow-visible rounded-none",
  desktopBodyClass: "overflow-hidden",
  mobileBodyClass: "overflow-y-auto",
  desktopContentClass: "px-4 pt-3 pb-4",
  mobileContentClass: "px-3 pt-3 pb-0",
});

const setBodyRef = (element: Element | ComponentPublicInstance | null) => {
  props.onBodyRefChange?.(element instanceof HTMLElement ? element : null);
};
</script>
