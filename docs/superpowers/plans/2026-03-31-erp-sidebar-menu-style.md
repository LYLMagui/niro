# ERP 风格侧边菜单样式实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在不改动页面整体壳层和菜单数据结构的前提下，把左侧菜单改成传统 ERP 后台风格：窄侧栏、白底、一级菜单更高更规整、选中项使用整块浅蓝背景。

**架构：** 保留现有 `Layout.vue` 与 `SidebarItem.vue` 的递归菜单结构，不重写菜单系统。实现集中在 `Layout.vue`：通过收紧 `t-aside` / `t-menu` 展开宽度，并在 `.erp-side-menu` 作用域下覆盖 TDesign 菜单局部样式，调整一级项高度、字体、图标、层级缩进、hover 和选中态；`SidebarItem.vue` 仅作为结构核对文件，不引入新逻辑。

**技术栈：** Vue 3、TypeScript、TDesign Vue Next、Tailwind CSS、pnpm

---

## 文件结构

### 会修改的文件
- `niro-client/src/components/Layout.vue`
  - 负责侧栏展开/折叠宽度、菜单容器内边距、ERP 风格菜单项样式覆盖。

### 需要核对但原则上不修改的文件
- `niro-client/src/components/SidebarItem.vue`
  - 核对递归节点是否仍满足 TDesign 的 `icon` / `title` / `value` 用法，不新增状态或结构分支。

### 参考文件
- `docs/plans/2026-03-31-erp-sidebar-menu-style-design.md`
  - 已确认的设计说明。
- `docs/superpowers/plans/2026-03-30-compact-sidebar-menu.md`
  - 之前的侧边栏样式计划，可作为宽度、折叠行为和验证方式参考。

### 验证相关
- `niro-client/package.json`
- 运行命令：`cd "D:/MySpace/niro/niro-client" && pnpm type-check`
- 运行命令：`cd "D:/MySpace/niro/niro-client" && pnpm build`

---

### 任务 1：收紧侧栏展开宽度到 ERP 导航范围

**文件：**
- 修改：`niro-client/src/components/Layout.vue:55-69`

- [ ] **步骤 1：记录当前宽度边界**

记录当前模板中的三处宽度值：
- `t-aside` 的 `:width`
- `t-aside` 的内联 `:style.width`
- `t-menu` 的 `:width`

预期：确认只调整菜单展开宽度，不动折叠态 `64px` 和 `expand-type="popup"`。

- [ ] **步骤 2：把展开态宽度统一调整到窄侧栏范围**

将展开态宽度统一收敛到约 `188px`（可在 `180px ~ 200px` 范围内微调，但三处必须一致）：

```vue
<t-aside
  :width="collapsed ? '64px' : '188px'"
  :style="{ width: collapsed ? '64px' : '188px' }"
>
```

```vue
<t-menu
  :width="['188px', '64px']"
  :expand-type="collapsed ? 'popup' : 'normal'"
>
```

验收：展开态视觉明显更接近传统 ERP 侧栏，而不是当前偏宽布局。

- [ ] **步骤 3：检查模板一致性**

确认三处宽度值完全一致，避免 `aside` 宽度和 `menu` 内部宽度不同步导致错位。

- [ ] **步骤 4：Commit**

```bash
git add niro-client/src/components/Layout.vue
git commit -m "refactor(ui): 收紧 ERP 侧栏宽度"
```

---

### 任务 2：把一级菜单项改成传统 ERP 风格

**文件：**
- 修改：`niro-client/src/components/Layout.vue:223-399`

- [ ] **步骤 1：调整菜单容器留白**

把侧栏内部容器的 `px` / `py` 调整到更适合窄侧栏的范围，目标是：
- 左右保留小幅空白，但不做卡片式外边距
- 顶部留白适中，不要让第一个菜单项贴边

例如把：

```vue
<div class="min-h-0 flex-1 overflow-y-auto px-3 py-4">
```

收敛到更适合 ERP 菜单的值。

验收：菜单外框留白更像传统导航列表，不像独立卡片区块。

- [ ] **步骤 2：统一一级菜单高度、字体和图标尺寸**

在 `.erp-shell` 变量或对应 `:deep(...)` 规则中调整：
- 一级菜单高度约 `46px ~ 50px`
- 字体约 `15px`
- 图标尺寸约 `18px`
- 图标和文本间距收口到稳定值

参考方向：

```css
.erp-shell {
  --erp-menu-item-height: 48px;
  --erp-menu-item-padding-x: 12px;
  --erp-menu-icon-size: 18px;
}
```

```css
:deep(.erp-side-menu .t-menu__item),
:deep(.erp-side-menu .t-submenu__title) {
  min-height: var(--erp-menu-item-height);
  font-size: 15px;
  line-height: var(--erp-menu-item-height);
}
```

约束：只改菜单视觉，不改业务逻辑。

- [ ] **步骤 3：把激活态改成浅蓝整块高亮**

将当前选中态从“左侧蓝条主导”收敛到“整块浅蓝背景 + 蓝字”的 ERP 风格。允许保留伪元素定义，但最终效果应以背景块为主；若左侧激活条仍显得突兀，应直接移除：

```css
:deep(.erp-side-menu .t-is-active.t-menu__item),
:deep(.erp-side-menu .t-submenu__title.t-is-active) {
  color: #1677ff !important;
  background: #e6f4ff !important;
}

:deep(.erp-side-menu .t-is-active.t-menu__item::before),
:deep(.erp-side-menu .t-submenu__title.t-is-active::before) {
  display: none;
}
```

验收：选中项明显接近目标截图，视觉中心在整块浅蓝底，不是细长蓝条。

- [ ] **步骤 4：收敛 hover 与箭头样式**

让 hover 保持轻量，不抢选中态；箭头对齐右侧，不额外制造拥挤感：

```css
:deep(.erp-side-menu .t-menu__item:hover),
:deep(.erp-side-menu .t-submenu__title:hover) {
  background: #f5f7fa;
}

:deep(.erp-side-menu .t-fake-arrow) {
  margin-left: auto;
}
```

验收：hover、展开箭头、选中态三者层级清晰，不互相打架。

- [ ] **步骤 5：Commit**

```bash
git add niro-client/src/components/Layout.vue
git commit -m "fix(ui): 调整 ERP 风格一级菜单样式"
```

---

### 任务 3：收敛二级菜单缩进和层级密度

**文件：**
- 修改：`niro-client/src/components/Layout.vue:223-399`
- 检查：`niro-client/src/components/SidebarItem.vue:1-63`

- [ ] **步骤 1：确认 `SidebarItem.vue` 无需结构改动**

检查以下点：
- `t-submenu` 只传递 `value`、`title`、`icon`
- `t-menu-item` 只渲染最小文本结构
- 不存在手工折叠逻辑或额外标题包裹层

预期：保持现状，不修改该文件。

- [ ] **步骤 2：调整二级/三级菜单缩进**

在 `Layout.vue` 中把二级和三级菜单缩进调整到更适合窄侧栏的值，目标是：
- 二级菜单一眼能看出层级
- 但不会因为缩进太深导致中文标题立即被截断

参考方向：

```css
.erp-shell {
  --erp-menu-level-2-indent: 42px;
  --erp-menu-level-3-indent: 54px;
}
```

```css
:deep(.erp-side-menu .t-submenu__children > .t-menu__item),
:deep(.erp-side-menu .t-submenu__children > .t-submenu > .t-submenu__title) {
  padding-left: var(--erp-menu-level-2-indent);
}
```

- [ ] **步骤 3：检查窄侧栏下的文本可读性**

人工核对以下点：
- 一级菜单标题不应大面积提前截断
- 二级菜单在 `188px` 左右宽度下仍可读
- 子菜单展开后没有明显挤压感

若不满足，只微调缩进和图标间距，不新增逻辑分支。

- [ ] **步骤 4：Commit**

```bash
git add niro-client/src/components/Layout.vue
git commit -m "fix(ui): 优化 ERP 侧边菜单层级缩进"
```

---

### 任务 4：确认折叠态兼容并完成验证

**文件：**
- 修改：`niro-client/src/components/Layout.vue`
- 检查：`niro-client/src/components/SidebarItem.vue`

- [ ] **步骤 1：人工检查折叠态菜单**

检查：
- 折叠态仍只显示图标
- 图标在 `64px` 宽度下保持居中和间距正常
- 子菜单仍通过 TDesign 官方 popup 展示

预期：本次改造只影响展开态视觉，不破坏折叠交互。

- [ ] **步骤 2：运行类型检查**

运行：`cd "D:/MySpace/niro/niro-client" && pnpm type-check`

预期：通过。若失败，优先修复本次模板或样式改动引入的问题。

- [ ] **步骤 3：运行生产构建**

运行：`cd "D:/MySpace/niro/niro-client" && pnpm build`

预期：通过。若失败，优先修复本次样式改动导致的构建问题。

- [ ] **步骤 4：最终人工验收**

检查：
- 展开态宽度已收窄到 ERP 风格范围
- 一级菜单高度、字体、图标更接近目标截图
- 选中态为浅蓝整块背景
- 二级菜单缩进清晰但不过深
- 折叠态 popup 行为未破坏

- [ ] **步骤 5：提交最终修复**

```bash
git add niro-client/src/components/Layout.vue niro-client/src/components/SidebarItem.vue
git commit -m "fix(ui): 调整 ERP 风格侧边菜单样式"
```

---

## 验收标准

- 展开态侧栏宽度收敛到约 `180px ~ 200px`
- 一级菜单项高度、字体、图标更接近传统 ERP 后台风格
- 当前选中项呈现浅蓝背景块 + 蓝字，而不是细长左侧激活条主导
- 二级和三级菜单缩进清晰且在窄侧栏中可读
- 折叠态继续使用 TDesign 官方 popup 行为
- `SidebarItem.vue` 不新增折叠逻辑或额外抽象
- `pnpm type-check` 通过
- `pnpm build` 通过
