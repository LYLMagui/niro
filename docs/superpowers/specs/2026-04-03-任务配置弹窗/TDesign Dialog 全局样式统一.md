# TDesign Dialog 全局样式统一设计

## 技术规格
Goal: 统一 `niro-client` 中所有 TDesign Dialog 的视觉样式，将圆角调整为 `2px`，并让 header / footer 分隔线延伸到弹窗左右边缘。
Scope: 仅修改前端全局样式入口 `niro-client/src/style.css`，必要时清理 `TaskConfig.vue` 中与全局样式重复的局部边框；不调整业务逻辑、不改 Dialog 打开/关闭流程、不改宽度与布局结构。
Risks: 全局覆盖会影响所有 `t-dialog`，如果个别页面已有局部边框或圆角覆盖，可能出现双线、线条缺失或视觉不一致。
Compatibility: 保持 TDesign 默认结构与现有页面调用方式不变；只覆盖公共视觉属性，不改变内容区、按钮区和关闭行为的交互逻辑。
Verification: 手动检查 `TaskConfig`、其他普通 `t-dialog`、仅 header 的弹窗、带 footer 的弹窗，确认圆角统一为 `2px`，分隔线贴边且无双线。

## 背景与现状

当前项目已经在 `niro-client/src/style.css` 中做了全局 TDesign Table 样式覆盖，但 Dialog 仍主要沿用默认样式或局部页面补丁。

当前问题有两个：

1. Dialog 圆角不符合当前前端整体偏硬朗的视觉风格，需要统一收敛到 `2px`
2. Dialog 的 header / footer 分隔线受内容层 padding 影响，没有完整延伸到左右边缘，视觉上不够干净

这不是单个页面问题，而是全局视觉规范问题，所以不应在 `TaskConfig.vue` 之类的业务组件里继续打补丁。

## 方案对比

### 方案 A（推荐）：在 `style.css` 全局覆盖 TDesign Dialog 公共类名

做法：

- 在 `niro-client/src/style.css` 中统一覆盖 Dialog 根节点圆角
- 让 header 自己承担底部分隔线
- 让 footer 自己承担顶部分隔线
- 如 `TaskConfig.vue` 仍保留与之重复的局部边框，则删掉重复部分

优点：

- 真正全局生效，符合目标
- 改动集中，风险可控
- 不污染业务组件

缺点：

- 依赖 TDesign 当前 DOM 结构和类名
- 需要检查是否影响少量特殊弹窗

### 方案 B：封装统一 Dialog 组件并逐步替换

优点：

- 后续控制力最强
- 视觉与行为都可统一收口

缺点：

- 明显超出本次需求范围
- 替换成本高，且容易漏改

### 方案 C：在各页面局部覆盖 Dialog 样式

优点：

- 单次改动看起来简单

缺点：

- 无法形成全局规范
- 会制造更多分散补丁
- 后续维护成本最高

## 结论

采用 **方案 A**。

原因：当前真实问题是 Dialog 的全局视觉不统一，而不是某个业务弹窗的结构设计错误。最简单且正确的做法，就是把视觉规则收敛到全局样式入口，避免继续在业务文件里堆局部补丁。

## 详细设计

### 1. 作用范围

样式变更落在：

- `niro-client/src/style.css`

必要时清理：

- `niro-client/src/views/TaskConfig.vue`

本次不新增任何组件、hook、配置项或样式抽象。

### 2. 全局 Dialog 样式策略

统一覆盖以下视觉规则：

- Dialog 外层容器圆角为 `2px`
- Header 底部分隔线由 header 层承担，并横向铺满
- Footer 顶部分隔线由 footer 层承担，并横向铺满

设计要点：

- 不通过额外伪元素造线，优先直接复用现有 header / footer 容器边框
- 线应该挂在整层容器上，而不是只挂在内容内层上
- 保持 header / body / footer 原有语义层级不变

### 3. 对 `TaskConfig` 的影响

`TaskConfig.vue` 当前局部样式里，`.form-container` 自身带有上下边框。如果全局 Dialog header/footer 已接管分隔线，需要检查是否出现以下问题：

- header 下方出现双线
- footer 上方出现双线
- body 区域边框与全局线条不连续

如果出现重复，只删除与全局规则冲突的那一条局部边框，不调整表单 padding、滚动和内容布局。

### 4. 不做的事

本次明确不做：

- 不修改 Dialog 宽度
- 不调整 header 文本字号或按钮位置
- 不修改 footer 按钮排列
- 不新增主题系统或 CSS 变量体系
- 不重构业务组件中的 Dialog 使用方式

### 5. 验证清单

至少检查以下场景：

1. `TaskConfig` 新增任务弹窗
2. `TaskConfig` 编辑任务弹窗
3. 其他普通 `t-dialog`
4. 带 footer 的弹窗
5. 无复杂表单内容的轻量弹窗

验收标准：

- 所有 Dialog 外层圆角为 `2px`
- header 下横线左右贴边
- footer 上横线左右贴边
- 不出现双线
- 不破坏现有关闭、提交、滚动与表单布局

## 实施备注

实现顺序应为：

1. 先在 `style.css` 增加全局 Dialog 覆盖
2. 再检查 `TaskConfig.vue` 是否需要删掉局部重复边框
3. 最后做页面回看验证

这样可以先解决结构问题，再处理少量局部冲突，避免反过来在业务组件里盲目补样式。