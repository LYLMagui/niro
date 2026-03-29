# 菜单错位修复实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 修复左侧菜单错位问题，收敛 TDesign 菜单样式覆盖，保留外层 27/37 内边距和现有展开/收起逻辑。

**架构：** 这次只动菜单渲染层的样式边界，不改路由、不改菜单数据、不改交互状态。当前问题来自 `Layout.vue` 和 `SidebarItem.vue` 对 TDesign `t-menu` 内部结构的双重覆盖，因此修复要把缩进和层级控制收回到单一位置，避免父子节点重复计算 padding/margin。

**技术栈：** Vue 3、TypeScript、TDesign Vue Next、Tailwind CSS、pnpm

---

## 文件结构

### 会修改的文件
- `niro-client/src/components/Layout.vue`
  - 负责侧边栏容器、`t-menu` 外层样式、菜单统一边距、选中态与 hover 态的全局覆盖。
- `niro-client/src/components/SidebarItem.vue`
  - 负责递归渲染菜单节点；移除每个节点的独立缩进样式，让 TDesign 自己处理层级结构。

### 不会修改的文件
- `niro-client/src/store/tabs.ts`
- `niro-client/src/router/index.ts`
- `niro-client/src/utils/menu.ts`

### 验证相关
- 运行命令：`cd niro-client && pnpm type-check`
- 运行命令：`cd niro-client && pnpm build`

---

### 任务 1：收敛菜单样式边界

**文件：**
- 修改：`niro-client/src/components/Layout.vue:59-347`
- 修改：`niro-client/src/components/SidebarItem.vue:1-108`

- [ ] **步骤 1：删除双重缩进来源**

移除 `SidebarItem.vue` 中的 `menuStyle` 计算与 `:style="menuStyle"` 绑定，保留节点递归渲染和点击跳转逻辑。让每个菜单项恢复为 TDesign 默认层级布局，不再对单个节点手动拼接 `paddingInlineStart` / `paddingInlineEnd`。

- [ ] **步骤 2：保留外层统一内边距**

在 `Layout.vue` 中继续保留侧边栏内容容器的 `pl-[27px] pr-[37px]`，只把边距控制放在这一层。不要新增新的包装层，不要为不同层级再加额外缩进规则。

- [ ] **步骤 3：删除会破坏子菜单结构的覆盖**

移除 `Layout.vue` 中对 `.erp-side-menu .t-submenu__content .t-menu__item` 的 `margin-left` 覆盖；同时检查 `.t-menu__item`、`.t-submenu__title` 的覆盖是否还需要保留，优先保留最少规则集，只留下宽度、最小高度、选中态、hover 态这些与视觉一致性直接相关的样式。

- [ ] **步骤 4：保留现有交互逻辑**

不改 `handleMenuClick`、`activeValue`、`collapsed`、`sidebarMenus` 这些逻辑，不改菜单数据来源，不改路由跳转方式。目标是只修“样式错位”，不要顺手重做菜单系统。

- [ ] **步骤 5：本地检查视觉结构**

运行页面后确认以下点：
- 顶层菜单项和子菜单项在同一缩进体系内
- 子菜单展开后不再出现重复左缩进
- 菜单项文本、图标、箭头对齐正常
- 侧边栏仍然保持左 27、右 37 的整体内边距

- [ ] **步骤 6：运行类型检查**

运行：`cd niro-client && pnpm type-check`

预期：通过。若失败，先修类型错误，不要继续做样式微调。

- [ ] **步骤 7：运行构建验证**

运行：`cd niro-client && pnpm build`

预期：通过，确认菜单样式改动没有破坏生产构建。

- [ ] **步骤 8：提交修复**

```bash
git add niro-client/src/components/Layout.vue niro-client/src/components/SidebarItem.vue
git commit -m "fix(ui): 修复侧边栏菜单错位"
```

---

## 验收标准

- 菜单项不再错位、挤压或重复缩进
- 左侧边栏仍保留 27/37 的外层边距
- 菜单展开/收起逻辑不变
- 路由跳转不变
- `pnpm type-check` 通过
- `pnpm build` 通过
