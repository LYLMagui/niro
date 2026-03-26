# 初始化角色、用户与菜单数据 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在 PostgreSQL 首次初始化时，自动写入基础角色、基础用户、用户-角色关联以及全部菜单数据，保证简化版环境启动后即可直接登录并加载路由。

**架构：** 继续沿用 `postgres` 官方镜像的 `docker-entrypoint-initdb.d` 初始化机制，把 schema 和 seed data 合并到同一套初始化脚本里。先建表，再插入角色、用户和菜单，再补关联关系，保持种子数据幂等，避免重复初始化时冲突。

**技术栈：** PostgreSQL 18、docker-compose、SQL、现有 `sys_*` 表结构、`init-menu.sql` 既有菜单数据源

---

## 文件结构

**创建文件：**
- 无

**修改文件：**
- `niro-server/sql/001_init_schema.sql`：在表结构之后追加 seed data，包含角色、用户、菜单与关联关系
- `docker-compose.test.yml`：已挂载 `niro-server/sql/`，无需再改初始化流程本身；如后续发现挂载路径不一致再微调

**职责约束：**
- `001_init_schema.sql` 继续作为初始化唯一入口，不拆成多份脚本
- seed data 只覆盖启动所需基础数据，不引入额外业务样本
- 角色、用户、菜单、关联关系必须彼此匹配，不能出现孤立数据
- 菜单插入顺序必须满足父子层级，先目录再子菜单，再按钮

---

### 任务 1：整理初始化种子数据边界

**文件：**
- 修改：`niro-server/sql/001_init_schema.sql`
- 参考：`niro-server/CLAUDE.md`
- 参考：`docker-compose.test.yml`

- [ ] **步骤 1：明确要写入的数据集**

固定 seed data 范围：
- `sys_role`：管理员、普通用户
- `sys_user`：`admin`、`user`
- `sys_user_role`：管理员绑定管理员角色，普通用户绑定普通用户角色
- `sys_menu`：写入当前简化版需要的全部菜单记录

- [ ] **步骤 2：确认初始化顺序**

顺序固定为：
1. `create table`
2. `comment on table/column`
3. `insert into sys_role`
4. `insert into sys_user`
5. `insert into sys_user_role`
6. `insert into sys_menu`

- [ ] **步骤 3：确认幂等策略**

每条 seed data 语句都必须能在重复执行时避免冲突。优先使用：
- `on conflict do nothing`
- 或者基于唯一键的精确冲突目标

---

### 任务 2：补充角色与用户初始化数据

**文件：**
- 修改：`niro-server/sql/001_init_schema.sql`
- 测试：`docker-compose.test.yml` 启动后的数据库内容

- [ ] **步骤 1：编写失败的检查**

先固定 seed data 目标，确认当前脚本里还没有角色、用户、关联关系插入语句。

```text
- sys_role 没有管理员/普通用户初始化数据
- sys_user 没有 admin/user 初始化数据
- sys_user_role 没有基础关联
```

- [ ] **步骤 2：运行检查验证当前失败**

运行：
```bash
git grep -nE "insert into public\.sys_role|insert into public\.sys_user|insert into public\.sys_user_role" -- niro-server/sql/001_init_schema.sql
```

预期：FAIL，没有 seed data 片段。

- [ ] **步骤 3：编写最少实现代码**

在 `001_init_schema.sql` 末尾追加插入语句：
- `sys_role` 插入两条基础角色
- `sys_user` 插入两条基础用户
- `sys_user_role` 插入对应关联

要求：
- 使用现有列结构，不额外加字段
- 默认密码沿用当前项目约定
- 不写无意义测试数据

- [ ] **步骤 4：运行检查验证通过**

运行：
```bash
git grep -nE "insert into public\.sys_role|insert into public\.sys_user|insert into public\.sys_user_role" -- niro-server/sql/001_init_schema.sql
```

预期：PASS，能看到三类插入语句。

- [ ] **步骤 5：Commit**

```bash
git add niro-server/sql/001_init_schema.sql
git commit -m "feat(sql): 初始化基础角色与用户数据"
```

---

### 任务 3：补充菜单初始化数据

**文件：**
- 修改：`niro-server/sql/001_init_schema.sql`
- 参考：当前数据库 `sys_menu` 结构

- [ ] **步骤 1：编写失败的检查**

先固定“菜单初始化数据存在且有层级顺序”的目标。

- [ ] **步骤 2：运行检查验证当前失败**

运行：
```bash
git grep -n "insert into public.sys_menu" -- niro-server/sql/001_init_schema.sql
```

预期：FAIL，没有菜单插入语句。

- [ ] **步骤 3：编写最少实现代码**

在 `001_init_schema.sql` 末尾追加 `sys_menu` 插入数据：
- 按父子层级顺序插入
- 保持 `path`、`component`、`permission` 与当前前端/后端路由约定一致
- 保留当前简化版仍在使用的菜单
- 如果某些菜单属于已下线能力，不要写入

要求：
- 父菜单先插，子菜单后插
- 菜单 id、parent_id、path、name、component 一一对应
- 不保留无效入口和“假按钮”

- [ ] **步骤 4：运行检查验证通过**

运行：
```bash
git grep -n "insert into public.sys_menu" -- niro-server/sql/001_init_schema.sql
```

预期：PASS，能看到菜单插入语句。

- [ ] **步骤 5：Commit**

```bash
git add niro-server/sql/001_init_schema.sql
git commit -m "feat(sql): 初始化系统菜单数据"
```

---

### 任务 4：验证 Docker 初始化链路

**文件：**
- 修改：`docker-compose.test.yml`
- 测试：容器启动后的 PostgreSQL 数据内容

- [ ] **步骤 1：编写失败的检查**

固定验证目标：容器首次启动后会自动执行 `niro-server/sql/001_init_schema.sql`。

- [ ] **步骤 2：运行检查验证当前失败**

运行：
```bash
docker compose -f docker-compose.test.yml config
```

预期：PASS，确认挂载路径仍指向 `/docker-entrypoint-initdb.d`。

- [ ] **步骤 3：执行初始化验证**

在清空测试卷或使用全新卷的前提下启动数据库容器，确认初始化脚本会执行。

建议命令：
```bash
docker compose -f docker-compose.test.yml up -d niro-db-test
```

- [ ] **步骤 4：验证种子数据落库**

启动后查询：
- `sys_role` 是否有管理员、普通用户
- `sys_user` 是否有 `admin`、`user`
- `sys_menu` 是否有菜单数据
- `sys_user_role` 是否有关联关系

- [ ] **步骤 5：Commit**

```bash
git add docker-compose.test.yml
# 如果没有改动，这一步跳过
git commit -m "fix(docker): 挂载数据库初始化脚本"
```

---

### 任务 5：回归检查与收尾

**文件：**
- 修改：`niro-server/sql/001_init_schema.sql`
- 可能修改：`docs/superpowers/plans/2026-03-26-init-seed-data.md`

- [ ] **步骤 1：检查 SQL 语法和顺序**

确认：
- 所有 `insert` 都在建表之后
- 所有菜单父节点都先于子节点
- 没有重复主键冲突
- 没有漏掉必要的 `comment` 和关联关系

- [ ] **步骤 2：手动验证初始化结果**

使用测试库验证种子数据是否可用于：
- 登录
- 路由加载
- 权限菜单展示

- [ ] **步骤 3：整理最终提交**

如果 seed data 和菜单数据最终一次完成，允许合并为一次提交；如果验证时拆开修复，保持小步提交。

- [ ] **步骤 4：更新计划状态**

把已完成步骤标记为完成，清掉不再相关的临时项。

---

## 验证标准

- PostgreSQL 首次初始化后，基础角色、用户、菜单、关联关系都存在
- `admin` 和普通用户能直接用于登录或权限验证
- 菜单数据能驱动前端动态路由加载
- 重复启动不会重复插入冲突数据
- 没有把已下线能力的菜单重新种回去
