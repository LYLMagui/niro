# 移除 C5 扫货深度锚点算法 设计文档

> 日期：2026-04-21
> 相关代码：`C5TradeStrategyImpl`、`BuffScanTask`、`useTaskForm`、`useUiState`、`TaskConfig.vue`

## 背景

C5 平台扫货任务当前存在"安全边际 + 价格阶梯锚定"的动态上限算法（`C5TradeStrategyImpl.calculateDynamicLimit`）。算法用锚点价（默认第二档市场价）× (1 − safeMargin 3%) 作为扫货过滤上限。

前端 UI 已移除"安全边际 / 锚定阶梯"配置入口，但代码层仍有残留：

- `niro-client/src/composables/useUiState.ts:68-72` 硬编码默认值 `{ safeMargin: 3, anchorTierIndex: 2, minConcurrency: 5 }`
- `niro-client/src/composables/useTaskForm.ts:91-99` 提交时仍带 `safetyMargin=0.03`、`ladderStep=2`、`extraConfig={...}`
- `BuffScanTask.safetyMargin / ladderStep / extraConfig` 被持久化
- `C5TradeStrategyImpl.buildStrategyConfig()` 读取后构建 `C5StrategyConfig(safeMargin=0.03, anchorTierIndex=1)`
- `calculateDynamicLimit()` 返回 `priceTiers[1] × 0.97` 作为扫货过滤上限

## 问题

扫货过滤上限低于用户配置的 `maxPrice`，且常常低于市场最低价，表现为"买不到接近用户指定价格以下的商品"，甚至触发"动态上限过低"告警。

举例：用户 `maxPrice = 10`，市场价阶梯 `[9.90, 10.00, 10.05, ...]`，当前算法算出 `dynamicLimit = 10.00 × 0.97 ≈ 9.70 < 9.90`，所有挂单被过滤。

## 目标

- 扫货过滤上限直接等于 `task.maxPrice`，不再做市场阶梯锚定
- 彻底删除深度锚点算法相关代码、字段、数据库列

## 不做的事

- 不替换为新的"市场感知"算法
- 不保留 `extra_config` 字段作为未来扩展槽
- 不重构 `doScan` 中与锚点无关的其他链路

## 变更清单

### 数据库

新增 Flyway migration：`niro-server/niro-web/src/main/resources/db/migration/V2026.04.21.001__drop_buff_scan_anchor_config.sql`

```sql
-- 变更日期：2026-04-21
-- 目标：删除 buff_scan_task 的深度锚点配置列
-- 幂等策略：drop column if exists
-- 回滚思路：如需回滚，补 add column migration 并回填默认值

begin;

alter table buff_scan_task drop column if exists safety_margin;
alter table buff_scan_task drop column if exists ladder_step;
alter table buff_scan_task drop column if exists extra_config;

commit;
```

（实际版本号以实施当天的最新序号为准。）

### 后端删除

- **类**：`niro-web/src/main/java/com/niro/web/service/strategy/impl/C5StrategyConfig.java`（整个文件）
- **方法**：`C5TradeStrategyImpl.calculateDynamicLimit()`
- **方法**：`C5TradeStrategyImpl.buildStrategyConfig()`
- **字段**：`BuffScanTask` 的 `safetyMargin` / `ladderStep` / `extraConfig`
- **字段**：`BuffScanTaskParam` 的 `safetyMargin` / `ladderStep` / `extraConfig`
- **字段**：`BuffScanTaskDTO` 的 `safetyMargin` / `ladderStep` / `extraConfig`
- **赋值行**：`BuffScanTaskServiceImpl:224` `task.setExtraConfig(param.getExtraConfig())`

### 后端修改

- `C5TradeStrategyImpl.doScan()`（L201-202 附近）：
  - 删除 `BigDecimal dynamicMaxPrice = calculateDynamicLimit(task, sortedItems, config);`
  - 调用方直接使用 `task.getMaxPrice()` 作为过滤上限
  - 一并清理仅为锚点算法存在的相关日志

### 前端删除

- `niro-client/src/composables/useUiState.ts`：
  - 删除 `C5Config` 接口（L22-28）
  - 删除 `c5Config` 响应式对象（L68-72）
  - 删除返回值中的 `c5Config`（L216）
- `niro-client/src/composables/useTaskForm.ts`：
  - 提交体不再包含 `safetyMargin / ladderStep / extraConfig`（L91-99）
  - 移除 `c5Config` 入参
- `niro-client/src/views/TaskConfig.vue`：
  - `useUiState` 解构处去掉 `c5Config`（L190）
  - 删除 editRow 解析 `extraConfig` / `safetyMargin` / `ladderStep` 的段落（L328-342）
  - `onFormSubmit` 调用 `handleSubmit` 不再传 `c5Config`（L383）
- `niro-client/src/types/task.ts`：
  - 删除 `safetyMargin` / `ladderStep` 字段（L10-11、L80-81）

## 架构

重构后 C5 扫货链路：

```
doScan()
  ├─ 获取市场商品列表 (sortedItems)
  ├─ 过滤上限 = task.getMaxPrice()
  └─ 挑选符合条件的 items 进入 doBatchBuy()
```

不再经过 `calculateDynamicLimit` / `buildStrategyConfig` / `C5StrategyConfig`。

## 兼容性与风险

- **数据库列删除**：应用启动时 Flyway 自动执行 migration，`drop column if exists` 幂等、事务包裹，对在跑任务无业务影响
- **在跑任务**：部署重启会中断扫货任务（正常行为，调度器会在启动后重新调度）
- **业务变化**：扫货过滤上限提升到 `userMax`，扫货行为相对更激进，这是用户明确期望的行为
- **前端版本兼容**：本次前后端同步发布；老前端若残留这些字段，被后端忽略即可，不需要额外兼容代码

## 测试 / 验证

- **后端**：
  - `grep` 搜索 `SafetyMargin`、`LadderStep`、`calculateDynamicLimit`、`C5StrategyConfig`、`extraConfig` 确认无残留
  - 按 `backend_jdk_execution_rule` 用 `D:\Environment\JDK\jdk-21.0.2` 执行 `mvn clean install -DskipTests`
  - 若存在相关单测，一并删除
- **前端**：
  - `grep` 搜索 `safetyMargin`、`ladderStep`、`c5Config`、`safeMargin`、`anchorTierIndex`、`extraConfig` 确认无残留
  - 按 `frontend_validation_rule` 仅跑 `pnpm type-check`，不执行 build/dev，UI 验证由用户手动完成
- **数据库**：
  - 应用启动后检查 `buff_scan_task` 表结构，确认三列已删除
