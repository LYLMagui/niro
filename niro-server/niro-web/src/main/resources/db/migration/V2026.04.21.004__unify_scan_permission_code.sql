-- ============================================================
-- 扫货权限码统一迁移（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-21
-- 目标:
-- 1. 将扫货菜单权限统一为 task:scan:list
-- 2. 将历史 task:buff:list / task:c5:list 菜单授权迁移到新的扫货菜单
-- 3. 保留历史菜单权限码作为兼容常量，不再作为前端主判断依据
--
-- 幂等说明:
-- - 按 sys_menu.name / permission 精确定位，重复执行安全
-- - sys_role_menu 通过 not exists 回填，避免重复授权
--
-- 回滚思路:
-- - 如需回滚，另开新 migration 将扫货菜单 permission 改回旧值并恢复角色授权
-- - 不直接修改历史 migration
-- ============================================================

begin;

-- 1. 统一“扫货”菜单权限码
update public.sys_menu
set permission = 'task:scan:list',
    update_time = now()
where del_flag = 0
  and name = 'Task'
  and type = 1
  and coalesce(permission, '') <> 'task:scan:list';

-- 2. 把历史任务菜单权限的角色授权迁移到新的“扫货”菜单
with scan_menu as (
    select id
    from public.sys_menu
    where del_flag = 0
      and name = 'Task'
      and type = 1
      and permission = 'task:scan:list'
    order by id
    limit 1
),
legacy_task_roles as (
    select distinct rm.role_id
    from public.sys_role_menu rm
    join public.sys_menu m on m.id = rm.menu_id
    where m.del_flag = 0
      and m.permission in ('task:buff:list', 'task:c5:list')
)
insert into public.sys_role_menu (role_id, menu_id)
select r.role_id, s.id
from legacy_task_roles r
cross join scan_menu s
where not exists (
    select 1
    from public.sys_role_menu rm
    where rm.role_id = r.role_id
      and rm.menu_id = s.id
);

commit;

-- ============================================================
-- 执行后复核（可选）
-- ============================================================
-- select id, title, name, path, permission, type, del_flag
-- from public.sys_menu
-- where name in ('Task', 'BuffTask', 'C5Task')
-- order by id;
--
-- select r.role_key, m.title, m.permission
-- from public.sys_role_menu rm
-- join public.sys_role r on r.role_id = rm.role_id and r.del_flag = 0
-- join public.sys_menu m on m.id = rm.menu_id and m.del_flag = 0
-- where m.permission in ('task:scan:list', 'task:buff:list', 'task:c5:list')
-- order by r.role_key, m.permission;
