<template>
  <div class="space-y-4">
    <!-- 合并后的商品列表卡片 -->
    <t-card :bordered="false" title="商品列表" class="transition-shadow duration-300 hover:shadow">
      <!-- 搜索栏 -->
      <div class="mb-6 border-b border-gray-100 pb-6">
        <t-form
          ref="form"
          :data="searchForm"
          layout="inline"
          @submit="handleSearch"
          @reset="handleReset"
        >
          <t-form-item label="商品名称" name="goodsId">
            <t-select
              v-model="searchForm.goodsId"
              :options="goodsOptions"
              filterable
              placeholder="请输入商品名称搜索"
              clearable
              style="width: 300px"
              :on-search="onRemoteSearch"
              :loading="searchLoading"
              reserve-keyword
            />
          </t-form-item>
          <t-form-item label="商品分类" name="categoryId">
            <t-cascader
              v-model="searchForm.categoryId"
              :options="categoryOptions"
              placeholder="请选择分类"
              clearable
              check-strictly
              style="width: 200px"
            />
          </t-form-item>
          <t-form-item label="外观磨损" name="exterior">
            <t-select
              v-model="searchForm.exterior"
              placeholder="请选择外观"
              clearable
              style="width: 160px"
            >
              <t-option
                v-for="item in ExteriorOptions"
                :key="item.value"
                :value="item.value"
                :label="item.label"
              />
            </t-select>
          </t-form-item>
          <t-form-item>
            <div class="flex gap-4">
              <t-button
                theme="primary"
                type="submit"
                class="rounded-lg transition-all duration-300 hover:shadow active:shadow-none"
              >
                <template #icon><search-icon /></template>
                查询
              </t-button>
              <t-button
                theme="default"
                variant="base"
                type="reset"
                class="rounded-lg transition-all duration-300 hover:shadow active:shadow-none"
              >
                <template #icon><refresh-icon /></template>
                重置
              </t-button>
            </div>
          </t-form-item>
        </t-form>
      </div>

      <!-- 数据表格 -->
      <t-table
        row-key="id"
        :data="dataList"
        :columns="columns"
        :loading="loading"
        :pagination="pagination"
        hover
        :header-affixed-top="true"
        class="custom-table"
        :pagination-affixed-bottom="true"
        @page-change="onPageChange"
      >
        <!-- 图片列自定义渲染 -->
        <template #iconUrl="{ row }">
          <div
            class="flex cursor-pointer items-center justify-center border border-gray-200 bg-white"
            style="width: 110px; height: 110px"
            @click="onPreview(row.iconUrl)"
          >
            <t-image
              :src="row.iconUrl"
              :style="{ width: '100px', height: '100px' }"
              fit="contain"
            />
          </div>
        </template>

        <!-- 商品名称列自定义渲染 -->
        <template #name="{ row }">
          <div class="flex flex-col">
            <span class="font-medium text-gray-900">{{ row.name }}</span>
            <span class="text-xs text-gray-500">{{ row.marketHashName }}</span>
          </div>
        </template>

        <!-- 磨损列自定义渲染 -->
        <template #exterior="{ row }">
          <t-tag
            v-if="row.exterior"
            variant="light"
            :style="{
              color: ExteriorColorMap[row.exterior] || '#333',
              backgroundColor: (ExteriorColorMap[row.exterior] || '#eee') + '20',
              borderColor: (ExteriorColorMap[row.exterior] || '#ccc') + '40',
            }"
          >
            {{ ExteriorMap[row.exterior] || row.exterior }}
          </t-tag>
          <span v-else>-</span>
        </template>

        <!-- 稀有度列自定义渲染 -->
        <template #rarity="{ row }">
          <t-tag
            v-if="row.rarity"
            variant="light"
            :style="{
              color: RarityColorMap[row.rarity] || '#333',
              backgroundColor: (RarityColorMap[row.rarity] || '#eee') + '20',
            }"
          >
            {{ RarityMap[row.rarity] || row.rarity }}
          </t-tag>
          <span v-else>-</span>
        </template>

        <!-- 操作列 -->
        <template #operation="{ row }">
          <t-button
            variant="text"
            theme="primary"
            size="small"
            class="transition-all hover:font-bold"
            @click="openBuffGoods(row.goodsId)"
          >
            <template #icon><link-icon /></template>
            详情
          </t-button>
          <t-button
            variant="text"
            theme="warning"
            size="small"
            class="ml-2 transition-all hover:font-bold"
            @click="openCreateTaskDialog(row)"
          >
            <template #icon><shop-icon /></template>
            扫货
          </t-button>
        </template>
      </t-table>
    </t-card>
    <!-- 图片预览组件 -->
    <t-image-viewer
      :images="[previewImage]"
      :visible="visible"
      mode="modal"
      :close-on-overlay="true"
      @close="visible = false"
    />

    <!-- 任务配置弹窗组件 (隐藏主体只用弹窗) -->
    <task-config ref="taskConfigRef" dialog-only />
  </div>
</template>

<script setup lang="ts">
import { categoryApi, type CategoryNode } from "@/api/category";
import { goodsApi } from "@/api/goods";
import TaskConfig from "@/views/TaskConfig.vue";
import type { Goods, GoodsPageQuery, GoodsSimple } from "@/types/goods";
import { LinkIcon, RefreshIcon, SearchIcon, ShopIcon } from "tdesign-icons-vue-next";
import type { PageInfo, PrimaryTableCol } from "tdesign-vue-next";
import { MessagePlugin } from "tdesign-vue-next";
import { onMounted, reactive, ref } from "vue";
import { debounce } from "lodash";

import { ExteriorColorMap, ExteriorMap, ExteriorOptions } from "@/enums/ExteriorEnum";
import { RarityColorMap, RarityMap } from "@/enums/RarityEnum";

// 图片预览状态
const visible = ref(false);
const previewImage = ref("");
const taskConfigRef = ref();

const onPreview = (url: string) => {
  previewImage.value = url;
  visible.value = true;
};

const openBuffGoods = (goodsId: number) => {
  if (!goodsId) return;
  window.open(`https://buff.163.com/goods/${goodsId}`, "_blank");
};

// 搜索表单
const searchForm = reactive<{
  goodsId?: number;
  exterior: string;
  categoryId?: number;
}>({
  goodsId: undefined,
  exterior: "",
  categoryId: undefined,
});

// 全量商品选项
const goodsOptions = ref<{ label: string; value: number }[]>([]);
const searchLoading = ref(false);

// 分类选项
const categoryOptions = ref<CategoryNode[]>([]);

// 表格数据
const loading = ref(false);

const openCreateTaskDialog = (row: GoodsSimple) => {
  taskConfigRef.value?.openWithGoods(row);
};

// ------------------------
const dataList = ref<Goods[]>([]);
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showJumper: true,
});

// 表格列定义
const columns: PrimaryTableCol<Goods>[] = [
  { colKey: "iconUrl", title: "图片", width: 140, align: "center" },
  { colKey: "name", title: "商品名称", minWidth: 250 },
  { colKey: "goodsId", title: "Goods ID", width: 100 },
  { colKey: "exterior", title: "外观", width: 120 },
  { colKey: "rarity", title: "稀有度", width: 120 },
  { colKey: "operation", title: "操作", width: 100, fixed: "right" },
];

// 加载数据
const fetchData = async () => {
  loading.value = true;
  try {
    const params: GoodsPageQuery = {
      page: pagination.current,
      pageSize: pagination.pageSize,
      goodsId: searchForm.goodsId,
      exterior: searchForm.exterior,
      categoryId: searchForm.categoryId,
    };
    const res = await goodsApi.getPage(params);
    // 这里需要断言一下类型，或者直接使用，因为 axios 拦截器已经处理了响应
    const pageResult = res as unknown as PageResult<Goods>;
    dataList.value = pageResult.records;
    pagination.total = Number(pageResult.total);
  } catch (error) {
    console.error(error);
    MessagePlugin.error("获取商品列表失败");
  } finally {
    loading.value = false;
  }
};

// 远程搜索商品 (防抖处理)
const onRemoteSearch = debounce(async (keyword: string) => {
  // 即使 keyword 为空也允许调用，以便加载默认列表
  searchLoading.value = true;
  try {
    // 全量商品列表接口返回的是数组，不是分页对象
    const list = await goodsApi.getSimpleList(keyword);
    // 这里需要断言一下类型，或者直接使用，因为 axios 拦截器已经处理了响应
    const items = list as unknown as GoodsSimple[];

    goodsOptions.value = items.map((item) => ({
      label: item.name,
      value: item.goodsId,
    }));
  } catch (error) {
    console.error(error);
  } finally {
    searchLoading.value = false;
  }
}, 500); // 延迟 500ms 触发

// 加载全量商品列表 (仅一次)
const fetchSimpleList = async () => {
  // 初始化加载前 50 个热门商品或默认商品
  // 传递空字符串会触发后端默认返回前50条
  onRemoteSearch("");
};

// 搜索
const handleSearch = () => {
  pagination.current = 1;
  fetchData();
};

// 重置
const handleReset = () => {
  searchForm.goodsId = undefined;
  searchForm.exterior = "";
  searchForm.categoryId = undefined;
  pagination.current = 1;
  fetchData();
};

// 分页变化
const onPageChange = (pageInfo: PageInfo) => {
  pagination.current = pageInfo.current;
  pagination.pageSize = pageInfo.pageSize;
  fetchData();
};

// 加载分类树
const fetchCategoryTree = async () => {
  try {
    const res = await categoryApi.getTree();
    categoryOptions.value = res as unknown as CategoryNode[];
  } catch (error) {
    console.error(error);
  }
};

onMounted(() => {
  fetchSimpleList();
  fetchCategoryTree();
  fetchData();
});
</script>

<style scoped>
/* 表头样式定制 */
:deep(.custom-table .t-table__header tr) {
  background-color: #fafafa !important;
}

:deep(.custom-table .t-table__header th) {
  font-weight: 700 !important;
  color: #1f2937 !important;
  background-color: transparent !important;
  border-bottom: 2px solid #e5e7eb !important;
  position: relative; /* 为伪元素定位 */
}

/* 悬浮时略微加深 */
:deep(.custom-table .t-table__header th:hover) {
  background-color: transparent !important;
}

/* 列分割短竖线 (使用伪元素实现) */
:deep(.custom-table .t-table__header th:not(:last-child)::after) {
  content: "";
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  height: 50%; /* 高度为表头的一半 */
  width: 1px;
  background-color: #d1d5db; /* 比背景深一点的灰色 */
}
</style>
