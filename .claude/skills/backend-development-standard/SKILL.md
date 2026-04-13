---
name: backend-development-standard
description: 当用户要求新增、修改、重构、规范化、沉淀或审查后端接口、业务链路、分层结构、DTO/VO/Param/Entity、持久层访问方式或后端开发流程时使用。本技能先识别当前项目的后端技术栈、既有分层和编码规范；若已存在 references/project-backend-standard.md 则优先按该规范执行，否则先从当前项目代码中归纳并生成项目规范，再按该规范落地实现。
tags: [backend, architecture, standards, api]
platforms: [Claude]
---

# Backend Development Standard

## Overview

这是一个“后端项目规范分析 + 落地执行”的总控技能。

它不预设所有项目都遵循同一套后端规范，也不把某个仓库的分层、返回值、ORM 用法、命名约束硬编码成通用真理。

它的职责是：

1. 识别当前项目使用的语言、框架、ORM、分层和返回模型。
2. 优先读取当前 skill 下已沉淀的项目规范参考文件。
3. 如果参考文件不存在，就从当前项目代码中归纳出一份项目规范。
4. 后续新增、修改、重构、review 后端代码时，按项目规范执行，而不是跨架构硬套。

## When to use this skill

在以下场景使用本技能：

- 用户要求新增、修改、重构或规范化后端接口
- 用户要求补业务链路、梳理分层、统一后端写法
- 用户要求 review 后端代码风格、架构一致性、数据访问边界
- 用户要求沉淀当前仓库的后端开发规范或落地流程
- 任务涉及 Controller、Service、Repository / Mapper / DAO、Entity、DTO、VO、Param、事务、鉴权、校验、分页、响应模型
- 你需要先判断当前项目是否已有稳定后端规范，再决定如何实现

以下场景不必强行使用本技能：

- 纯前端页面、样式或交互任务
- 纯数据库建模任务，且不涉及当前项目后端落地约束
- 纯部署、运维、CI/CD 配置
- 只做概念解释，不需要按当前项目规范落地

## Rules

- 先识别当前项目是什么，再决定如何写，不跨架构硬套。
- 先读当前项目已有实现和相似代码，再提炼规范，不凭印象输出“标准答案”。
- 参考文件存在时优先使用，但不能盲信；若与当前代码事实明显冲突，以代码事实为准并更新参考文件。
- 参考文件不存在时，先归纳项目规范，再开始大规模实现或规范化。
- 规范的目标是帮助当前项目保持一致，不是为了制造新的抽象层或重写整仓库。
- 新代码优先遵循项目推荐写法；旧代码若存在历史包袱，除非任务明确要求，不顺手做跨模块大清洗。

## Instructions

### Step 1: Classify the backend task
先判断本次任务属于哪类：

- 新增接口
- 修改已有接口
- 新增业务链路
- 重构已有分层
- 代码审查 / 风格审查
- 规范文档沉淀或更新

明确范围、目标层级和兼容性要求，避免上来就跨层乱改。

### Step 2: Load or create the project backend standard
优先检查当前 skill 目录下是否存在：

- `references/project-backend-standard.md`

处理规则：

1. **如果存在**：
   - 先读取它。
   - 将它视为当前项目默认规范来源。
   - 如果本次任务涉及的代码明显与参考文件冲突，补读实际代码，再修正参考文件。

2. **如果不存在**：
   - 读取当前项目有代表性的 Controller、Service、持久层、DTO/VO/Param/Entity、配置或项目说明。
   - 归纳该项目的后端规范。
   - 在 `references/project-backend-standard.md` 中写入项目规范。
   - 后续按新生成的参考文件执行。

### Step 3: Analyze the project shape before prescribing structure
至少识别这些维度：

- 语言与运行时（Java / Go / Node / Python ...）
- Web 框架（Spring Boot / NestJS / Gin / FastAPI ...）
- 持久层模式（MyBatis-Plus / JPA / Repository / DAO / Prisma / raw SQL ...）
- 常见分层（Controller / Service / Manager / Repository / Domain ...）
- 接口返回模型（直接返回 DTO、统一包装、自动响应增强等）
- 参数校验方式
- 鉴权方式
- 事务边界
- 包结构、命名规则、DTO/Entity 边界

目标是回答：这个项目本来就是怎么写的，而不是你希望它怎么写。

### Step 4: Read the current implementation and similar modules
至少阅读：

- 当前目标文件
- 同模块相似 Controller / Service / Repository / Mapper / Entity
- 当前接口的入参和返回对象
- 相关异常、事务、鉴权、校验、分页写法
- 如有项目说明文件，也要读（例如 `CLAUDE.md`、模块说明、架构文档）

如果项目已经有稳定模式，优先复用；如果项目处于迁移期，要同时识别“推荐写法”和“历史遗留写法”。

### Step 5: Design the change against project conventions
在开始写代码前，先确认：

- 当前接口应该返回什么类型
- 业务逻辑应该放在哪一层
- 数据访问应该经由哪一层
- DTO / VO / Param / Entity 的边界是否清晰
- 当前任务应该遵循推荐写法，还是需要兼容某段已有旧实现

如果你发现当前项目并不采用某种常见模式，例如没有 `MapperManager`、没有统一 `Result`、没有 `ServiceImpl`，那就不要把这种模式硬塞进去。

### Step 6: Implement with the project standard, and refresh the reference when needed
实现时遵循这些边界：

- 小改动优先沿用所在模块的稳定写法
- 新增模块或规范化改造优先按项目参考规范落地
- 只有当你确认发现了稳定、可复用、值得长期保留的新规范时，才更新 `references/project-backend-standard.md`
- 如果只是某个文件的临时例外，不要把它升级成项目规范

### Step 7: Verify the result with compatibility in mind
根据任务范围选择最小必要验证：

- 编译通过
- 单测通过
- 静态检查通过
- 接口签名兼容
- 调用链没有被错误分层破坏
- 返回字段、事务边界、异常路径与现有调用方一致

如果无法执行某项验证，要明确说明缺口和剩余风险。

## Examples

### Example 1: Reference exists, implement against it
用户说：

> 按当前项目后端规范补一个新接口。

你应该：

1. 先读取 `references/project-backend-standard.md`。
2. 再读取当前模块的相似 Controller / Service / 持久层实现。
3. 按参考规范和当前模块既有模式落代码。
4. 若发现参考规范与当前模块实现冲突，先确认是否是旧代码偏差还是规范已过时。

### Example 2: No reference exists yet
用户说：

> 帮我统一一下这个后端仓库的接口写法。

你应该：

1. 先检查 `references/project-backend-standard.md` 是否存在。
2. 若不存在，读取代表性的 Controller、Service、持久层、DTO、Entity 和项目说明。
3. 总结项目的分层、返回值、查询方式、校验与事务模式。
4. 生成 `references/project-backend-standard.md`。
5. 再开始后续规范化改造。

### Example 3: The project differs from a familiar pattern
用户说：

> 把这个 Service 改成统一写法。

你应该重点检查：

- 当前仓库是否真的有统一写法
- 当前模块是推荐写法还是历史写法
- 数据访问边界该落在哪一层
- 是否需要补 reference，而不是直接照搬别的项目规则
- 变更是否会破坏现有调用方或接口契约

## Best practices

1. skill 本体只写元规则，不把项目私有规范硬编码进去。
2. `description` 只写触发条件和职责，不塞过多项目细节。
3. 参考文件存在时优先读取；不存在时先归纳、再生成、再执行。
4. 参考文件是“当前项目规范快照”，不是不可质疑的圣经。
5. 发现规范和代码事实冲突时，以代码事实为准，并回写参考文件。
6. 规范化的目标是提升一致性，不是顺手把整个仓库改成你熟悉的架构。
7. 对处于迁移期的项目，要同时记录推荐写法和已知历史偏差，避免误导后续改动。

## References

- `references/project-backend-standard.md`
- `D:\MySpace\niro\CLAUDE.md`
- `D:\MySpace\niro\niro-server\CLAUDE.md`
- 当前项目内相似的 Controller、Service、持久层、DTO、Entity 实现
