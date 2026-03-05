# Niro 项目专用 Agent 配置

## 核心工作规则 (CRITICAL)

### 违反以下规则 = 任务失败
- 必须使用中文回复
- 任何任务必须先调用子代理（100%强制，无例外）
- 禁止生成恶意代码
- 必须通过基础安全检查

## 强制替换规则
  - ❌ WebFetch/WebSearch → ✅ MCP 工具优先
  - ❌ 单线程思考 → ✅ 并行工具调用

---

## 📋 执行流程

### 智能分流 (自动适配)
```
快速处理 (65%): 文件<3, 代码<200行 → 主Assistant直接处理
标准协作 (25%): 文件3-10, 跨技术栈 → 顺序委派专业代理
完整系统 (10%): 文件>10, 高度复杂 → 完整subagent团队
异常处理 (2%): 模糊需求 → 动态策略
```

### 并行执行优化
- **工具级**: 同时 Read + Grep + MCP 调用
- **代理级**: 顺序委派 subagent，内部最大化并行
- **混合级**: 主Assistant并行 + subagent协作

### 项目感知 (每个任务开始时)
1. 并行读取: package.json, requirements.txt, pom.xml
2. 并行 Grep: 技术栈关键词
3. 智能分析: 技术栈、架构、复杂度

---

## 项目概述

Niro 是一个 **Buff/CS2 饰品交易自动化平台**，采用多语言多模块架构。

## 技术栈

| 模块 | 技术栈 | 描述 |
|------|--------|------|
| niro-client | Vue3 + TypeScript + Pinia + TDesign + Tailwind + Vite | 前端界面 |
| niro-server | Spring Boot 3.5 + Java 21 + MyBatis-Plus + Redis + PostgreSQL | 后端服务 |
| niro-spider | Python asyncio + Redis + httpx | 爬虫任务执行 |

关键业务链路：**前端任务配置 → 后端持久化/调度 → Redis 队列 → 爬虫消费执行 → 状态/日志回流**

## 项目结构

```
niro/
├── niro-client/          # 前端应用
│   └── src/
│       ├── api/           # API 请求
│       ├── components/   # 组件
│       ├── views/        # 页面视图
│       ├── stores/       # Pinia 状态管理
│       └── router/       # 路由配置
│
├── niro-server/          # 后端服务
│   ├── niro-core/       # 核心模块（公共组件）
│   ├── niro-web/        # Web 应用模块（业务逻辑）
│   │   └── controller/  # API 控制器
│   │   ├── service/     # 业务服务
│   │   ├── mapper/      # MyBatis Mapper
│   │   ├── entity/      # 实体类
│   │   ├── dto/         # 数据传输对象
│   │   ├── enums/       # 枚举
│   │   ├── jobhandler/  # XXL-JOB 任务处理器
│   │   └── mq/          # RocketMQ 消息
│   └── niro-sdk/        # SDK 模块（C5Game 平台集成）
│
└── niro-spider/         # 爬虫模块
    ├── config/           # 配置入口
    ├── spiders/          # 爬虫实现
    ├── storage/          # 存储层
    └── utils/            # 工具类
```

## 核心业务模块

### 后端核心 Controller
- `BuffAccountController` - Buff 账号管理
- `BuffGoodsController` - Buff 商品管理
- `BuffScanTaskController` - 扫货任务管理
- `TradeOrderController` - 交易订单管理
- `C5OrderSyncController` - C5 订单同步
- `UserController` - 用户管理

### 前端核心模块
- 任务配置与调度
- 库存看板
- 订单管理
- 用户设置

## 常用命令

### 前端 (niro-client)
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
mvn clean install -DskipTests  # 构建跳过测试
mvn spring-boot:run -pl niro-web  # 运行 Web 模块

# 测试
mvn test -Dtest=ResponseAdviceTest#testSuccessResponse
mvn -pl niro-web test -Dtest=RocketMQProducerTest
```

### 爬虫 (niro-spider)
```bash
cd niro-spider
python main.py                    # 启动（Redis 消息驱动）
pytest -q tests/test_c5_response.py  # 运行测试
```

## 强制编程规范

### 后端规范 (Java)
- **依赖注入**：使用 `@RequiredArgsConstructor` + `private final`，严禁 `@Autowired`
- **查询风格**：使用 `lambdaQuery()` 链式调用，严禁 `QueryWrapper`
- **工具库**：使用 Hutool 工具类，严禁自造轮子
- **业务断言**：使用 `Assert.notNull/isTrue`，严禁 `if (x==null) throw`
- **分布式锁**：余额/订单/库存操作必须使用 `RedisUtil` 加锁

### 前端规范 (Vue 3 + TS)
- 使用 `<script setup>` 和 Composition API
- 禁止 `any` 类型
- API 必须封装在 `src/api/`
- 使用 TDesign + Tailwind 样式

### 爬虫规范 (Python)
- 保持 Redis 消息驱动模型
- 配置集中在 `config/settings.py`
- 使用 `utils.logger` 记录日志
- 关键协程需支持取消/退出/心跳

## 可用 Skills

| Skill | 说明 |
|-------|------|
| coding-standards | Niro 编码规范 |
| code-reviewer | 代码审查 |
| c5-business-integration | C5 平台业务集成 |
| arch-guard | 架构守护 |
| api-consistency-and-safe-build | 前后端接口一致性 |
| frontend-design | 前端界面设计 |
| postgresql-table-design | PostgreSQL 表设计 |
| ui-ux-pro-max | UI/UX 设计专家 |
| generate-acceptance-test | 验收测试生成 |

## 工作流程

1. **需求分析**：理解业务需求，评估架构影响
2. **上下文收集**：使用 codex 收集相关代码和文档
3. **代码实现**：遵循编码规范，使用必要的 skills
4. **验证测试**：运行 lint/type-check/test
5. **任务完成**：等待用户指令，禁止自动提交。

## 代码提交原则
- 严禁自动提交代码。只有在用户明确发出“提交”、“commit”或“/commit”指令时，方可执行 git commit 操作。
- 提交信息必须为中文，准确描述改动内容，禁止直接使用用户的提问作为提交信息。
- 除非用户明确要求推送（push），否则禁止执行 git push。
