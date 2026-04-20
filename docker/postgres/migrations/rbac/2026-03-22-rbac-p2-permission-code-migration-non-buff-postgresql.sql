-- ============================================================
-- RBAC P2 - 非 Buff 权限码三段式迁移脚本（PostgreSQL）
-- 日期: 2026-03-22
-- 目标:
-- 1. 将历史权限码迁移到三段式:
--    settings:save -> system:settings:save
--    settings:test-notify -> system:settings:test-notify
--    notify:send -> system:notify:send
-- 2. 脚本可重复执行
-- ============================================================

begin;

update public.sys_menu
set permission = 'system:settings:save'
where del_flag = 0
  and permission = 'settings:save';

update public.sys_menu
set permission = 'system:settings:test-notify'
where del_flag = 0
  and permission = 'settings:test-notify';

update public.sys_menu
set permission = 'system:notify:send'
where del_flag = 0
  and permission = 'notify:send';

commit;

-- 复核 SQL（可选）
-- select id, title, path, permission, status, del_flag
-- from public.sys_menu
-- where permission in (
--   'settings:save', 'settings:test-notify', 'notify:send',
--   'system:settings:save', 'system:settings:test-notify', 'system:notify:send'
-- )
-- order by id;
