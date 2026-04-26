-- ============================================================
-- 新增 C5 扫货账号配置权限资源（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-26
-- 目标:
-- 1. 将前端已注册页面 C5SnipingAccountConfig 挂到新权限系统“扫货”目录下
-- 2. 同步写入 resource_draft 与 resource_published，确保草稿态和发布态导航一致
-- 3. 为 admin 角色补齐 draft / published 授权，确保左侧菜单可见“扫货账号配置”
-- 幂等策略:
-- - 资源按稳定 resource_key 做 upsert，不依赖自增 id
-- - 父子关系通过 nav_scan 的 resource_key 解析 parent_resource_id
-- - admin 授权按 role_id + resource_id 做 not exists 保护
-- 回滚思路:
-- - 如需回滚，可新增 migration 将 page_c5_sniping_account_config 软删除，并清理对应 role_resource 授权
-- - 本脚本不删除、不 drop 表列，影响面仅限新增/修正该页面资源与 admin 授权
-- ============================================================

begin;

do $$
begin
    if not exists (
        select 1
        from public.resource_draft
        where resource_key = 'nav_scan'
          and del_flag = 0
    ) then
        raise exception '迁移终止：resource_draft 缺少 nav_scan，请先执行新权限全量资源种子';
    end if;

    if not exists (
        select 1
        from public.resource_published
        where resource_key = 'nav_scan'
          and del_flag = 0
    ) then
        raise exception '迁移终止：resource_published 缺少 nav_scan，请先执行新权限全量资源种子';
    end if;
end $$;

with page_seed(resource_key, parent_resource_key, page_key, title, icon, sort_order, remark) as (
    values
        ('page_c5_sniping_account_config', 'nav_scan', 'C5SnipingAccountConfig', '扫货账号配置', 'setting', 40, 'C5扫货账号配置页面')
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
        ('page_c5_sniping_account_config', 'nav_scan', 'C5SnipingAccountConfig', '扫货账号配置', 'setting', 40, 'C5扫货账号配置页面')
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

with target_resource(resource_key) as (
    values
        ('page_c5_sniping_account_config')
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
        ('page_c5_sniping_account_config')
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
-- select resource_key, resource_type, parent_resource_id, page_key, title, sort_order, status, del_flag
-- from public.resource_published
-- where resource_key = 'page_c5_sniping_account_config';
--
-- select r.role_key, rp.resource_key, rp.page_key, rp.title
-- from public.role_resource_published rrp
-- join public.sys_role r on r.role_id = rrp.role_id and r.del_flag = 0
-- join public.resource_published rp on rp.id = rrp.resource_id and rp.del_flag = 0
-- where r.role_key = 'admin'
--   and rp.resource_key = 'page_c5_sniping_account_config';
