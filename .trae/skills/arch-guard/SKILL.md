---
name: arch-guard
description: Niro 项目架构守护，确保 Spring Boot 后端、Vue 前端、Python 爬虫三层架构的边界清晰，防止腐化。
---

# Niro Architecture Guard

## 触发时机
- 新增模块或功能时
- 重构现有代码时
- 引入新的第三方依赖时

## 1. 宏观架构 (System Architecture)

Niro 采用典型的 **分层架构 (Layered Architecture)**，各部分职责明确：

```
┌─────────────────┐       ┌──────────────────┐       ┌─────────────────┐
│  Frontend (Vue) │ <---> │ Backend (Spring) │ <---> │ Spider (Python) │
└─────────────────┘       └──────────────────┘       └─────────────────┘
   管理台 UI                 核心业务/API               数据采集/执行
```

### 依赖红线
- **Frontend** 只能调用 **Backend API**，禁止直连数据库。
- **Backend** 可以调用 **Spider** (通过 HTTP/MQ)，也可以直接读写 DB。
- **Spider** 独立运行，通过 DB/Redis 与 Backend 共享数据，或通过 API 回调。

---

## 2. 后端架构 (Spring Boot)

采用经典的 **Controller-Service-Mapper** 三层架构。

```
src/main/java/com/niro/
├── controller/      # Web 层 (入口)
├── service/         # 业务逻辑层 (核心)
│   └── impl/
├── mapper/          # 数据访问层 (MyBatis-Plus)
├── entity/          # 数据库实体 (POJO)
└── common/          # 公共组件 (Utils, Config)
```

### 依赖规则 (Dependency Rules)

1. **Controller 层**:
   - ✅ 只能依赖 Service。
   - ❌ 禁止依赖 Mapper。
   - ❌ 禁止包含复杂业务逻辑。
   - ✅ 负责参数校验、权限控制、统一响应封装。

2. **Service 层**:
   - ✅ 可以依赖 Mapper 或其他 Service。
   - ❌ 禁止依赖 Controller。
   - ❌ 禁止处理 HttpServletRequest/Response。
   - ❌ 禁止返回 `Result` / `ResponseEntity` (只返回业务对象)。

3. **Mapper 层**:
   - ✅ 只负责 SQL 操作。
   - ❌ 禁止包含业务逻辑。

4. **Entity 层**:
   - ✅ 纯 POJO，与数据库表一一对应。
   - ❌ 禁止包含 Repository/Service 逻辑。

### 常见违规与修正

| 违规行为 | 修正方案 |
|---------|---------|
| Controller 直接调 Mapper | 注入 Service，由 Service 调 Mapper |
| Service 返回 `Result.success(data)` | Service 返回 `data`，Controller 或 Advice 包装 |
| Service 处理 `HttpSession` | 参数透传，不要依赖 Web 容器对象 |
| Mapper 中写复杂业务计算 | 移至 Service 层 |

---

## 3. 前端架构 (Vue 3)

```
src/
├── api/             # API 定义 (必须在此)
├── views/           # 页面组件
├── components/      # 通用组件
├── store/           # 状态管理 (Pinia)
└── hooks/           # 逻辑复用 (Composables)
```

### 依赖规则
- **UI 组件 (views/components)**:
  - ❌ 禁止直接调用 `axios`。
  - ✅ 必须调用 `api/` 下定义的函数。
  - ✅ 复杂状态必须放入 `store/`。

---

## 4. 爬虫架构 (Python)

```
niro-spider/
├── spiders/         # 具体的爬虫逻辑
├── storage/         # 数据库/Redis 操作
├── utils/           # 工具库
└── main.py          # 启动入口
```

### 依赖规则
- **业务隔离**: 爬虫只负责“拿数据”和“存数据”，不负责“怎么展示”。
- **直接入库**: 爬虫通常直接写入 PG 或 Redis，不经过后端 API (为了性能)。

---

## 检查清单 (Checklist)

### 新增功能时
- [ ] Controller 是否只做路由转发？
- [ ] 业务逻辑是否全在 Service？
- [ ] 是否在 Mapper 层处理了所有 SQL 细节（如 JSONB）？
- [ ] 前端组件是否没有直接写 API URL？

### 重构时
- [ ] 是否存在循环依赖 (Service A <-> Service B)？-> 提取第三个 Service 或使用事件驱动。
- [ ] 是否有 Util 类依赖了 Service/Mapper？-> Util 应该是纯静态或无状态的。
