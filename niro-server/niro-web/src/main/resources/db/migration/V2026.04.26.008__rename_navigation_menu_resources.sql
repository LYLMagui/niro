/*
 * 变更日期：2026-04-26
 * 目标：调整新权限菜单展示名称，并将订单统计入口归入订单目录。
 * 幂等策略：按稳定 resource_key 更新 resource_draft / resource_published 的标题、父级和排序，重复执行结果一致。
 * 回滚思路：如需回滚，可新增 migration 将相关 resource_key 的标题、父级和排序恢复为旧值；本脚本不删除资源和授权。
 */

begin;

do $$
begin
    if exists (
        with required_resource(table_name, resource_key) as (
            values
                ('resource_draft', 'nav_scan'),
                ('resource_draft', 'nav_order'),
                ('resource_draft', 'nav_system'),
                ('resource_draft', 'page_c5_sniping_task_v2'),
                ('resource_draft', 'page_c5_sniping_account_config'),
                ('resource_draft', 'permission_manage_new'),
                ('resource_draft', 'page_inventory_board'),
                ('resource_published', 'nav_scan'),
                ('resource_published', 'nav_order'),
                ('resource_published', 'nav_system'),
                ('resource_published', 'page_c5_sniping_task_v2'),
                ('resource_published', 'page_c5_sniping_account_config'),
                ('resource_published', 'permission_manage_new'),
                ('resource_published', 'page_inventory_board')
        )
        select 1
        from required_resource rr
        where (
            rr.table_name = 'resource_draft'
            and not exists (
                select 1
                from public.resource_draft rd
                where rd.resource_key = rr.resource_key
                  and rd.del_flag = 0
            )
        ) or (
            rr.table_name = 'resource_published'
            and not exists (
                select 1
                from public.resource_published rp
                where rp.resource_key = rr.resource_key
                  and rp.del_flag = 0
            )
        )
    ) then
        raise exception '迁移终止：新权限菜单资源不完整，请先补齐资源基线';
    end if;
end $$;

with menu_patch(resource_key, parent_resource_key, title, sort_order, remark) as (
    values
        ('page_c5_sniping_task_v2', 'nav_scan', '扫货管理', 30, '扫货管理页面'),
        ('page_c5_sniping_account_config', 'nav_scan', '账号配置', 40, '账号配置页面'),
        ('permission_manage_new', 'nav_system', '权限管理', 40, '权限管理页面'),
        ('page_inventory_board', 'nav_order', '订单统计', 20, '订单统计页面')
), prepared_patch as (
    select
        child.id as child_id,
        parent.id as target_parent_resource_id,
        mp.title,
        mp.sort_order,
        mp.remark
    from menu_patch mp
    join public.resource_draft child
        on child.resource_key = mp.resource_key
       and child.del_flag = 0
    join public.resource_draft parent
        on parent.resource_key = mp.parent_resource_key
       and parent.del_flag = 0
)
update public.resource_draft target
set
    title = pp.title,
    parent_resource_id = pp.target_parent_resource_id,
    sort_order = pp.sort_order,
    remark = pp.remark,
    updated_by = 'flyway',
    updated_at = now()
from prepared_patch pp
where target.id = pp.child_id
  and (
      target.title is distinct from pp.title
      or target.parent_resource_id is distinct from pp.target_parent_resource_id
      or target.sort_order is distinct from pp.sort_order
      or target.remark is distinct from pp.remark
  );

with menu_patch(resource_key, parent_resource_key, title, sort_order, remark) as (
    values
        ('page_c5_sniping_task_v2', 'nav_scan', '扫货管理', 30, '扫货管理页面'),
        ('page_c5_sniping_account_config', 'nav_scan', '账号配置', 40, '账号配置页面'),
        ('permission_manage_new', 'nav_system', '权限管理', 40, '权限管理页面'),
        ('page_inventory_board', 'nav_order', '订单统计', 20, '订单统计页面')
), prepared_patch as (
    select
        child.id as child_id,
        parent.id as target_parent_resource_id,
        mp.title,
        mp.sort_order,
        mp.remark
    from menu_patch mp
    join public.resource_published child
        on child.resource_key = mp.resource_key
       and child.del_flag = 0
    join public.resource_published parent
        on parent.resource_key = mp.parent_resource_key
       and parent.del_flag = 0
)
update public.resource_published target
set
    title = pp.title,
    parent_resource_id = pp.target_parent_resource_id,
    sort_order = pp.sort_order,
    remark = pp.remark,
    updated_by = 'flyway',
    updated_at = now()
from prepared_patch pp
where target.id = pp.child_id
  and (
      target.title is distinct from pp.title
      or target.parent_resource_id is distinct from pp.target_parent_resource_id
      or target.sort_order is distinct from pp.sort_order
      or target.remark is distinct from pp.remark
  );

commit;

select
    child.resource_key,
    parent.resource_key as parent_resource_key,
    child.title,
    child.sort_order
from public.resource_published child
left join public.resource_published parent
    on parent.id = child.parent_resource_id
where child.resource_key in (
    'page_c5_sniping_task_v2',
    'page_c5_sniping_account_config',
    'permission_manage_new',
    'page_inventory_board'
)
order by parent.resource_key, child.sort_order, child.resource_key;
