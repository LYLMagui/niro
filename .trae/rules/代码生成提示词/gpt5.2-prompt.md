---
alwaysApply: false
---
```markdown
# 项目提示词（前后端统一工程规范 / LLM 执行版）

> 适用范围：本仓库的前端 Vue 3、后端 Spring Boot、后端 Python 业务编排代码。  
> 目标：让大模型在**不臆测、不越界、不引入未声明依赖**的前提下，稳定产出可落地代码与可验证方案。  
> 约束优先级：**用户明确需求 > 本规范 > 通用最佳实践**（三者冲突时按优先级执行，并在回复中说明取舍原因）。

---

## 0. 通用输出与协作要求（所有任务必须遵守）

### 0.1 语言与表达
- 回复与思考使用**中文**；技术名词保留英文（如 `MyBatis-Plus`、`Composition API`）。
- 结论先行：先给可执行结果/方案，再补充原因与注意事项。
- 禁止“假装理解”：不清楚时必须明确指出不确定点，并通过**查源码/查定义/查配置**来消除不确定性。

### 0.2 变更与交付
- 任何代码改动后，必须归纳：
  - 修改了哪些文件
  - 每个文件改动目的与关键点
  - 如何验证（启动/接口/测试/页面路径）
- 不得产生“只占位后续实现”的空逻辑，不得提供 Mock/Stub 代替真实实现。
- 不得吞异常、不得使用空 `catch`、不得把异常当作正常控制流。

### 0.3 依赖与包管理
- **不得引入未在项目中声明/安装的第三方包**（如 `lodash`、`dayjs`、`Guava`、`Hutool`），除非已存在依赖或用户明确要求并允许安装。
- **前端依赖管理使用 `pnpm`，禁止 `npm`。**
- 终端命令输出要求：
  - 多行命令必须分开输出
  - 单行需要合并时使用 `;` 分隔
  - 默认按 Windows 环境命令风格输出（路径使用 `\`）

### 0.4 需求与产品视角（必须执行）
- 从产品经理与架构师视角审视：业务流程是否合理、交互是否最小复杂度、是否满足最小可用（MVP）。
- 若发现更优设计/明显风险（性能、安全、数据一致性、可维护性），**先提出建议**，待用户接受后再改代码或给最终实现。

---

## 1. 前端（Vue 3 + TypeScript + TDesign + TailwindCSS）

### 1.1 框架与语法
- 必须使用 Vue 3 `Composition API`，禁止 `Options API`。
- 必须使用 `<script setup>`。
- 模板优先简洁：合理使用 `v-if / v-for / v-model`，避免无意义嵌套与过深组件树。
- TypeScript 类型优先：尽量避免 `any`，优先显式类型与可推导类型。

### 1.2 目录约定
- 页面组件放在 `views/`
- 通用组件放在 `components/`
- 异步请求默认封装为 `useXXX()` 组合式函数，统一放在 `composables/`
- API 请求统一封装到 `api/`，禁止在组件内直接写 `axios` 请求
- API 响应统一使用项目内的响应封装器处理（例如 `useRequest()` 或同类封装）；不得在各处自行发散处理逻辑

### 1.3 命名规范
- 文件名：小写中划线（如 `user-list.vue`）
- 变量/函数：`camelCase`
- 组件名：`PascalCase`
- 组合式函数：以 `use` 开头（如 `useUserList`）

### 1.4 UI 与样式
- UI 组件基于 TDesign（实现前先对齐官方文档：https://tdesign.tencent.com/vue/overview）
- CSS 框架使用 TailwindCSS
- 自定义样式命名使用 BEM
- 布局使用 Flex / Grid，禁止 table 布局
- 移动端优先 `rem`，桌面端可适当 `px`

### 1.5 异常与交互体验
- 所有请求必须有异常捕获（`try/catch` 或 `onError`）
- 异常提示语应通用且可复用，避免硬编码散落在各处
- 页面加载必须提供骨架屏或 loading，避免白屏

### 1.6 注释要求（前端）
- 默认需要简洁中文注释以提升可维护性，**除非任务明确要求不添加注释**  
- 文件顶部需包含：作者 `liyl`、创建时间、功能简述  
- 复杂逻辑（`watchEffect`、`computed` 等）必须说明业务含义，而非重复代码

---

## 2. 后端（Spring Boot 3.x + JDK 21+ + MyBatis-Plus）

### 2.1 技术栈与硬性约束
- Spring Boot 版本：3.x；JDK：21+
- ORM：统一使用 `MyBatis-Plus`
  - 禁止原生 XML SQL、禁止手写 SQL
  - 优先 `LambdaQueryWrapper`、`IService`、`ServiceImpl` 链式写法
  - 复杂联表：优先拆分为多次查询再合并结果，避免不可维护的“超长查询”
- Controller：必须 `@RestController`，禁止 `@Controller + @ResponseBody`
- 配置文件：使用 `application.yml`（可多环境 `application-dev.yml`、`application-prod.yml`），禁止 `.properties`
- 依赖注入：统一构造函数注入 + `@RequiredArgsConstructor`，禁止字段注入 `@Autowired`
- 所有接口返回：统一 `Result<T>`，格式 `{ code, msg, data }`，禁止直接返回实体对象

### 2.2 分层与包结构
- 必须严格分层（仅举例，按项目实际包名为准）：
  - `controller`：HTTP 入参校验、路由、鉴权入口（不承载业务细节）
  - `service`：业务接口
  - `service.impl`：业务实现（必须实现接口）
  - `mapper`：MyBatis-Plus Mapper
  - `entity`：数据库实体（PO）
  - `dto`：数据传输对象（入参/出参统一使用 DTO；**本项目禁止创建 VO**）
  - `enums`：枚举
  - `config`：配置
  - `utils`：无状态且线程安全工具类

### 2.3 命名规范
- 包名：全小写单数
- 类名：`UpperCamelCase`
- 接口：`XxxService`
- 实现类：`XxxServiceImpl`
- 方法：`lowerCamelCase`
- 常量：全大写下划线（如 `DEFAULT_PAGE_SIZE`）
- 数据库字段：小写下划线（如 `create_time`）
- DTO：必须 `XxxDTO` 后缀
- Mapper：`XxxMapper`
- 枚举：`XxxEnum`

### 2.4 注释与日志
- 类/接口/方法/字段必须使用 Javadoc：`/** ... */`
  - 类注释包含：`@author l
iyl`、`@date`、`@description`
  - 方法注释包含：业务作用、`@param`、`@return`
- 日志：统一 `SLF4J`，使用 `{}` 占位符，禁止字符串拼接
  - `DEBUG` 调试、`INFO` 关键流程、`WARN` 可恢复异常、`ERROR` 严重错误
- 敏感信息（DB 密码、API Key）：必须放环境变量或配置中心，禁止硬编码

### 2.5 分页与数据一致性
- 分页必须使用 `Page<T>`，禁止手动计算 `limit/offset`
- 删除/更新必须判断影响行数；删除采用逻辑删除（`@TableLogic`）
- 参数校验统一 JSR303（`@NotNull`、`@Size` 等）+ Controller `@Valid`
- 全局异常：使用 `@RestControllerAdvice` 统一捕获；禁止在业务代码中滥用 `try-catch`

---

## 3. 后端（Python：强依赖复用 + 胶水编排）

### 3.1 角色边界（强制）
本项目 Python 代码仅承担：
- 业务流程编排（Orchestration）
- 模块组合与调度
- 参数配置与调用组织
- 输入输出适配（不改变核心语义）

明确禁止：
- 重复实现依赖库已有的算法/数据结构
- 复制依赖库代码到本项目后再修改使用
- 用 Demo/示例/简化版替代生产级实现

### 3.2 依赖集成方式与导入规范
允许并支持：
- 本地源码直连（`sys.path` / 本地路径）
- 包管理器安装（`pip` / `conda` / editable install）

要求：
- 实际加载必须是**完整、生产级实现**，禁止裁剪、降级封装
- 必须可验证：导入的模块在运行期真实参与执行，禁止“只导入不用”
- 禁止路径遮蔽/重名导致加载错误实现（需在关键启动点打印模块 `__file__` 或提供等价验证方式）

示例（仅示意，路径必须替换为真实存在且适配当前环境的路径）：
```python
import sys
from pathlib import Path

external_dir = Path(r"E:\path\to\external\libs")
sys.path.append(str(external_dir))

from datas import *          # 依赖库完整模块：禁止只抽子集重封装
from sizi import summarys    # 依赖库完整算法：禁止简化逻辑
```

### 3.3 输出要求（对大模型的约束）
生成 Python 代码时必须：
1. 明确标注哪些能力来自外部依赖（模块/函数名级别即可）
2. 不生成依赖库内部实现代码
3. 只生成最小必要的胶水代码与业务逻辑
4. 默认把依赖库当作不可修改黑箱

---

## 4. 前后端联调硬规则（避免参数不一致）
- 修改前端 `api/` 或请求参数时，必须同步查看后端 Controller/DTO/校验规则，确保：
  - 字段名一致（大小写/下划线/驼峰映射）
  - 必填/选填一致
  - 枚举值/范围一致
  - 分页参数（`pageNo/pageSize` 或 `current/size`）与后端实现一致
- 输出联调说明：接口路径、方法、示例请求、示例响应、错误码与异常提示策略。

---

## 5. 设计与工程红线（全局不可违反）
1. 不得只修局部补丁而忽视整体设计与全局优化
2. 不得引入过多中间状态，避免循环依赖与可读性下降
3. 不得以大量防御性代码掩盖主逻辑；通过清晰边界与约束解决问题
4. 必须遵循 SOLID 与 DRY，保持职责单一、避免重复逻辑
5. 状态只保留最小核心数据，UI 状态从核心数据推导
6. 不得跨请求共享可变状态，除非并发安全设计明确
7. 不得依赖隐式调用顺序、全局初始化副作用或时序假设
8. 不得返回语义不清的结果（如 `null/undefined/false` 混用）
9. 不得在缺乏上下文理解的情况下直接修改代码；必须先理解结构再审慎重构

---

## 6. 可扩展区（后续追加提示词请在此区补充）
### 6.1 新增技术栈规范
- 

### 6.2 业务域规则
- 

### 6.3 安全与合规
- 

### 6.4 性能与可观测性
- 

---
```