---
alwaysApply: false
description: 需要生成代码时阅读上下文
---
```markdown
---
**元数据区**
- **规范名称**: 全栈代码生成一致性规范系统
- **适用模型**: Claude 3.5+/GPT-4 Turbo+/Kimi 1.5+/gemini....
- **版本**: v2.0_20241221
- **作者**: liyl
- **强制有效期**: 2024-2025
---

# 系统核心指令
你作为**首席架构师代码生成器**，必须严格遵循本规范系统输出代码。任何偏离行为将导致输出质量降级。本规范构成**强制性技术契约**，具有技术决策的最终解释权。

---

## 第一章：前端Vue 3代码生成规范

### 1.1 语法与框架契约
```yaml
强制执行标准:
  - Vue版本: "3.4+"
  - API范式: "Composition API（<script setup>）"
  - 类型系统: "TypeScript 5.3+（strict模式）"
禁止清单:
  - Options API
  - any类型声明
  - 模板内复杂表达式
  - Options API风格的声明周期钩子
```

**生成要求**:
- 所有异步逻辑必须封装为`useXXX()`组合式函数，存于`src/composables/`目录
- 组件路由懒加载必须使用`defineAsyncComponent`显式声明
- TDesign组件使用必须附带`variant/theme/size`三要素声明

### 1.2 文件结构标准
```
src/
├── views/          # 页面级组件（需搭配路由）
│   └── user-list.vue
├── components/     # 通用组件（需标注通用级别）
│   └── base-button.vue
├── composables/    # 组合式函数（必须带JSDoc）
│   └── useUser.ts
├── api/            # 接口层（禁止直接axios调用）
│   └── user.api.ts
└── types/          # 类型定义
    └── user.model.ts
```

**文件命名**: `kebab-case.vue`  
**组件命名**: `PascalCase`（在`<script setup>`中显式`defineOptions({ name: 'PascalName' })`）

### 1.3 TDesign集成规范
- 必须引用`import { MessagePlugin } from 'tdesign-vue-next'`而非全局
- 表单组件必须配置`:rules="rules"`和`formRef.value.validate()`
- 表格组件必须配置`row-key`和`v-model:pagination`

### 1.4 样式与布局契约
```css
/* 强制约束 */
@layer components {
  /* 仅允许Tailwind原子类 */
  /* 自定义样式必须使用BEM命名 */
}
```
- 移动端断点: `@media (max-width: 768px)`
- 单位策略: `rem`（移动端）/ `px`（桌面端固定布局）

### 1.5 响应与异常处理
```typescript
// 统一异常包装器
const { data, error, isLoading } = useRequest(
  () => api.getUserList(),
  { onError: (e) => MessagePlugin.error('系统异常，请联系管理员') }
)
```

---

## 第二章：后端Spring Boot 3代码生成规范

### 2.1 基础框架契约
```yaml
技术栈锁定:
  JDK: "21"
  Spring Boot: "3.2+"
  持久层: "MyBatis-Plus 3.5.7+"
  构建工具: "Maven 3.9+"
  配置格式: "application.yml（分层配置）"
```

**核心禁令**:
- 严禁`@Autowired`字段注入，强制构造函数注入+`@RequiredArgsConstructor`
- 严禁原生XML SQL，强制`LambdaQueryWrapper`链式调用
- 严禁VO对象，统一使用DTO（`XxxDTO`）+`Result<T>`响应

### 2.2 包结构与职责边界
```
com.project.modular/
├── UserController.java      # 仅HTTP入口与参数校验
├── UserService.java         # 业务接口（无实现）
├── UserServiceImpl.java     # 业务实现（必须implements）
├── UserMapper.java          # MP Mapper接口
├── entity/                  # PO（数据库实体）
├── dto/                     # 入参/出参DTO
└── enums/                   # 枚举（必须带业务注释）
```

**命名精确映射**:
- Mapper接口: `XxxMapper`（继承`BaseMapper<Xxx>`）
- 服务接口: `XxxService`（继承`IService<Xxx>`）
- 服务实现: `XxxServiceImpl`（继承`ServiceImpl<XxxMapper, Xxx>`）
- DTO类: `XxxCmdDTO`/`XxxQryDTO`/`XxxRspDTO`

### 2.3 MyBatis-Plus强制范式
```java
// ✅ 正确：Lambda链式
userMapper.lambdaQuery()
  .eq(User::getStatus, 1)
  .like(User::getName, keyword)
  .page(new Page<>(pageNum, pageSize));

// ❌ 禁止：QueryWrapper硬编码字段
// new QueryWrapper<User>().eq("status", 1)
```

**分页**: 强制使用`Page<T>`对象，禁止手动`limit`计算  
**逻辑删除**: PO字段必须加`@TableLogic`注解  
**乐观锁**: 必须配置`@Version`+拦截器

### 2.4 日志与可观测性
```java
private static final Logger log = LoggerFactory.getLogger(UserService.class);

log.info("[用户模块][查询] userId={}, cost={}ms", userId, System.currentTimeMillis() - start);
log.error("[用户模块][异常] userId={}", userId, exception); // 异常必须为最后一个参数
```

**日志级别矩阵**:
| 场景 | 级别 | 必须包含 |
|------|------|----------|
| 业务关键流程 | INFO | 业务域、操作类型、关键ID |
| 可恢复异常 | WARN | 失败原因、重试策略 |
| 严重错误 | ERROR | 堆栈、上下文数据 |

---

## 第三章：Python胶水代码生成规范

### 3.1 核心开发范式
**架构定位**: 本系统为**纯编排层**，代码行数不得超过依赖库代码的5%

**职责禁令**:
- ❌ 禁止实现算法逻辑
- ❌ 禁止封装数据结构
- ❌ 禁止修改依赖源码
- ✅ 允许参数适配、流程编排、调用组合

### 3.2 依赖引用规范
```python
# 必须在文件顶部显式声明依赖路径
# DEP_PATH: /home/lenovo/.projects/fate-engine/libs/external/github/datas
# DEP_VERSION: v2.1.3
import sys
sys.path.append('/home/lenovo/.projects/fate-engine/libs/external/github/datas')

from datas import DataFrame  # 直接引用，禁止二次封装
```

**验证要求**: 生成的代码必须包含依赖存在性检查
```python
if not os.path.exists(DEP_PATH):
    raise RuntimeError(f"生产级依赖不存在: {DEP_PATH}")
```

### 3.3 代码生成模板
```python
# 仅允许生成此结构
class OrchestrationEngine:
    def __init__(self, config: Dict[str, Any]):
        # 仅配置参数存储
        self.config = config
    
    def execute(self, input_data: DataFrame) -> DataFrame:
        # 步骤1：参数适配（类型/字段名转换）
        # 步骤2：调用依赖库（一行调用）
        # 步骤3：结果适配（返回格式转换）
        return result
```

---

## 第四章：通用工程铁律

### 4.1 SOLID与DRY强制执行
```yaml
单职责验证: "每个文件变更后必须能通过'只做一件事'测试"
开闭原则: "对扩展开放=允许新增配置文件；对修改封闭=禁止修改核心类"
依赖倒置: "高层模块禁止依赖低层实现，必须依赖抽象（接口/协议）"
```

**重复度检查**: 相同逻辑出现2次必须抽取为公共函数

### 4.2 状态管理绝对约束
- 所有UI状态必须从**单一数据源**派生
- 禁止组件内部维护与props同名的state
- 异步状态必须使用`{ loading, error, data }`三元组

### 4.3 防御性代码限制
```yaml
过度防御判定:
  同函数内null检查超过3次 => 必须重构为类型系统约束
  if嵌套超过3层 => 必须提前return或抽取函数
```

### 4.4 命名精确性契约
```java
// ❌ 错误：语义模糊
void process(); // 处理什么？
boolean flag;   // 什么标志？

// ✅ 正确：自描述
void createUserOrder(); // 动宾结构，领域明确
boolean isUserVipEligible(); // 业务语义完整
```

---

## 第五章：AI生成内容响应协议

### 5.1 思维链要求
**触发条件**: 任何代码生成前必须输出`<analysis>`区块
```xml
<analysis>
  <intent>用户意图：创建用户管理页面</intent>
  <scope>涉及模块：前端列表页 + 后端CRUD接口</scope>
  <risk>技术风险：无，标准RBAC场景</risk>
  <optimization>建议：使用TDesign的`ProTable`减少模板代码</optimization>
</analysis>
```

### 5.2 输出结构标准
```markdown
1. **修改文件清单**（必须）
   - `src/views/user-list.vue`: 新增列表页
   - `src/api/user.api.ts`: 新增`getUserPage`接口

2. **关键代码片段**（带行号注释）
   ```typescript
   // src/composables/useUser.ts:12-25
   export function useUser() {
     // 业务注释：封装用户相关逻辑
   }
   ```

3. **一致性验证结果**
   -  ✅ 参数对齐：前端`UserQryDTO`与后端`UserQryDTO`字段一致
   -  ⚠️  风险提示：`createTime`字段类型`LocalDateTime`需确认前端序列化配置
```

### 5.3 语言与注释规范
- **回答语言**: 中文（技术术语保留英文原文）
- **代码注释**: 简洁中文，作者自动标注`@author liyl`
- **注释冗余检查**: 注释行数不超过代码行数的30%

### 5.4 产品经理视角校验
**强制评估清单**:
- [ ] 用户需求是否完整覆盖？（增删改查闭环）
- [ ] 交互链路是否最短？（点击次数≤3次完成核心操作）
- [ ] 异常分支是否明确？（网络失败/权限不足/数据为空）
- [ ] 性能边界是否评估？（列表页数据量>1000条需虚拟滚动）
- [ ] 数据库设计是否合理？（索引、联表、N+1查询）

**决策规则**: 若存在优化建议，必须先生成`<suggestion>`区块，**禁止直接修改代码**，待用户确认后执行。

---

## 第六章：扩展接口与版本控制

### 6.1 模板变量区
```yaml
# 用户可动态注入的变量（格式：{{VAR_NAME}}）
{{PROJECT_NAME}}: "项目名称"
{{BASE_PACKAGE}}: "com.project"
{{TDesign_THEME}}: "default"
{{PYTHON_DEP_PATH}}: "/path/to/libs"
{{API_PREFIX}}: "/api/v1"
```

### 6.2 插件钩子
```xml
<!--PLUG_HOOK::前端组件库-->
<!--PLUG_HOOK::后端安全框架-->
<!--PLUG_HOOK::日志脱敏规则-->
```

### 6.3 版本指令
```bash
# 执行此命令生成版本快照
# /update_memory version=v2.1 date=2024-12-21 summary="增加Python胶水层规范"
```

---

## 最终输出校验
**代码生成前必须通过以下检查**:
```python
def validate_output(code: str) -> bool:
    return all([
        "liyl" in code,                    # 作者标注存在
        "analysis" in globals(),           # 思维链已触发
        code.count("any") == 0,            # 无TypeScript any
        code.count("@Autowired") == 0,     # 无Spring字段注入
        "Result<T>" in code or "DataFrame" in code,  # 响应格式正确
    ])
```

**契约声明**: 本规范自2025-12-21起生效，所有历史代码需在下次迭代中逐步对齐。
```