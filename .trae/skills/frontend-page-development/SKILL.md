---
name: frontend-page-development
description: 当用户要求修改前端页面、重构布局、优化 UI、补表单/表格/弹窗、实现响应式适配，或改造 Vue 页面时使用。这是前端页面开发与界面改造的默认技能。
---

# Frontend Page Development

## Overview

用于指导前端页面与界面的设计、实现、改造和审查。

目标不是把页面“做出来”就算完，而是在现有项目约束内做出可落地、可维护、体验一致的页面。

## 前置依赖检查

在开始任何页面工作前，必须先确认下列技能是否已安装：

- `frontend-design`
- `ui-ux-pro-max`
- `tailwind-design-system`

[//]: # (- `web-design-guidelines`)

如果本地没有安装，先用 `find-skills` 搜索并安装到项目内，再继续。

### TDesign 可插拔依赖

`tdesign-mcp-server` 不是强制前提，而是一个可插拔能力：

- 如果当前项目**没有使用 TDesign UI 组件**，忽略这条，不要强行查文档或配置 MCP。
- 如果当前项目**使用了 TDesign**，且`tdesign-mcp-server`中获取不到组件的API和使用方式时，必须通过 `markdown-proxy` skill 获取官方文档：
  `https://tdesign.tencent.com/vue-next/getting-started`
- 同时检查 `tdesign-mcp-server` 是否已配置；如果没有配置，再按当前客户端把它补到项目的 MCP 配置里。
- 使用 TDesign 相关组件时，优先参考官方文档和 MCP 输出，不凭印象猜 API。

## When to use this skill

遇到以下请求时使用本技能：

- 修改已有前端页面
- 新建页面、视图、组件
- 重构页面布局或信息层级
- 优化视觉设计、交互体验、响应式表现
- 为现有业务补充表单、表格、弹窗、抽屉、详情页、列表页、仪表板
- 用户没有明确说“设计”，但本质上是在让 AI 改 UI / 页面 / 前端代码

如果只是解释一小段前端代码、修一个纯逻辑 bug、或只改文案，不必强行套用整套流程；但只要涉及页面结构、样式、组件、交互中的任一项，就必须使用本技能。

## Rules

- 先理解业务目标，再动 UI。
- 先读现有代码和页面上下文，避免凭空重写。
- 优先复用现有设计模式、组件模式和样式约定，不凭喜好另起炉灶。
- 视觉、交互、代码实现一起考虑，不做只好看但难维护的页面。
- 涉及 UI 组件时，先查组件文档和属性，再写代码。
- 涉及 TDesign 组件时，优先使用 `tdesign-mcp-server` 查询官方组件API和使用方式，不凭印象猜组件 API。

## Instructions

### Step 1: Clarify goals and constraints
先提炼这些信息：

- 页面目标：这个页面是做什么的
- 用户目标：用户希望完成什么操作
- 数据对象：页面展示和编辑的核心数据是什么
- 当前状态：是新建页面还是修改现有页面
- 技术栈：Vue / TDesign / 路由 / 状态管理 / 现有样式体系
- 边界约束：是否必须复用现有组件、布局、接口、样式规范

如果信息不足，先补最关键的缺口，再实现。

### Step 2: Read the current implementation
优先阅读：

- 当前文件
- 同模块相邻页面
- 已存在的布局组件、表单组件、列表组件、弹窗组件
- 全局样式、主题变量、设计约束
- 当前仓库是否已经在用 TDesign 组件

目标是识别：

- 现有页面模式
- 现有组件复用点
- 当前项目的视觉基线
- 是否已经有类似页面可以借鉴

### Step 3: Design the page approach
在写代码前，先明确这些内容：

- 页面结构：头部、筛选区、内容区、操作区、详情区如何分层
- 交互路径：用户最常见的操作路径是什么
- 状态设计：空态、加载态、错误态、禁用态是否需要体现
- 响应式策略：桌面端和移动端如何变化
- 组件策略：哪些用已有组件，哪些需要新增

若需求偏设计导向或UI重构/样式调整，必须主动使用以下技能：
- `frontend-design`：做布局和视觉方向收敛
- `ui-ux-pro-max`：做风格、排版、交互质量判断
- `tailwind-design-system`：做样式系统约束

### Step 4: Check component APIs before coding
如果当前项目使用了 TDesign 组件，至少做下面之一：

- 查询组件列表，确认是否已有合适组件
- 查询目标组件文档，确认 props / events / slots
- 查询 DOM 结构，确认样式覆盖方式
- 先用 `tdesign-mcp-server` 获取官方组件的API和使用方式

适用组件包括但不限于：
- 表单：Input / Select / Form / DatePicker / Upload
- 展示：Table / Card / Tag / Tooltip / Badge
- 反馈：Dialog / Drawer / Message / Notification / Loading
- 导航：Tabs / Menu / Breadcrumb / Steps

如果项目明确不用 TDesign，就遵循项目现有组件体系；但只要要用 TDesign，就必须先查官方文档和 MCP。

### Step 5: Implement with minimal disruption
- 修改已有页面时，优先在现有结构上改
- 不为一次改动引入新的重型抽象
- 不随意大改用户未要求的区域
- 保持现有路由、数据流、API 接口兼容
- 新建页面时，优先参考已有页面骨架、布局容器、样式变量和通用组件

### Step 6: Review quality before delivery
交付前至少检查：

- 层级是否清晰，重点信息是否一眼可见
- 间距、字号、颜色、圆角、阴影是否一致
- 点击区域是否足够大，反馈是否明确
- 表单是否有标签、提示、错误反馈
- 是否存在明显的键盘不可达问题
- 是否缺少焦点态
- 是否存在文本对比度过低
- 是否有移动端横向滚动
- 是否有只靠颜色传达状态的问题
- 是否有 loading / empty / error 状态缺失

[//]: # (必需调用 `web-design-guidelines` 做 review。)

## Examples

### Example 1: Page refactor
- “帮我把这个 Vue 页面改得更好看一点，并补全移动端适配”
- “把这个列表页改成筛选 + 表格 + 详情抽屉的结构”

### Example 2: New page or feature block
- “新建一个任务详情页，要有基础信息、执行记录和操作区”
- “用 TDesign 给这个页面补一个弹窗和表单”

### Example 3: UI review
- “检查一下这个前端页面的 UI 和可访问性问题”
- “帮我重构这个表单页面，交互太乱了”

## Best practices

1. 优先把 skill 定位成本仓库的前端页面开发总控，而不是单纯视觉设计 skill。
2. description 只写触发条件，不把工作流和依赖 skill 塞进 frontmatter。
3. 前置依赖必须先确认安装；缺失时用 `find-skills` 补齐后再继续。
4. 需要设计方向时调用 `frontend-design`、`ui-ux-pro-max`、`tailwind-design-system` 辅助判断，必须优先使用`tailwind-design-system`编写css，如果无法实现再使用传统的css。

[//]: # (5. 需要做 UI 规范和可访问性检查时调用 `web-design-guidelines`。)
6. 只要涉及 TDesign 组件选型、属性、插槽、事件或 DOM 结构，就先走 `tdesign-mcp-server`，并且修改组件样式时，必须优先检查是否有对应的属性，优先使用属性控制组件样式。

## References

- `frontend-design`
- `tailwind-design-system`
- `ui-ux-pro-max`

[//]: # (- `web-design-guidelines`)
- `tdesign-mcp-server`
- `markdown-proxy`
