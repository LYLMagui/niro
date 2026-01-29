## 1. C5 任务停止失效 Bug 修复方案

### 问题分析

* **状态竞争**: `C5TaskScheduler.startSession` 在被 Cron 触发启动时，没有检查任务是否已被手动停止。如果停止操作发生在 Cron 触发之后、Worker 启动之前，Worker 会被错误启动并覆盖数据库状态为 `RUNNING`。

* **中断响应缺失**: `C5TradeStrategyImpl.executeTrade` 内部使用了不可中断的 `CompletableFuture.join()`，且未在关键步骤检查线程中断标志。导致 `cancel(true)` 发出的中断信号无法立即终止当前的扫描和下单流程。

* 调用niro-analysis进行分析

### 修复步骤

* 根据niro-analysis给出的提示词调用niro-coding进行修复

- **后端 (C5TaskScheduler.java)**:

  * 在 `startSession` 方法开头增加数据库状态校验，若状态为 `STOPPED` 则立即终止启动。

- **后端 (C5TradeStrategyImpl.java)**:

  * 将 `CompletableFuture.allOf(...).join()` 替换为可响应中断的 `get()` 方法。

  * 在执行批量下单 `doBatchBuy` 之前增加 `Thread.currentThread().isInterrupted()` 检查。

***

## 2. C5 平台列表列显示优化

### 修改方案

* **前端 (TaskList.vue)**:

  * 将 `columns` 静态数组重构为 `computed` 计算属性。

  * 根据 `currentPlatform` 动态过滤掉 `accounts` (执行账号) 列。

***

## 3. 计划配置中“扫描间隔”隐藏与逻辑分析

### 逻辑分析

* **实际用途**: 扫描间隔在 `C5TaskScheduler` 中确实被用于控制 Worker 的扫描频率。

* **场景评估**: 对于 C5 “炼金扫货”场景，其核心诉求是极速抢购。目前前端默认将其设为 1s，用户通常不需要也无需修改此值。过大的间隔会导致抢购失败，过小的间隔（<1s）可能触发 API 限制。

* **结论**: 为简化 C5 专属页面的复杂度，防止用户误配置，应在前端隐藏此选项，并强制锁定为 1s。

### 修改步骤

* **前端 (TaskConfig.vue)**:

  * 在“扫描间隔”表单项上增加 `v-if="formData.platform !== PlatformEnum.C5"`。

  * 在 `handleEdit` 初始化逻辑中，针对 C5 平台强制将 `uiState.intervalMinValue` 和 `uiState.intervalMaxValue` 设为 1。

  * 确保 `executionSummary` 在 C5 平台下显示正确的“1秒”描述。

***

## 验证方案

1. **停止测试**: 启动一个 C5 任务，立即点击停止，观察控制台是否仍有该任务的 `executeTrade` 日志输出，并确认数据库状态保持为 `STOPPED`。
2. **UI 检查**: 进入 C5 任务列表，确认“执行账号”列已消失；点击新增/编辑，确认“扫描间隔”配置项已隐藏。

