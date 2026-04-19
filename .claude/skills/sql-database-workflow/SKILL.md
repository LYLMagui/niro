---
name: sql-database-workflow
description: 当用户要求设计数据库、修改表结构、编写 SQL 迁移脚本、补充字段注释，或需要在 PostgreSQL / MySQL 之间按项目实际方言输出数据库语法时使用。本技能先识别当前项目使用的数据库，再读取对应 reference 规范并按该方言执行。
---

# SQL Database Workflow

## Overview

这是一个“数据库设计与数据库变更工作流”的通用 skill。

它不把 PostgreSQL 或 MySQL 的语法硬编码成默认真理，而是先判断当前项目到底在用哪种数据库，再选择对应的 reference 规范输出 SQL。

它的职责是：

1. 识别当前项目使用的是 PostgreSQL 还是 MySQL。
2. 读取 `references` 目录中的数据库专用规范。
3. 在建表、改表、索引、约束、迁移脚本场景下，按目标数据库方言输出 SQL。
4. 强制执行数据库注释规则，确保字段与表都有注释。
5. 在变更现有表前，先检查兼容性与锁影响，而不是直接给出高风险 DDL。

## When to use this skill

在以下场景使用本技能：

- 用户要求设计新表、新字段、新索引、新约束
- 用户要求修改已有表结构
- 用户要求编写数据库迁移脚本
- 用户要求补齐表注释、字段注释
- 用户要求输出 PostgreSQL 或 MySQL 的 SQL 方言语句
- 你需要先判断当前项目使用哪种数据库，再决定 SQL 写法

以下场景不必强行使用本技能：

- 纯 ORM 实体代码修改，但不涉及数据库结构变化
- 纯查询 SQL 调优讨论，且不涉及表结构设计或变更工作流
- 只做数据库概念解释，不需要落成具体 SQL

## Rules

- 先识别数据库，再写 SQL；不要凭印象默认某个方言。
- skill 主体只保留通用流程，数据库专有语法放到 `references`。
- 设计表结构或修改字段时，必须同时考虑注释、索引、约束和兼容性。
- 所有新表和新字段都必须补齐注释；不允许只建结构不写注释。
- 输出 SQL 时必须遵循目标数据库方言，不混用 PostgreSQL / MySQL 语法。
- 对 PostgreSQL 输出示例时，优先复用当前项目现有 SQL 基线，不要拿通用偏好覆盖项目现实；尤其是默认值写法，以及 `created_at` / `updated_at` 这类通用字段的类型与默认值。
- PostgreSQL reference 中禁止使用外键；关系约束改由业务约束、唯一约束、检查约束和关联字段索引表达。
- PostgreSQL reference 中字段默认要带默认值；除非 PostgreSQL 官方明确说明该类型或场景不允许设置默认值。
- PostgreSQL reference 中通用时间类字段必须使用无时区类型。
- 修改已有表前，优先评估默认值、非空约束、索引创建方式、数据回填和锁影响。
- 如果无法从项目中可靠识别数据库类型，先问用户，不要猜。

## Instructions

### Step 1: Classify the database task
先判断本次任务属于哪类：

- 新建表
- 给已有表新增字段
- 修改字段类型 / 默认值 / 非空约束
- 新增或调整索引
- 新增或调整约束
- 编写迁移脚本
- 补注释或规范化现有 SQL

先明确目标表、影响范围、兼容性要求和执行环境，再开始输出 SQL。

### Step 2: Detect the database used by the current project
按以下顺序识别数据库类型：

1. 读取项目配置文件，例如 `application*.yml`、`application*.properties`、`database.yml`
2. 读取依赖文件，例如 `pom.xml`、`build.gradle`、`package.json`
3. 读取容器或部署文件，例如 `docker-compose*.yml`、Dockerfile、部署脚本
4. 查看现有迁移脚本命名、数据库驱动、JDBC URL、镜像名、方言配置

识别依据示例：

- `jdbc:postgresql://...`、`org.postgresql.Driver`、`postgres:` 镜像 → PostgreSQL
- `jdbc:mysql://...`、`com.mysql.cj.jdbc.Driver`、`mysql:` 镜像 → MySQL

如果多个信号冲突，优先以实际连接配置和运行配置为准；仍不确定时先向用户确认。

### Step 3: Load the matching database reference
根据识别结果选择 reference：

- PostgreSQL → `references/postgresql-sql-standard.md`
- MySQL → `references/mysql-sql-standard.md`

处理规则：

1. 如果 reference 已存在，先读取它，再输出 SQL。
2. 如果 reference 不存在，不要凭空混写两种方言；先基于当前项目补对应 reference，再继续。
3. 任何数据库专有语法、避坑点、注释写法、迁移习惯，都应该沉淀在对应 reference 中。
4. 如果 PostgreSQL reference 已经存在项目内的现成建表 SQL 基线，优先沿用这些真实写法，例如 `timestamp not null default now()`、`text not null default ''`、`numeric(4,2) not null default 0.00`。

### Step 4: Design the schema change before writing SQL
在开始写 SQL 前，至少确认：

- 这是增量变更还是全新建表
- 现有数据是否需要回填
- 新字段是否真的需要 `not null`
- 默认值是否会触发高成本表重写
- 唯一约束、检查约束、关联字段索引是否需要同步设计
- 迁移是否需要分步骤执行以降低锁风险

目标是先把变更路径想清楚，再写 SQL，而不是用 DDL 硬顶生产数据。

### Step 5: Enforce comment rules
这是通用硬规则：

- 新建表时，必须写表注释
- 新建字段时，必须写字段注释
- 给已有表新增字段时，必须把字段注释纳入同一次变更
- 输出示例 SQL 时，也必须带上注释写法，不要只给裸结构

不同数据库的注释语法以对应 reference 为准：

- MySQL 常见为列定义中的 `comment '...'`
- PostgreSQL 常见为 `comment on table ... is '...'` 与 `comment on column ... is '...'`

### Step 6: Output SQL in the project's dialect
输出 SQL 时：

- 只使用目标数据库支持的语法
- 命名、类型、默认值、索引、约束、注释都要与目标方言一致
- 若任务是迁移脚本，优先给出可执行顺序，而不是只给最终表结构
- 若存在高风险步骤，明确拆成多步 SQL

### Step 7: Verify compatibility and execution safety
交付前至少检查：

- 是否误用了另一种数据库的语法
- 表和字段是否都有注释
- 是否遗漏关键业务索引、唯一约束或检查约束
- 是否存在高风险整表重写、长事务或阻塞写入风险
- SQL 是否适合当前任务场景：建表、迁移、修复、补注释

如果无法确认执行风险，要明确标出假设条件与剩余风险。

## Examples

### Example 1: Detect PostgreSQL and design a new table
用户说：

> 帮我给这个项目设计一张订单表。

你应该：

1. 先查当前项目配置和依赖，确认是不是 PostgreSQL。
2. 读取 `references/postgresql-sql-standard.md`。
3. 按 PostgreSQL 语法输出 `create table`、索引、`comment on table`、`comment on column`，并优先贴合项目现有 SQL 基线中的默认值与通用字段类型。
4. 不使用外键，改用唯一约束、检查约束和关联字段索引表达关系约束。

### Example 2: Add a new column to an existing table
用户说：

> 给用户表加一个状态字段，要非空，默认正常。

你应该：

1. 先判断数据库类型。
2. 识别这是“已有表增量变更”，不是全量重建。
3. 评估是否需要分步：加列 → 回填 → 加 `not null` / 默认值。
4. 同时补字段注释。

### Example 3: Avoid dialect mixing
用户说：

> 给我一段 SQL，兼容 MySQL 和 PostgreSQL。

你应该：

1. 先说明 DDL 通常不能直接一套语句兼容两种数据库。
2. 先识别当前项目实际使用的数据库。
3. 按项目实际方言输出一版标准 SQL；若用户明确需要双版本，再分别输出 PostgreSQL / MySQL 两版。

## Best practices

1. skill 主体只管流程，不把数据库方言细节硬塞进主体。
2. 先判库，再落 SQL，不要凭经验默认 MySQL 或 PostgreSQL。
3. 建表与改表都必须把注释当成强制项，不是可选项。
4. 结构变更优先考虑兼容性和执行风险，不直接给高风险一步到位 SQL。
5. 同一个 skill 可以兼容多数据库，但每次输出必须只落一个确定方言。
6. reference 文件要沉淀数据库专有规则，后续新增数据库支持时只补 reference，不重写主流程。

## References

- `references/postgresql-sql-standard.md`
- `references/mysql-sql-standard.md`（未来扩展；识别为 MySQL 时应先读取或补齐该文件）
- `D:\MySpace\niro\.claude\skills\postgresql-table-design\SKILL.md`
- `D:\MySpace\niro\docs\sql\开箱记录表设计.sql`
- 当前项目中的数据库配置、依赖与迁移脚本
