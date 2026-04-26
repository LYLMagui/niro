-- ============================================================
-- 修复新权限导航父子关系（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-25
-- 目标:
-- 1. 修复新权限导航根目录与页面资源在 draft / published 中的 parent_resource_id
-- 2. 保证侧边栏能按 nav_* -> page_* 的目录层级正确组树
-- 3. 避免历史试点平铺数据在后续环境中继续污染已发布导航
-- 幂等策略:
-- - 仅按 resource_key 修正固定导航资源的 parent_resource_id
-- - 仅在 parent_resource_id 发生变化时更新，重复执行结果一致
-- - 不依赖自增 id，父子关系统一通过 resource_key 解析
-- 回滚思路:
-- - 如需回滚，只能新增 migration 将目标资源恢复为旧的 parent_resource_id
-- - 本脚本不删除资源、不修改授权绑定，影响面仅限导航父子结构
-- ============================================================

begin;

do $$
begin
    if exists (
        with navigation_seed(resource_key, parent_resource_key) as (
            values
                ('nav_dashboard', ''),
                ('nav_scan', ''),
                ('nav_order', ''),
                ('nav_unbox', ''),
                ('nav_goods', ''),
                ('nav_system', ''),
                ('page_dashboard', 'nav_dashboard'),
                ('page_task_list', 'nav_scan'),
                ('page_task_config', 'nav_scan'),
                ('page_c5_sniping_task_v2', 'nav_scan'),
                ('page_order_record', 'nav_order'),
                ('page_profit_stats', 'nav_order'),
                ('page_unbox_record', 'nav_unbox'),
                ('page_goods_list', 'nav_goods'),
                ('page_sticker_list', 'nav_goods'),
                ('page_inventory_board', 'nav_goods'),
                ('page_settings', 'nav_system'),
                ('invite_code_manage_new', 'nav_system'),
                ('logs_new', 'nav_system'),
                ('permission_manage_new', 'nav_system')
        )
        select 1
        from navigation_seed ns
        where not exists (
            select 1
            from public.resource_draft rd
            where rd.resource_key = ns.resource_key
              and rd.del_flag = 0
        )
    ) then
        raise exception '迁移终止：resource_draft 缺少新权限导航资源，请先补齐 full resource seed';
    end if;

    if exists (
        with navigation_seed(resource_key, parent_resource_key) as (
            values
                ('nav_dashboard', ''),
                ('nav_scan', ''),
                ('nav_order', ''),
                ('nav_unbox', ''),
                ('nav_goods', ''),
                ('nav_system', ''),
                ('page_dashboard', 'nav_dashboard'),
                ('page_task_list', 'nav_scan'),
                ('page_task_config', 'nav_scan'),
                ('page_c5_sniping_task_v2', 'nav_scan'),
                ('page_order_record', 'nav_order'),
                ('page_profit_stats', 'nav_order'),
                ('page_unbox_record', 'nav_unbox'),
                ('page_goods_list', 'nav_goods'),
                ('page_sticker_list', 'nav_goods'),
                ('page_inventory_board', 'nav_goods'),
                ('page_settings', 'nav_system'),
                ('invite_code_manage_new', 'nav_system'),
                ('logs_new', 'nav_system'),
                ('permission_manage_new', 'nav_system')
        )
        select 1
        from navigation_seed ns
        where ns.parent_resource_key <> ''
          and not exists (
              select 1
              from public.resource_draft parent
              where parent.resource_key = ns.parent_resource_key
                and parent.del_flag = 0
          )
    ) then
        raise exception '迁移终止：resource_draft 缺少新权限导航父资源，请先补齐目录型 MENU 基线';
    end if;

    if exists (
        with navigation_seed(resource_key, parent_resource_key) as (
            values
                ('nav_dashboard', ''),
                ('nav_scan', ''),
                ('nav_order', ''),
                ('nav_unbox', ''),
                ('nav_goods', ''),
                ('nav_system', ''),
                ('page_dashboard', 'nav_dashboard'),
                ('page_task_list', 'nav_scan'),
                ('page_task_config', 'nav_scan'),
                ('page_c5_sniping_task_v2', 'nav_scan'),
                ('page_order_record', 'nav_order'),
                ('page_profit_stats', 'nav_order'),
                ('page_unbox_record', 'nav_unbox'),
                ('page_goods_list', 'nav_goods'),
                ('page_sticker_list', 'nav_goods'),
                ('page_inventory_board', 'nav_goods'),
                ('page_settings', 'nav_system'),
                ('invite_code_manage_new', 'nav_system'),
                ('logs_new', 'nav_system'),
                ('permission_manage_new', 'nav_system')
        )
        select 1
        from navigation_seed ns
        where not exists (
            select 1
            from public.resource_published rp
            where rp.resource_key = ns.resource_key
              and rp.del_flag = 0
        )
    ) then
        raise exception '迁移终止：resource_published 缺少新权限导航资源，请先补齐并发布 full resource seed';
    end if;

    if exists (
        with navigation_seed(resource_key, parent_resource_key) as (
            values
                ('nav_dashboard', ''),
                ('nav_scan', ''),
                ('nav_order', ''),
                ('nav_unbox', ''),
                ('nav_goods', ''),
                ('nav_system', ''),
                ('page_dashboard', 'nav_dashboard'),
                ('page_task_list', 'nav_scan'),
                ('page_task_config', 'nav_scan'),
                ('page_c5_sniping_task_v2', 'nav_scan'),
                ('page_order_record', 'nav_order'),
                ('page_profit_stats', 'nav_order'),
                ('page_unbox_record', 'nav_unbox'),
                ('page_goods_list', 'nav_goods'),
                ('page_sticker_list', 'nav_goods'),
                ('page_inventory_board', 'nav_goods'),
                ('page_settings', 'nav_system'),
                ('invite_code_manage_new', 'nav_system'),
                ('logs_new', 'nav_system'),
                ('permission_manage_new', 'nav_system')
        )
        select 1
        from navigation_seed ns
        where ns.parent_resource_key <> ''
          and not exists (
              select 1
              from public.resource_published parent
              where parent.resource_key = ns.parent_resource_key
                and parent.del_flag = 0
          )
    ) then
        raise exception '迁移终止：resource_published 缺少新权限导航父资源，请先补齐并发布目录型 MENU 基线';
    end if;
end $$;

with navigation_seed(resource_key, parent_resource_key) as (
    values
        ('nav_dashboard', ''),
        ('nav_scan', ''),
        ('nav_order', ''),
        ('nav_unbox', ''),
        ('nav_goods', ''),
        ('nav_system', ''),
        ('page_dashboard', 'nav_dashboard'),
        ('page_task_list', 'nav_scan'),
        ('page_task_config', 'nav_scan'),
        ('page_c5_sniping_task_v2', 'nav_scan'),
        ('page_order_record', 'nav_order'),
        ('page_profit_stats', 'nav_order'),
        ('page_unbox_record', 'nav_unbox'),
        ('page_goods_list', 'nav_goods'),
        ('page_sticker_list', 'nav_goods'),
        ('page_inventory_board', 'nav_goods'),
        ('page_settings', 'nav_system'),
        ('invite_code_manage_new', 'nav_system'),
        ('logs_new', 'nav_system'),
        ('permission_manage_new', 'nav_system')
), prepared_parent as (
    select
        child.id as child_id,
        case
            when ns.parent_resource_key = '' then 0::bigint
            else parent.id
        end as target_parent_resource_id
    from navigation_seed ns
    join public.resource_draft child
        on child.resource_key = ns.resource_key
       and child.del_flag = 0
    left join public.resource_draft parent
        on parent.resource_key = ns.parent_resource_key
       and parent.del_flag = 0
)
update public.resource_draft target
set parent_resource_id = pp.target_parent_resource_id,
    updated_by = 'flyway',
    updated_at = now()
from prepared_parent pp
where target.id = pp.child_id
  and target.parent_resource_id is distinct from pp.target_parent_resource_id;

with navigation_seed(resource_key, parent_resource_key) as (
    values
        ('nav_dashboard', ''),
        ('nav_scan', ''),
        ('nav_order', ''),
        ('nav_unbox', ''),
        ('nav_goods', ''),
        ('nav_system', ''),
        ('page_dashboard', 'nav_dashboard'),
        ('page_task_list', 'nav_scan'),
        ('page_task_config', 'nav_scan'),
        ('page_c5_sniping_task_v2', 'nav_scan'),
        ('page_order_record', 'nav_order'),
        ('page_profit_stats', 'nav_order'),
        ('page_unbox_record', 'nav_unbox'),
        ('page_goods_list', 'nav_goods'),
        ('page_sticker_list', 'nav_goods'),
        ('page_inventory_board', 'nav_goods'),
        ('page_settings', 'nav_system'),
        ('invite_code_manage_new', 'nav_system'),
        ('logs_new', 'nav_system'),
        ('permission_manage_new', 'nav_system')
), prepared_parent as (
    select
        child.id as child_id,
        case
            when ns.parent_resource_key = '' then 0::bigint
            else parent.id
        end as target_parent_resource_id
    from navigation_seed ns
    join public.resource_published child
        on child.resource_key = ns.resource_key
       and child.del_flag = 0
    left join public.resource_published parent
        on parent.resource_key = ns.parent_resource_key
       and parent.del_flag = 0
)
update public.resource_published target
set parent_resource_id = pp.target_parent_resource_id,
    updated_by = 'flyway',
    updated_at = now()
from prepared_parent pp
where target.id = pp.child_id
  and target.parent_resource_id is distinct from pp.target_parent_resource_id;

commit;

-- ============================================================
-- 执行后复核（可选）
-- ============================================================
-- select resource_key, resource_type, parent_resource_id, page_key, title, sort_order, status, del_flag
-- from public.resource_published
-- where resource_key in (
--     'nav_dashboard', 'page_dashboard', 'nav_scan', 'page_task_list', 'page_task_config', 'page_c5_sniping_task_v2',
--     'nav_order', 'page_order_record', 'page_profit_stats', 'nav_unbox', 'page_unbox_record',
--     'nav_goods', 'page_goods_list', 'page_sticker_list', 'page_inventory_board',
--     'nav_system', 'page_settings', 'invite_code_manage_new', 'logs_new', 'permission_manage_new'
-- )
-- order by parent_resource_id, sort_order, id;
