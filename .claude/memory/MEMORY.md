# 项目核心记忆 (L3)

> 三层记忆系统 - 手动维护的核心规则

## Niro 项目特定规范

### 技术栈
- 前端：Vue3 + TypeScript + Pinia + TDesign + Tailwind + Vite
- 后端：Spring Boot 3.5 + Java 21 + MyBatis-Plus + Redis + PostgreSQL
- 爬虫：Python asyncio + Redis + httpx

### 编码规范（强制）

#### 后端 (Java)
- 依赖注入：`@RequiredArgsConstructor + private final`，严禁 `@Autowired`
- 查询风格：`lambdaQuery()` 链式调用，严禁 `QueryWrapper`
- 工具库：使用 Hutool，严禁自造轮子
- 业务断言：使用 `Assert.notNull/isTrue`

#### 前端 (Vue3 + TS)
- 使用 `<script setup>` 和 Composition API
- 禁止 `any` 类型
- API 封装在 `src/api/`

#### 爬虫 (Python)
- Redis 消息驱动模型
- 配置集中管理 `config/settings.py`
- 协程需支持取消/退出/心跳

### 强制工作流程

1. **sequential-thinking 前置**：复杂任务必须先调用 `sequential-thinking`
2. **Codex 收集上下文**：代码/文档查阅必须委托 Codex 执行
3. **默认自动执行**：预设边界内自动执行，无需确认
4. **工具链顺序**：sequential-thinking → Codex 收集 → 任务规划 → 主AI + Codex 编码

### Codex 协作规则

- 调用必须使用 `model: "gpt-5.3-codex"`、`sandbox: "danger-full-access"`、`approval-policy: "on-failure"`
- 所有代码生成/重构默认交给 Codex
- 琐碎修改（<20行）可由 CC 处理

### 代码提交原则

- 严禁自动提交，必须等待用户明确指令
- 提交信息必须为中文，准确描述改动内容

### 已知陷阱

- Windows 路径：使用正斜杠 `/`，避免硬编码反斜杠
- Docker 时区：必须在 Dockerfile 中设置 `TZ=Asia/Shanghai`
- 数据库迁移：必须先备份再执行

### Skills 使用

当前可用 Skills：
- `coding-standards` - 编码规范
- `code-reviewer` - 代码审查
- `c5-business-integration` - C5 业务集成
- `arch-guard` - 架构守护
- `api-consistency-and-safe-build` - 接口一致性
- `frontend-design` - 前端设计
- `postgresql-table-design` - 数据库设计
- `generate-acceptance-test` - 验收测试

---

*此文件由三层记忆系统管理，自动加载到 Claude Code 上下文*
