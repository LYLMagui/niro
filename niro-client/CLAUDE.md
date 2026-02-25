# niro-client CLAUDE.md

> Niro 项目前端模块专用规范
> 版本：1.0.0 | 更新日期：2026-02-24

---

## 1. 项目概述

### 1.1 核心定位

Niro 前端是 Buff/CS2 饰品交易自动化平台的 Web 管理界面，基于 Vue 3 + TypeScript 构建。

### 1.2 技术栈

| 类别     | 技术             | 版本   | 用途     |
| -------- | ---------------- | ------ | -------- |
| 框架     | Vue              | 3.5.x  | 核心框架 |
| 语言     | TypeScript       | 5.8.x  | 类型安全 |
| 状态管理 | Pinia            | 3.x    | 状态管理 |
| UI 框架  | TDesign Vue Next | 1.9.x  | 组件库   |
| 样式     | Tailwind CSS     | 4.x    | 样式方案 |
| 构建工具 | Vite             | 6.x    | 开发构建 |
| HTTP     | Axios            | 1.10.x | 网络请求 |
| 路由     | Vue Router       | 4.5.x  | 路由管理 |

### 1.3 关键依赖

```json
{
  "@vueuse/core": "^13.2.0",
  "crypto-js": "^4.2.0",
  "dayjs": "^1.11.19",
  "lodash-es": "^4.17.21",
  "cron-parser": "^5.4.0",
  "tdesign-vue-next": "^1.9.0"
}
```

---

## 2. 项目结构

```
niro-client/src/
├── api/                    # API 请求封装
│   ├── task.ts            # 任务相关 API
│   ├── goods.ts           # 商品相关 API
│   ├── order.ts           # 订单相关 API
│   ├── user.ts            # 用户相关 API
│   ├── settings.ts        # 设置相关 API
│   ├── category.ts        # 分类相关 API
│   ├── sticker.ts         # 贴纸相关 API
│   └── log.ts             # 日志相关 API
│
├── components/             # 公共组件
│   ├── Layout.vue         # 布局组件
│   ├── SidebarItem.vue    # 侧边栏项
│   ├── ParentView.vue     # 父子视图
│   ├── CronEditor.vue     # Cron 表达式编辑器
│   ├── TaskProgressCard.vue  # 任务进度卡片
│   └── task/              # 任务相关组件
│       ├── AccountSelector.vue
│       └── ScheduleConfig.vue
│
├── views/                 # 页面视图
│   ├── Dashboard.vue      # 仪表盘
│   ├── GoodsList.vue      # 商品列表
│   ├── InventoryBoard.vue # 库存看板
│   ├── StickerList.vue    # 贴纸列表
│   ├── TaskConfig.vue     # 任务配置
│   ├── TaskList.vue       # 任务列表
│   ├── OrderRecord.vue    # 订单记录
│   ├── Settings.vue       # 系统设置
│   ├── Logs.vue           # 日志查看
│   ├── login.vue          # 登录页
│   ├── 403.vue           # 无权限
│   └── 404.vue           # 页面不存在
│
├── stores/                # Pinia 状态管理
│   ├── user.ts           # 用户状态
│   ├── task.ts           # 任务状态
│   └── permission.ts     # 权限状态
│
├── router/               # 路由配置
│   ├── index.ts         # 路由入口
│   ├── componentMap.ts  # 组件映射
│   └── permission.ts    # 路由权限
│
├── types/                # TypeScript 类型定义
│   ├── http.d.ts        # HTTP 响应类型
│   ├── user.ts          # 用户类型
│   ├── task.ts          # 任务类型
│   ├── goods.ts         # 商品类型
│   ├── order.ts         # 订单类型
│   └── router.ts        # 路由类型
│
├── enums/                # 枚举定义
│   ├── PlatformEnum.ts      # 平台枚举
│   ├── TaskStatusEnum.ts    # 任务状态枚举
│   ├── TaskTypeEnum.ts      # 任务类型枚举
│   ├── TaskRunModeEnum.ts   # 任务运行模式枚举
│   ├── BuffAccountStatusEnum.ts  # 账号状态枚举
│   ├── ExteriorEnum.ts     # 外观等级枚举
│   ├── RarityEnum.ts        # 稀有度枚举
│   └── WeaponTypeEnum.ts    # 武器类型枚举
│
├── composables/          # 组合式函数
│   ├── useRequest.ts     # 请求封装
│   ├── useAccountSelect.ts  # 账号选择
│   ├── useGoodsSearch.ts   # 商品搜索
│   ├── useTaskForm.ts      # 任务表单
│   └── useUiState.ts       # UI 状态
│
├── utils/                # 工具函数
│   ├── request.ts        # Axios 封装
│   ├── constants.ts      # 常量定义
│   ├── crypto.ts         # 加密工具
│   ├── icon-map.ts       # 图标映射
│   └── menu.ts           # 菜单工具
│
├── constant/             # 常量
│   └── GlobalConstant.ts # 全局常量
│
├── directive/            # 指令
│   └── permission.ts     # 权限指令
│
├── hooks/               # 钩子
│   └── usePermission.ts # 权限钩子
│
├── main.ts              # 入口文件
├── App.vue              # 根组件
└── style.css            # 全局样式
```

---

## 3. 开发规范

### 3.1 强制规范

#### 3.1.1 脚本语法

- **必须**使用 `<script setup lang="ts">` 语法
- **禁止**使用 Options API

```vue
<!-- ✅ 正确 -->
<script setup lang="ts">
import { ref, computed } from "vue";
const count = ref(0);
const doubled = computed(() => count.value * 2);
</script>

<!-- ❌ 错误 -->
<script>
export default {
  data() {
    return { count: 0 };
  },
};
</script>
```

#### 3.1.2 类型安全

- **禁止**使用 `any` 类型
- **禁止**使用 `@ts-ignore` / `@ts-expect-error`
- **必须**为 props 和 emits 定义类型

```typescript
// ✅ 正确
interface Props {
  title: string;
  count?: number;
}
const props = withDefaults(defineProps<Props>(), {
  count: 0,
});

// ❌ 错误
const props = defineProps({
  title: String,
  count: any, // 禁止
});
```

#### 3.1.3 API 请求

- **必须**将所有 API 封装在 `src/api/` 目录
- **禁止**在视图组件中直接写裸 axios

```typescript
// ✅ 正确：src/api/task.ts
import request from "@/utils/request";
export function getTaskList(params: TaskQueryParam) {
  return request.get<PageResult<Task>>("/task/page", { params });
}

// ✅ 正确：视图中使用
import { getTaskList } from "@/api/task";
const { data } = await getTaskList({ pageNum: 1, pageSize: 10 });

// ❌ 错误：视图中直接使用 axios
import axios from "axios";
axios.get("/api/task/page");
```

### 3.2 样式规范

#### 3.2.1 样式方案

- **优先**使用 TDesign 组件 + Tailwind CSS
- **禁止**使用内联样式（除动态样式外）
- **禁止**使用未封装的选择器

```vue
<!-- ✅ 正确 -->
<template>
  <t-button type="primary">确定</t-button>
  <div class="flex items-center gap-4">
    <span class="text-gray-500">状态</span>
  </div>
</template>

<!-- ❌ 错误 -->
<template>
  <button style="background: blue; color: white;">确定</button>
  <div style="display: flex; align-items: center;">
    <span style="color: gray;">状态</span>
  </div>
</template>
```

#### 3.2.2 响应式断点

- 使用 Tailwind CSS 响应式前缀
- 移动优先设计

```vue
<!-- 响应式示例 -->
<div class="text-sm md:text-base lg:text-lg">
  响应式文本
</div>
```

### 3.3 组件规范

#### 3.3.1 组件结构

```vue
<script setup lang="ts">
// 1. 类型导入
import type { PropType } from "vue";

// 2. Props 定义
interface Props {
  modelValue: string;
  options?: Option[];
}
const props = withDefaults(defineProps<Props>(), {
  options: () => [],
});

// 3. Emits 定义
const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
  (e: "change", value: string): void;
}>();

// 4. Ref 和 Computed
const localValue = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
});
</script>

<template>
  <!-- 组件模板 -->
</template>

<style scoped>
/* 组件样式 */
</style>
```

#### 3.3.2 组件命名

- 文件名使用 PascalCase：`TaskProgressCard.vue`
- 目录名使用 kebab-case：`task-config/`
- 组件名使用 PascalCase：`<TaskProgressCard>`

### 3.4 状态管理

#### 3.4.1 Pinia Store

```typescript
// src/stores/task.ts
import { defineStore } from "pinia";
import { ref } from "vue";
import type { Task } from "@/types/task";

export const useTaskStore = defineStore("task", () => {
  // State
  const tasks = ref<Task[]>([]);
  const loading = ref(false);

  // Actions
  async function fetchTasks() {
    loading.value = true;
    try {
      // 业务逻辑
    } finally {
      loading.value = false;
    }
  }

  return {
    tasks,
    loading,
    fetchTasks,
  };
});
```

---

## 4. 常用命令

### 4.1 开发命令

```bash
# 安装依赖
pnpm install

# 开发启动
pnpm dev

# 生产构建
pnpm build

# 类型检查
pnpm type-check

# 代码检查（全部）
pnpm lint

# 代码检查（单项）
pnpm lint:eslint
pnpm lint:prettier
pnpm lint:stylelint
```

### 4.2 Git 命令

```bash
# 提交（带交互式）
pnpm commit

# 准备 hooks
pnpm prepare
```

---

## 5. 业务模块

### 5.1 任务管理

#### 5.1.1 核心页面

- `TaskConfig.vue` - 任务配置（创建/编辑）
- `TaskList.vue` - 任务列表（查看/启停）

#### 5.1.2 关键组件

- `AccountSelector.vue` - 账号选择器
- `ScheduleConfig.vue` - 调度配置（Cron 表达式）
- `TaskProgressCard.vue` - 任务进度卡片

#### 5.1.3 状态展示

任务相关页面**必须**准确展示以下状态：

- 运行中 / 已停止 / 执行中 / 失败

### 5.2 商品管理

#### 5.2.1 核心页面

- `GoodsList.vue` - 商品列表
- `InventoryBoard.vue` - 库存看板
- `StickerList.vue` - 贴纸列表

#### 5.2.2 筛选条件

- 武器类型（WeaponTypeEnum）
- 稀有度（RarityEnum）
- 外观等级（ExteriorEnum）

### 5.3 订单管理

#### 5.3.1 核心页面

- `OrderRecord.vue` - 订单记录

#### 5.3.2 状态同步

- 订单状态必须与后端保持同步
- 异常订单需展示错误信息

### 5.4 用户认证

#### 5.4.1 登录流程

- 登录页：`login.vue`
- Token 存储在本地存储
- 请求拦截器自动携带 Token

---

## 6. 工具函数

### 6.1 请求封装 (request.ts)

```typescript
import axios, { AxiosInstance, AxiosResponse } from "axios";
import { MessagePlugin } from "tdesign-vue-next";
import { useUserStore } from "@/stores/user";

// 创建实例
const instance: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE,
  timeout: 30000,
});

// 请求拦截器
instance.interceptors.request.use((config) => {
  const userStore = useUserStore();
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`;
  }
  return config;
});

// 响应拦截器
instance.interceptors.response.use(
  (response: AxiosResponse) => response.data,
  (error) => {
    // 错误处理
    return Promise.reject(error);
  }
);

export default instance;
```

### 6.2 常用工具

| 工具            | 用途       | 位置               |
| --------------- | ---------- | ------------------ |
| request         | Axios 封装 | `@/utils/request`  |
| encryptPassword | 密码加密   | `@/utils/crypto`   |
| getIcon         | 获取图标   | `@/utils/icon-map` |

---

## 7. 类型定义

### 7.1 常用类型

```typescript
// 分页结果
interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
}

// 任务类型
interface Task {
  id: number;
  name: string;
  type: TaskTypeEnum;
  status: TaskStatusEnum;
  runMode: TaskRunModeEnum;
  accountIds: number[];
  config: TaskConfig;
  createTime: string;
  updateTime: string;
}

// 商品类型
interface Goods {
  goodsId: string;
  name: string;
  categoryId: number;
  rarity: RarityEnum;
  exterior: ExteriorEnum;
  price: number;
  imageUrl: string;
}
```

---

## 8. 质量门禁

### 8.1 提交前检查

```bash
# 必须全部通过
pnpm type-check  # TypeScript 类型检查
pnpm lint        # ESLint + Prettier + Stylelint
pnpm build       # 构建成功
```

### 8.2 常见问题

| 问题     | 原因                | 解决方案          |
| -------- | ------------------- | ----------------- |
| 类型错误 | 缺少类型定义        | 添加接口定义      |
| 构建失败 | 依赖缺失或版本冲突  | 检查 package.json |
| 样式异常 | Tailwind 未正确配置 | 检查 postcss 配置 |

---

## 9. 参考资料

- [Vue 3 文档](https://vuejs.org/)
- [TypeScript 文档](https://www.typescriptlang.org/)
- [TDesign Vue Next](https://tdesign.tencent.com/vue-next)
- [Tailwind CSS](https://tailwindcss.com/)
- [Pinia](https://pinia.vuejs.org/)

---

**维护者**：Niro 前端开发团队
