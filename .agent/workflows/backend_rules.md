---
description: Niro 后端工程军规
---

## 1. 依赖与注入 (Strict Dependency)
- **构造注入**：严禁使用 `@Autowired`，统一使用 `@RequiredArgsConstructor` + `private final` 字段。
- **工具选型**：判空、集合操作、对象拷贝强制使用 `Hutool`；严禁自造轮子。
- **类名引用**：禁止使用全路径类名（如 `com.niro.Xxx`），必须通过 `import` 引入。

## 2. 数据库与 ORM (MyBatis-Plus)
- **链式查询**：统一使用 `xxxService.lambdaQuery().eq(...).one()`，禁止使用老旧的 `QueryWrapper`。
- **软删除**：所有删除操作必须通过 `@TableLogic` 逻辑删除。执行 `delete` 后必须断言 `rows > 0`。
- **JSON处理**：PG JSONB 字段查询允许使用 `@Select`，但必须在 Mapper 层封装，禁止污染 Service。

## 3. 异常与业务逻辑 (Business Logic)
- **业务断言**：禁止使用 `if (x == null) throw...`。统一使用项目内置断言：`Assert.notNull(obj, "错误消息")`。
- **异常捕获**：业务代码严禁捕获 `Exception`，由 `@RestControllerAdvice` 统一处理。
- **响应封装**：拦截器已处理 `Result` 封装，Controller 仅需返回数据对象或 `void`。

## 4. 扫货业务专项 (Domain Specific)
- **高并发保护**：涉及余额变更、订单创建，必须通过 `RedisUtil` 实现分布式锁，防止并发超卖。
- **风控规避**：所有涉及 `BuffAccount` 的操作必须包含异常计数逻辑，失败后强制调用 `accountService.markFailed()`。