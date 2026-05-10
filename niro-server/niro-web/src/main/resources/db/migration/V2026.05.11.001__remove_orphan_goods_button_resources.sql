/*
 * 变更日期: 2026-05-11
 * 目标: 清理旧商品列表页面删除后遗留的按钮资源，修复权限发布孤儿资源校验。
 * 幂等策略: 按稳定 resource_key 和 permission_code 删除草稿态、发布态资源及角色授权，重复执行无影响。
 * 回滚思路: 如需恢复旧商品列表页面，需新增 migration 先恢复 page_goods_list，再按稳定键重建对应按钮资源和角色授权。
 */
begin;

with removed_draft_resources as (
    select id
    from public.resource_draft
    where resource_key in (
        'button_goods_list',
        'button_task_scan_create'
    )
       or permission_code in (
        'system:goods:list',
        'task:scan:create'
    )
)
delete from public.role_resource_draft rr
where rr.resource_id in (select id from removed_draft_resources);

delete from public.resource_draft
where resource_key in (
    'button_goods_list',
    'button_task_scan_create'
)
   or permission_code in (
    'system:goods:list',
    'task:scan:create'
);

with removed_published_resources as (
    select id
    from public.resource_published
    where resource_key in (
        'button_goods_list',
        'button_task_scan_create'
    )
       or permission_code in (
        'system:goods:list',
        'task:scan:create'
    )
)
delete from public.role_resource_published rr
where rr.resource_id in (select id from removed_published_resources);

delete from public.resource_published
where resource_key in (
    'button_goods_list',
    'button_task_scan_create'
)
   or permission_code in (
    'system:goods:list',
    'task:scan:create'
);

commit;

-- 复核:
-- select resource_key, permission_code, parent_resource_id from public.resource_draft where resource_key in ('button_goods_list', 'button_task_scan_create') or permission_code in ('system:goods:list', 'task:scan:create');
-- select resource_key, permission_code, parent_resource_id from public.resource_published where resource_key in ('button_goods_list', 'button_task_scan_create') or permission_code in ('system:goods:list', 'task:scan:create');
