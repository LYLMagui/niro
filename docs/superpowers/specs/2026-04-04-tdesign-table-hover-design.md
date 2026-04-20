# TDesign 表格 Hover 全局样式调浅设计

## 技术规格
Goal: 统一调浅项目内所有 TDesign 表格的行 hover 样式，避免 hover 背景过重导致浅色 Tag 边界被吃掉，同时保留清晰但克制的悬停反馈。
Scope: 仅修改 `niro-client/src/style.css` 中的全局 `.t-table` hover 相关样式；不改具体页面组件、不改单个业务表格结构、不改 TDesign 组件用法。
Risks: 全局 hover 反馈变弱后，依赖较强 hover 对比的页面可能在感知上略微变淡；需要控制在“变浅但仍可识别”的范围内。
Compatibility: 保持现有表格结构、交互、分页、排序、选中等行为不变，仅调整视觉样式；影响项目所有 TDesign 表格，符合全局统一要求。
Verification: 手动检查至少一个带浅色 Tag 的列表页和一个普通数据表格页，确认 hover 可见、Tag 不再被背景吃掉、表格视觉风格仍统一。

## 背景与现状

当前项目在 `niro-client/src/style.css:98` 附近定义了 TDesign 表格全局样式，其中 hover 态位于 `niro-client/src/style.css:146`：

- `.t-table .t-table__row--hover td { background: #f5f5f5 !important; }`

现状问题：

- hover 背景偏重，悬停到带浅色 `Tag`（尤其 `light-outline`）的单元格时，Tag 的边界和背景对比被削弱
- hover 虽然明显，但内容层级会被背景抢掉一些注意力
- 这是全局样式入口，只改单页会导致项目内表格风格不统一

真实问题不是某个页面写错了，而是全局 hover 样式过重。

## 方案对比

### 方案 A：只调浅背景

做法：

- 将 hover 背景从 `#f5f5f5` 调整为更浅的 `#fafafa` 或 `#fcfcfc`

优点：

- 改动最小
- 风险最低

缺点：

- hover 反馈可能略显平
- 某些页面在纯白背景下会缺少一点层次感

### 方案 B（推荐）：调浅背景 + 极轻微内阴影

做法：

- hover 背景改为 `#fafafa`
- 同时增加极轻微 `inset` 内阴影，提供弱边界感

建议值：

- `background: #fafafa`
- `box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.02)`

优点：

- 既能减轻背景压迫感，又保留 hover 可感知性
- 对浅色 Tag 更友好，不容易被背景吃掉
- 风格仍保持中性，不会把表格改成品牌色 hover

缺点：

- 比单纯改背景多一层样式，但复杂度仍很低

### 方案 C：改成极浅品牌色 hover

做法：

- 使用如 `rgba(24, 144, 255, 0.04)` 的品牌浅蓝 hover

优点：

- 品牌感更强

缺点：

- 会改变当前项目表格偏中性的视觉基调
- 全局影响更明显，不适合这次“只做降噪”的目标

## 结论

采用 **方案 B**。

理由：这次目标不是重做表格交互视觉，而是把过重的 hover 降下来，同时保留用户能感知到的悬停反馈。`#fafafa + 极浅 inset 阴影` 是最小且稳妥的全局方案。

## 详细设计

### 1. 修改入口

统一修改全局样式文件：

- `niro-client/src/style.css`

只调整现有规则：

- `.t-table .t-table__row--hover td`

不新增页面级覆盖，不做局部特判。

### 2. Hover 视觉规则

原规则：

```css
.t-table .t-table__row--hover td {
  background: #f5f5f5 !important;
}
```

调整后：

```css
.t-table .t-table__row--hover td {
  background: #fafafa !important;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.02);
}
```

设计意图：

- `#fafafa` 比当前更接近白底，减少对单元格内容的压制
- 极浅的 `inset` 阴影只提供边界感，不制造真实厚重阴影
- 中性灰方案兼容当前所有页面，不额外引入品牌色偏移

### 3. 兼容性边界

本次明确不做：

- 不改单元格 padding
- 不改表头样式
- 不改 stripe / selected / active 等其他状态
- 不改单页局部覆盖
- 不引入 CSS 变量重构

原因：

- 目标明确，就是统一调浅 hover
- 扩大到其他状态会增加验证面，超出当前需求

### 4. 验证重点

实现后重点检查：

- 带浅色 Tag 的任务列表/记录页，在 hover 时 Tag 边界是否仍清晰
- 普通纯文本表格在 hover 时是否仍能明显感知到行反馈
- 固定列、分页区、空表格状态是否未受影响
- 项目内多处 TDesign 表格风格是否保持统一

## 影响范围

直接修改：

- `niro-client/src/style.css`

预期受影响：

- 项目内所有使用 TDesign Table 的页面

## 成功标准

改动完成后应满足：

- 所有 TDesign 表格 hover 风格统一变浅
- 浅色 Tag 在 hover 下不再出现“像消失了一样”的视觉问题
- hover 反馈仍然存在，不会弱到不可感知
- 不引入页面级视觉割裂
