# 跨会话记忆

最后更新：2026-03-21

## 当前关注

- RBAC 重构继续按 `docs/2026-03-15-rbac-refactor-plan.md` 推进，且范围前提保持不变：**后续会移除 Buff 相关功能**。
- `P2` 和 `P3` 已在**非 Buff 面**收口完成；`P4` 已完成首版落地，当前阻塞是本机 Java 版本未对齐（JDK8，无法完成后端编译验收）。
- Buff 相关权限继续执行“冻结规划、不回退保护、功能删除时统一清理”的策略。
- 后续仍坚持“不扩权限码范围”，先复用已有权限码。
- `ProfitStats.vue` 和 `UnboxRecord.vue` 继续按本地工具页处理，只保留菜单级访问，不纳入 RBAC 细粒度权限体系。
- 提交规则补充：所有 Git commit message 必须使用中文。

## 已完成

- 后端关键接口已接入 `SaCheckPermission`，登录相关接口与个人配置入口已补显式 `@SaCheckLogin`。
- 权限码已统一到 `PermissionConstants` / `PermissionConstant.ts`，`/category/tree` 对齐到 `system:goods:list`，`/buff/sticker/page` 与 `/buff/sticker/sync` 对齐到 `system:sticker:list` / `system:sticker:sync`。
- `v-permission` 指令已修正，不再强制 `display: block`，避免破坏布局。
- `TaskList`、`TaskConfig`、`OrderRecord`、`GoodsList`、`Settings`、`Dashboard`、`TaskProgressCard`、`Logs`、`StickerList`、`InventoryBoard` 的核心按钮已接入 `v-permission`。
- `/health` 已明确加入匿名白名单，真正作为健康检查口使用。
- `/buff/account/report/status`、`/task/callback/status` 已补可选共享令牌守卫：
  - 后端新增 `InternalCallbackGuard`
  - 未配置 token 时保持兼容
  - 配置后要求请求头携带内部令牌
- Spider 侧账号状态上报已同步携带内部回调请求头，配置入口在 `niro-spider/config/settings.py`。
- `TaskList`、`GoodsList`、`OrderRecord`、`StickerList`、`InventoryBoard`、`Dashboard`、`Logs`、`Settings` 已补“无读权限不主动请求”守卫，并改成权限就绪后自动拉取。
- `Settings.vue` 已按混合页边界处理：
  - 全局配置仍按登录态读取
  - 账号列表只在具备 `system:account:list` 时主动加载
- `TaskConfig.vue` 已补齐表单级读入口守卫：
  - 商品搜索仅在具备 `system:goods:list` 时主动请求
  - 执行账号列表仅在具备 `system:account:list` 时主动请求
  - 关联下单任务仅在具备 `task:buff:list` 时主动请求
  - 无权限时相关选择器进入禁用态，并给出明确提示
- `useGoodsSearch`、`useAccountSelect`、`AccountSelector` 已同步支持无权限不请求与禁用态占位，避免从表单辅助入口绕过前端收口。
- `CronEditor.vue`、`Layout.vue` 已复核为本地 UI/路由壳组件，不属于需要继续收口的 RBAC 真入口。
- 非 Buff 面新增 C5 手动同步前端入口：
  - `orderApi.triggerC5Sync(daysBefore)` 对齐后端 `/api/c5/order-sync/trigger`
  - `OrderRecord.vue` 新增“同步C5订单”按钮，并绑定 `task:c5:list` 权限
- 计划文档已同步：
  - `P2`、`P3` 状态更新为“已完成（非 Buff 面）”
  - 冲刺任务清单段落已移除
  - 后端接口矩阵与前端权限点清单均升级为验收版
- 验证已通过：
  - 后端编译：`mvn -s maven-settings.xml -pl niro-web -am -DskipTests compile`
  - 前端类型检查：`pnpm type-check`
  - Spider 语法检查：项目 `venv` 下 `python -m py_compile ...`
- `P4` 最小权限管理能力首版已落地（不扩权限码）：
  - 后端新增 `RbacManageController`，提供管理员接口：用户列表（含角色）、角色列表、菜单列表、用户分配角色、角色分配菜单。
  - 后端补齐 `SysUserRoleService` / `SysRoleMenuService` 的关系替换能力（先删后增，事务保护）。
  - 前端新增 `rbacApi` 与 `RbacPermissionPanel`，并在 `Settings.vue` 仅对 `admin` 角色展示权限管理面板。
- 本轮验证结果：
  - 前端 `pnpm type-check` 通过。
  - 后端 Maven 编译未通过，原因是本机 `java -version` 为 `1.8.0_472`，项目目标为 Java 21（报错：`无效的目标发行版: 21`）。

## 本轮总结

- 本轮核心结果是 `P4` 首版能力已落地：管理员可进行“用户分配角色 + 角色分配菜单”。
- 这次实现严格保持了最小范围，不新增权限码，不扩管理平台能力边界。
- 当前唯一阻塞是本机开发环境未切到 JDK21，导致后端编译验收未闭环；前端类型检查已通过。

## 下一步

- 切换到 JDK21 后重跑后端编译：`mvn -s maven-settings.xml -pl niro-web -am -DskipTests compile`。
- 以管理员账号联调 `P4` 新增接口与前端面板，确认分配后权限即时生效（刷新/重新登录）。
- 在 `P4` 验收通过后进入 `P5` 的回归与验收清单执行。
- 按既定策略准备 Buff 退场清单：菜单、路由映射、权限常量、历史授权残留统一删除。
- 保持“不扩权限码范围”的原则，只有遇到真实无法复用的入口时再新增权限码。
- 每轮前端改动后继续跑 `pnpm type-check`；若后端控制器、白名单或内部回调边界再动，重跑 Maven 编译。

## 备注

- 这个文件只在你明确要求“记录”时更新。
- 这里只保留跨会话可复用的信息，不记录无关过程细节。
