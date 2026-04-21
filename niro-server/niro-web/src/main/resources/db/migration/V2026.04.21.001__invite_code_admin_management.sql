-- ============================================================
-- 邀请码管理与一码一人模型（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-21
-- 目标:
-- 1. 为 sys_invite_code 新增 used_user_id、used_at，切到“一码一人”模型
-- 2. 补齐“系统 -> 邀请码管理”菜单与页面权限
-- 3. 为 admin 角色补发该菜单授权
--
-- 幂等说明:
-- - 字段通过 add column if not exists 保护
-- - 字段注释重复执行安全
-- - 菜单按 parent/path/component/permission 多条件兜底，避免重复插入
-- - admin 授权通过 not exists 保护
--
-- 回滚思路:
-- - 如需回滚，另开新 migration 软删除菜单并移除新增字段引用
-- - 不直接修改或回滚历史 migration
-- ============================================================

begin;

-- 1. 邀请码表新增“一码一人”字段 ---------------------------------
alter table public.sys_invite_code
    add column if not exists used_user_id bigint not null default 0;

alter table public.sys_invite_code
    add column if not exists used_at timestamp not null default '1970-01-01 00:00:00';

update public.sys_invite_code
set used_user_id = -1,
    used_at = case
                  when updated_at is not null then updated_at
                  else now()
              end,
    updated_at = now()
where used_user_id = 0
  and used_count > 0;

comment on column public.sys_invite_code.used_user_id is '已使用时绑定的用户ID，0 表示未使用，-1 表示历史已使用但无绑定用户';
comment on column public.sys_invite_code.used_at is '已使用时间，1970-01-01 00:00:00 表示未使用';

create index if not exists idx_sys_invite_code_status_used_user_id
    on public.sys_invite_code (status, used_user_id);

create index if not exists idx_sys_invite_code_used_user_id
    on public.sys_invite_code (used_user_id);

-- 2. 系统目录下新增“邀请码管理”菜单 -------------------------------
with system_dir as (
    select id
    from public.sys_menu
    where del_flag = 0
      and type = 0
      and path = 'system'
    order by id
    limit 1
),
inserted_menu as (
    insert into public.sys_menu (
        parent_id,
        title,
        name,
        path,
        component,
        icon,
        sort_order,
        type,
        permission,
        status,
        hidden,
        keep_alive,
        redirect,
        del_flag
    )
    select
        s.id,
        '邀请码管理',
        'InviteCodeManage',
        'invite-code',
        'invite-code',
        'secured',
        coalesce((
            select max(m.sort_order)
            from public.sys_menu m
            where m.parent_id = s.id
              and m.del_flag = 0
        ), 0) + 1,
        1,
        'system:invite-code:manage',
        1,
        false,
        true,
        null,
        0
    from system_dir s
    where not exists (
        select 1
        from public.sys_menu e
        where e.del_flag = 0
          and e.parent_id = s.id
          and (
              e.permission = 'system:invite-code:manage'
              or e.path = 'invite-code'
              or e.component in ('invite-code', 'invitecode')
          )
    )
    returning id
),
target_menu as (
    select id from inserted_menu
    union all
    select e.id
    from system_dir s
    join public.sys_menu e on e.parent_id = s.id and e.del_flag = 0
    where e.permission = 'system:invite-code:manage'
       or e.path = 'invite-code'
       or e.component in ('invite-code', 'invitecode')
    order by id
    limit 1
),
admin_role as (
    select role_id
    from public.sys_role
    where del_flag = 0
      and role_key = 'admin'
    order by role_id
    limit 1
)
insert into public.sys_role_menu (role_id, menu_id)
select ar.role_id, tm.id
from admin_role ar
cross join target_menu tm
where not exists (
    select 1
    from public.sys_role_menu rm
    where rm.role_id = ar.role_id
      and rm.menu_id = tm.id
);

commit;

-- ============================================================
-- 执行后复核（可选）
-- ============================================================
-- select code, status, used_user_id, used_at, expire_time
-- from public.sys_invite_code
-- order by id desc;
--
-- select id, parent_id, title, name, path, component, permission, type, status
-- from public.sys_menu
-- where del_flag = 0 and (path = 'invite-code' or permission = 'system:invite-code:manage')
-- order by id;
