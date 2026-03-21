-- ============================================================
-- RBAC P2 - 新增权限管理菜单（PostgreSQL）
-- 日期: 2026-03-21
-- 目标:
-- 1. 在“系统管理”目录下新增“权限管理”菜单（幂等）
-- 2. 为 admin 角色补齐该菜单授权（幂等）
-- ============================================================

begin;

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
        '权限管理',
        'PermissionManage',
        'permission',
        'permission',
        'secured',
        coalesce((
            select max(m.sort_order)
            from public.sys_menu m
            where m.parent_id = s.id
              and m.del_flag = 0
        ), 0) + 1,
        1,
        'system:permission:manage',
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
              e.permission = 'system:permission:manage'
              or e.path = 'permission'
              or e.component = 'permission'
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
    where e.permission = 'system:permission:manage'
       or e.path = 'permission'
       or e.component = 'permission'
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

-- 复核 SQL（可选）
-- select id, parent_id, title, name, path, component, permission, type, status
-- from public.sys_menu
-- where del_flag = 0 and (path = 'permission' or component = 'permission' or permission = 'system:permission:manage')
-- order by id;
