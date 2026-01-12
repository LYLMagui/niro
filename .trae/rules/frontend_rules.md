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

## 3. 异常与交互
- **闭环**：所有请求必须 `try/catch` 或 `onError`。
- **体验**：全局统一异常提示；必须提供 `loading` 或骨架屏，禁止白屏。
- **注释**：文件头包含作者、日期、功能简述；复杂计算属性/监听器说明业务含义。
