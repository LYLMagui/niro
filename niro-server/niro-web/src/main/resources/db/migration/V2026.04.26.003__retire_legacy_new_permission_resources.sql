-- ============================================================
-- 下线新权限系统旧页面与按钮资源（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-26
-- 目标:
-- 1. 软下线旧页面资源：page_profit_stats、page_sticker_list、page_task_list、page_task_config
-- 2. 软下线贴纸旧接口按钮资源：system:sticker:list、system:sticker:sync
-- 3. 清理旧页面及其旧按钮在 role_resource_draft / role_resource_published 中的授权关系
-- 4. 仅移除旧 page_task_list / page_task_config 关系，不删除 task:scan:list 等可能被 C5 扫货 2.0 复用的授权资源
-- 幂等策略:
-- - 目标资源通过稳定 resource_key / permission_code 定位，不依赖自增 id
-- - 资源软下线使用 status = 0、del_flag = 1，重复执行结果一致
-- - 授权关系按目标资源 id 删除，目标不存在时 delete 无影响
-- 回滚思路:
-- - 如需恢复，可新增 migration 将对应 resource_key / permission_code 的 status 置 1、del_flag 置 0
-- - 角色授权需由权限管理后台重新发布或新增 migration 按角色重新补齐
-- ============================================================

begin;

with target_resource(resource_key, permission_code) as (
    values
        ('page_profit_stats', ''),
        ('page_sticker_list', ''),
        ('page_task_list', ''),
        ('page_task_config', ''),
        ('button_sticker_list', 'system:sticker:list'),
        ('button_sticker_sync', 'system:sticker:sync')
), target_draft_resource as (
    select rd.id
    from public.resource_draft rd
    join target_resource tr
        on rd.resource_key = tr.resource_key
        or (tr.permission_code <> '' and rd.permission_code = tr.permission_code)
)
delete from public.role_resource_draft rrd
using target_draft_resource tdr
where rrd.resource_id = tdr.id;

with target_resource(resource_key, permission_code) as (
    values
        ('page_profit_stats', ''),
        ('page_sticker_list', ''),
        ('page_task_list', ''),
        ('page_task_config', ''),
        ('button_sticker_list', 'system:sticker:list'),
        ('button_sticker_sync', 'system:sticker:sync')
), target_published_resource as (
    select rp.id
    from public.resource_published rp
    join target_resource tr
        on rp.resource_key = tr.resource_key
        or (tr.permission_code <> '' and rp.permission_code = tr.permission_code)
)
delete from public.role_resource_published rrp
using target_published_resource tpr
where rrp.resource_id = tpr.id;

with target_resource(resource_key, permission_code) as (
    values
        ('page_profit_stats', ''),
        ('page_sticker_list', ''),
        ('page_task_list', ''),
        ('page_task_config', ''),
        ('button_sticker_list', 'system:sticker:list'),
        ('button_sticker_sync', 'system:sticker:sync')
)
update public.resource_draft rd
set status = 0,
    del_flag = 1,
    updated_by = 'flyway',
    updated_at = now(),
    remark = case
        when rd.remark like '%已由 V2026.04.26.003 下线%' then rd.remark
        when rd.remark = '' then '已由 V2026.04.26.003 下线'
        else rd.remark || '；已由 V2026.04.26.003 下线'
    end
from target_resource tr
where (rd.resource_key = tr.resource_key
        or (tr.permission_code <> '' and rd.permission_code = tr.permission_code))
  and (rd.status <> 0
       or rd.del_flag <> 1
       or rd.updated_by <> 'flyway'
       or rd.remark not like '%已由 V2026.04.26.003 下线%');

with target_resource(resource_key, permission_code) as (
    values
        ('page_profit_stats', ''),
        ('page_sticker_list', ''),
        ('page_task_list', ''),
        ('page_task_config', ''),
        ('button_sticker_list', 'system:sticker:list'),
        ('button_sticker_sync', 'system:sticker:sync')
)
update public.resource_published rp
set status = 0,
    del_flag = 1,
    updated_by = 'flyway',
    updated_at = now(),
    remark = case
        when rp.remark like '%已由 V2026.04.26.003 下线%' then rp.remark
        when rp.remark = '' then '已由 V2026.04.26.003 下线'
        else rp.remark || '；已由 V2026.04.26.003 下线'
    end
from target_resource tr
where (rp.resource_key = tr.resource_key
        or (tr.permission_code <> '' and rp.permission_code = tr.permission_code))
  and (rp.status <> 0
       or rp.del_flag <> 1
       or rp.updated_by <> 'flyway'
       or rp.remark not like '%已由 V2026.04.26.003 下线%');

commit;

-- ============================================================
-- 执行后复核（可选）
-- ============================================================
-- select resource_key, resource_type, page_key, permission_code, status, del_flag
-- from public.resource_published
-- where resource_key in ('page_profit_stats', 'page_sticker_list', 'page_task_list', 'page_task_config', 'button_sticker_list', 'button_sticker_sync')
--    or permission_code in ('system:sticker:list', 'system:sticker:sync')
-- order by resource_key;
--
-- select count(*) as legacy_grant_count
-- from public.role_resource_published rrp
-- join public.resource_published rp on rp.id = rrp.resource_id
-- where rp.resource_key in ('page_profit_stats', 'page_sticker_list', 'page_task_list', 'page_task_config', 'button_sticker_list', 'button_sticker_sync')
--    or rp.permission_code in ('system:sticker:list', 'system:sticker:sync');
