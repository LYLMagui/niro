# 紧凑型侧边菜单实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在不重写菜单系统的前提下，将左侧菜单调整为紧凑布局，修复内边距过大、图标过大、多级菜单显示不全的问题，并保证折叠态继续使用官方 popup 行为。

**架构：** 保留现有 `Layout.vue` 与 `SidebarItem.vue` 的递归渲染结构，只在 `Layout.vue` 中优先使用 TDesign Vue Next 官方菜单属性收口布局，再通过局部 `:deep(...)` 覆盖收紧菜单密度。`SidebarItem.vue` 仅在需要时做最小结构修正，不引入新的菜单状态或抽象。

**技术栈：** Vue 3、TypeScript、TDesign Vue Next、Tailwind CSS、pnpm

---

## 文件结构

### 会修改的文件
- `niro-client/src/components/Layout.vue`
  - 负责侧边栏宽度、`t-menu` 官方属性、菜单紧凑样式覆盖。
- `niro-client/src/components/SidebarItem.vue`
  - 负责菜单递归节点；若官方属性已足够，仅保留现有结构。

### 参考文件
- `docs/plans/2026-03-30-compact-sidebar-menu-design.md`
  - 已确认的设计说明。
- `docs/superpowers/plans/2026-03-30-menu-layout-bug-fix-design.md`
  - 既有菜单折叠修复计划，可用于比对已做过的收敛方向。

### 验证相关
- `niro-client/package.json`
- 运行命令：`cd niro-client && pnpm type-check`
- 运行命令：`cd niro-client && pnpm build`

---

### 任务 1：收紧侧栏宽度与官方菜单属性

**文件：**
- 修改：`niro-client/src/components/Layout.vue:55-69`

- [ ] **步骤 1：编写失败前的对照记录**

记录当前实现的三个关键值，作为修改边界：
- `t-aside` 展开宽度为 `150px`
- `t-menu` 的 `width` 为 `['150px', '64px']`
- 折叠态使用 `expand-type="popup"`

预期：确认本任务不改折叠机制，只调紧凑密度与展开态宽度。

- [ ] **步骤 2：修改展开态宽度到紧凑范围**

将以下值统一调整到约 `168px`：

```vue
<t-aside
  :width="collapsed ? '64px' : '168px'"
  :style="{ width: collapsed ? '64px' : '168px' }"
>
```

并同步更新：

```vue
<t-menu
  :width="['168px', '64px']"
  :expand-type="collapsed ? 'popup' : 'normal'"
>
```

约束：只改这组宽度，不顺手改动其他布局容器。

- [ ] **步骤 3：快速检查模板一致性**

确认 `t-aside` 的 `:width`、内联 `:style.width`、`t-menu` 的 `width` 三处值一致，避免展开态视觉与菜单内部宽度不一致。

验收：没有出现 `aside` 宽度和 `menu` 宽度错位的情况。

- [ ] **步骤 4：Commit**

```bash
git add niro-client/src/components/Layout.vue
git commit -m "refactor(ui): 收紧侧边栏官方宽度配置"
```

---

### 任务 2：压缩菜单项内边距、图标尺寸和层级缩进

**文件：**
- 修改：`niro-client/src/components/Layout.vue:301-349`

- [ ] **步骤 1：删除放大空白的局部样式**

删除或收紧以下样式：

```css
:deep(.erp-side-menu .t-menu__content) {
  margin-left: 8px;
}
```

以及所有把 submenu children 额外归零但未真正控制缩进密度的样式，重新收口为更小的一组规则。

预期：不再额外制造菜单左侧空白。

- [ ] **步骤 2：写一个失败的视觉目标清单**

以注释或计划执行记录方式确认以下目标当前未满足：
- 菜单项高度偏大
- 左右 padding 过宽
- 图标尺寸偏大
- 多级缩进叠加过快

预期：后续修改只服务这 4 个问题，不扩展范围。

- [ ] **步骤 3：编写最小样式收敛代码**

在 `Layout.vue` 中新增或替换为类似下面的局部覆盖：

```css
:deep(.erp-side-menu .t-menu__item),
:deep(.erp-side-menu .t-submenu__title) {
  min-height: 40px;
  padding: 0 10px;
  font-size: 14px;
}

:deep(.erp-side-menu .t-menu__item .t-menu__icon),
:deep(.erp-side-menu .t-submenu__title .t-menu__icon) {
  width: 16px;
  min-width: 16px;
  font-size: 16px;
}

:deep(.erp-side-menu .t-submenu__children .t-menu__item),
:deep(.erp-side-menu .t-submenu__children .t-submenu__title) {
  padding-left: 28px;
}

:deep(.erp-side-menu .t-submenu__children .t-submenu__children .t-menu__item),
:deep(.erp-side-menu .t-submenu__children .t-submenu__children .t-submenu__title) {
  padding-left: 40px;
}
```

说明：具体值可微调，但目标必须是“更紧凑”，而不是“继续堆特判”。

- [ ] **步骤 4：同步收紧箭头和激活条**

将箭头间距、激活条上下留白与宽度一起压缩，例如：

```css
:deep(.erp-side-menu .t-fake-arrow) {
  margin-left: 4px;
}

:deep(.erp-side-menu .t-is-active.t-menu__item::before),
:deep(.erp-side-menu .t-submenu__title.t-is-active::before) {
  top: 6px;
  bottom: 6px;
  width: 2px;
}
```

验收：激活态仍清晰，但不再显得笨重。

- [ ] **步骤 5：人工检查展开态多级菜单**

检查以下点：
- 一级菜单标题不再大面积截断
- 二级菜单缩进更小但层级仍可辨认
- 三级菜单仍在侧栏内可读

若失败，只微调尺寸值，不新增逻辑分支。

- [ ] **步骤 6：Commit**

```bash
git add niro-client/src/components/Layout.vue
git commit -m "fix(ui): 收紧侧边菜单间距与图标尺寸"
```

---

### 任务 3：确认递归菜单结构无需额外折叠逻辑

**文件：**
- 检查：`niro-client/src/components/SidebarItem.vue:1-63`
- 如有必要才修改：`niro-client/src/components/SidebarItem.vue`

- [ ] **步骤 1：检查 `SidebarItem.vue` 是否与官方折叠能力冲突**

重点看：
- `t-submenu` 是否只传递 `value`、`title`、`icon`
- `t-menu-item` 是否只渲染最小标签结构
- 是否存在额外包裹层、手工拼接标题、手工折叠判断

预期：当前文件大概率不需要改。

- [ ] **步骤 2：如果无需修改，明确保留现状**

在执行记录里注明：
- 当前递归结构已经足够简单
- 不新增折叠逻辑
- 不调整路由跳转逻辑

验收：避免为了“整齐”而动无关代码。

- [ ] **步骤 3：如果确实发现冲突，只做最小修正**

允许修改的范围仅限：
- 删除多余包裹
- 删除影响官方 title/icon 识别的结构

禁止：
- 新增菜单状态
- 自定义 popup 行为
- 改动 `handleMenuClick` 的业务逻辑

- [ ] **步骤 4：Commit（仅在有改动时）**

```bash
git add niro-client/src/components/SidebarItem.vue
git commit -m "refactor(ui): 精简侧边菜单节点结构"
```

---

### 任务 4：运行验证并收尾

**文件：**
- 修改：`niro-client/src/components/Layout.vue`
- 可能修改：`niro-client/src/components/SidebarItem.vue`

- [ ] **步骤 1：运行类型检查**

运行：`cd niro-client && pnpm type-check`

预期：通过。若失败，先修正类型问题，再继续。

- [ ] **步骤 2：运行生产构建**

运行：`cd niro-client && pnpm build`

预期：通过。若失败，优先修复本次样式或模板改动造成的问题。

- [ ] **步骤 3：人工验收菜单行为**

检查：
- 展开态宽度约 `168px`
- 菜单项更紧凑
- 图标更小
- 多级菜单可正常显示
- 折叠态只显示图标
- 子菜单 popup 仍正常

- [ ] **步骤 4：提交最终修复**

```bash
git add niro-client/src/components/Layout.vue niro-client/src/components/SidebarItem.vue
git commit -m "fix(ui): 优化侧边菜单紧凑布局"
```

---

## 验收标准

- 展开态宽度约为 `168px`，折叠态为 `64px`
- 菜单项高度、左右 padding、图标尺寸和层级缩进均明显收紧
- 中文菜单标题不再因空白和图标过大而被过早挤压
- 二级与三级菜单在展开态可正常显示
- 折叠态继续使用 TDesign 官方 popup 行为
- `SidebarItem.vue` 不引入新的折叠逻辑或额外抽象
- `pnpm type-check` 通过
- `pnpm build` 通过
