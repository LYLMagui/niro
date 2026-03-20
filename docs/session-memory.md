# 跨会话记忆

最后更新：2026-03-21

## 当前关注

- RBAC 重构继续按 `docs/2026-03-15-rbac-refactor-plan.md` 推进，且范围前提保持不变：**后续会移除 Buff 相关功能**。
- `P2` 和 `P3` 已在**非 Buff 面**收口完成；当前主线已切换到 `P4`（最小权限管理能力）。
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

## 本轮总结

- 本轮核心结果是把 `P2/P3` 从“冲刺中”推进到“已完成（非 Buff 面）”。
- 后端侧完成了非 Buff 面接口矩阵验收化；前端侧完成了非 Buff 面权限点清单验收化。
- 补齐了 `task:c5:list` 的前端真实入口（C5 同步按钮），消除了“常量有定义但无使用入口”的缺口。
- 计划已明确进入 `P4` 阶段，Buff 相关部分保持冻结保护直到功能下线统一清理。

## 下一步

- 启动 `P4` 最小权限管理能力设计与实现：只做“用户分配角色 + 角色分配菜单”，不扩平台。
- 补 `P4` 最小接口与前端入口后，进入 `P5` 的回归与验收清单执行。
- 按既定策略准备 Buff 退场清单：菜单、路由映射、权限常量、历史授权残留统一删除。
- 保持“不扩权限码范围”的原则，只有遇到真实无法复用的入口时再新增权限码。
- 每轮前端改动后继续跑 `pnpm type-check`；若后端控制器、白名单或内部回调边界再动，重跑 Maven 编译。

## 备注

- 这个文件只在你明确要求“记录”时更新。
- 这里只保留跨会话可复用的信息，不记录无关过程细节。
