# TaskConfig 弹窗替换为 TDesign Dialog 设计

## 技术规格
Goal: 将 `niro-client/src/views/TaskConfig.vue` 中的新建/编辑任务弹窗外壳从 `PageOverlayDialog` 替换为 TDesign 官方 `Dialog`，同时保留现有表单逻辑、提交流程与主要尺寸表现。
Scope: 仅修改 `niro-client/src/views/TaskConfig.vue`，必要时调整该文件内状态与样式；不修改 `useTaskForm.ts`、不改接口、不改字段定义。
Risks: `PageOverlayDialog` 可能带有额外的定位/过渡行为，替换为官方 Dialog 后页面内挂载和关闭行为可能出现细微差异。
Compatibility: 保持表单字段、提交方法、标题文案、宽度规则（普通任务 `820px`，系统任务或周期模式 `920px`）不变；系统任务与普通任务的内容分支继续保留。
Verification: 手动验证普通任务/系统任务的新增、编辑、取消、关闭、提交；确认宽度、按钮、表单校验和提交成功后的关闭清理行为正常。

## 背景与现状

当前 `TaskConfig.vue` 中存在两套弹窗外壳：

- 普通任务弹窗：`niro-client/src/views/TaskConfig.vue:2`
- 系统任务弹窗：`niro-client/src/views/TaskConfig.vue:266`

两者共性：

- 都使用 `PageOverlayDialog`
- 都复用 `dialogTitle`
- 都使用相同的表单容器与底部按钮结构
- 都依赖 `overlayAttach` 进行挂载

主要差异：

- 普通任务与系统任务的表单内容不同
- 使用不同的表单引用：`formRef` / `systemFormRef`
- 使用不同的提交入口：`submitTaskForm` / `submitSystemTaskForm`
- 宽度规则不同：普通任务根据 `uiState.isCycleMode` 在 `820px` 与 `920px` 间切换，系统任务固定 `920px`

问题本质不是表单逻辑复杂，而是弹窗外壳重复。

## 方案对比

### 方案 A（推荐）：一个 `t-dialog` 复用外壳，内部按模式切换表单内容

做法：

- 用一个官方 `t-dialog` 替换两处 `PageOverlayDialog`
- 引入统一 `visible` 状态
- 引入 `isSystemDialog` 区分系统任务与普通任务
- Dialog 的 body 中按 `isSystemDialog` 渲染两套现有表单内容
- Dialog 的 footer 中根据 `isSystemDialog` 决定调用 `submitTaskForm` 或 `submitSystemTaskForm`

优点：

- 直接消除重复的弹窗外壳
- 改动聚焦，风险低
- 与“复用一个官方 Dialog、保留现有尺寸”的目标一致

缺点：

- 文件中仍保留两段表单模板
- 状态切换需要统一清理，避免关闭后残留

### 方案 B：一个 `t-dialog` + 两个内部子组件

做法：

- 新增普通任务表单组件和系统任务表单组件
- 外层仅保留 Dialog 和状态控制

优点：

- 结构最清晰
- 后续扩展更方便

缺点：

- 超出本次需求范围
- 引入额外拆分，增加改动面和验证成本

### 方案 C：继续保留两个弹窗，只把外壳平移成两个 `t-dialog`

优点：

- 迁移最直接
- 逻辑影响最小

缺点：

- 重复结构仍在
- 不符合“复用”的明确要求

## 结论

采用 **方案 A**。

理由：当前真实问题是重复外壳，而不是表单内容耦合。最小且正确的改法就是复用一个官方 Dialog，保留两套表单内容分支，不额外拆组件，不碰表单逻辑。

## 详细设计

### 1. Dialog 外壳设计

在 `TaskConfig.vue` 中将两处 `PageOverlayDialog` 合并为一个 `t-dialog`，核心属性如下：

- `v-model:visible="visible"`
- `:header="dialogTitle"`
- `:width="dialogWidth"`
- `:attach="overlayAttach || 'body'"`
- `:show-in-attached-element="!!overlayAttach"`
- 使用自定义 `footer` 插槽

说明：

- `dialogWidth` 为计算属性
  - 系统任务：`920px`
  - 普通任务：`uiState.isCycleMode ? '920px' : '820px'`
- 不启用 `destroyOnClose`，避免关闭后销毁内容影响当前表单状态与现有提交流程
- 保留默认关闭按钮
- 关闭时统一走收口方法，清理 `overlayAttach` 与 `overlayOriginRect`

### 2. 状态设计

新增或收敛以下状态：

- `visible: Ref<boolean>`：统一控制 Dialog 显示
- `isSystemDialog: Ref<boolean>`：标记当前是否为系统任务弹窗
- `dialogWidth: ComputedRef<string>`：根据模式与 `uiState.isCycleMode` 决定宽度

保留以下已有状态：

- `dialogTitle`
- `formRef`
- `systemFormRef`
- `overlayAttach`
- `overlayOriginRect`

移除或废弃：

- `dialogVisible`
- `systemDialogVisible`

### 3. 打开逻辑设计

#### `handleAdd`

- `resetForm()`
- 设置 `formData.platform`
- 初始化普通任务默认值
- 设置 `isSystemDialog = false`
- 设置 `dialogTitle = '新增任务'`
- 设置 `overlayAttach`、`overlayOriginRect`
- 设置 `visible = true`
- `nextTick` 后清理普通任务表单校验
- 拉取账号列表

#### `handleAddSystem`

- `resetForm()`
- 设置系统任务默认 `taskType`
- 设置 `isSystemDialog = true`
- 设置 `dialogTitle = '新增系统任务'`
- 设置 `overlayAttach`、`overlayOriginRect`
- 设置 `visible = true`
- `nextTick` 后清理系统任务表单校验
- 拉取账号列表

#### `handleEdit`

- 保留现有初始化逻辑，包括：
  - `Object.assign(formData, row)`
  - C5 配置解析
  - UI 状态同步
  - 商品与关联任务回填
- 通过 `row.taskType >= 2` 判断是否为系统任务
  - 是：`isSystemDialog = true`
  - 否：`isSystemDialog = false`
- 设置 `dialogTitle = '编辑任务'`
- 设置 `visible = true`
- `nextTick` 后清理对应表单校验
- 拉取账号列表

### 4. 内容区域设计

Dialog body 内采用条件渲染：

- `v-if="!isSystemDialog"`：渲染现有普通任务表单
- `v-else`：渲染现有系统任务表单

约束：

- 表单字段、表单项顺序、校验规则、`ScheduleConfig` 用法均保持不变
- 不改 `useTaskForm.ts`
- 不合并 `formRef` 与 `systemFormRef`，避免一次性扩大影响面

### 5. Footer 设计

统一使用一个 footer 插槽，按钮文案保持当前行为：

- 取消按钮：关闭 Dialog
- 提交按钮：
  - 普通任务时触发 `submitTaskForm`
  - 系统任务时触发 `submitSystemTaskForm`

按钮权限与 loading 行为保持现状：

- `v-permission="PermissionConstant.TASK_BUFF_LIST"`
- `:loading="submitLoading"`

### 6. 关闭与清理设计

新增统一关闭方法，例如 `closeDialog()`，负责：

- `visible = false`
- 清空 `overlayAttach`
- 清空 `overlayOriginRect`

以下场景统一调用该方法：

- 点击取消按钮
- 点击右上角关闭按钮
- 遮罩关闭（若保留）
- 提交成功后的关闭逻辑

`onFormSubmit` 中原先同时关闭两个弹窗的逻辑，需要改为调用统一关闭方法。

### 7. 样式设计

保留现有样式主体：

- `.dialog-shell`
- `.form-container`
- `.compact-form`

按实际模板结果处理样式：

- 若 `.dialog-footer` 不再被使用，则删除
- 若 TDesign Dialog 默认 body padding 与现有样式冲突，则通过 `dialogClassName` 或局部样式覆盖，使表单区域维持当前尺寸感和滚动区域表现

目标不是重做视觉，而是尽量保持当前弹窗体积、留白和滚动体验。

## 影响范围

直接影响文件：

- `niro-client/src/views/TaskConfig.vue`

不应影响：

- `niro-client/src/composables/useTaskForm.ts`
- `niro-client/src/views/TaskList.vue`
- 后端接口与任务字段结构

## 验证清单

### 普通任务

- [ ] 点击“新增任务”能打开 Dialog
- [ ] 默认宽度为 `820px`
- [ ] 切换到周期模式后宽度变为 `920px`
- [ ] 点击取消/关闭按钮能正确关闭
- [ ] 提交校验与成功提示正常
- [ ] 提交成功后 Dialog 关闭且状态清理正常

### 系统任务

- [ ] 点击“新增系统任务”能打开 Dialog
- [ ] 宽度为 `920px`
- [ ] 表单项与原行为一致
- [ ] 点击取消/关闭按钮能正确关闭
- [ ] 提交校验与成功提示正常

### 编辑场景

- [ ] 编辑普通任务进入普通表单内容
- [ ] 编辑系统任务进入系统表单内容
- [ ] 编辑时原有回填逻辑正常

### 兼容性

- [ ] `attach` 挂载节点正常
- [ ] 页面滚动区域没有明显异常
- [ ] 不引入 API / 数据结构兼容性破坏

## 非目标

本次不做以下事项：

- 不拆分新的表单子组件
- 不重构 `useTaskForm.ts`
- 不重做弹窗动画、锚点展开或过渡效果
- 不调整字段文案、业务逻辑、接口入参
- 不修改任务列表页表格和按钮行为

## 规格自检

已检查以下问题并确认当前规格可执行：

- 无 `TODO` / `待定` / 占位描述
- 范围聚焦在单文件实现，可由一个实现计划覆盖
- 推荐方案、状态设计、关闭清理、验证项之间无明显冲突
- “复用一个 Dialog，保留两套表单内容与现有尺寸”的目标在全文中保持一致
