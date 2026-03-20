# 跨会话记忆

最后更新：2026-03-21

## 当前关注

- RBAC 重构继续按 `docs/2026-03-15-rbac-refactor-plan.md` 推进，主线位置仍在 `P3` 后半段。
- `P2` 后端授权闭环已基本收口，当前只剩少量边界整理与权限矩阵补齐。
- `P3` 前端权限落地已经从“按钮显隐”继续推进到“页面读权限不主动请求”。
- 这轮没有继续扩张权限码范围，仍坚持先复用已有权限码，再判断是否真有必要新增。
- `ProfitStats.vue` 和 `UnboxRecord.vue` 继续按本地工具页处理，只保留菜单级访问，不纳入 RBAC 细粒度权限体系。

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
- 验证已通过：
  - 后端编译：`mvn -s maven-settings.xml -pl niro-web -am -DskipTests compile`
  - 前端类型检查：`pnpm type-check`
  - Spider 语法检查：项目 `venv` 下 `python -m py_compile ...`

## 本轮总结

- 本轮先把 `P2` 的内部入口边界补强，避免 `/health`、账号状态回报、任务状态回调继续处于语义模糊状态。
- 然后把 `P3` 从“控按钮”进一步推进到“控页面读入口”，核心目标是不让无权限用户进入页面后还主动打受控接口。
- 现在主干管理页已经完成两层收口：
  - 后端真实鉴权
  - 前端按钮显隐 + 页面读守卫
- 当前剩余工作已经明显变少，主要是组件级残余入口判定和少量接口矩阵整理。

## 下一步

- 继续扫描 `TaskConfig.vue`、`CronEditor.vue`、`Layout.vue`、`TaskProgressCard.vue`，确认哪些只是辅助交互，哪些仍属于需要收口的真入口。
- 继续补完 `P2` 剩余接口权限矩阵，尤其把当前已经收口的接口与权限码整理成更完整的可审查对照表。
- 复核 `/api/buff/account/report/status` 与 `/buff/account/report/status` 的双入口现状，判断后续是否需要统一成单一入口，避免历史兼容路径长期漂着。
- 继续观察 `Settings.vue` 这种混合页是否还需要进一步拆分边界，避免一个页面混合过多不同权限语义。
- 保持“不扩权限码范围”的原则，只有遇到真实无法复用的入口时再新增权限码。
- 每轮前端改动后继续跑 `pnpm type-check`；若后端控制器、白名单或内部回调边界再动，重跑 Maven 编译。

## 备注

- 这个文件只在你明确要求“记录”时更新。
- 这里只保留跨会话可复用的信息，不记录无关过程细节。
