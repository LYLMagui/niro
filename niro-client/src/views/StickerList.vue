<template>
  <div class="sticker-list-container p-6">
    <t-card :bordered="false" class="embedded-card shadow-sm">
      <template #title>
        <div class="flex items-center">
          <t-icon name="view-module" class="mr-2 text-blue-600" />
          <span class="text-lg font-bold text-gray-800">印花价值管理</span>
          <t-tag theme="primary" variant="light" class="ml-4">数据同步频率：由任务配置管理</t-tag>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="border-b border-gray-100 p-6">
        <t-form :data="queryParams" layout="inline" @submit="handleSearch">
          <t-form-item label="搜索名称" name="keyword">
            <t-input
              v-model="queryParams.keyword"
              placeholder="输入印花名称关键词"
              clearable
              @blur="(v: any) => handleInputTrim(v, queryParams, 'keyword')"
            />
          </t-form-item>
          <t-form-item>
            <div class="flex gap-4">
              <t-button
                theme="primary"
                type="submit"
                size="medium"
                class="rounded-lg transition-all duration-300 hover:shadow active:shadow-none"
              >
                查询
              </t-button>
              <t-button
                variant="outline"
                size="medium"
                class="rounded-lg transition-all duration-300 hover:shadow active:shadow-none"
                @click="handleReset"
              >
                重置
              </t-button>
            </div>
          </t-form-item>
        </t-form>
      </div>

      <!-- 表格 -->
      <t-table
        row-key="id"
        :data="dataList"
        :columns="columns"
        :loading="loading"
        :pagination="pagination"
        hover
        :header-affixed-top="true"
        class="embedded-table w-full"
        @page-change="onPageChange"
      >
        <template #empty>
          <t-empty icon="view-module" description="暂无印花数据" />
        </template>
        <template #imageUrl="{ row }">
          <div
            class="mx-auto flex cursor-pointer items-center justify-center border border-gray-200 bg-white"
            style="width: 110px; height: 110px"
            @click="onPreview(row.imageUrl)"
          >
            <t-image
              :src="row.imageUrl"
              fit="contain"
              :style="{ width: '100px', height: '100px' }"
            />
          </div>
        </template>
        <template #price="{ row }">
          <span class="font-bold text-orange-600">¥{{ row.price }}</span>
        </template>
        <template #updateTime="{ row }">
          <span class="text-gray-500">{{ formatTime(row.updateTime) }}</span>
        </template>
      </t-table>
    </t-card>

    <!-- 图片预览组件 -->
    <t-image-viewer
      :images="[previewImage]"
      :visible="imageVisible"
      mode="modal"
      :close-on-overlay="true"
      @close="imageVisible = false"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * 作者: liyl
 * 创建时间: 2026-01-08
 * 功能简述: 印花价值列表展示与同步管理
 */
import { stickerApi } from "@/api/sticker";
import { DEFAULT_PAGE_SIZE } from "@/utils/constants";
import { onMounted, reactive, ref } from "vue";
import { type PrimaryTableCol } from "tdesign-vue-next";

const loading = ref(false);
const dataList = ref<any[]>([]);

/**
 * 自动清除换行符和首尾空格
 */
const handleInputTrim = (val: any, target: any, key: string) => {
  if (typeof val === "string") {
    target[key] = val.replace(/[\r\n]/g, "").trim();
  }
};

// 图片预览状态
const imageVisible = ref(false);
const previewImage = ref("");

const onPreview = (url: string) => {
  if (!url) return;
  previewImage.value = url;
  imageVisible.value = true;
};

const queryParams = reactive({
  keyword: "",
  page: 1,
  pageSize: DEFAULT_PAGE_SIZE,
});

const pagination = reactive({
  current: 1,
  pageSize: DEFAULT_PAGE_SIZE,
  total: 0,
  showJumper: true,
});

const columns: PrimaryTableCol<any>[] = [
  { colKey: "imageUrl", title: "图标", width: 140, align: "center" },
  { colKey: "name", title: "印花名称", ellipsis: true, minWidth: 200, align: "left" },
  { colKey: "price", title: "当前价格", width: 120, align: "right" },
  { colKey: "sellNum", title: "在售数量", width: 100, align: "right" },
  { colKey: "updateTime", title: "最后更新", width: 180, align: "left" },
];

// 获取列表数据
const fetchData = async () => {
  loading.value = true;
  try {
    const res = await stickerApi.getStickerList({
      page: queryParams.page,
      pageSize: queryParams.pageSize,
      name: queryParams.keyword,
    });
    dataList.value = (res as any).records;
    pagination.total = (res as any).total;
  } catch (error) {
    console.error("获取印花列表失败:", error);
  } finally {
    loading.value = false;
  }
};

// 分页变化
const onPageChange = (pageInfo: any) => {
  queryParams.page = pageInfo.current;
  queryParams.pageSize = pageInfo.pageSize;
  pagination.current = pageInfo.current;
  pagination.pageSize = pageInfo.pageSize;
  fetchData();
};

// 搜索
const handleSearch = () => {
  queryParams.page = 1;
  pagination.current = 1;
  fetchData();
};

// 重置
const handleReset = () => {
  queryParams.keyword = "";
  handleSearch();
};

// 格式化时间
const formatTime = (time: string) => {
  if (!time) return "-";
  const date = new Date(time);
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  const h = String(date.getHours()).padStart(2, "0");
  const min = String(date.getMinutes()).padStart(2, "0");
  const s = String(date.getSeconds()).padStart(2, "0");
  return `${y}-${m}-${d} ${h}:${min}:${s}`;
};

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
/* 图片预览样式保持 */
</style>
