<template>
  <div class="hidden items-center justify-between px-0 py-5 md:flex">
    <div class="flex items-center gap-4">
      <div
        class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-blue-50 text-blue-600 shadow-sm ring-1 shadow-blue-100/20 ring-blue-100/50"
      >
        <slot name="icon" />
      </div>
      <h1 class="text-xl font-bold tracking-tight text-slate-900">{{ title }}</h1>
    </div>
    <div class="flex items-center gap-6">
      <slot name="extra" />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string;
}>();
</script>
