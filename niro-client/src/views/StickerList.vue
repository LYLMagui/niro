<template>
  <div class="sticker-list-container p-6">
    <t-card :bordered="false" class="shadow-sm">
      <div class="flex justify-between items-center mb-6">
        <div class="flex items-center">
          <h2 class="text-lg font-bold mr-4">印花价值管理</h2>
          <t-tag theme="primary" variant="light">数据同步频率：由任务配置管理</t-tag>
        </div>
      </div>

      <!-- 搜索栏 -->
      <t-form :data="queryParams" layout="inline" class="mb-6" @submit="handleSearch">
        <t-form-item label="搜索名称" name="keyword">
          <t-input v-model="queryParams.keyword" placeholder="输入印花名称关键词" clearable />
        </t-form-item>
        <t-form-item>
          <t-button theme="primary" type="submit">查询</t-button>
          <t-button variant="outline" class="ml-2" @click="handleReset">重置</t-button>
        </t-form-item>
      </t-form>

      <!-- 表格 -->
      <t-table
        row-key="id"
        :data="dataList"
        :columns="columns"
        :loading="loading"
        :pagination="pagination"
        hover
        :header-affixed-top="true"
        class="custom-table"
        @page-change="onPageChange"
      >
        <template #imageUrl="{ row }">
          <div
            class="flex cursor-pointer items-center justify-center border border-gray-200 bg-white mx-auto"
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
import { stickerApi } from '@/api/sticker';
import { ADMIN_USER_ID, DEFAULT_PAGE_SIZE } from '@/utils/constants';
import { onMounted, reactive, ref } from 'vue';
import { MessagePlugin } from 'tdesign-vue-next';

// 权限控制：使用常量定义的管理员ID
const currentUserId = ADMIN_USER_ID; 

const loading = ref(false);
const dataList = ref([]);

// 图片预览状态
const imageVisible = ref(false);
const previewImage = ref("");

const onPreview = (url: string) => {
  if (!url) return;
  previewImage.value = url;
  imageVisible.value = true;
};

const queryParams = reactive({
  keyword: '',
  page: 1,
  pageSize: DEFAULT_PAGE_SIZE,
});

const pagination = reactive({
  current: 1,
  pageSize: DEFAULT_PAGE_SIZE,
  total: 0,
  showJumper: true,
});

const columns = [
  { colKey: 'imageUrl', title: '图标', width: 140, align: 'center' },
  { colKey: 'name', title: '印花名称', ellipsis: true, minWidth: 200 },
  { colKey: 'price', title: '当前价格', width: 120, align: 'right' },
  { colKey: 'sellNum', title: '在售数量', width: 100, align: 'right' },
  { colKey: 'updateTime', title: '最后更新', width: 180 },
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
    console.error('获取印花列表失败:', error);
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
  queryParams.keyword = '';
  handleSearch();
};


// 格式化时间
const formatTime = (time: string) => {
  if (!time) return '-';
  const date = new Date(time);
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  const h = String(date.getHours()).padStart(2, '0');
  const min = String(date.getMinutes()).padStart(2, '0');
  const s = String(date.getSeconds()).padStart(2, '0');
  return `${y}-${m}-${d} ${h}:${min}:${s}`;
};

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
.sticker-list-container {
  background-color: #f3f4f6;
  min-height: calc(100vh - 64px);
}

/* 表头样式定制 (参考 GoodsList.vue) */
:deep(.custom-table .t-table__header tr) {
  background-color: #fafafa !important;
}

:deep(.custom-table .t-table__header th) {
  font-weight: 700 !important;
  color: #1f2937 !important;
  background-color: transparent !important;
  border-bottom: 2px solid #e5e7eb !important;
  position: relative;
}

:deep(.custom-table .t-table__header th:not(:last-child)::after) {
  content: "";
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  height: 50%;
  width: 1px;
  background-color: #d1d5db;
}
</style>
