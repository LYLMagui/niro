<template>
  <div class="space-y-4">
    <!-- 顶部搜索栏 -->
    <t-card :bordered="false" class="transition-shadow duration-300 hover:shadow">
      <t-form
        ref="form"
        :data="searchForm"
        layout="inline"
        @submit="handleSearch"
        @reset="handleReset"
      >
        <t-form-item label="商品名称" name="name">
          <t-input
            v-model="searchForm.name"
            placeholder="请输入商品名称或简称"
            style="width: 240px"
          />
        </t-form-item>
        <t-form-item label="外观磨损" name="exterior">
          <t-select
            v-model="searchForm.exterior"
            placeholder="请选择外观"
            clearable
            style="width: 160px"
          >
            <t-option v-for="ext in exteriorOptions" :key="ext" :value="ext" :label="ext" />
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
    </t-card>

    <!-- 数据表格 -->
    <t-card :bordered="false" title="商品列表" class="transition-shadow duration-300 hover:shadow">
      <t-table
        row-key="id"
        :data="dataList"
        :columns="columns"
        :loading="loading"
        :pagination="pagination"
        @page-change="onPageChange"
        hover
      >
        <!-- 图片列自定义渲染 -->
        <template #iconUrl="{ row }">
          <div class="cursor-pointer" @click="onPreview(row.iconUrl)">
            <t-image
              :src="row.iconUrl"
              :style="{ width: '80px', height: '60px' }"
              fit="contain"
              shape="round"
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
          <t-tag v-if="row.exterior" variant="light-outline" theme="primary">
            {{ row.exterior }}
          </t-tag>
          <span v-else>-</span>
        </template>

        <!-- 操作列 -->
        <template #operation="{ row }">
          <t-button
            variant="text"
            theme="primary"
            :href="`https://buff.163.com/market/csgo#game=csgo&page_num=1&search=${encodeURIComponent(row.name)}&tab=selling`"
            target="_blank"
            size="small"
            class="transition-all hover:font-bold"
          >
            <template #icon><link-icon /></template>
            详情
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
  </div>
</template>

<script setup lang="ts">
import type { Goods, GoodsPageQuery } from "@/types/goods";
import { LinkIcon, RefreshIcon, SearchIcon } from "tdesign-icons-vue-next";
import type { PageInfo, PrimaryTableCol } from "tdesign-vue-next";
import { ImageViewer, MessagePlugin } from "tdesign-vue-next";
import { onMounted, reactive, ref } from "vue";

// 图片预览状态
const visible = ref(false);
const previewImage = ref("");

const onPreview = (url: string) => {
  previewImage.value = url;
  visible.value = true;
};

// 搜索表单
const searchForm = reactive<Pick<GoodsPageQuery, "name" | "exterior">>({
  name: "",
  exterior: "",
});

// 表格数据
const loading = ref(false);
const dataList = ref<Goods[]>([]);
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showJumper: true,
});

// 磨损选项 (硬编码常用选项，后续可改为从后端获取)
const exteriorOptions = ["崭新出厂", "略有磨损", "久经沙场", "破损不堪", "战痕累累", "无涂装"];

// 表格列定义
const columns: PrimaryTableCol<Goods>[] = [
  { colKey: "iconUrl", title: "图片", width: 100, align: "center" },
  { colKey: "name", title: "商品名称", minWidth: 200 },
  { colKey: "goodsId", title: "Goods ID", width: 100 },
  { colKey: "exterior", title: "外观", width: 120 },
  { colKey: "rarity", title: "稀有度", width: 120 },
  { colKey: "operation", title: "操作", width: 100, fixed: "right" },
];

// 加载数据
const fetchData = async () => {
  loading.value = true;
  try {
    // 模拟数据
    await new Promise((resolve) => setTimeout(resolve, 500));
    dataList.value = [
      {
        goodsId: 968284,
        name: "挂件 | 小水枪",
        marketHashName: "Charm | Lil' Squirt",
        iconUrl:
          "https://market.fp.ps.netease.com/file/66fe0dc7e59cb55014f4aff0w5YKVLyf05?fop=imageView/6/f/webp/q/75",
        exterior: "",
        rarity: "legendary",
        category: "挂件",
        id: 16466,
      },
      {
        goodsId: 968153,
        name: "挂件 | 背板",
        marketHashName: "Charm | Backsplash",
        iconUrl:
          "https://market.fp.ps.netease.com/file/66fdfeea0cdd2e81b940816eoZAYaJDe05?fop=imageView/6/f/webp/q/75",
        exterior: "",
        rarity: "rare",
        category: "挂件",
        id: 16465,
      },
      {
        goodsId: 968238,
        name: "挂件 | K金CT",
        marketHashName: "Charm | Baby Karat CT",
        iconUrl:
          "https://market.fp.ps.netease.com/file/66fe0962dd1388d78c4d03c0iUr7PWNi05?fop=imageView/6/f/webp/q/75",
        exterior: "",
        rarity: "ancient",
        category: "挂件",
        id: 16464,
      },
      {
        goodsId: 968115,
        name: "挂件 | 魅力一击",
        marketHashName: "Charm | Glamour Shot",
        iconUrl:
          "https://market.fp.ps.netease.com/file/66fdfb9d6ad4f2ef5c47654eBpkwudZ905?fop=imageView/6/f/webp/q/75",
        exterior: "",
        rarity: "mythical",
        category: "挂件",
        id: 16463,
      },
      {
        goodsId: 968131,
        name: "挂件 | 迪斯科MAC",
        marketHashName: "Charm | Disco MAC",
        iconUrl:
          "https://market.fp.ps.netease.com/file/66fdfcb5d907403efe01cba8q410KYOo05?fop=imageView/6/f/webp/q/75",
        exterior: "",
        rarity: "mythical",
        category: "挂件",
        id: 16462,
      },
      {
        goodsId: 968082,
        name: "挂件 | 钛金AWP",
        marketHashName: "Charm | Titeenium AWP",
        iconUrl:
          "https://market.fp.ps.netease.com/file/66fdf888c03cfe1dcaae0840MfqWWPEP05?fop=imageView/6/f/webp/q/75",
        exterior: "",
        rarity: "legendary",
        category: "挂件",
        id: 16461,
      },
      {
        goodsId: 967983,
        name: "挂件 | 针织",
        marketHashName: "Charm | Stitch-Loaded",
        iconUrl:
          "https://market.fp.ps.netease.com/file/66fded639c3e79742581ed37GHkCQGlj05?fop=imageView/6/f/webp/q/75",
        exterior: "",
        rarity: "rare",
        category: "挂件",
        id: 16460,
      },
      {
        goodsId: 968270,
        name: "挂件 | 半宝石",
        marketHashName: "Charm | Semi-Precious",
        iconUrl:
          "https://market.fp.ps.netease.com/file/66fe0cac399d36e7a31bcaa8mdaN5aTZ05?fop=imageView/6/f/webp/q/75",
        exterior: "",
        rarity: "legendary",
        category: "挂件",
        id: 16459,
      },
      {
        goodsId: 968091,
        name: "挂件 | 木刻",
        marketHashName: "Charm | Whittle Knife",
        iconUrl:
          "https://market.fp.ps.netease.com/file/66fdf926cd54e655cd7099e8KTQsrghc05?fop=imageView/6/f/webp/q/75",
        exterior: "",
        rarity: "rare",
        category: "挂件",
        id: 16458,
      },
    ] as any;
    pagination.total = 100;

    /*
    const params: GoodsPageQuery = {
      page: pagination.current,
      pageSize: pagination.pageSize,
      name: searchForm.name,
      exterior: searchForm.exterior,
    };
    const res = await goodsApi.getPage(params);
    dataList.value = res.records;
    pagination.total = res.total;
    */
  } catch (error) {
    console.error(error);
    MessagePlugin.error("获取商品列表失败");
  } finally {
    loading.value = false;
  }
};

// 搜索
const handleSearch = () => {
  pagination.current = 1;
  fetchData();
};

// 重置
const handleReset = () => {
  searchForm.name = "";
  searchForm.exterior = "";
  pagination.current = 1;
  fetchData();
};

// 分页变化
const onPageChange = (pageInfo: PageInfo) => {
  pagination.current = pageInfo.current;
  pagination.pageSize = pageInfo.pageSize;
  fetchData();
};

onMounted(() => {
  fetchData();
});
</script>

<style scoped></style>
