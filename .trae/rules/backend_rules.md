# 后端开发规范 (Spring Boot 3 + MP)

## 1. 技术栈与约束
- **核心**：JDK 21 + Spring Boot 3.x。
- **ORM**：MyBatis-Plus。禁止 XML/手写 SQL，统一 `lambdaQuery()` 链式写法。
- **注入**：构造函数注入 + `@RequiredArgsConstructor`，禁止 `@Autowired`。
- **工具**：优先 `Hutool`（BeanUtil 转换、判空等）。禁止 VO，统一使用 DTO。

## 2. 分层与结构
- **层级**：`controller` (入参校验) -> `service` (业务逻辑) -> `mapper` (数据访问)。
- **规范**：`XxxService` 接口 + `XxxServiceImpl` 实现。`XxxDTO` 后缀，`XxxEnum` 枚举。
- **响应**：直接返回实体，由框架统一封装为 `Result<T>`。

## 3. 数据库与一致性
- **分页**：统一使用 `Page<T>`。
- **删除**：必须逻辑删除 (`@TableLogic`)，操作后判断影响行数。
- **校验**：JSR303 (`@Valid`)。
- **异常**：`@RestControllerAdvice` 全局捕获，业务中禁止滥用 `try-catch`。

## 4. 注释与日志
- **Javadoc**：类/接口/方法/字段必填（含作者、日期、参数说明）。
- **日志**：SLF4J `{}` 占位符，禁止字符串拼接。按等级划分：`INFO` 流程, `ERROR` 异常。
