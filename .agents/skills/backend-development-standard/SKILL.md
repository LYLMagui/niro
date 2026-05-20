---
name: backend-development-standard
description: >
  在 Niro 仓库中按固定项目规范处理后端接口、业务链路、Controller、Service、MapperManager、Mapper、Entity、DTO、VO、Param、SDK 接入、接口返回模型与 MyBatis 查询。
  Use when 用户要求新增、修改、重构、规范化或审查这些后端代码；即使用户没有明确说“后端规范”，只要意图是按 Niro 现有后端分层和约束落地实现，也应触发本技能。
allowed-tools: Read Grep Glob Edit Write Bash
compatibility: Niro repository only. Assumes the fixed Java/Spring Boot multi-module backend in this workspace and the repository CLAUDE.md constraints.
---

# Niro Backend Development

## When to use this skill

在 Niro 仓库内处理以下任务时启用本技能：

- 新增或修改后端接口、业务链路或分层落点
- 新增或调整 Controller、Service、MapperManager、Mapper、Entity、DTO、VO、Param
- 修正 MyBatis 查询写法、接口返回模型或事务边界
- 修改 `niro-sdk` 中的第三方平台接入代码，且需要符合 Niro 当前后端边界
- review 后端实现是否符合 Niro 既有风格与兼容性约束
- 统一某个模块的后端写法，但仍需保持局部收敛和历史兼容

以下场景不作为默认入口：

- 纯前端页面、样式或交互任务
- 纯数据库建模或 SQL 迁移，且不涉及 Java 后端落地
- 纯部署、CI/CD、环境配置调整
- 只做概念讲解，不需要在 Niro 代码中落地实现

## Instructions

将本技能视为 **Niro 固定仓库专用执行器**。不要再走“识别任意项目形态、自动生成通用后端规范”的流程。优先读取 Niro 已沉淀的项目规则，再结合目标模块现状完成最小必要改动。

### Step 1: Classify the task

先判断任务落点：

- Controller 接口调整
- Service 业务逻辑调整
- MapperManager / Mapper 查询调整
- DTO / VO / Param / Entity 调整
- SDK / 第三方接口接入调整
- 链路补齐或规范化 review

明确目标层级、影响范围、上下游调用方和兼容性要求。不要在范围未清前开始改代码。

### Step 2: Load Niro rules first

默认先读取以下内容：

1. `references/project-backend-standard.md`
2. 当前目标文件
3. 同模块相似实现
4. 项目级 `CLAUDE.md` / `PROJECT_RULES.md` / `CODING_RULES.md` 中与分层、兼容性、验证相关的约束

如果 reference 与当前稳定代码事实冲突：

- 分层职责、返回模型默认约束、MapperManager 边界、`lambdaQuery` 默认写法、构造注入、DTO/Param/VO/Entity 边界、SDK 敏感日志约束等，优先遵守 reference
- 历史接口兼容性、旧返回结构、既有非 RESTful 路径、被调用方依赖的字段结构、局部旧模块稳定写法等，优先尊重当前稳定实现
- 只有在确认出现了长期稳定新规则时，才回写 reference

### Step 3: Reconstruct the call chain before editing

对常规业务接口，优先还原 Niro 推荐链路：

```text
Controller
  -> Service
  -> MapperManager
  -> Mapper
  -> Entity
```

对第三方接入任务，至少确认：

```text
Controller / Service
  -> SDK Client / Engine
  -> Third-party API
```

至少确认：

- 参数从哪一层进入
- 业务判断该落在哪一层
- 数据查询由谁负责
- 返回对象在哪一层组装
- SDK 是否只负责协议适配而未混入业务流程
- 现有调用方是否依赖当前路径、签名或字段结构

如果当前任务只落在链路中的一个局部节点，也要先看上下游，不要孤立修改。

### Step 4: Decide layer ownership

按 Niro 既有职责边界分配改动：

- **niro-core**：公共组件、响应增强、异常处理、配置、工具类，不放具体业务流程
- **Controller**：接参与鉴权，调用 Service，返回业务对象，不直接访问持久层
- **Service**：业务判断、事务控制、DTO / VO 组装、跨资源协调
- **MapperManager**：承载语义明确的数据访问入口、分页、统计、批量查询与保存更新
- **Mapper**：承载映射能力和必要复杂 SQL
- **Entity / DTO / VO / Param**：只表达数据边界，不承载流程逻辑
- **niro-sdk**：只封装第三方协议，不承载 Niro 业务编排，不直接依赖 `niro-web`

如果当前模块已有固定分页转换、返回模型、命名方式或历史兼容写法，优先沿用模块内模式，不横向移植别处规则。

### Step 5: Implement minimally

开始改代码时，控制改动粒度：

- 小改动沿用目标模块现有风格
- 新增接口优先复用已有 DTO、VO、Param、MapperManager、分页和鉴权模式
- 缺少查询能力时，新增语义明确的 MapperManager 方法，不把查询细节塞进 Controller 或 Service
- Controller 默认直接返回 `DTO / VO / List / Page / void`，不要为新代码手动包 `Result`
- MyBatis 查询默认优先使用 `lambdaQuery()` / `lambdaUpdate()` 和方法引用
- 业务断言优先使用项目已有 `Assert` 工具类
- 不跨层落代码：Controller 不堆业务，Service 不越过 MapperManager 直接查库，SDK 不承载 Niro 业务流程
- 不顺手做无关重构，不为了“更优雅”额外制造抽象

默认追求“当前需求最小闭环”，不为单次修改提前铺大框架。

### Step 6: Check compatibility before finishing

完成代码后，至少从兼容性角度复核：

- Controller 是否越层访问持久层
- Service / MapperManager 职责是否清晰
- 接口返回结构、路径、参数绑定是否影响现有调用方
- 历史 `Result<T>` 接口或非 RESTful 路径是否被无意破坏
- DTO / Param / VO / Entity 边界是否被混用
- SDK 是否泄露敏感日志，是否正确区分 HTTP 失败、解析失败和业务失败
- 涉及余额、订单、库存、状态回写等并发敏感逻辑时，锁、幂等和状态流转是否保持原有约束

如果无法执行某项验证，明确说明缺口、影响和剩余风险。

### Step 7: Update the project reference only when justified

仅在以下场景更新 `references/project-backend-standard.md`：

- 发现 reference 与当前稳定代码事实不一致
- 某类新写法已经在多个模块稳定出现，值得上升为项目规则
- 新增了后续高频复用的明确约束

不要把某个文件的临时例外写成项目规范。本项目不自动进行构建；如需编译、测试或启动，只报告建议，默认由用户手动执行。若确需执行后端命令，必须显式使用 `D:\Environment\JDK\jdk-21.0.2`。

## Examples

### Example 1: Add a backend endpoint

输入：

> 在 Niro 里给某个业务模块补一个新接口，按现有后端规范来。

执行要点：

1. 先读 `references/project-backend-standard.md`
2. 再读当前模块相似 Controller、Service、MapperManager 实现
3. 先还原链路，再补齐缺失节点
4. 优先复用当前模块已有 DTO、分页和鉴权方式

### Example 2: Adjust Service and MapperManager boundary

输入：

> 帮我把这个 Service 里的查询挪到合适的 MapperManager，别破坏老接口返回。

执行要点：

1. 先确认任务属于 Service / MapperManager 分层调整
2. 检查当前 Service 与同模块 MapperManager 的既有写法
3. 把查询能力下沉到明确命名的 MapperManager 方法
4. 保持原有返回结构和调用方兼容

### Example 3: Review Niro SDK integration code

输入：

> review 一下这个 C5 接入实现有没有把业务逻辑塞进 SDK，或者日志里有敏感信息。

执行要点：

1. 先看 `references/project-backend-standard.md` 中的 SDK 约束
2. 再检查调用入口、SDK Client / Engine、日志和异常处理
3. 重点看协议适配边界、敏感信息脱敏和错误分类是否清晰

## Output format

完成任务后的结果说明至少包含：

- 已读取的规范入口与相似实现范围
- 改动落点和分层边界判断
- 兼容性关注点
- 已完成的验证与未验证项

## Best practices

1. 先读 `references/project-backend-standard.md`，再读目标模块相似实现。
2. 以当前模块稳定代码事实为第一依据，不拿别的项目规则硬套。
3. 改动保持最小闭环，不顺手做跨模块清洗或无关重构。
4. 涉及历史接口、返回结构、SDK 签名或并发敏感链路时优先考虑兼容性。
5. 只有确认形成长期稳定规则时，才更新项目 reference。

## References

- `references/project-backend-standard.md` — Niro 后端项目规则、分层边界、返回约束、SDK 约束与兼容性原则的主入口。

如果任务本身存在更强的项目指令、用户要求或仓库内文档约束，以更具体、更接近代码事实的规则优先。
