---
name: backend-development-standard
description: 通用后端开发规范技能。只要用户要求新增、修改、重构、规范化或讲解后端接口、业务链路、分层结构，就应主动使用本技能，哪怕用户没有明确说“按规范来”。尤其适用于新增或调整 Controller、Service、MapperManager、Mapper、Entity、DTO、VO、Param，整理后端编码通用流程，统一接口直接返回 DTO/VO/List/Page/void、不额外包 Result，落实 Service 不继承 ServiceImpl、MapperManager 继承 ServiceImpl<Mapper, Entity>、以及 MyBatis-Plus 必须优先使用 lambdaQuery 的场景。只要用户提到后端规范、分层、接口返回、Result、ServiceImpl、MapperManager、MyBatis-Plus、lambdaQuery，或要求“按标准写”“按统一分层写”，都应触发本技能。
---

# Backend Development Standard

## When to use this skill

在以下场景使用本技能：

- 用户要求按统一后端规范开发新接口或新业务链路
- 用户要求整理、沉淀、输出后端编码标准或通用流程
- 用户提到 `Controller -> Service -> MapperManager -> Mapper -> Entity` 分层
- 用户要求接口直接返回 `DTO / VO / List / Page / void`
- 用户明确要求不要额外包 `Result`
- 用户提到 `ServiceImpl`、`MapperManager`、`lambdaQuery`、MyBatis-Plus 查询规范
- 用户要求把现有后端代码改成统一写法

以下场景不必强行使用本技能：

- 纯前端页面与样式任务
- 纯数据库表结构设计
- 纯部署、运维、CI 配置
- 只做概念解释、不涉及后端落地约束
- 当前项目明确不采用 `MapperManager + MyBatis-Plus` 这一套模式

## Rules

### 1. 固定分层
后端统一按以下链路分层：

`Controller -> Service -> MapperManager -> Mapper -> Entity`

职责必须切干净：

- `Controller`：收参、鉴权、调 Service、返回结果
- `Service`：做业务编排、事务控制、状态流转
- `MapperManager`：统一承接数据库访问
- `Mapper`：只承接 MyBatis-Plus / SQL 映射能力
- `Entity`：映射数据库表

### 2. Controller 返回值规范
Controller 方法返回值直接写业务结果类型：

- `DTO`
- `VO`
- `List<DTO/VO>`
- `Page<DTO/VO>`
- `void`

硬规则：**不额外包一层 `Result`。**

### 3. Service 层规范
- `Service` 只定义接口，不继承 `ServiceImpl`
- `ServiceImpl` 只实现 `Service` 接口
- `Service` 只做业务逻辑和编排，不直接查库
- 事务优先放在 `Service` 层
- 不在 `Service` 中直接写 `lambdaQuery()`、`baseMapper.xxx()`、`mapper.xxx()`

### 4. MapperManager 层规范
- 所有数据库访问统一放到 `MapperManager`
- `MapperManager` 必须继承 `ServiceImpl<Mapper, Entity>`
- 对外暴露语义化方法，不向上层泄露底层查询细节
- 查询、保存、更新、统计等数据库操作优先在这一层封装

### 5. Mapper 层规范
- `Mapper` 继承 `BaseMapper<Entity>`
- 默认只保留接口定义
- 确实超出 `lambdaQuery` 能力时，再增加自定义 SQL / XML
- 即使有自定义 Mapper 方法，也应由 `MapperManager` 调用，而不是让 `Service` 直接注入 Mapper

### 6. MyBatis 查询规范
硬规则：**MyBatis 查库必须优先使用 `lambdaQuery` 链式表达式。**

推荐写法：

```java
return this.lambdaQuery()
        .eq(User::getUsername, username)
        .one();
```

禁止作为标准推荐的写法：

- `Service` 层直接查库
- `QueryWrapper` + 字符串字段名
- `.eq("username", username)` 这类字符串字段查询
- 绕开 `MapperManager` 直接在业务层拼查询

### 7. Param / DTO / VO / Entity 规范
#### Param
- 入参对象统一用 `Param` 结尾
- 参数校验注解写在 `Param` 上
- 可使用 `@Valid`、`@NotBlank`、`@NotNull`、`@Min`、`@Max`、`@Schema`
- 不写持久化注解

#### DTO
- 返回传输对象统一用 `DTO` 结尾
- 只保留接口实际需要返回的字段
- 不写 `@TableId`、`@TableLogic` 等持久化注解

#### VO
- 页面聚合展示对象统一用 `VO` 结尾
- 用于组合多个来源的数据，不直接映射数据库表

#### Entity
- 实体类映射数据库表
- 必须按需要使用 `@TableName`、`@TableId`、`@TableLogic`
- 不混入纯展示字段

### 8. 注解规范
常见约束如下：

- `Controller`：`@RestController`、`@RequestMapping`、`@RequiredArgsConstructor`、`@Operation`、`@Tag`
- 鉴权：使用项目既有鉴权框架注解，例如 `@SaCheckLogin`、`@SaCheckPermission` 或等价方案
- `Service / MapperManager`：`@Service`、`@RequiredArgsConstructor`、`@Transactional`
- `Entity`：`@Data`、`@TableName`、`@TableId`、`@TableLogic`
- `Enum`：`@Getter`、`@AllArgsConstructor`、`@EnumValue`、`@JsonValue`

## Instructions

### Step 1: 先识别任务属于哪类后端改动
先判断本次任务是：

- 新增接口
- 修改已有接口
- 新增业务链路
- 重构已有分层
- 整理规范文档

然后确认本次任务涉及哪些层，避免上来就跨层乱改。

### Step 2: 先读现有实现和相似代码
至少阅读：

- 当前目标文件
- 同模块相似 Controller / Service / MapperManager / Mapper / Entity
- 当前接口返回对象和入参对象
- 相关枚举、异常、事务写法

目标不是照抄，而是识别现有模式并把改动收敛到统一标准内。

### Step 3: 先确认当前项目是否适用这套分层
先验证当前仓库是否满足这些前提：

- 使用 Java 后端分层架构
- 使用 MyBatis-Plus 或兼容 `ServiceImpl + BaseMapper + lambdaQuery` 的模式
- 接受 `MapperManager` 作为数据库访问桥接层

如果当前项目不满足这些前提，不要强行套用；应先说明差异，再按现有架构做最接近的规范化落地。

### Step 4: 先按固定分层设计，再开始写代码
在动手前先确认：

- Controller 返回什么类型
- Service 负责编排什么业务
- MapperManager 需要提供哪些数据库访问方法
- Mapper 是否只需 `BaseMapper`
- Entity / DTO / Param 的边界是否清晰

如果某段逻辑让 `Service` 直接查库，说明分层已经歪了，先改结构再写逻辑。

### Step 5: 按正确顺序落代码
推荐顺序：

1. 定义或补齐 `Param / DTO / VO / Entity`
2. 定义 `Mapper`
3. 在 `MapperManager` 中实现数据库访问方法
4. 在 `Service` 中完成业务编排
5. 在 `Controller` 中暴露接口

不要从 Controller 一路写到数据库。先把底层数据访问边界钉死。

### Step 6: 写完后逐条检查禁令
交付前至少检查这些硬约束是否被违反：

- 是否出现 `Controller` 返回 `Result<T>`
- 是否出现 `Service` 继承 `ServiceImpl`
- 是否出现 `Service` 直接查库
- 是否绕过 `MapperManager` 直接注入 `Mapper`
- 是否用了字符串字段名查询
- 是否把持久化注解写进了 `DTO`

发现任一项，优先修正结构，不要打补丁掩盖。

### Step 7: 做最小必要验证
根据任务范围选择验证：

- 编译通过
- 单测通过
- 静态检查通过
- 接口签名无兼容性破坏
- 返回对象字段与调用方预期一致

如果无法执行某项验证，要明确说明原因和剩余风险。

## Examples

### Example 1: 新增登录接口
用户说：

> 按统一后端规范补一个登录接口，直接返回 `UserDTO`。

你应该：

- 先定义 `UserLoginParam` 和 `UserDTO`
- 在 `UserMapperManager` 中提供 `getByUsername`
- 在 `UserService` 中完成账号校验、密码校验、登录态处理
- 在 `UserController` 中直接返回 `UserDTO`
- 避免返回 `Result<UserDTO>`

### Example 2: 新增列表查询接口
用户说：

> 新增一个任务列表接口，按统一分层写。

你应该：

- 在 `MapperManager` 中封装列表查询和分页查询
- 使用 `lambdaQuery` 组织筛选条件
- 在 `Service` 中处理分页、状态转换和结果映射
- 在 `Controller` 中直接返回分页结果对象

### Example 3: 规范化已有业务代码
用户说：

> 把这个 Service 改成统一写法。

你应该重点检查：

- 是否继承了 `ServiceImpl`
- 是否直接注入 `Mapper`
- 是否直接写了 `lambdaQuery`
- 是否应拆出 `MapperManager`
- 是否把接口返回值改成直接返回 DTO/VO

## Best practices

1. 先确认项目是否适用这套模式，再执行，不要跨架构硬套。
2. 先修正分层，再补业务逻辑，别在错误结构上堆功能。
3. 优先消灭特殊情况，避免为了兼容坏结构继续加判断。
4. `MapperManager` 方法名要表达业务语义，不要暴露底层实现细节。
5. Controller 签名一眼就要能看出真实返回类型，不要再包 `Result`。
6. 只要涉及 MyBatis 查询，优先检查是否能用 `lambdaQuery` 解决。
7. DTO、Param、Entity 三类对象的边界要硬，别混用。
8. 改动保持聚焦，只改当前任务需要的层和链路。

## References

- Spring Boot Web / Validation 相关文档
- MyBatis-Plus `ServiceImpl`、`BaseMapper`、`lambdaQuery` 相关文档
- OpenAPI / Swagger 注解文档
- 当前项目内相似的 Controller、Service、MapperManager、Mapper、Entity 实现
