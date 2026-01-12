# 前端开发规范 (Vue 3 + TS)

## 1. 技术栈与语法
- **框架**：Vue 3 `Composition API` + `<script setup>`。
- **类型**：严格 TypeScript，禁止 `any`，优先显式/推导类型。
- **UI/样式**：TDesign + TailwindCSS。禁止传统 CSS，必要时用 BEM 命名。
- **布局**：Flex / Grid。移动端 `rem`，桌面端 `px`。

## 2. 目录与命名
- **目录**：页面 `views/`，组件 `components/`，逻辑 `composables/`，接口 `api/`。
- **命名**：文件 `kebab-case`，组件 `PascalCase`，变量/函数 `camelCase`，Hook `useXxx`。
- **请求**：API 必须封装在 `api/`，组件内禁止直接写 `axios`。统一响应处理器。

## 4. 公共能力 (可复用)
- **请求工具**：`src/api`。基于 Axios 封装，支持拦截器与统一错误处理。
- **状态管理**：`Pinia`。用于用户配置、Cookie、Token 等全局状态持久化。
- **图标系统**：`tdesign-icons-vue-next` + `Tailwind Iconify`。优先使用内置图标库。
- **工具函数**：优先使用 `@vueuse/core` 提供的 Hook，减少自定义逻辑。
