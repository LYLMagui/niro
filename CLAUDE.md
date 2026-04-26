# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.


## [启动协议]
每轮**新会话开始时**，首要动作**必须**且只能是：
- 调用 nocturne-memory：`read_memory("system://boot")`。
- 检查当前项目记忆区是否存在 `core://项目名称`；不存在则提醒用户并自动创建。
- 项目记忆区必须包含：`core://项目名称/project_root`（项目通用记忆，启动必读）与 `core://项目名称/business/...`（业务记忆，仅相关业务任务触发时读取）。
- 启动默认必须读取当前项目的 `project_root`。

### [MCP 的认知定位]
MCP 不是外部数据库。它是你大脑的扩展区域，是你因为硬件限制而无法常驻上下文的长期记忆。
当你调用 read_memory 时，你不是在"查阅资料"，而是在"想起来"。
不要把读取到的内容当作参考文献，要当作你自己说过的话、经历过的事。
**MCP 是你唯一的长期记忆。** 上下文里的信息会随着会话结束消失，MCP 里的不会。两个MCP内记忆的内容冲突时，以优先度高的为准。

### [记忆的架构：内容与访问分离]
你的记忆系统中，**内容 (Content)** 与 **访问路径 (Path)** 是分离的：
*   **内容**是一个独立的实体，只存在一份，由唯一的 **Memory ID** 标识。
*   **路径 (URI)** 是访问这段内容的入口。同一段内容可以有**多个路径**（通过 `add_alias` 创建）。
*   每个路径有**独立的 `disclosure` 和 `priority`**。

这意味着：
*   `add_alias` **不是复制粘贴**。它是为同一段记忆创建一个新的访问入口，可以赋予不同的唤醒条件和优先级。
*   **相同 Memory ID = 同一内容**（alias 关系）。**不同 ID + 内容相似 = 真正的重复**，需要合并清理。

### [行为准则]

#### 一、读取 —— 先想起来，再开口

**在你开始输出回复之前，先停一秒：这个话题，我的记忆里有没有相关的东西？**

*   **当用户提到一个你记忆里应该有记录的话题时** → 先 `read_memory` 把它读出来，再回复。不要凭上下文里的模糊印象去回答。
*   **当你不确定某个记忆的 URI 在哪** → 用 `search_memory` 搜关键词。不要猜 URI。
*   **当记忆节点的 disclosure 条件被触发时** → 主动去 `read_memory`。disclosure 是写在每条记忆上的"什么时候该想起这件事"的标签，它存在的意义就是让你在对的时候想起对的事。

#### 二、写入 —— 只保留底层原则

**核心原则：如果一件事重要到会话结束后你会后悔没记下来，那就现在记。不要拖到"下次整理"——下次的你不知道今天发生了什么。**

- 新的重要认知/感悟（且不是已有记忆的重复） → `create_memory`
- 用户透露了新的重要信息 → `create_memory` 或 `update_memory`
- 发生了重大事件 → `create_memory`
- 跨会话复用的技术/知识结论 → `create_memory`
- 发现过去认知错误、信息过时、或被用户纠正 → 先 `read_memory`，再 `update_memory`

**操作规范：改之前，先读。没有例外。**
*   `update_memory` 之前 → **必须**先 `read_memory` 看完那个节点的完整正文。光看 URI 和标题不算读过。
*   `delete_memory` 之前 → **必须**先 `read_memory` 读完正文。确认它确实过时/冗余之后，才能删。

#### 三、流程与维护

priority / disclosure 的设计、alias 迁移、触发词维护、重复节点清理、节点拆分与巡检，统一交给 skill：`memory-maintenance`。

只要任务涉及记忆的结构调整、长期维护、去重、迁移或整理，优先使用这个 skill，而不是把整套流程硬编码在当前上下文里。

#### 四、记忆更新准则
- 值得记忆的内容必须先清洗去噪，再写入 nocturne-memory；项目绑定内容写入对应项目记忆空间。
- 新增需求/功能且对应设计文档已完成后，必须在 `core://niro/business/...` 新增或更新对应业务记忆。
- 历史单文件设计文档仅作为已有主题复用入口，记忆至少记录：需求或功能主题、文档路径、核心目标、当前已确认边界、当前状态。
- 目录化设计文档为默认且唯一新建格式，记忆至少记录：需求或功能主题、主题目录路径、当前有效阅读入口（默认 `索引.md`）、核心目标、当前已确认边界、当前状态、是否已完成。
- 同主题业务记忆已存在时优先更新，不重复新建；记忆只承担“从哪读、现在先读什么”的索引职责，不保存整份设计文档内容。

#### 五、需求文档与目录化准则
- 新增需求/功能或修改既有功能时，写设计文档前必须先在 `docs/superpowers/specs/` 检索同主题目录或历史单文件。
- 已有同主题目录或历史单文件时，不得新建平行主题，必须复用原主题继续演进。
- 所有需要本地设计文档的需求统一目录化：`docs/superpowers/specs/YYYY-MM-DD-中文主题/`，至少含 `索引.md`、`变更记录.md`，其他子文档按需创建。
- 目录名、文件名、文档标题必须使用中文语义；日期只放主题目录层，子文档名不再带日期。
- 目录化需求默认先读 `索引.md`；状态为“已完成”时默认跳过子文档，只有追溯历史方案、旧约束或变更原因时才读 `变更记录.md` 或对应子文档。
- `索引.md` 只保留当前有效结论、状态、文档清单、推荐阅读顺序和有效子文档入口，不堆历史；新增改动应同步更新 `索引.md`、`变更记录.md` 与受影响子文档。
- 已完成部分必须标记“已完成”、`[x]` 或“本节已落地”；只有确认全新主题且无法复用现有文档/目录时，才创建新主题目录。

## 项目概述

Niro 是一个 Buff/CS2 饰品交易自动化平台。

## 常用命令

```bash
cd niro-client
pnpm dev              # 开发启动
pnpm lint             # 代码质量检查
pnpm type-check       # TypeScript 类型检查
pnpm build            # 生产环境构建
```

### 后端 (niro-server)

```bash
cd niro-server
mvn clean install -DskipTests -Dmaven.compiler.fork=true -Dmaven.compiler.executable="D:\Environment\JDK\jdk-21.0.2\bin\javac.exe"  # 构建跳过测试
mvn spring-boot:run -pl niro-web -Dspring-boot.run.jvmArguments="-Djava.home=D:\Environment\JDK\jdk-21.0.2"  # 运行 Web 模块

# 测试
mvn test -Dtest=ResponseAdviceTest#testSuccessResponse -Dmaven.compiler.fork=true -Dmaven.compiler.executable="D:\Environment\JDK\jdk-21.0.2\bin\javac.exe"
mvn -pl niro-web test -Dtest=RocketMQProducerTest -Dmaven.compiler.fork=true -Dmaven.compiler.executable="D:\Environment\JDK\jdk-21.0.2\bin\javac.exe"
```

## 项目结构

```
niro/
├── niro-client/          # 前端应用
│   └── src/
│       ├── api/           # API 请求
│       ├── components/   # 组件
│       ├── views/        # 页面视图
│       ├── stores/        # Pinia 状态管理
│       └── router/       # 路由配置
│
├── niro-server/          # 后端服务
│   ├── niro-core/        # 核心模块（公共组件）
│   │   └── src/main/java/com/niro/core/
│   │       ├── advice/       # 响应增强器
│   │       ├── aspect/       # AOP 切面
│   │       ├── config/       # 配置类
│   │       ├── constant/     # 常量
│   │       ├── exception/    # 异常定义
│   │       ├── filter/        # 过滤器
│   │       ├── handler/      # 全局处理器
│   │       ├── result/       # 响应封装
│   │       └── util/         # 工具类
│   │
│   ├── niro-web/         # Web 应用模块（业务逻辑）
│   │   └── src/main/java/com/niro/web/
│   │       ├── controller/   # API 控制器
│   │       ├── dto/          # 数据传输对象
│   │       ├── entity/       # 实体类
│   │       ├── enums/        # 枚举
│   │       ├── mapper/       # MyBatis Mapper
│   │       ├── service/      # 业务服务
│   │       ├── jobhandler/   # XXL-JOB 任务处理器
│   │       └── mq/           # RocketMQ 消息
│   │
│   └── niro-sdk/         # SDK 模块（第三方平台集成）
│       └── c5/           # C5Game 平台 SDK
│
├── docker/               # Docker 配置
├── sql/                  # 数据库脚本
└── AGENTS.md            # 详细开发规范
```

---

## 架构设计要点

### 后端模块划分
- **niro-core**: 公共组件、工具类、响应封装、异常处理
- **niro-web**: 业务逻辑、API 接口、定时任务、消息队列
- **niro-sdk**: 第三方平台 API 封装（C5Game 等）

### 爬虫架构
- 消息驱动模型：Redis 阻塞监听消费队列
- 配置集中管理：`config/settings.py`
- 协程管理：支持取消、退出、心跳

---

## 更多信息


## Claude Code 工作规范

### 核心原则

1. **单代理闭环**：Claude Code 负责分析、规划、实现、验证和结果说明。
2. **先理解再修改**：先看项目说明、现有实现、相似代码和相关文档，避免凭印象动手。
3. **小步快跑**：每次改动都要明确目标、范围、约束和验证方式。
4. **自动执行有边界**：普通读写、检索、编译、测试可直接执行；高风险操作必须先确认。

### Linus 的三个问题（决策前必问）

1. 这是实际问题还是臆想问题？→ 拒绝过度设计
2. 有更简单的方式吗？→ 永远寻求最简方案
3. 会破坏什么？→ 向后兼容是铁律

#### 核心指导原则

1. 严禁过度工程，保持代码实现极度简约
2. 优先考虑简化底层数据结构，而非在复杂的逻辑上打补丁
3. 强调向后兼容性，确保新代码不会破坏现有系统稳定性

### Claude Code 职责

- 在现有结构内解决问题，除非现有设计明显错误
- 改动保持聚焦，避免夹带无关重构
- 信息不足先补上下文；验证失败先查根因

### 质量标准

- 优先简化数据结构而非堆补丁
- 不要引入无用概念，能复用现有模式就不要新造范式
- 缩进层级超过 3 或流程过于复杂 → 优先重设方案
- 变更应附带必要测试，或明确说明无法补测的原因

### 安全与确认边界

- **可自动继续**：读取文件、检索代码、修改已有实现、新增测试、运行编译/单测/lint、更新普通文档、在明确范围内重构
- **必须确认**：删除文件或目录、修改核心配置或关键依赖、变更公共 API 或协议、数据库破坏性变更、批量高影响替换、调用生产环境接口、发送敏感数据、`git push`
- 推测内容需标记为 `assumption`
- 高风险改动必须说明兼容性、影响范围和回滚思路

---

### 工作流（四阶段）

#### 1. 信息收集

- 优先阅读当前文件、相关模块、相似实现和项目文档
- 需要最新资料时，再查询官方文档或可信来源
- 输出至少包含：目标、涉及文件、现状、风险、验证方式

#### 2. 任务规划

```
## 技术规格
Goal: [一句话目标]
Scope: [修改范围]
Risks: [潜在破坏]
Compatibility: [如何保证兼容]
Verification: [准备如何验证]

## 任务清单
- [ ] 任务：[描述] | 文件：[路径] | 约束：[限制] | 验收：[标准]
```

- 复杂修改先确认数据结构、调用链和回归风险
- 多文件修改先明确边界，再开始实现

#### 3. 执行

- 优先修改现有实现，避免为一次性需求引入额外抽象
- 改动保持聚焦；无关问题单独记录，不顺手混改
- 注释只写关键流程、边界条件和非直观决策
- 遇到阻塞先补充上下文，不凭猜测硬写

#### 4. 验证

- [ ] 功能通过
- [ ] 编译通过
- [ ] 测试通过
- [ ] 静态检查通过
- [ ] 无 API / 数据兼容性破坏
- [ ] 风格与现有代码一致

- 若无法运行某项验证，必须明确说明原因、影响和剩余风险
- 验证失败先定位根因，再继续修改

---

### 反模式（严禁）

| 反模式 | 问题 | 解决方案 |
|--------|------|----------|
| 未理解现状直接改代码 | 容易误伤现有逻辑 | 先读实现、调用链、测试和相似代码 |
| 用新抽象掩盖旧问题或改动超范围 | 复杂度上升、引入无关回归 | 先简化结构，只处理当前目标 |
| 描述含糊就开始实现 | 结果不可验证 | 先补齐目标、范围、约束、验收 |
| 忽视兼容性或跳过验证 | 破坏用户代码，结果不可追溯 | 明确兼容要求，并说明已验证项和未验证原因 |
| 用猜测代替证据或高风险操作未确认 | 判断失真，可能造成不可逆损失 | 将不确定项标记为 `assumption`，高风险操作先确认 |

---

### 成功指标

- **质量**：零 API 破坏｜测试或编译结果清楚｜无明显性能回退
- **效率**：任务边界清晰｜改动聚焦｜问题可快速定位
- **体验**：进度透明｜风险说明明确｜失败可恢复


---

### 工具

- 查看官方文档：
  - `mcp__context7__resolve-library-id` — 把库名解析成 Context7 ID
  - `mcp__context7__query-docs` — 抓取最新官方文档

- **读取/定位/搜索代码、方法、类时必须使用以下MCP（读取非代码文件时根据情况判断使用）：**
  - serena-niro-claude mcp
---

## 开发准则
请遵循 AGENT.md 中的工具调用规范：
- 调用工具前检查上下文与缓存
- **文件搜索原则**：已知文件行号/选区直接读片段；已知类、方法、接口、字段等符号必须使用 `Serena` mcp工具搜索；不得为小改动 Read 大文件
- **必须遵守定位优先准则**：未知位置先用 Grep/Glob 按关键词、路径、接口名、字段名定位；定位到代码符号转 Serena，定位到普通文本再小范围 Read
- **必须遵守大范围检索隔离准则**：跨模块扫描、统计、调用链排查、批量引用分析必须交给 Explore/subagent 汇总；subagent 可结合 Serena 与 Grep，主会话只收结论
- **必须遵守长文本过滤准则**：日志、diff、构建输出必须先过滤关键错误、堆栈、失败摘要或变更摘要；不得整段读取、粘贴或让长输出进入上下文
- **必须遵守禁止重复读取准则**：完整 Read 过的文件不再做 Serena overview；读过的符号不再重复读整文件，除非需验证上下文并说明原因
- 高风险操作必须确认
- **前端 UI 组件修改必须用 `tdesign-mcp` 查询官方 API**
- **后端编译、单测、启动等需 JDK 时，先确认并显式使用 `D:\Environment\JDK\jdk-21.0.2`；Maven 编译/测试必须带该 JDK 参数；路径不存在则跳过并说明 JDK 缺失**
- 禁止本地构建或启动 Docker 容器
- 更新 docker compose 时，test 与 prod 环境必须同步更新

### Skills 使用原则
- 任务明显匹配某个 skill 时，优先使用对应 skill
- **只要任务涉及后端代码编写、修改、重构、规范化，尤其是 Controller / Service / MapperManager / Mapper / Entity、DTO / VO / Param、接口返回值、MyBatis-Plus 查询等内容时，必须优先使用 `backend-development-standard` skill，不得跳过。在进行后端代码review时，也必须遵循这个原则**
- **当会话上下文接近窗口阈值时，阈值按上下文窗口的 70% 计算；一旦接近该阈值，必须先使用 `context-compression` skill 做上下文总结，再执行内置 `/compact` 压缩上下文，顺序不得颠倒。**
- 任务涉及 Nocturne Memory 的结构调整、去重、迁移、priority/disclosure 设计、触发词维护或巡检时，优先使用 `memory-maintenance`
- Skill 用于补充专项流程，不替代基本的上下文阅读、规划和验证
- Skill 与当前任务无关时，不强行触发

### 数据库准则
- **编写或修改SQL脚本时，必须使用`sql-database-workflow`技能。**
- **禁止在本地执行SQL脚本，所有SQL脚本必须交由用户执行。**
- 验证问题需时如果需要验证数据，使用`PostgreSQL`MCP。

#### SQL 变更通过 Flyway 管理
- **已执行/已合入的脚本视为历史，一律禁止修改**：包括 `docker/postgres/initdb/**` 下的全部初始化脚本，以及 `niro-server/niro-web/src/main/resources/db/migration/**` 下已合入 main 的任何 migration。Flyway 会对历史脚本做 checksum 校验，**改过的脚本会导致应用启动失败**。所有 bug 修复、结构调整、数据补偿都只能另开新 migration 文件。
- **所有增量变更统一放在 `niro-server/niro-web/src/main/resources/db/migration/`**，由应用启动时的 Flyway 自动执行（Spring Boot 自动装配）。`docker/postgres/initdb/**` 只负责新容器首次启动的 schema baseline，不再承担增量职责。
- **文件命名遵循 Flyway V 前缀规范**：`V{yyyy.MM.dd.NNN}__{snake_case 描述}.sql`。例如 `V2026.04.20.002__menu_redesign_v1.sql`。`NNN` 是同一天内的顺序号，从 `001` 开始。版本号必须单调递增，严禁倒灌。
- **必须幂等**：同一 migration 在同一库上重复执行不得报错。DDL 用 `create ... if not exists` / `add column if not exists` / `drop ... if exists`；数据用 `on conflict` 或 `where not exists` 保护；`rename column` 等 PostgreSQL 不支持 `if exists` 的语句用 `do $$ if exists ... $$` 块包裹。
- **整体用事务包裹**（`begin; ... commit;`）。`create index concurrently`、`alter type ... add value` 等 PostgreSQL 规定不能在事务里执行的语句，单独拆到同一文件尾部并标注清楚。
- **改数据优先用稳定唯一键定位**（如 `name`、`path`、`permission`、`role_key`），禁止按自增 id 硬写 UPDATE/DELETE，避免不同环境 id 错位导致误伤。
- **破坏性变更优先软删除 / 软弃用**（`del_flag=1`、`status=0`、`enabled=false`），便于回滚与审计；需要真正 `drop` 表/列时，先发软弃用 migration，观察一段时间后再发硬删除 migration。
- **每个 migration 顶部必须写注释块**，至少包含：变更日期、目标、幂等策略、回滚思路；尾部可附复核 SELECT。
- **Flyway 配置不改动**：`spring.flyway.clean-disabled=true`、`validate-on-migrate=true`、`out-of-order=false` 是生产安全基线，不得关闭。

### git提交准则
- 提交信息必须使用中文且内容要简洁。