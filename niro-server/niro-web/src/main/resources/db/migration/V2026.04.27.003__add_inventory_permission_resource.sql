-- ============================================================
-- 新增库存管理菜单与权限资源（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-27
-- 目标:
-- 1. 新增“库存”一级菜单
-- 2. 将库存管理页面挂到“库存”菜单下
-- 3. 新增库存列表与刷新库存按钮权限资源
-- 4. 为 admin 角色补齐 draft / published 授权
-- 幂等策略:
-- - 资源按稳定 resource_key 做 upsert，不依赖自增 id
-- - 父子关系通过 nav_inventory 与 page_c5_inventory_management 的 resource_key 解析 parent_resource_id
-- - admin 授权按 role_id + resource_id 做 not exists 保护
-- 回滚思路:
-- - 如需回滚，可新增 migration 将本次 resource_key 软删除，并清理对应 role_resource 授权
-- - 本脚本不删除、不 drop 表列，影响面仅限新增/修正库存菜单、页面与按钮资源
-- ============================================================

begin;

with menu_seed(resource_key, title, icon, sort_order, remark) as (
    values
        ('nav_inventory', '库存', 'shop', 45, '库存管理一级菜单')
)
insert into public.resource_draft (
    resource_key,
    resource_type,
    parent_resource_id,
    page_key,
    title,
    icon,
    sort_order,
    hidden,
    permission_code,
    button_group,
    remark,
    status,
    created_by,
    created_at,
    updated_by,
    updated_at,
    del_flag
)
select
    ms.resource_key,
    'MENU',
    0,
    '',
    ms.title,
    ms.icon,
    ms.sort_order,
    false,
    '',
    '',
    ms.remark,
    1,
    'flyway',
    now(),
    'flyway',
    now(),
    0
from menu_seed ms
on conflict (resource_key) do update set
    resource_type = excluded.resource_type,
    parent_resource_id = excluded.parent_resource_id,
    page_key = excluded.page_key,
    title = excluded.title,
    icon = excluded.icon,
    sort_order = excluded.sort_order,
    hidden = excluded.hidden,
    permission_code = excluded.permission_code,
    button_group = excluded.button_group,
    remark = excluded.remark,
    status = excluded.status,
    updated_by = excluded.updated_by,
    updated_at = excluded.updated_at,
    del_flag = excluded.del_flag;

with menu_seed(resource_key, title, icon, sort_order, remark) as (
    values
        ('nav_inventory', '库存', 'shop', 45, '库存管理一级菜单')
)
insert into public.resource_published (
    resource_key,
    resource_type,
    parent_resource_id,
    page_key,
    title,
    icon,
    sort_order,
    hidden,
    permission_code,
    button_group,
    remark,
    status,
    created_by,
    created_at,
    updated_by,
    updated_at,
    del_flag
)
select
    ms.resource_key,
    'MENU',
    0,
    '',
    ms.title,
    ms.icon,
    ms.sort_order,
    false,
    '',
    '',
    ms.remark,
    1,
    'flyway',
    now(),
    'flyway',
    now(),
    0
from menu_seed ms
on conflict (resource_key) do update set
    resource_type = excluded.resource_type,
    parent_resource_id = excluded.parent_resource_id,
    page_key = excluded.page_key,
    title = excluded.title,
    icon = excluded.icon,
    sort_order = excluded.sort_order,
    hidden = excluded.hidden,
    permission_code = excluded.permission_code,
    button_group = excluded.button_group,
    remark = excluded.remark,
    status = excluded.status,
    updated_by = excluded.updated_by,
    updated_at = excluded.updated_at,
    del_flag = excluded.del_flag;

with page_seed(resource_key, parent_resource_key, page_key, title, icon, sort_order, remark) as (
    values
        ('page_c5_inventory_management', 'nav_inventory', 'InventoryManagement', '库存管理', 'view-module', 10, 'C5库存管理页面')
), prepared_page as (
    select
        ps.resource_key,
        parent.id as parent_resource_id,
        ps.page_key,
        ps.title,
        ps.icon,
        ps.sort_order,
        ps.remark
    from page_seed ps
    join public.resource_draft parent
        on parent.resource_key = ps.parent_resource_key
       and parent.del_flag = 0
)
insert into public.resource_draft (
    resource_key,
    resource_type,
    parent_resource_id,
    page_key,
    title,
    icon,
    sort_order,
    hidden,
    permission_code,
    button_group,
    remark,
    status,
    created_by,
    created_at,
    updated_by,
    updated_at,
    del_flag
)
select
    pp.resource_key,
    'PAGE',
    pp.parent_resource_id,
    pp.page_key,
    pp.title,
    pp.icon,
    pp.sort_order,
    false,
    '',
    '',
    pp.remark,
    1,
    'flyway',
    now(),
    'flyway',
    now(),
    0
from prepared_page pp
on conflict (resource_key) do update set
    resource_type = excluded.resource_type,
    parent_resource_id = excluded.parent_resource_id,
    page_key = excluded.page_key,
    title = excluded.title,
    icon = excluded.icon,
    sort_order = excluded.sort_order,
    hidden = excluded.hidden,
    permission_code = excluded.permission_code,
    button_group = excluded.button_group,
    remark = excluded.remark,
    status = excluded.status,
    updated_by = excluded.updated_by,
    updated_at = excluded.updated_at,
    del_flag = excluded.del_flag;

with page_seed(resource_key, parent_resource_key, page_key, title, icon, sort_order, remark) as (
    values
        ('page_c5_inventory_management', 'nav_inventory', 'InventoryManagement', '库存管理', 'view-module', 10, 'C5库存管理页面')
), prepared_page as (
    select
        ps.resource_key,
        parent.id as parent_resource_id,
        ps.page_key,
        ps.title,
        ps.icon,
        ps.sort_order,
        ps.remark
    from page_seed ps
    join public.resource_published parent
        on parent.resource_key = ps.parent_resource_key
       and parent.del_flag = 0
)
insert into public.resource_published (
    resource_key,
    resource_type,
    parent_resource_id,
    page_key,
    title,
    icon,
    sort_order,
    hidden,
    permission_code,
    button_group,
    remark,
    status,
    created_by,
    created_at,
    updated_by,
    updated_at,
    del_flag
)
select
    pp.resource_key,
    'PAGE',
    pp.parent_resource_id,
    pp.page_key,
    pp.title,
    pp.icon,
    pp.sort_order,
    false,
    '',
    '',
    pp.remark,
    1,
    'flyway',
    now(),
    'flyway',
    now(),
    0
from prepared_page pp
on conflict (resource_key) do update set
    resource_type = excluded.resource_type,
    parent_resource_id = excluded.parent_resource_id,
    page_key = excluded.page_key,
    title = excluded.title,
    icon = excluded.icon,
    sort_order = excluded.sort_order,
    hidden = excluded.hidden,
    permission_code = excluded.permission_code,
    button_group = excluded.button_group,
    remark = excluded.remark,
    status = excluded.status,
    updated_by = excluded.updated_by,
    updated_at = excluded.updated_at,
    del_flag = excluded.del_flag;

with button_seed(resource_key, parent_resource_key, title, permission_code, button_group, sort_order, remark) as (
    values
        ('button_c5_inventory_list', 'page_c5_inventory_management', '查看库存', 'c5:inventory:list', '库存管理', 10, '库存管理页面查看权限'),
        ('button_c5_inventory_refresh', 'page_c5_inventory_management', '刷新库存', 'c5:inventory:refresh', '库存管理', 20, '刷新C5库存按钮')
), prepared_button as (
    select
        bs.resource_key,
        parent.id as parent_resource_id,
        bs.title,
        bs.permission_code,
        bs.button_group,
        bs.sort_order,
        bs.remark
    from button_seed bs
    join public.resource_draft parent
        on parent.resource_key = bs.parent_resource_key
       and parent.del_flag = 0
)
insert into public.resource_draft (
    resource_key,
    resource_type,
    parent_resource_id,
    page_key,
    title,
    icon,
    sort_order,
    hidden,
    permission_code,
    button_group,
    remark,
    status,
    created_by,
    created_at,
    updated_by,
    updated_at,
    del_flag
)
select
    pb.resource_key,
    'BUTTON',
    pb.parent_resource_id,
    '',
    pb.title,
    '',
    pb.sort_order,
    false,
    pb.permission_code,
    pb.button_group,
    pb.remark,
    1,
    'flyway',
    now(),
    'flyway',
    now(),
    0
from prepared_button pb
on conflict (resource_key) do update set
    resource_type = excluded.resource_type,
    parent_resource_id = excluded.parent_resource_id,
    page_key = excluded.page_key,
    title = excluded.title,
    icon = excluded.icon,
    sort_order = excluded.sort_order,
    hidden = excluded.hidden,
    permission_code = excluded.permission_code,
    button_group = excluded.button_group,
    remark = excluded.remark,
    status = excluded.status,
    updated_by = excluded.updated_by,
    updated_at = excluded.updated_at,
    del_flag = excluded.del_flag;

with button_seed(resource_key, parent_resource_key, title, permission_code, button_group, sort_order, remark) as (
    values
        ('button_c5_inventory_list', 'page_c5_inventory_management', '查看库存', 'c5:inventory:list', '库存管理', 10, '库存管理页面查看权限'),
        ('button_c5_inventory_refresh', 'page_c5_inventory_management', '刷新库存', 'c5:inventory:refresh', '库存管理', 20, '刷新C5库存按钮')
), prepared_button as (
    select
        bs.resource_key,
        parent.id as parent_resource_id,
        bs.title,
        bs.permission_code,
        bs.button_group,
        bs.sort_order,
        bs.remark
    from button_seed bs
    join public.resource_published parent
        on parent.resource_key = bs.parent_resource_key
       and parent.del_flag = 0
)
insert into public.resource_published (
    resource_key,
    resource_type,
    parent_resource_id,
    page_key,
    title,
    icon,
    sort_order,
    hidden,
    permission_code,
    button_group,
    remark,
    status,
    created_by,
    created_at,
    updated_by,
    updated_at,
    del_flag
)
select
    pb.resource_key,
    'BUTTON',
    pb.parent_resource_id,
    '',
    pb.title,
    '',
    pb.sort_order,
    false,
    pb.permission_code,
    pb.button_group,
    pb.remark,
    1,
    'flyway',
    now(),
    'flyway',
    now(),
    0
from prepared_button pb
on conflict (resource_key) do update set
    resource_type = excluded.resource_type,
    parent_resource_id = excluded.parent_resource_id,
    page_key = excluded.page_key,
    title = excluded.title,
    icon = excluded.icon,
    sort_order = excluded.sort_order,
    hidden = excluded.hidden,
    permission_code = excluded.permission_code,
    button_group = excluded.button_group,
    remark = excluded.remark,
    status = excluded.status,
    updated_by = excluded.updated_by,
    updated_at = excluded.updated_at,
    del_flag = excluded.del_flag;

with target_resource(resource_key) as (
    values
        ('nav_inventory'),
        ('page_c5_inventory_management'),
        ('button_c5_inventory_list'),
        ('button_c5_inventory_refresh')
), admin_role as (
    select role_id
    from public.sys_role
    where role_key = 'admin'
      and del_flag = 0
    order by role_id
    limit 1
), target_draft_resource as (
    select rd.id
    from public.resource_draft rd
    join target_resource tr on tr.resource_key = rd.resource_key
    where rd.del_flag = 0
)
insert into public.role_resource_draft (
    role_id,
    resource_id,
    created_by,
    created_at,
    updated_by,
    updated_at,
    del_flag
)
select
    ar.role_id,
    tdr.id,
    'flyway',
    now(),
    'flyway',
    now(),
    0
from admin_role ar
cross join target_draft_resource tdr
where not exists (
    select 1
    from public.role_resource_draft rrd
    where rrd.role_id = ar.role_id
      and rrd.resource_id = tdr.id
);

with target_resource(resource_key) as (
    values
        ('nav_inventory'),
        ('page_c5_inventory_management'),
        ('button_c5_inventory_list'),
        ('button_c5_inventory_refresh')
), admin_role as (
    select role_id
    from public.sys_role
    where role_key = 'admin'
      and del_flag = 0
    order by role_id
    limit 1
), target_published_resource as (
    select rp.id
    from public.resource_published rp
    join target_resource tr on tr.resource_key = rp.resource_key
    where rp.del_flag = 0
)
insert into public.role_resource_published (
    role_id,
    resource_id,
    created_by,
    created_at,
    updated_by,
    updated_at,
    del_flag
)
select
    ar.role_id,
    tpr.id,
    'flyway',
    now(),
    'flyway',
    now(),
    0
from admin_role ar
cross join target_published_resource tpr
where not exists (
    select 1
    from public.role_resource_published rrp
    where rrp.role_id = ar.role_id
      and rrp.resource_id = tpr.id
);

commit;

-- ============================================================
-- 执行后复核（可选）
-- ============================================================
-- select resource_key, resource_type, parent_resource_id, page_key, title, permission_code, sort_order, status, del_flag
-- from public.resource_published
-- where resource_key in ('nav_inventory', 'page_c5_inventory_management', 'button_c5_inventory_list', 'button_c5_inventory_refresh')
-- order by sort_order;
