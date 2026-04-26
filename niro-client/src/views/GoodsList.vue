<template>
  <PageFrame
    :is-mobile="false"
    desktop-outer-class="!p-0"
    desktop-content-class="px-4 pt-0 pb-0"
    mobile-content-class="px-3 pt-3 pb-3"
  >
    <section class="overflow-hidden bg-white">
      <div class="flex flex-col gap-3 px-0 py-4">
        <div class="flex items-center">
          <t-icon name="shop" class="mr-2 text-blue-600" />
          <span class="text-lg font-bold text-gray-800">商品列表</span>
        </div>
        <div class="grid grid-cols-1 gap-4 xl:grid-cols-4 xl:items-end">
          <label class="flex flex-col gap-1.5">
            <span class="text-sm font-medium text-slate-700">商品名称</span>
            <t-select
              v-model="searchForm.goodsId"
              :options="goodsOptions"
              filterable
              placeholder="请输入商品名称搜索"
              clearable
              class="w-full"
              :on-search="onRemoteSearch"
              :loading="searchLoading"
              reserve-keyword
              @change="handleSearch"
            />
          </label>
          <label class="flex flex-col gap-1.5">
            <span class="text-sm font-medium text-slate-700">商品分类</span>
            <t-cascader
              v-model="searchForm.categoryId"
              :options="categoryOptions"
              placeholder="请选择分类"
              clearable
              check-strictly
              class="w-full"
              @change="handleSearch"
            />
          </label>
          <label class="flex flex-col gap-1.5">
            <span class="text-sm font-medium text-slate-700">外观磨损</span>
            <t-select
              v-model="searchForm.exterior"
              placeholder="请选择外观"
              clearable
              class="w-full"
              @change="handleSearch"
            >
              <t-option
                v-for="item in ExteriorOptions"
                :key="item.value"
                :value="item.value"
                :label="item.label"
              />
            </t-select>
          </label>
          <div class="flex items-center gap-2">
            <t-button v-permission="PermissionConstant.GOODS_LIST" theme="primary" @click="handleSearch">
              <template #icon><search-icon /></template>
              查询
            </t-button>
            <t-button v-permission="PermissionConstant.GOODS_LIST" theme="default" variant="base" @click="handleReset">
              <template #icon><refresh-icon /></template>
              重置
            </t-button>
            <t-button
              v-if="canSyncGoods"
              theme="warning"
              variant="base"
              @click="syncDialogVisible = true"
            >
              <template #icon><cloud-download-icon /></template>
              同步
            </t-button>
          </div>
        </div>
      </div>
      <div class="relative min-h-0 flex-1">
        <t-table
          row-key="id"
          :data="dataList"
          :columns="columns"
          :loading="loading"
          :pagination="pagination"
          hover
          :header-affixed-top="{ offsetTop: 0, container: '.t-layout__content' }"
          class="niro-unified-table w-full bg-white"
          @page-change="onPageChange"
        >
          <template #empty>
            <t-empty icon="queue" description="未搜索到相关商品" />
          </template>

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
            <div class="flex flex-col min-w-0">
              <t-tooltip :content="row.name" placement="top-left">
                <span class="truncate font-medium text-gray-900">{{ row.name }}</span>
              </t-tooltip>
              <t-tooltip :content="row.marketHashName" placement="top-left">
                <span class="truncate text-xs text-gray-500">{{ row.marketHashName }}</span>
              </t-tooltip>
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
            <div
              v-permission="PermissionConstant.GOODS_LIST"
              class="niro-table-actions niro-table-actions--center"
            >
              <t-button
                variant="outline"
                size="small"
                theme="primary"
                class="niro-table-action-btn"
                @click="openBuffGoods(row.goodsId)"
              >
                <template #icon><link-icon /></template>
                详情
              </t-button>
              <t-button
                v-if="canCreateScanTask"
                variant="outline"
                size="small"
                theme="warning"
                class="niro-table-action-btn"
                disabled
              >
                <template #icon><shop-icon /></template>
                扫货
              </t-button>
            </div>
          </template>
        </t-table>
      </div>
    </section>
  </PageFrame>
  <!-- 分类同步弹窗 -->
  <t-dialog
    v-model:visible="syncDialogVisible"
    header="分类商品同步"
    :confirm-btn="syncLoading ? { content: '同步中...', loading: true } : '开始同步'"
    :on-confirm="handleSync"
    width="450px"
  >
    <div class="py-4">
      <div class="mb-4 text-sm text-gray-500">
        请选择一个二级分类进行商品数据同步。系统将创建一个一次性同步任务，并在后台由爬虫执行。
      </div>
      <t-form :data="syncForm" label-align="top">
        <t-form-item label="选择二级分类" name="categoryId">
          <t-cascader
            v-model="syncForm.categoryId"
            :options="categoryOptions"
            placeholder="请选择具体分类 (如: 自动步枪 -> AK-47)"
            filterable
            clearable
            class="w-full"
          />
        </t-form-item>
      </t-form>
    </div>
  </t-dialog>

  <!-- 图片预览组件 (移回主容器内，防止干扰布局) -->
  <t-image-viewer
    :images="[previewImage]"
    :visible="visible"
    mode="modal"
    :close-on-overlay="true"
    @close="visible = false"
  />
</template>

<script setup lang="ts">
import { categoryApi, type CategoryNode } from "@/api/category";
import { goodsApi } from "@/api/goods";
import { usePermission } from "@/hooks/usePermission";
import useNewPermission from "@/hooks/useNewPermission";
import type { Goods, GoodsPageQuery, GoodsSimple, PageResult } from "@/types/goods";
import {
  CloudDownloadIcon,
  LinkIcon,
  RefreshIcon,
  SearchIcon,
  ShopIcon,
} from "tdesign-icons-vue-next";
import type { PageInfo, PrimaryTableCol } from "tdesign-vue-next";
import { MessagePlugin } from "tdesign-vue-next";
import { computed, reactive, ref, watch } from "vue";
import { debounce } from "lodash-es";

import { ExteriorColorMap, ExteriorMap, ExteriorOptions } from "@/enums/ExteriorEnum";
import { RarityColorMap, RarityMap } from "@/enums/RarityEnum";
import { PermissionConstant } from "@/constant/PermissionConstant";

const { hasPermission } = usePermission();
const { hasButtonPermission } = useNewPermission();
const canViewGoods = computed(() => hasPermission(PermissionConstant.GOODS_LIST));
const canSyncGoods = computed(() => hasButtonPermission(PermissionConstant.GOODS_SYNC));
const canCreateScanTask = computed(() => hasButtonPermission(PermissionConstant.TASK_SCAN_CREATE));

// 图片预览状态
const visible = ref(false);
const previewImage = ref("");

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

// 分类同步状态
const syncDialogVisible = ref(false);
const syncLoading = ref(false);
const syncForm = reactive({
  categoryId: undefined,
});

const handleSync = async () => {
  if (!canViewGoods.value) {
    MessagePlugin.warning("当前账号没有商品管理权限");
    syncDialogVisible.value = false;
    return;
  }
  if (!syncForm.categoryId) {
    MessagePlugin.warning("请选择分类");
    return;
  }

  syncLoading.value = true;
  try {
    await goodsApi.syncCategory(syncForm.categoryId);
    MessagePlugin.success("同步任务已创建，请在任务管理中查看进度");
    syncDialogVisible.value = false;
    syncForm.categoryId = undefined;
  } catch (error) {
    console.error(error);
    MessagePlugin.error("同步任务创建失败");
  } finally {
    syncLoading.value = false;
  }
};

// 分类选项
const categoryOptions = ref<CategoryNode[]>([]);

// 表格数据
const loading = ref(false);

// ------------------------
const dataList = ref<Goods[]>([]);
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showJumper: true,
  size: "small" as const,
});

// 表格列定义
const columns: PrimaryTableCol[] = [
  { colKey: "iconUrl", title: "图片", width: 140, align: "center" },
  { colKey: "name", title: "商品名称", minWidth: 250, align: "left" },
  { colKey: "goodsId", title: "Goods ID", width: 100, align: "left" },
  { colKey: "exterior", title: "外观", width: 120, align: "left" },
  { colKey: "rarity", title: "稀有度", width: 120, align: "left" },
  { colKey: "operation", title: "操作", width: 160, fixed: "right", align: "center" },
];

// 加载数据
const fetchData = async () => {
  if (!canViewGoods.value) {
    dataList.value = [];
    pagination.total = 0;
    return;
  }
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
  if (!canViewGoods.value) {
    goodsOptions.value = [];
    return;
  }
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
  if (!canViewGoods.value) {
    categoryOptions.value = [];
    return;
  }
  try {
    const res = await categoryApi.getTree();
    categoryOptions.value = res as unknown as CategoryNode[];
  } catch (error) {
    console.error(error);
  }
};

watch(
  canViewGoods,
  (allowed) => {
    if (allowed) {
      fetchSimpleList();
      fetchCategoryTree();
      fetchData();
      return;
    }
    dataList.value = [];
    goodsOptions.value = [];
    categoryOptions.value = [];
    pagination.total = 0;
    syncDialogVisible.value = false;
  },
  { immediate: true }
);
</script>

<style scoped>
:deep(.t-card__body) {
  padding: 0;
}

/* 图片预览样式保持 */
.goods-img-container {
  transition: transform 0.2s;
}
.goods-img-container:hover {
  transform: scale(1.05);
}
</style>
