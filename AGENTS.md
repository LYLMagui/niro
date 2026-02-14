# Niro AGENTS.md

适用范围：`D:/MySpace/niro` 全仓库（client/server/spider/docs）。

目标：给所有 agent 一个可执行、可验证、可落地的统一工作基线。

## 1) Rule Priority

冲突时按优先级执行：
1. 用户明确指令
2. 本文件（根 AGENTS.md）
3. 子模块规则（尤其 `niro-server/AGENTS.md`）
4. 模块内工具配置（tsconfig/prettier/eslint/maven 等）
5. 通用工程最佳实践

## 2) Module Map + Boundaries

- `niro-client`: Vue3 + TS + Pinia + TDesign + Tailwind + Vite
- `niro-server`: Spring Boot 3.5 + Java 21 + MyBatis-Plus + Redis + PostgreSQL
- `niro-spider`: Python asyncio + Redis + httpx (消息驱动消费)
- `docs`: 文档与资产

关键链路不可破坏：前端任务配置 -> 后端持久化/调度 -> Redis 队列 -> 爬虫消费执行 -> 状态/日志回流。

## 3) Build / Lint / Test Commands

### 3.1 niro-client

```bash
cd niro-client
pnpm dev
pnpm lint
pnpm type-check
pnpm build
```

说明：
- 脚本来源：`niro-client/package.json`
- 当前未定义标准 `test` 脚本（无 `pnpm test`）

### 3.2 niro-server

```bash
cd niro-server
mvn clean install
mvn clean install -DskipTests
mvn spring-boot:run -pl niro-web
```

单测（重点，单文件/单方法）：

```bash
cd niro-server
mvn test -Dtest=ResponseAdviceTest
mvn test -Dtest=ResponseAdviceTest#testSuccessResponse
```

模块化运行（可选）：

```bash
cd niro-server
mvn -pl niro-web test -Dtest=RocketMQProducerTest
mvn -pl niro-sdk test -Dtest=C5MarketClientTest
```

### 3.3 niro-spider

```bash
cd niro-spider
python main.py
```

仓库内存在 `tests/test_*.py`，可按 Python 约定执行单测：

```bash
cd niro-spider
pytest -q tests/test_c5_response.py
pytest -q tests/test_c5_response.py::test_c5_response
```

说明：`requirements.txt` 未显式声明 pytest，若缺失需先安装。

## 4) Cross-Language Coding Rules

1. 最小改动：只改与需求直接相关代码，禁止顺手重构。
2. 严禁吞异常：禁止空 `catch`、禁止伪成功返回、禁止静默失败。
3. 禁止逃避式写法：禁止 `any`、`@ts-ignore`、`@ts-expect-error`。
4. 禁止泄露敏感信息：Cookie/Token/密钥/密码不得写日志或回传。
5. 常量优先：避免魔法值，优先复用既有 enum/constant。
6. 可追踪性：关键日志保留 `taskId/userId/traceId` 上下文。

## 5) Frontend Conventions (niro-client)

- 使用 Composition API + `<script setup lang="ts">`。
- 请求必须经 `src/api/*`，禁止在视图层直接写裸 axios。
- 路径优先使用 `@/` 与 `@component/` 别名（见 `tsconfig.json`）。
- TypeScript `strict: true`，且启用 `noUnusedLocals/noUnusedParameters`。
- 格式化遵循 Prettier 配置（2 空格、`semi: true`、`singleQuote: false`、`trailingComma: es5`）。
- 样式优先 TDesign + Tailwind，表单必须具备校验、loading、错误反馈。
- 与任务相关页面必须准确展示状态（运行中/停止/失败/执行中）。

## 6) Backend Conventions (niro-server)

以 `niro-server/AGENTS.md` 为准；此处仅列强约束摘要：

1. 仅允许构造注入：`@RequiredArgsConstructor + final`，禁用字段注入。
2. Controller 只做入参/鉴权/转发，不承载复杂业务。
3. 查询统一 MyBatis-Plus Lambda 风格，禁用 `QueryWrapper`。
4. 业务断言统一 `Assert.*`，禁用 `if (...) throw ...` 分散写法。
5. Controller 不手动包 `Result`；响应由 `ResponseAdvice` 统一处理。
6. 删除语义优先逻辑删除；删除后断言影响行数 `> 0`。
7. 业务层不捕获泛型 `Exception` 进行吞并，交给全局异常机制。
8. 涉及余额/下单/库存扣减必须加分布式锁（`RedisUtil`）。

命名规范（Java）：包名小写；类名 PascalCase；方法/变量 camelCase；常量 UPPER_SNAKE_CASE。

## 7) Spider Conventions (niro-spider)

- 必须保持消息驱动（Redis 阻塞监听）模型，不回退 DB 轮询。
- `config/settings.py` 是配置入口，禁止在各处散落读取环境变量。
- 关键协程必须考虑取消、退出、心跳与失败日志可观测性。
- 外部请求失败不可静默；重试必须有上限。
- 代理/Cookie/风控相关改动要求兼容旧路径。

## 8) API / Contract Rules

1. 前端类型、后端 DTO、爬虫 DTO 的字段语义保持一致。
2. 新增字段优先向后兼容；删除/改名必须提供迁移方案。
3. 时间语义统一 `Asia/Shanghai`。
4. 明确区分 `null`、空字符串、`0`、未传字段。

## 9) Cursor / Copilot Rules

已检查以下位置：
- `.cursor/rules/`
- `.cursorrules`
- `.github/copilot-instructions.md`

当前仓库未发现上述文件。若后续新增，请将其规则纳入本文件优先级体系。

## 10) Agent Workflow (Required)

1. 先定位模块边界，再给最小改动方案。
2. 先读配置和同类文件，再动代码。
3. 改动后按影响范围执行校验命令（至少 lint/type-check/test 的最小必要集）。
4. 输出必须包含：改动文件、原因、验证结果、潜在风险。

## 11) Definition of Done

- 需求闭环完成且不破坏前后端-爬虫链路。
- 通过模块最低质量门禁：
  - client: `pnpm lint && pnpm type-check && pnpm build`
  - server: 至少目标模块可编译/可测（优先单测）
  - spider: 可运行主流程，关键测试可复现
- 无明显并发/一致性/风控退化。
- 文档与实现保持一致。

## 12) Maintenance

- 根规则变更时同步更新本文件。
- 后端规则变更优先更新 `niro-server/AGENTS.md`，再回写根摘要。
- 新增工具链配置（lint/test/format）时，补充到第 3 节命令清单。
