/*
 * 变更日期：2026-05-11
 * 目标：新增“统计 > 数据导出”页面资源，并补齐开箱记录导出创建权限资源。
 * 幂等策略：按稳定 resource_key 对 resource_draft / resource_published 做 upsert；admin 授权按 role_id + resource_id 做 not exists 保护。
 * 回滚思路：如需回滚，可新增 migration 将相关 resource_key 软删除，并清理对应 role_resource 授权；本脚本不删除资源、不修改历史脚本。
 */

begin;

with menu_seed(resource_key, title, icon, sort_order, remark) as (
    values
        ('nav_statistics', '统计', 'chart', 35, '统计一级菜单')
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
        ('nav_statistics', '统计', 'chart', 35, '统计一级菜单')
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
        ('page_data_export', 'nav_statistics', 'DataExport', '数据导出', 'download', 10, '数据导出页面')
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
        ('page_data_export', 'nav_statistics', 'DataExport', '数据导出', 'download', 10, '数据导出页面')
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
        ('button_data_export_view', 'page_data_export', '查看导出历史', 'statistics:data-export:view', '数据导出', 10, '数据导出页面查看权限'),
        ('button_unbox_record_export', 'page_unbox_record', '导出当前筛选', 'unbox:record:export', '批次', 110, '开箱记录导出当前筛选权限')
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
        ('button_data_export_view', 'page_data_export', '查看导出历史', 'statistics:data-export:view', '数据导出', 10, '数据导出页面查看权限'),
        ('button_unbox_record_export', 'page_unbox_record', '导出当前筛选', 'unbox:record:export', '批次', 110, '开箱记录导出当前筛选权限')
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
        ('nav_statistics'),
        ('page_data_export'),
        ('button_data_export_view'),
        ('button_unbox_record_export')
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
        ('nav_statistics'),
        ('page_data_export'),
        ('button_data_export_view'),
        ('button_unbox_record_export')
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

-- select resource_key, resource_type, page_key, title, permission_code, sort_order
-- from public.resource_published
-- where resource_key in ('nav_statistics', 'page_data_export', 'button_data_export_view', 'button_unbox_record_export')
-- order by sort_order, resource_key;
