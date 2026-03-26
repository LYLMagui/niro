# Niro Simple 简化版实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在 `niro-simple` 分支上移除 `niro-spider`、RocketMQ 和 XXL-JOB，保留前端 + Spring Boot 后端的简化版本，并让保留下来的页面与接口以简化行为稳定运行。

**架构：** 将现有“前端 + 后端 + Spider + MQ + 调度”的链路收敛成“Vue 前端 + Spring Boot 后端”双层结构。后端删除 MQ 和调度依赖，前端按页面保留可用主体、删除失效组件，纯依赖型页面与菜单直接移除。

**技术栈：** Vue 3 + TypeScript + Pinia + TDesign + Spring Boot 3.5 + Java 21 + MyBatis-Plus + Redis + PostgreSQL + Maven + pnpm

---

## 文件结构

**创建文件：**

- `docs/superpowers/plans/2026-03-26-niro-simple-implementation.md`：本实现计划

**修改文件：**

- `niro-server/pom.xml`：移除父 POM 中 XXL-JOB、RocketMQ 版本与说明
- `niro-server/niro-web/pom.xml`：移除 Web 模块中的 RocketMQ 依赖
- `niro-server/niro-core/src/main/java/com/niro/core/config/XxlJobConfig.java`：删除或下线 XXL-JOB 配置入口
- `niro-server/niro-core/src/main/java/com/niro/core/util/MqTxSender.java`：删除 MQ 事务发送工具
- `niro-server/niro-core/src/main/java/com/niro/core/util/RocketMqHelper.java`：删除 RocketMQ 工具
- `niro-server/niro-web/src/main/resources/config/common/common.yml`：移除 spider、xxl、rocketmq 配置段
- `niro-server/niro-web/src/main/java/com/niro/web/mq/C5OrderDetailConsumer.java`：删除 RocketMQ 消费者
- `niro-server/niro-web/src/main/java/com/niro/web/mq/C5OrderStatusSyncConsumer.java`：删除 RocketMQ 消费者
- `niro-server/niro-web/src/main/java/com/niro/web/mq/DemoMessageConsumer.java`：删除 RocketMQ 示例消费者
- `niro-server/niro-web/src/main/java/com/niro/web/jobhandler/C5OrderSyncJobHandler.java`：删除 XXL-JOB 执行器
- `niro-server/niro-web/src/main/java/com/niro/web/jobhandler/CategoryTaskJobHandler.java`：删除 XXL-JOB 执行器
- `niro-server/niro-web/src/main/java/com/niro/web/jobhandler/TestJobHandler.java`：删除 XXL-JOB 执行器
- `niro-server/niro-web/src/main/java/com/niro/web/scheduler/CategoryTaskMonitor.java`：移除调度监控逻辑或改为简化行为
- `niro-server/niro-web/src/main/java/com/niro/web/service/impl/C5OrderSyncServiceImpl.java`：去掉 MQ / 调度分支，保留简化业务或兼容壳子
- `niro-server/niro-web/src/main/java/com/niro/web/service/impl/BuffStickerServiceImpl.java`：去掉 Spider 强依赖分支，保留简化逻辑
- `niro-server/niro-web/src/main/java/com/niro/web/controller/LogController.java`：去掉对 `niro-spider` 日志的依赖，保留后端日志简化能力或空壳返回
- `niro-server/niro-web/src/main/java/com/niro/web/dto/BuffTaskMessage.java`：移除仅服务于 MQ 的字段或下线 DTO
- `niro-server/niro-web/src/test/java/com/niro/web/mq/RocketMQProducerTest.java`：删除 RocketMQ 测试
- `niro-server/niro-web/src/test/java/com/niro/web/mq/TestMessageConsumer.java`：删除 RocketMQ 测试/示例
- `niro-client/src/views/TaskConfig.vue`：删除调度、Spider、异步下发相关组件和配置块，保留可工作主体
- `niro-client/src/views/Logs.vue`：移除对 Spider 实时日志的依赖，保留后端日志视图或简化空状态
- `niro-client/src/components/task/ScheduleConfig.vue`：删除或下线调度配置组件
- `niro-client/src/components/CronEditor.vue`：若仅被调度配置使用则删除；若仍被其他页面使用则同步收口
- `niro-client/src/composables/useTaskForm.ts`：删除调度 / Spider 相关表单逻辑
- `niro-client/src/composables/useUiState.ts`：移除为调度 / Spider 页面服务的状态分支
- `niro-client/src/types/task.ts`：删除调度 / Spider 相关字段定义
- `niro-client/src/enums/TaskStatusEnum.ts`：移除仅服务于异步链路的状态定义
- `docker/rocketmq/docker-compose.yml`：删除 RocketMQ 部署文件
- `docker/rocketmq/.env`：删除 RocketMQ 环境配置
- `docker/rocketmq/conf/broker.conf`：删除 RocketMQ 配置
- `docker/xxl-job/docker-compose.yml`：删除 XXL-JOB 部署文件
- `docker/xxl-job/.env`：删除 XXL-JOB 环境配置
- `docker/xxl-job/tables_xxl_job.sql`：删除 XXL-JOB 初始化脚本
- `docker-compose.test.yml`：清理对 `niro-spider` / RocketMQ / XXL-JOB 的依赖
- `CLAUDE.md`：更新顶层项目说明，去掉 Spider / MQ / 调度描述
- `niro-server/CLAUDE.md`：更新后端说明，去掉 RocketMQ / XXL-JOB 依赖说明
- `niro-client/CLAUDE.md`：更新前端页面与组件说明，去掉调度与 Spider 相关描述

**删除文件/目录：**

- `niro-spider/`：整个 Python 爬虫模块
- `niro-server/niro-web/src/main/java/com/niro/web/mq/`：整个 RocketMQ 包（若确认仅含 MQ 代码）
- `niro-server/niro-web/src/main/java/com/niro/web/jobhandler/`：整个 XXL-JOB 包（若确认仅含调度代码）
- `docker/rocketmq/`：整个 RocketMQ 目录
- `docker/xxl-job/`：整个 XXL-JOB 目录

**测试文件：**

- `niro-server/niro-web/src/test/java/com/niro/web/mq/RocketMQProducerTest.java`
- `niro-server/niro-web/src/test/java/com/niro/web/mq/TestMessageConsumer.java`
- `niro-client` 现有构建与类型检查命令
- `niro-server` 现有 Maven 编译命令

**职责约束：**

- 后端继续遵守 Controller-Service-Mapper 边界
- 前端视图不得直接写裸请求，继续通过 `src/api/` 调用
- 简化版允许保留兼容壳子，但不允许保留点了必报错的假入口
- 先清依赖链，再删目录，避免把仓库删成半残状态

---

### 任务 1：清理后端构建依赖与基础配置

**文件：**
- 修改：`niro-server/pom.xml`
- 修改：`niro-server/niro-web/pom.xml`
- 修改：`niro-server/niro-web/src/main/resources/config/common/common.yml`
- 测试：`niro-server/pom.xml`

- [ ] **步骤 1：编写失败的检查**

用文本断言先固定目标：父 POM、Web POM、公共配置中不应再出现 `rocketmq`、`xxl-job`、`spider` 关键配置。

```text
- niro-server/pom.xml 不再包含 xxl-job.version / rocketmq-spring-boot-starter.version
- niro-server/niro-web/pom.xml 不再包含 rocketmq-spring-boot-starter
- common.yml 不再包含 spider / xxl / rocketmq 配置段
```

- [ ] **步骤 2：运行检查验证当前失败**

运行：
```bash
git grep -nE "rocketmq|xxl-job|spider:" -- niro-server/pom.xml niro-server/niro-web/pom.xml niro-server/niro-web/src/main/resources/config/common/common.yml
```

预期：FAIL，能搜到现有依赖和配置。

- [ ] **步骤 3：编写最少实现代码**

修改：
- 从 `niro-server/pom.xml` 删除 RocketMQ、XXL-JOB 的版本属性与 dependencyManagement 条目
- 从 `niro-server/niro-web/pom.xml` 删除 RocketMQ 依赖
- 从 `common.yml` 删除 spider、xxl、rocketmq 配置段

要求：
- 不顺手改无关依赖
- 不引入新的配置体系
- 保留仍被简化版使用的 Redis / PostgreSQL / C5 配置

- [ ] **步骤 4：运行检查验证通过**

运行：
```bash
git grep -nE "rocketmq|xxl-job|spider:" -- niro-server/pom.xml niro-server/niro-web/pom.xml niro-server/niro-web/src/main/resources/config/common/common.yml
```

预期：PASS，无输出。

- [ ] **步骤 5：Commit**

```bash
git add niro-server/pom.xml niro-server/niro-web/pom.xml niro-server/niro-web/src/main/resources/config/common/common.yml
git commit -m "$(cat <<'EOF'
refactor(后端依赖): 移除简化版无用消息与调度配置

从构建与公共配置层移除 RocketMQ、XXL-JOB 和 Spider 相关依赖入口。
先把基础依赖链收干净，避免后续代码清理继续受旧配置牵制。

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### 任务 2：下线后端 MQ / 调度代码入口

**文件：**
- 删除：`niro-server/niro-core/src/main/java/com/niro/core/config/XxlJobConfig.java`
- 删除：`niro-server/niro-core/src/main/java/com/niro/core/util/MqTxSender.java`
- 删除：`niro-server/niro-core/src/main/java/com/niro/core/util/RocketMqHelper.java`
- 删除：`niro-server/niro-web/src/main/java/com/niro/web/mq/C5OrderDetailConsumer.java`
- 删除：`niro-server/niro-web/src/main/java/com/niro/web/mq/C5OrderStatusSyncConsumer.java`
- 删除：`niro-server/niro-web/src/main/java/com/niro/web/mq/DemoMessageConsumer.java`
- 删除：`niro-server/niro-web/src/main/java/com/niro/web/jobhandler/C5OrderSyncJobHandler.java`
- 删除：`niro-server/niro-web/src/main/java/com/niro/web/jobhandler/CategoryTaskJobHandler.java`
- 删除：`niro-server/niro-web/src/main/java/com/niro/web/jobhandler/TestJobHandler.java`
- 修改：`niro-server/niro-web/src/main/java/com/niro/web/scheduler/CategoryTaskMonitor.java`
- 测试：`niro-server/niro-web/src/test/java/com/niro/web/mq/RocketMQProducerTest.java`
- 测试：`niro-server/niro-web/src/test/java/com/niro/web/mq/TestMessageConsumer.java`

- [ ] **步骤 1：编写失败的检查**

先固定“源码目录中不再保留 MQ / XXL-JOB 入口类”的目标。

- [ ] **步骤 2：运行检查验证当前失败**

运行：
```bash
git grep -nE "RocketMQ|rocketmq|XxlJob|xxl-job" -- niro-server/niro-core/src/main/java niro-server/niro-web/src/main/java niro-server/niro-web/src/test/java
```

预期：FAIL，能定位到现有配置、消费者、执行器和测试。

- [ ] **步骤 3：编写最少实现代码**

处理：
- 删除纯 MQ / 纯 XXL-JOB 类和对应测试
- `CategoryTaskMonitor.java` 若只是调度壳子则删除；若有少量可保留逻辑则改为不依赖调度框架的简化实现
- 若 `mq/`、`jobhandler/` 目录清空，则直接删除整个包目录

要求：
- 不把业务逻辑硬塞进别的工具类里
- 对残留引用要同步清理 import 和装配

- [ ] **步骤 4：运行检查验证通过**

运行：
```bash
git grep -nE "RocketMQ|rocketmq|XxlJob|xxl-job" -- niro-server/niro-core/src/main/java niro-server/niro-web/src/main/java niro-server/niro-web/src/test/java
```

预期：PASS，无输出或只剩文档注释以外的零星无害文本。

- [ ] **步骤 5：Commit**

```bash
git add niro-server/niro-core/src/main/java/com/niro/core/config/XxlJobConfig.java niro-server/niro-core/src/main/java/com/niro/core/util/MqTxSender.java niro-server/niro-core/src/main/java/com/niro/core/util/RocketMqHelper.java niro-server/niro-web/src/main/java/com/niro/web niro-server/niro-web/src/test/java/com/niro/web/mq
git commit -m "$(cat <<'EOF'
refactor(后端调度): 下线 RocketMQ 与 XXL-JOB 代码入口

删除简化版不再使用的消息消费、任务执行器和辅助工具类。
把后端运行入口收敛到同步业务链路，避免旧异步框架继续占位。

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### 任务 3：收敛后端业务服务与兼容接口

**文件：**
- 修改：`niro-server/niro-web/src/main/java/com/niro/web/service/impl/C5OrderSyncServiceImpl.java`
- 修改：`niro-server/niro-web/src/main/java/com/niro/web/service/impl/BuffStickerServiceImpl.java`
- 修改：`niro-server/niro-web/src/main/java/com/niro/web/controller/LogController.java`
- 修改：`niro-server/niro-web/src/main/java/com/niro/web/dto/BuffTaskMessage.java`
- 测试：相关后端编译命令

- [ ] **步骤 1：编写失败的检查**

梳理这几个类中仍然存在的 Spider / MQ / 调度引用，并确定哪些接口保留为兼容壳子、哪些逻辑改为空结果或简化实现。

- [ ] **步骤 2：运行检查验证当前失败**

运行：
```bash
git grep -nE "spider|RocketMQ|rocketmq|Xxl|xxl|MQ|调度" -- niro-server/niro-web/src/main/java/com/niro/web/service/impl/C5OrderSyncServiceImpl.java niro-server/niro-web/src/main/java/com/niro/web/service/impl/BuffStickerServiceImpl.java niro-server/niro-web/src/main/java/com/niro/web/controller/LogController.java niro-server/niro-web/src/main/java/com/niro/web/dto/BuffTaskMessage.java
```

预期：FAIL，能看到旧依赖分支。

- [ ] **步骤 3：编写最少实现代码**

处理：
- `C5OrderSyncServiceImpl` 去掉 RocketMQ / job 触发路径，只保留同步可成立的部分；完全无法成立的能力返回明确的简化版结果
- `BuffStickerServiceImpl` 去掉 Spider 强依赖分支，只保留后端本地可完成的逻辑
- `LogController` 不再直接读 `niro-spider` 日志文件；若只剩后端日志则收口为后端日志，若无法稳定提供则返回空列表 / 简化提示
- `BuffTaskMessage` 若仅服务 MQ，则删除；若仍是接口兼容 DTO，则删减到最小字段集

要求：
- 不返回 500 占位
- 保留 Controller-Service 边界
- 对外行为要么能用，要么明确不可用

- [ ] **步骤 4：运行检查验证通过**

运行：
```bash
cd niro-server && mvn -pl niro-web -DskipTests compile
```

预期：PASS，编译通过。

- [ ] **步骤 5：Commit**

```bash
git add niro-server/niro-web/src/main/java/com/niro/web/service/impl/C5OrderSyncServiceImpl.java niro-server/niro-web/src/main/java/com/niro/web/service/impl/BuffStickerServiceImpl.java niro-server/niro-web/src/main/java/com/niro/web/controller/LogController.java niro-server/niro-web/src/main/java/com/niro/web/dto/BuffTaskMessage.java
git commit -m "$(cat <<'EOF'
refactor(后端业务): 收敛简化版异步链路依赖

把仍然绑在 Spider、MQ 和调度上的业务实现改成简化版可运行行为。
保留必要接口壳子，避免前端直接因为旧入口消失而失稳。

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### 任务 4：收前端任务与日志页面的失效组件

**文件：**
- 修改：`niro-client/src/views/TaskConfig.vue`
- 修改：`niro-client/src/views/Logs.vue`
- 修改：`niro-client/src/components/task/ScheduleConfig.vue`
- 修改：`niro-client/src/components/CronEditor.vue`
- 修改：`niro-client/src/composables/useTaskForm.ts`
- 修改：`niro-client/src/composables/useUiState.ts`
- 修改：`niro-client/src/types/task.ts`
- 修改：`niro-client/src/enums/TaskStatusEnum.ts`

- [ ] **步骤 1：编写失败的检查**

先把简化版页面目标固定：
- `TaskConfig.vue` 不再展示调度 / Spider / 异步下发相关区域
- `Logs.vue` 不再依赖 Spider 实时日志链路
- 调度组件若无剩余使用场景，则删除引用链

- [ ] **步骤 2：运行检查验证当前失败**

运行：
```bash
git grep -nE "Cron|调度|spider|实时监听|xxl|schedule" -- niro-client/src/views/TaskConfig.vue niro-client/src/views/Logs.vue niro-client/src/components/task/ScheduleConfig.vue niro-client/src/components/CronEditor.vue niro-client/src/composables/useTaskForm.ts niro-client/src/composables/useUiState.ts niro-client/src/types/task.ts niro-client/src/enums/TaskStatusEnum.ts
```

预期：FAIL，能搜到旧组件、文案、状态和类型。

- [ ] **步骤 3：编写最少实现代码**

处理：
- `TaskConfig.vue` 删除与调度、Spider、异步执行直接相关的表单块、提示和按钮，保留可工作的配置主体
- `Logs.vue` 删除 Spider 实时链路依赖，只保留后端日志可用部分；若无稳定数据源则展示简化空状态
- `ScheduleConfig.vue` / `CronEditor.vue` 若只为已删除能力服务，则删除组件与引用；若仍被其他地方使用，则改成最小保留
- 同步清理 `useTaskForm.ts`、`useUiState.ts`、`types/task.ts`、`TaskStatusEnum.ts` 中已失效字段与状态

要求：
- 不留下死按钮
- 不直接在页面写裸请求
- 页面主体还能工作时尽量保留，不机械整页删除

- [ ] **步骤 4：运行检查验证通过**

运行：
```bash
cd niro-client && pnpm type-check
```

预期：PASS，类型检查通过。

- [ ] **步骤 5：Commit**

```bash
git add niro-client/src/views/TaskConfig.vue niro-client/src/views/Logs.vue niro-client/src/components/task/ScheduleConfig.vue niro-client/src/components/CronEditor.vue niro-client/src/composables/useTaskForm.ts niro-client/src/composables/useUiState.ts niro-client/src/types/task.ts niro-client/src/enums/TaskStatusEnum.ts
git commit -m "$(cat <<'EOF'
refactor(前端页面): 移除简化版失效调度与爬虫组件

从任务配置和日志页面中删除已无法工作的调度、Spider 与异步执行区块。
保留仍有业务价值的主体页面，避免用户继续看到失效操作入口。

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### 任务 5：删除 Spider 模块与部署残留

**文件：**
- 删除：`niro-spider/`
- 删除：`docker/rocketmq/`
- 删除：`docker/xxl-job/`
- 修改：`docker-compose.test.yml`

- [ ] **步骤 1：编写失败的检查**

固定目标：仓库中不再保留 `niro-spider`、RocketMQ、XXL-JOB 的部署目录和测试编排引用。

- [ ] **步骤 2：运行检查验证当前失败**

运行：
```bash
git grep -nE "niro-spider|rocketmq|xxl-job" -- docker-compose.test.yml docker CLAUDE.md niro-server/CLAUDE.md niro-client/CLAUDE.md
```

预期：FAIL，能搜到现有部署与说明。

- [ ] **步骤 3：编写最少实现代码**

处理：
- 删除 `niro-spider/` 整个目录
- 删除 `docker/rocketmq/`、`docker/xxl-job/` 目录
- 更新 `docker-compose.test.yml`，移除对应服务和依赖

要求：
- 只删除确定不再使用的模块与部署项
- 保留其他独立 docker 目录不动

- [ ] **步骤 4：运行检查验证通过**

运行：
```bash
git grep -nE "niro-spider|rocketmq|xxl-job" -- docker-compose.test.yml docker
```

预期：PASS，仅允许无关历史归档或注释之外没有命中。

- [ ] **步骤 5：Commit**

```bash
git add docker-compose.test.yml docker niro-spider
git commit -m "$(cat <<'EOF'
refactor(部署): 删除简化版无用模块与运行编排

移除 niro-spider、RocketMQ 和 XXL-JOB 的目录与部署配置。
让仓库的运行面与简化版目标一致，避免继续暴露不会启用的基础设施。

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### 任务 6：更新项目文档并完成整体验证

**文件：**
- 修改：`CLAUDE.md`
- 修改：`niro-server/CLAUDE.md`
- 修改：`niro-client/CLAUDE.md`
- 测试：后端编译、前端类型检查、前端构建、Git 清理检查

- [ ] **步骤 1：编写失败的检查**

把文档与最终系统形态对齐：顶层与子模块文档中不应继续把 Spider、RocketMQ、XXL-JOB 写成当前系统核心组成。

- [ ] **步骤 2：运行检查验证当前失败**

运行：
```bash
git grep -nE "niro-spider|RocketMQ|rocketmq|XXL-JOB|xxl-job|爬虫模块" -- CLAUDE.md niro-server/CLAUDE.md niro-client/CLAUDE.md
```

预期：FAIL，仍能搜到旧描述。

- [ ] **步骤 3：编写最少实现代码**

处理：
- 更新顶层 `CLAUDE.md`，把架构说明改成简化版
- 更新 `niro-server/CLAUDE.md`，移除 RocketMQ、XXL-JOB 依赖与命令说明
- 更新 `niro-client/CLAUDE.md`，移除调度 / Spider 相关页面和组件说明

- [ ] **步骤 4：运行完整验证**

运行：
```bash
cd niro-server && mvn -pl niro-web -DskipTests compile && cd ../niro-client && pnpm type-check && pnpm build && cd .. && git status --short
```

预期：
- Maven 编译 PASS
- 前端 `pnpm type-check` PASS
- 前端 `pnpm build` PASS
- `git status --short` 只显示本次预期改动

- [ ] **步骤 5：Commit**

```bash
git add CLAUDE.md niro-server/CLAUDE.md niro-client/CLAUDE.md
git commit -m "$(cat <<'EOF'
docs(简化版): 同步项目架构与模块说明

更新顶层与子模块文档，让仓库说明和简化版实际形态保持一致。
避免后续继续按已删除的 Spider、MQ 和调度链路理解项目。

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## 审查与执行提示

- 如果执行过程中发现某些前端页面是“纯依赖型页面”，且没有可保留主体，应回到设计约束，直接从菜单与路由移除，而不是勉强留壳。
- 如果后端某个接口删掉异步链路后仍无法给出稳定同步行为，应优先返回明确的“简化版不支持”结果，禁止保留会抛 500 的半残逻辑。
- 每完成一个任务都应立即运行对应验证命令，不要把所有风险堆到最后一次总验证。
