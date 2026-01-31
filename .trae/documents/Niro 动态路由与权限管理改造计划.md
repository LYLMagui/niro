# Niro 动态路由与权限管理改造计划

本方案旨在将现有的前端静态路由改造为由后端数据库驱动的动态 RBAC（基于角色的访问控制）路由体系。

## 1. 数据库设计 (Database Schema)

我们需要引入标准的 RBAC 五表模型。

* **sys\_role**: 角色定义（如：管理员、交易员）。

* **sys\_menu**: 菜单与权限定义（核心表，存储路由路径、组件路径、图标）。

* **sys\_user\_role**: 用户与角色的多对多关系。

* **sys\_role\_menu**: 角色与菜单的多对多关系。

> *注：`sys_user`* *表已存在，无需创建。*

## 2. 后端开发 (Backend Development)

### 2.1 实体与数据层 (Entity & Mapper)

* 创建 `SysMenu`, `SysRole` 等实体类，对应上述数据库表。

* 创建对应的 MyBatis-Plus `Mapper` 接口。

### 2.2 业务逻辑层 (Service)

* **SysMenuService**:

  * `selectMenuTreeByUserId(Long userId)`: 根据用户 ID 查询其拥有的所有菜单。

  * **树形构建**: 使用递归或工具类将扁平的数据库记录转换为树形结构。

* **SysRoleService**: 处理角色的分配与查询。

### 2.3 接口层 (Controller)

* 新增接口 `GET /api/user/menus`：

  * 返回经过转换的前端路由结构（Vue Router 格式）。

  * 数据结构示例：

    ```json
    [
      {
        "name": "System",
        "path": "/system",
        "component": "Layout",
        "children": [
          {
            "name": "User",
            "path": "user",
            "component": "views/system/user/index",
            "meta": { "title": "用户管理", "icon": "user" }
          }
        ]
      }
    ]
    ```

## 3. 前端改造 (Frontend Refactoring)

### 3.1 路由配置 (Router)

* **瘦身**: 将 `router/index.ts` 中的业务路由全部移除，只保留“静态白名单路由”（如 Login, 404, Dashboard）。

* **动态加载**: 将原本的静态路由配置转换为组件映射逻辑（字符串 -> `import`）。

### 3.2 状态管理 (Pinia)

* 新增 `usePermissionStore`:

  * `actions`: `generateRoutes()` —— 调用后端 API 获取菜单数据，将其转换为 Vue Router 路由对象，并保存到 State。

### 3.3 路由守卫 (Permission Guard)

* 创建或修改 `src/permission.ts`（全局路由前置守卫 `beforeEach`）：

  * 判断用户是否已获取路由信息。

  * 若未获取，触发 `usePermissionStore.generateRoutes()`。

  * 使用 `router.addRoute()` 动态挂载路由。

  * 确保 `next({ ...to, replace: true })` 以处理动态路由加载的异步问题。

### 3.4 侧边栏 (Sidebar)

* 修改侧边栏组件，使其不再读取固定的 `routes`，而是从 `PermissionStore` 读取动态生成的菜单树进行渲染。

* 侧边栏需要可以点击按钮收起侧边栏

* 调整用户头像位置，将其放在菜单栏的最底部固定，风格参考：<https://agent.minimaxi.com/>

## 4. 实施步骤

1. **数据库变更**: 执行 SQL 脚本创建表结构。
2. **后端实现**: 编写 Entity, Mapper, Service, Controller。
3. **前端重构**: 改造 Router, Store, Permission Guard。
4. **联调验证**: 配置一个测试角色，登录并验证菜单是否按预期显示。

