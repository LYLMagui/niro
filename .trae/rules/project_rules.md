## 前端Vue项目代码规范
### 1. 语法和框架规范：
- 使用 Vue 3 的 **Composition API**，不使用 Options API；
- 脚本部分必须使用 `<script setup>` 语法；
- 模板中尽量使用简洁语法，如 `v-if / v-for / v-model`，避免多余嵌套；
- 尽量避免使用 `any` 类型，优先使用 TypeScript 明确类型；
- 如果使用了异步请求，默认封装为 `useXXX()` 的组合式函数，统一放在 `composables/` 文件夹中；
- 所有页面组件统一放在 `views/` 文件夹中，通用组件放在 `components/` 中。
### 2. 命名规范：
- 文件命名统一为 **小写中划线格式**（如：`user-list.vue`）；
- 变量、函数名使用 **camelCase**；
- 组件名使用 **PascalCase**；
- 钩子函数命名使用 `use` 前缀，如 `useUserList`；
- 使用ui框架组件时，需要https://tdesign.tencent.com/vue/overview阅读该官方文档
### 3. 注释风格：
- 所有方法必须添加注释，注释风格清晰简洁，使用自然语言说明该方法的作用；
- 对于复杂逻辑、watchEffect、computed 计算过程请写明业务含义；
- 文件顶部应注明作者、创建时间和功能简述；
- 注释语气中性，不带个人语气，不重复代码内容。
### 4. 样式和布局：
- CSS框架使用tailwindcss
- 样式统一使用 BEM 命名规范；
- 组件布局使用 CSS Grid 或 Flex，禁止使用 table；
- 移动端优先使用 rem 单位，桌面端可适当使用 px
### 5. 代码结构和项目约定：
- 每个组件文件中顺序为：模板（template）> 脚本（script）> 样式（style）；
- API 请求统一封装到 `api/` 文件夹中，禁止在组件内直接写 `axios` 请求；
- 所有 API 响应结果请使用统一的响应封装器处理（如 `useRequest()`）；
- 表单组件封装时需支持 `v-model` 双向绑定。
- 依赖管理必须使用pnpm，不允许使用npm。
- 执行终端命令的时候必须分开，如果要合并成一行，要用;分割。
### 6. 响应和异常处理：
- 所有请求必须添加异常捕获处理，如 `try/catch` 或 `onError` 钩子；
- 异常提示语应通用，避免硬编码字符串；
- 页面加载时使用骨架屏或 loading 动画，避免页面空白。
### 7. 回复和生成内容要求：
- 绘制前端页面时，需要站在产品经理的角度思考，考虑用户需求和交互逻辑，避免设计过于复杂的页面。
- 回答和思考都请使用中文；
- 回答内容结构清晰，重点内容可使用列表展示；
- 不推荐使用未经引入的三方包，如 lodash、dayjs，除非已有安装；
- 如生成表单、表格代码，请基于 TDesign 框架；
- 建议代码块加上简要解释，便于理解。
- 每次改动代码后，都需要简要归纳修改了哪些文件，以及修改的内容

## 后端springboot项目代码规范

### **1. 语法和框架规范：**
- 使用 **Spring Boot 3.x** 版本，JDK 版本要求 **21 及以上**；
- 持久层统一使用 **MyBatis-Plus**，禁止使用原生 XML SQL 或手写 SQL，优先使用 `LambdaQueryWrapper` 和 `IService` 接口；
- 控制器层必须使用 `@RestController` 注解，禁止使用 `@Controller` + `@ResponseBody` 组合；
- 配置类使用 `@Configuration` 注解，并明确标识为配置类，禁止在启动类中扫描非业务包；
- 使用 Lombok 简化实体类代码，必须引入 `lombok` 依赖并使用 `@Data`、`@Builder` 等注解；
- 所有接口方法返回值必须封装为 `Result<T>` 统一响应对象，禁止直接返回实体对象；
- 使用 `application.yml` 作为配置文件，禁止使用 `.properties` 格式；
- 依赖注入统一使用 **构造函数注入**，禁止使用 `@Autowired` 字段注入；

### **2. 命名规范：**
- **包名**统一使用 **全小写单数**形式，如：`com.project.system.service`；
- **类名**使用 **UpperCamelCase**，如：`UserServiceImpl`、`OrderController`；
- **接口名**以功能描述 + `Service` 结尾，如：`UserService`；
- **实现类**必须在接口名后加 `Impl` 后缀，如：`UserServiceImpl`；
- **方法名**使用 **lowerCamelCase**，如：`getUserById()`、`handleUserLogin()`；
- **常量名**使用 **全大写 + 下划线**，如：`MAX_RETRY_TIMES`、`DEFAULT_PAGE_SIZE`；
- **数据库表字段**统一使用 **小写 + 下划线**，如：`user_name`、`create_time`；
- **DTO / VO / PO** 类名必须带上后缀，如：`UserQueryDTO`、`UserDetailVO`；
- MyBatis-Plus 的 **Mapper** 接口以 `Mapper` 结尾，如：`UserMapper`；
- **枚举类**以 `Enum` 结尾，如：`UserStatusEnum`；

### **3. 注释风格：**
- 所有 **类、接口、方法、字段** 必须添加 **Javadoc** 标准注释，格式为 `/** 内容 */`；
- 类注释必须包含： **@author**、 **@date** 创建时间、 **@description** 功能简述；
- 方法注释必须清晰说明业务作用、 **@param** 参数含义、 **@return** 返回值含义；
- 对于复杂业务逻辑、算法实现、边界条件处理，必须在方法内部使用 `//` 分段注释说明；
- 注释内容使用自然语言描述 **"做什么"** ，禁止重复描述 **"怎么做"** （即不重复代码逻辑）；
- 注释语气保持中性、专业，禁止出现个人语气词或无用标记（如：`// TODO`、`// FIXME` 必须附带责任人）；
- 枚举类必须注释每个枚举值的业务含义，`Mapper` 方法必须注释对应的 SQL 业务场景；

### **4. 日志和配置规范：**
- 日志统一使用 **SLF4J** 门面，变量占位符使用 `{}`，禁止字符串拼接；
- 日志级别规范：`DEBUG` 用于开发调试，`INFO` 用于业务关键流程，`WARN` 用于可恢复异常，`ERROR` 用于严重错误；
- 配置文件必须区分多环境，如：`application-dev.yml`、`application-prod.yml`；
- 敏感信息（数据库密码、API Key）必须配置在环境变量或使用配置中心，禁止硬编码；
- 代码缩进使用 **4 个空格**，禁止使用 Tab 字符；
- 方法行数不超过 **80 行**，单参数列表超 5 个必须封装为 DTO 对象；
- **魔法数字** 必须定义为静态常量，禁止在业务代码中直接出现数字字面量；

### **5. 代码结构和项目约定：**
- 项目包结构必须严格分层：
  - `controller`：仅处理 HTTP 请求与参数校验
  - `service`：业务逻辑接口层
  - `service.impl`：业务逻辑实现层，必须实现 service 接口
  - `mapper`：MyBatis-Plus Mapper 接口层
  - `entity`：数据库实体对象（PO）
  - `dto`：数据传输对象（入参）
  - `vo`：视图返回对象（出参）
  - `enums`：枚举类
  - `config`：配置类
  - `utils`：工具类（需无状态且线程安全）
- API 请求统一封装在 `api/` 模块（若微服务架构），禁止在 Controller 中直接调用远程服务；
- 必须使用 MyBatis-Plus 提供的 `Page<T>` 对象进行分页，禁止手动计算 `limit` 和 `offset`；
- 所有 **实体类** 必须实现 `Serializable` 接口，并生成 `serialVersionUID`；
- 使用 `mybatis-plus-generator` 生成代码时，必须自定义模板，确保符合本规范；
- 依赖管理必须使用 **Maven** 或 **Gradle**，禁止使用本地 JAR 包引入；
- 执行终端命令时多行命令必须分开，单行内多条命令使用 `;` 分隔；
- 注入bean使用构造函数注入，并加上`@RequiredArgsConstructor`注解，禁止使用`@Autowired`字段注入；
- 禁止创建VO对象，当前项目中只使用DTO对象进行数据传输；
### **6. 响应和异常处理：**
- 所有 Controller 方法必须返回 `Result<T>` 统一响应体，格式固定为：`{ code, msg, data }`；
- 全局异常处理必须通过 `@RestControllerAdvice` 统一捕获，禁止在业务代码中大量使用 `try-catch`；
- 业务异常定义 `BusinessException` 继承 `RuntimeException`，错误码使用枚举管理；
- 参数校验统一使用 **JSR303** 注解（`@NotNull`、`@Size` 等），在 Controller 层添加 `@Valid` 注解；
- MyBatis-Plus 的删除和更新操作必须判断影响行数，删除操作需进行逻辑删除（使用 `@TableLogic`）；
- 所有外部接口调用、数据库操作、文件操作必须记录 **INFO** 级别日志，异常时记录 **ERROR** 并附带业务上下文；
- HTTP 状态码使用规范：`200` 代表成功，`400` 参数错误，`401` 未认证，`403` 无权限，`500` 服务器错误；

### **7. 回复和生成内容要求：**
- 回答和思考均使用 **中文**，技术术语保留英文（如：`MyBatis-Plus`、`LambdaQueryWrapper`）；
- 回答内容结构清晰，重点内容使用 **列表** 或 **加粗** 突出；
- 不推荐使用未经引入的第三方工具类，如 `Guava`、`Hutool`，除非显式声明依赖；
- 生成代码示例必须基于 **MyBatis-Plus** 官方推荐写法，优先使用 `IService` 和 `ServiceImpl`；
- 生成的 **Mapper**、**Service**、**Controller** 代码必须附带简要说明，解释关键注解和业务逻辑；
- 对于复杂查询，必须展示 `lambdaQuery()`的链式调用写法,禁止写`XML SQL`，复杂联表查询语句拆分为多次查询再合并结果；
- 生成的代码风格必须参考已有代码的风格，保持一致，不能使用不同的代码风格；
- 禁止在示例代码中硬编码数据库连接信息或敏感数据，必须使用占位符；
- 生成单元测试代码时，必须使用 **JUnit 5** + **Mockito**，并展示 MyBatis-Plus 的 `Mock` 测试方案；
- 每次改动代码后，都需要简要归纳修改了哪些文件，以及修改的内容

## 其他要求
- 每次回答问题时，需要从架构师和产品经理的角度去思考，业务是否符合实际需求？还有哪些操作或步骤可以优化？如何提升用户体验？如何提高系统性能？数据库设计是否合理？功能设计是否能满足当前的最小功能需求？如果用户提出的问题或逻辑在你思考后觉得有优化空间或者更好的建议，请不要直接改动或生成代码，而是回复建议，用户接受了再改动代码