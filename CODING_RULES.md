# Agent 编码规则

## Skill 使用规范

本规范用于约束 Agent 在 Niro 项目中的 skill 选择、调用顺序和执行边界。

### 一、总则

- 满足触发条件时，相关 skill 必须使用，不能跳过。
- 编码前必须先判断任务类型，再决定进入哪个 skill。
- 多个 skill 同时命中时，必须串行执行，禁止并行混用和跳步。
- 所有 skill 输出都必须继续服从 `PROJECT_RULES.md`、`CLAUDE.md`、目标模块既有实现和当前稳定代码事实。
- 若 skill 规则与项目现状冲突，以更具体的项目规则和当前稳定实现为准。

### 二、brainstorming 使用规则

#### 1. 必用场景

以下任务必须先使用 `brainstorming`：

- 新增功能
- 修改现有行为
- 调整接口语义
- 重构业务链路
- 新增数据库结构或迁移方案
- 任何需要先明确目标、边界、方案、风险、验证方式的实现任务

#### 2. 强制要求

- 未完成方案展示并得到用户确认前，禁止开始实现。
- 任务看起来再小，也不能跳过设计确认。
- 小型 bug 修复可以不落本地设计文档，但仍必须先完成对话内轻量确认。
- 非小型 bug 修复或新增需求，必须先完成设计沉淀，再进入实现。

#### 3. 小型 bug 修复判定

同时满足以下条件时，才可按小型 bug 修复处理：

- 改动收敛在单一模块、单一调用链或 1 到 2 个文件
- 不涉及数据库结构、公共 API、权限模型、协议或系统基础边界调整
- 可以用简短方案说明清楚修复边界、风险和验证方式

只要不能明确满足以上条件，就按正式设计流程处理。

### 三、sql-database-workflow 使用规则

#### 1. 必用场景

以下任务必须使用 `sql-database-workflow`：

- 新建表、字段、索引、约束
- 修改表结构、字段类型、默认值、非空约束
- 编写迁移脚本
- 补表注释、字段注释
- 输出 PostgreSQL 或 MySQL 方言 SQL

#### 2. 强制要求

- 必须先识别项目实际使用的数据库，再写 SQL。
- 禁止凭经验默认 MySQL 或 PostgreSQL。
- 必须先读取对应 reference，再输出 SQL。
- 所有新表和新字段必须补齐注释。
- 修改已有表前，必须先评估兼容性、数据回填、索引策略、锁影响和执行顺序。
- 一次输出只能落一个确定方言，禁止混写 PostgreSQL 和 MySQL 语法。
- 无法可靠识别数据库类型时，必须先问用户，禁止猜测。

#### 3. 与其他技能的关系

- 数据库变更属于更大功能的一部分时，先走 `brainstorming`，再进入 `sql-database-workflow`。
- 纯数据库设计或 SQL 变更任务，也不能跳过必要的设计确认。

### 四、backend-development-standard 使用规则

#### 1. 必用场景

以下任务必须使用 `backend-development-standard`：

- 新增或修改 Controller、Service、MapperManager、Mapper、Entity 链路
- 新增或调整 DTO、VO、Param
- 修改后端分层边界、调用链路、持久层访问方式
- 修改接口返回值约定或 MyBatis 查询写法
- 审查或规范化 Niro 后端实现

#### 2. 强制要求

- 必须先查看目标文件和同模块相似实现，再开始修改。
- 修改前必须先还原调用链，禁止孤立改单点。
- 必须按 Niro 既有分层放置逻辑：`Controller -> Service -> MapperManager -> Mapper -> Entity`。
- 禁止把业务判断塞进 Controller。
- 禁止让 Service 直接访问 Mapper 或越过 MapperManager 访问数据库。
- Controller 方法直接返回 `DTO / VO / List / Page / void`，不额外包一层 `Result`。
- MyBatis 查询默认优先使用 `lambdaQuery` 链式表达式，DTO 不应携带持久化注解。
- 改动必须以最小闭环为目标，禁止无关重构和提前铺大框架。
- 当前模块已有稳定写法时，必须优先沿用模块内模式。

### 五、组合顺序规则

#### 1. 新增后端功能

必须按以下顺序执行：

1. `brainstorming`
2. `backend-development-standard`
3. 如涉及表结构或迁移，再进入 `sql-database-workflow`

#### 2. 后端功能同时涉及数据库变更

必须按以下顺序执行：

1. `brainstorming`
2. `sql-database-workflow`
3. `backend-development-standard`

额外要求：

- 禁止只改 Java 代码而跳过数据库变更设计。
- 禁止只给 SQL 而不检查后端链路是否需要同步调整。

#### 3. 小型后端 bug 修复

必须按以下顺序执行：

1. `brainstorming` 轻量确认
2. `backend-development-standard`
3. 不涉及数据库结构时，不进入 `sql-database-workflow`

#### 4. 纯数据库任务

必须按以下顺序执行：

1. 必要的设计确认
2. `sql-database-workflow`
3. 仅当数据库结构变化会影响后端接口、对象或链路时，再进入 `backend-development-standard`

### 六、禁止事项

- 禁止跳过 `brainstorming` 直接实现创造性任务。
- 禁止未识别数据库类型就直接输出 SQL。
- 禁止脱离 Niro 既有分层习惯随意设计后端结构。
- 禁止把局部 skill 规则误当成整个项目的通用事实。
- 禁止把设计确认、数据库设计、后端实现混成一次无边界编码。
- 禁止在未确认兼容性影响时擅自修改历史接口、公共字段结构或高风险 DDL。

### 七、编码前检查

进入编码前，必须自检以下问题：

- 是否已经判断任务类型？
- 是否已经进入正确的 skill？
- 是否已经按正确顺序执行相关 skill？
- 是否已经向用户明确边界、风险和验证方式？
- 是否遵守当前模块既有实现、分层边界和兼容性约束？

### 八、简化决策表

| 任务类型 | 必用技能 | 顺序要求 |
| --- | --- | --- |
| 新增功能 | brainstorming | 先设计确认，再实现 |
| 小型 bug 修复 | brainstorming、backend-development-standard | 先轻量确认，再编码 |
| 后端接口/链路调整 | brainstorming、backend-development-standard | 先确认方案，再按 Niro 分层落地 |
| 表结构/迁移脚本 | brainstorming、sql-database-workflow | 先确认方案，再按实际数据库方言输出 |
| 后端功能 + 数据库变更 | brainstorming、sql-database-workflow、backend-development-standard | 先设计，后定 SQL，再落后端链路 |
