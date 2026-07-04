---
description: 审查改动代码是否符合项目开发规范（格式、风格、Javadoc、常量抽离、分层等），只产出报告不改动代码
argument-hint: [scope]
---

# 代码规范审查

对当前改动的代码做一次**只读**的规范审查，输出可执行的问题清单。

## 严格边界

- **只审查规范，不审查业务逻辑**：是否实现正确、是否完成需求一律不评。
- **只输出报告，不修改任何文件**：禁止调用 `Edit` / `Write` / `replace_symbol_body` / `insert_*_symbol` 等任何写工具。主会话与所有 reviewer agent 同此约束。
- **禁止运行编译、构建、测试、启动命令**。
- 报告全部使用**中文**；不使用 emoji。
- Team 模式下不得复用 `backend-implementer` / `frontend-implementer` / `database-implementer` 这类带写权限的项目自定义 agent；只能用 `general-purpose` 或 `Explore`。

## 参数解析

`$ARGUMENTS` 决定审查范围，按以下规则解析：

- **为空** → 审查当前工作区所有未提交改动（`git diff HEAD` + 已 `git add` 的内容 + 新增未追踪文件中可审查的代码文件）。
- **`staged`** → 仅审查 `git diff --cached` 的改动。
- **匹配 commit hash 形如 `[0-9a-f]{6,40}`** → 审查 `git show <hash>` 范围。
- **包含 `..`（如 `main..HEAD` / `origin/test..HEAD`）** → 审查 `git diff <range>` 范围。
- **匹配现有文件或 glob（如 `niro-server/.../Foo.java`、`niro-client/src/**/*.ts`）** → 审查这些文件的当前内容。
- **解析不出来** → 直接报错让用户重新给参数，不要猜测。

## 执行步骤

### 第 1 步：建立改动清单

1. 用 Bash 跑 `git status --short` 与对应的 `git diff`（按上面参数解析的范围）拿到改动文件清单和 hunk。
2. **长 diff 不要整段拉进上下文**：超过 ~400 行 diff 时，记录文件名与改动行号区间，进入第 3 步时再按需要用 Serena 局部读取。
3. 按文件类型分桶，至少识别：
   - 后端 Java（`niro-server/**/*.java`）
   - 前端 Vue / TS（`niro-client/**/*.{vue,ts,tsx}`）
   - SQL 迁移（`niro-server/niro-web/src/main/resources/db/migration/**/*.sql`、`docker/postgres/initdb/**/*.sql`）
   - Docker / 部署（`docker-compose*.yml`、`docker/**`）
   - 文档（`docs/**`、`*.md`）
   - 其他配置（`*.yml` / `*.yaml` / `*.properties` / `*.json`）
4. 文件清单为空时直接报"无改动可审查"并结束。

### 第 2 步：加载规范上下文

只在还没读过的前提下读一次，避免重复：

- `PROJECT_RULES.md`（总则）
- `CODING_RULES.md`（skill 触发与决策表）
- 后端必读项目根记忆：`core://niro/project_root/backend_layering_rule`、`backend_return_and_query_rule`、`backend_jdk_execution_rule`、`backend_skill_required`、`frontend_validation_rule`、`remote_config_local_first`
- `MEMORY.md` 中已索引的相关条目（如错误提示硬编码、C5 常量引用、请求参数名不抽常量等例外规则）

把这些规则当作判定基准，**不要凭训练数据里的通用最佳实践代替项目规则**。

### 第 3 步：分桶执行（轻量直审 / Team 并发审查）

#### 3.1 模式判定

- **改动文件总数 ≤ 3** 且**仅命中 1 个桶** → 走「轻量直审」：主会话顺序处理，不建团队。
- **改动文件总数 ≥ 4** 或 **命中桶种类 ≥ 2** → 走「Team 并发审查」。

#### 3.2 轻量直审

按第 4 步逐文件核对，直接出第 5 步的报告结构。

#### 3.3 Team 并发审查

**3.3.1 桶到 reviewer 的映射**（任一桶无文件则跳过该 reviewer，不要创建空任务）：

| 桶 | reviewer 名 | agent 类型 | 说明 |
|---|---|---|---|
| 后端 Java | `java-reviewer` | `general-purpose` | 主桶，要跨文件查引用 / 常量定义 |
| 前端 Vue/TS | `frontend-reviewer` | `general-purpose` | 主桶，要查 hook / 组件复用 |
| SQL 迁移 | `sql-reviewer` | `Explore` | 文件少、规则集中，只读窗口够用 |
| Docker / 部署 | `infra-reviewer` | `Explore` | 同上 |
| 文档 / 配置 | `doc-reviewer` | `Explore` | 同上 |

**3.3.2 建团与建任务**

1. `TeamCreate` 创建团队，名称用 `code-style-review-<yyyyMMddHHmm>`，description 写「代码规范审查：<参数原文>」。
2. 对每个非空桶 `TaskCreate` 一条任务，内容包含：
   - 桶名 + 文件清单（含行号区间）
   - 该桶对应的第 4 步审查维度小节标题
   - 严格边界（只读、不写、中文、引规则出处）
   - 必须按第 5 步报告结构子集返回（不写"未评审范围"那段，由主会话统一收口）
3. 任务暂不指定 owner，等 reviewer 加入后由 reviewer 自行 claim，或由 team-lead 用 `TaskUpdate(owner=...)` 指派。

**3.3.3 Spawn reviewer**

用 `Agent` 工具 spawn 时必须带 `team_name` 与 `name` 参数，prompt 至少覆盖：

- 身份与任务编号
- 严格边界（不要 Edit/Write/replace_symbol_body/insert_*_symbol/Bash 写入；不要执行 mvn/pnpm/docker/git commit；不要做业务逻辑评审）
- 引用项目规则的硬性要求（必读 `PROJECT_RULES.md` 与对应桶的 skill / 记忆条目）
- 完成后用 `SendMessage` 把结构化结论发给 `team-lead`，并 `TaskUpdate` 标记 completed
- 不要用 JSON 状态消息（如 `{"type":"task_completed"}`）；纯文本汇报

主桶 reviewer（Java / 前端）用 `general-purpose`；辅助桶（SQL / Docker / 文档）用 `Explore`。后者读取窗口受限，分配时务必把文件列表写全。

**3.3.4 协同与收口**

1. 主会话不要主动 `SendMessage` 催进度；reviewer 完成会自动投递消息。
2. 收到 reviewer 的结论消息后，主会话只做合并和分级重排，不要重写 reviewer 的判定。
3. 出现冲突结论时（同一文件不同 reviewer 给不同结论），按"最严结论保留 + 在报告底部列入待澄清项"处理。
4. 全部 reviewer 完成后：
   - 主会话用 `SendMessage` 发 `{"type":"shutdown_request","reason":"review done"}` 给每个 reviewer
   - 收到 `shutdown_response` 后调用 `TeamDelete` 清理团队与任务列表
5. 任何 reviewer 报错或长时间未响应：在最终报告里列出"未完成桶"，不要静默吞掉。

### 第 4 步：按文件类型逐项核对

#### 4.1 后端 Java（`niro-server`）

依次核对以下点，命中即记一条：

- **分层**：是否遵守 `Controller -> Service -> MapperManager -> Mapper -> Entity`；Controller 是否塞了业务判断；Service 是否绕过 MapperManager 直接用 Mapper。
- **返回值**：Controller 方法是否还在外层包 `Result`（应直接返回 `DTO / VO / List / Page / void`）。
- **DTO 纯净度**：DTO 是否携带 `@TableName / @TableField / @TableId` 等持久化注解（不应携带）。
- **MyBatis 查询**：是否优先使用 `lambdaQuery`；是否出现字符串字段名或把 `QueryWrapper` 当默认写法。
- **命名**：类 `PascalCase`、方法/变量 `camelCase`、常量 `UPPER_SNAKE_CASE`；常量字段必须有注释。
- **常量抽离**：
  - 应抽离：重复出现的魔法值、URL/路径、Redis key 前缀、定时任务 cron、业务枚举值
  - **不要抽离**（项目例外）：
    - 错误/提示文案保持硬编码
    - C5 SDK request query/body 参数名直接内联
    - 通用请求参数名不抽常量类
  - C5 相关常量必须用 `C5SnipingTaskV2Constants.xxx` 等显式类名引用，不允许 `static import`
- **Javadoc**：`public` 类与对外方法是否缺 Javadoc；已有 Javadoc 的 `@param` / `@return` / `@throws` 是否完整且与签名一致。
- **import**：未使用的 import、通配符 `import xxx.*`、IDE 自动加入但不再引用的依赖。
- **异常处理**：是否吞异常（空 catch / 只 `log.error` 不抛/不返回错误）；是否用 `null`/默认值静默兜底；只在真实边界做校验。
- **日志**：是否漏脱敏（参考 `LogSanitizer` / `LogSanitizeConstant`）。
- **格式**：缩进、空行、行尾空格、过长行、import 顺序。

#### 4.2 前端 Vue / TS（`niro-client`）

- **命名**：组件文件 `PascalCase.vue` 或目录 `kebab-case`、变量/方法 `camelCase`、类型/接口 `PascalCase`。
- **类型**：是否出现不必要的 `any`；接口/响应类型是否声明完整；`ref` / `reactive` 泛型是否明确。
- **import 整洁**：未使用 import、相对路径过深（应优先 `@/` 别名）。
- **TDesign 用法**：是否按官方 API 调用（涉及不确定用法时在报告里建议用 `tdesign-mcp` 复核）。
- **样式**：是否未提取重复样式；移动端容器是否遵守"主内容铺满"基线（无外层留白和圆角收口）。
- **常量抽离**：枚举/字典/路由 path/接口 path 是否散落在多处。
- **格式**：与 `eslint` / `prettier` 风格一致；模板缩进、属性换行。

#### 4.3 SQL / Flyway 迁移

- **文件位置**：增量必须在 `niro-server/niro-web/src/main/resources/db/migration/`；禁止改 `docker/postgres/initdb/**` 和已合入主线的旧 migration。
- **命名**：`V{yyyy.MM.dd.NNN}__snake_case_描述.sql`，同日序号从 `001` 起。
- **顶部注释块**：是否包含「变更日期 / 目标 / 幂等策略 / 回滚思路」。
- **幂等**：DDL 是否用 `if exists / if not exists`；数据变更是否用 `on conflict` 或 `where not exists`。
- **事务**：默认整体事务；不能放事务的语句是否单独处理并注明。
- **定位**：UPDATE/DELETE 是否用稳定唯一键，不允许按自增 `id` 硬写。
- **破坏性变更**：是否优先软删除/软弃用；硬删除是否分阶段。

#### 4.4 Docker / 部署配置

- 是否同步评估了 `docker-compose.yml` 与 `docker-compose.test.yml`。
- 是否在本地仓库修改而非直接改远程文件（`.env` 是允许的例外）。

#### 4.5 文档

- 新增需求文档是否落在 `docs/superpowers/specs/YYYY-MM-DD-中文主题/` 且包含 `索引.md`、`变更记录.md`。
- 标题、目录、文件名是否使用中文语义；子文档名不带日期。

### 第 5 步：输出报告

固定结构，按严重级倒序：

```text
# 代码规范审查报告

审查范围：<解析后的 git 范围或文件清单>
改动文件数：<N>，命中问题数：<M>（阻断 a / 重要 b / 建议 c / 提示 d）

## [阻断] 必须修复才能合并
1. <相对路径>:<行号>
   - 规则：<规则来源，如 PROJECT_RULES § 数据库准则 / project_root/backend_return_and_query_rule>
   - 问题：<一句话说清违反点>
   - 建议：<具体怎么改，给出代码片段或字段名即可，不要展开实现>

## [重要] 强烈建议在本次改动一并修复
...

## [建议] 可放到下一次清理一并处理
...

## [提示] 仅供参考
...

## 未评审范围
- 业务逻辑正确性：未评审
- 性能与并发：未评审
- 安全性：未评审（仅看到表层日志脱敏命中，不代表完整审计）
- 自动化运行结果：未运行 build / lint / test
```

严重级判定参考：

- **阻断**：违反项目硬性规则（分层、Controller 返回值、Flyway 文件位置、改历史迁移、远程配置直改、SQL 在本地执行）。
- **重要**：缺 Javadoc / 吞异常 / 魔法值未抽（且不在例外清单） / 命名严重不符 / 缺迁移注释块。
- **建议**：import 整洁、格式细节、重复表达式、可读性优化。
- **提示**：风格偏好、可选优化、对照项目历史模式更顺手的写法。

### 第 6 步：结束

- 报告输出后停止；不要追加"我可以帮你修复"之类的承诺，让用户自行决定是否要新开任务修复。
- 如果发现规则之间存在冲突或项目规则未覆盖的灰色场景，单独在报告底部列出"待澄清项"，请用户确认后再决定是否纳入硬性标准。

## 工具使用约束

- 改动定位优先 `git diff` + `git status`；读单个符号上下文优先 Serena (`find_symbol` / `get_symbols_overview`)，不要整文件 Read。
- 跨文件统计、引用扫描在 Team 模式下交由对应 reviewer 在自己的桶里完成；轻量直审模式下可委托给 Explore / general-purpose agent，主会话只收结论。
- 整个流程不调用任何写工具，不启动进程，不执行 mvn / pnpm / docker 命令。

## Team 工具使用约束

- 仅在第 3.3 节判定为 Team 模式时才使用 `TeamCreate` / `SendMessage` / `TeamDelete`。
- `Agent` spawn reviewer 必须显式带 `team_name` 和 `name`，缺一不可，否则 reviewer 不会加入团队，消息无法路由。
- reviewer 之间互不通信，只对 `team-lead` 汇报；主会话也不要让 reviewer 直接互发 DM。
- `SendMessage` 一律用纯文本 + `summary`；除了 `shutdown_request` / `shutdown_response` / `plan_approval_response` 三类协议消息外，不要构造其它 `{"type":...}` JSON。
- `TaskUpdate` 是 reviewer 标记完成的唯一手段，不要靠纯消息当"完成信号"。
- 审查结束必须 `TeamDelete` 清理；如有 reviewer 卡住未关闭，先记入报告"未完成桶"，再人工决定是否强删。
