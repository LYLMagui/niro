---
name: code-reviewer
description: Niro 项目专用代码审查，严格执行 backend_rules, frontend_rules, python_rules 及工程军规。
---

# Niro Code Reviewer

## 触发时机
- Pull Request 创建或更新
- 开发人员请求代码审查
- 关键业务逻辑变更（交易、风控、资损相关）

## 核心任务
1. **军规执行**：严格审查是否违反 `rules/*.md` 中的“严禁”、“必须”项。
2. **安全风控**：重点审查交易、余额、库存扣减逻辑的并发控制。
3. **技术栈合规**：确保使用了规定的工具库（Hutool, MyBatis-Plus Lambda, RedisUtil）。

---

## 审查维度与清单

### 1. Java 后端 (Spring Boot)
> 依据: `backend_rules.md`

#### A. 依赖注入与类结构
- [ ] **构造注入**: 是否使用了 `@RequiredArgsConstructor` + `private final`？(❌ 严禁 `@Autowired`)
- [ ] **工具类**: 判空、集合、拷贝是否使用了 `Hutool`？(❌ 严禁造轮子或用原生繁琐写法)
- [ ] **类引用**: 是否避免了全限定类名，使用了 `import`？

#### B. 数据库与 ORM
- [ ] **查询风格**: 是否使用了 `lambdaQuery()` 链式调用？(❌ 严禁老旧 `QueryWrapper`)
- [ ] **软删除**: 删除操作是否依赖 `@TableLogic`？
- [ ] **删除断言**: 执行 `delete` / `update` 后是否断言了 `rows > 0`？
- [ ] **JSON 处理**: PG JSONB 查询是否封装在 Mapper 层？(❌ 严禁在 Service 层拼接 SQL 片段)

#### C. 异常与业务逻辑
- [ ] **断言使用**: 是否使用了 `Assert.notNull/isTrue`？(❌ 严禁 `if (x==null) throw`)
- [ ] **异常捕获**: 是否移除了业务代码中的 `try-catch` (由全局异常处理)？
- [ ] **响应返回**: Controller 是否直接返回数据对象？(❌ 严禁手动封装 `Result`)

#### D. 扫货与高并发 (高危)
- [ ] **分布式锁**: 余额变更/下单是否使用了 `RedisUtil` 加锁？
- [ ] **风控计数**: 涉及 `BuffAccount` 是否有失败计数与熔断调用 `markFailed()`？

### 2. 前端 (Vue 3 + TS)
> 依据: `frontend_rules.md`

- [ ] **语法**: 是否全量使用 `<script setup>` 和 Composition API？
- [ ] **类型**: 是否消灭了 `any` 类型？
- [ ] **样式**: 是否使用了 TailwindCSS / TDesign？(❌ 严禁传统 CSS 文件)
- [ ] **API**: 请求是否封装在 `src/api`？(❌ 组件内严禁直接调用 axios)

### 3. Python 爬虫
> 依据: `python_rules.md`

- [ ] **模块边界**: 代码是否在 `niro-spider` 模块内？
- [ ] **依赖**: 是否使用了 `utils.logger` 而非 `print`？
- [ ] **网络**: 是否使用了 `utils.network_util` 进行 IP 检测？

---

## 审查报告模板

请使用以下格式输出审查结果：

```markdown
# Code Review Report

## 🚫 阻断性问题 (Must Fix)
- **[规则 1.1]** `UserService.java`: 使用了 `@Autowired`，请改为 `@RequiredArgsConstructor`。
- **[规则 2.1]** `OrderService.java`: 第 45 行使用了 `QueryWrapper`，请改为 `lambdaQuery()`。
- **[规则 4.1]** `TradeService.java`: 扣减余额未加 Redis 锁，存在超卖风险。

## ⚠️ 建议优化 (Should Fix)
- **[规则 1.2]** `StringUtil` 使用了 JDK 原生方法，建议改为 `StrUtil` (Hutool)。

## ✅ 亮点与通过
- 业务逻辑清晰，断言使用规范。
```

## 命令行工具

```bash
# 审查当前变更
python .trae/skills/code-reviewer/scripts/review.py --diff

# 审查特定文件
python .trae/skills/code-reviewer/scripts/review.py --file src/main/java/com/niro/service/OrderService.java
```
