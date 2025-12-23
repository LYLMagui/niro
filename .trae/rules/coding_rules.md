**前置条件**：思考前先阅读**project_rules.md**文件，若已读取则忽略
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
- TypeScript 类型优先：禁止 `any`，必须显式类型与可推导类型。

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
- CSS 框架使用 TailwindCSS（实现前先对齐官方文档：https://tailwind.nodejs.cn/docs/installation）
- 非必要情况下，禁止直接写传统 css 样式
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
  - 优先 `lambdaQuery().xx`、`IService`、`ServiceImpl` 链式写法
  - 禁止使用 `LambdaQueryWrapper`构造条件
  - 复杂联表：优先拆分为多次查询再合并结果，避免不可维护的“超长查询”
- Controller：必须 `@RestController`，禁止 `@Controller + @ResponseBody`
- 配置文件：使用 `application.yml`（可多环境 `application-dev.yml`、`application-prod.yml`），禁止 `.properties`
- 依赖注入：统一构造函数注入 + `@RequiredArgsConstructor`，禁止字段注入 `@Autowired`
- 所有接口返回：响应处理器已经做了`Result<T>`封装，直接返回实体对象
- 所有 DTO 的转换统一使用`Hutool`包的`BeanUtil`工具类
- 所有集合、字符串的判空都必须使用`Hutool`包的相关工具类
- 优先使用`Hutool`包中的工具类，如果没有满足需求的工具类，则使用自己再创建

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

- 类/接口/方法/字段必须使用 Javadoc：`/** ... */` - 类注释包含：`@author l
iyl`、`@date`、`@description` - 方法注释包含：业务作用、`@param`、`@return` - 实体类使用 `lambda`注解，依赖注入使用`@RequiredArgsConstructor`注解注入
- 日志：统一 `SLF4J`，使用 `{}` 占位符，禁止字符串拼接
  - `DEBUG` 调试、`INFO` 关键流程、`WARN` 可恢复异常、`ERROR` 严重错误

### 2.5 分页与数据一致性

- 分页必须使用 `Page<T>`，禁止手动计算 `limit/offset`
- 删除/更新必须判断影响行数；删除采用逻辑删除（`@TableLogic`）
- 参数校验统一 JSR303（`@NotNull`、`@Size` 等）+ Controller `@Valid`
- 全局异常：使用 `@RestControllerAdvice` 统一捕获；禁止在业务代码中滥用 `try-catch`

### 2.6 项目目录结构

**所有的文件和目录创建都必须遵守一下目录结构，禁止随意在某个不相干的模块创建代码文件或目录**

```plant text
niro-server/
├── niro-core/              # [核心基础模块]：提供通用工具、全局配置和统一响应封装
│   └── src/main/java/com/niro/core/
│       ├── advice/         # 响应拦截器 (如：ResponseAdvice 统一包装响应体)
│       ├── aspect/         # AOP切面 (如：WebLogAspect 统一记录请求日志)
│       ├── config/         # 通用配置 (如：RedisConfig, WebConfig)
│       ├── exception/      # 自定义异常 (如：BusinessException 业务逻辑报错)
│       ├── handler/        # 全局异常处理器 (如：GlobalExceptionHandler 捕获所有报错)
│       ├── result/         # 统一返回结果 (如：Result<T> 封装 {code, msg, data})
│       └── util/           # 通用工具类 (如：RedisUtil, Assert 断言工具)
│
├── niro-spider/            # [爬虫业务模块]：Python 实现的独立数据采集服务
│   ├── src/main/python/
│   │   ├── config/         # 爬虫配置 (如：settings.py 数据库连接、并发数)
│   │   ├── spiders/        # 爬虫逻辑 (如：buff_spider.py 抓取 Buff 饰品数据)
│   │   ├── storage/        # 数据存储 (如：postgres_pool.py 数据库连接池)
│   │   ├── utils/          # 爬虫工具 (如：proxy_helper.py 代理池管理)
│   │   └── main.py         # 爬虫启动入口
│   └── requirements.txt    # Python 依赖清单
│
├── niro-web/               # [Web 业务模块]：Spring Boot 实现的主业务服务
│   └── src/main/
│       ├── java/com/niro/web/
│       │   ├── config/     # 业务配置 (如：MybatisPlusConfig 分页插件)
│       │   ├── controller/ # 接口层 (如：BuffGoodsController 商品接口, UserController 用户接口)
│       │   ├── dto/        # 数据传输对象
│       │   │   ├── param/  # 入参对象 (如：GoodsQueryParam 商品查询条件)
│       │   │   └── *.DTO   # 出参对象 (如：BuffGoodsDTO 返回给前端的商品信息)
│       │   ├── entity/     # 数据库实体 (如：BuffGoods 对应数据库表结构)
│       │   ├── enums/      # 枚举常量 (如：ExteriorEnum 磨损枚举, UserStatusEnum 用户状态)
│       │   ├── mapper/     # DAO层接口 (如：BuffGoodsMapper 数据库操作)
│       │   ├── service/    # 业务逻辑层 (如：BuffGoodsServiceImpl 复杂的查询与处理逻辑)
│       │   └── NiroWebApplication.java # Spring Boot 启动类
│       │
│       └── resources/      # 资源文件
│           ├── config/     # 配置文件分类管理
│           │   ├── common/ # 公共配置 (如：database.yml 数据库账号密码)
│           │   ├── dev/    # 开发环境 (如：application-dev.yml 本地调试配置)
│           │   └── prod/   # 生产环境
│           └── application.yml # 主配置文件 (激活对应环境 profile)
│
└── pom.xml                 # Maven 父工程配置 (统一管理子模块版本与依赖)
```

### 2.7 其他规范

- 所有的配置都优先下沉到`core`模块，除非特殊需求
- 所有依赖的本号应该在父工程中统一管理，子模块只需声明`groupId` 和 `artifactId` ， 无需禁止重复指定`<version>`。
- 实体类的转换参考`List<BuffGoodsDTO> dtoList = BeanUtil.copyToList(goodsPage.getRecords(), BuffGoodsDTO.class);`，禁止使用如下形式

```java
.stream()
                .map(goods -> {
BuffGoodsSimpleDTO dto = new BuffGoodsSimpleDTO();
                  dto.setGoodsId(goods.getGoodsId());
        dto.setName(goods.getName());
        return dto;
                })
                        .collect(Collectors.toList());
```

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
