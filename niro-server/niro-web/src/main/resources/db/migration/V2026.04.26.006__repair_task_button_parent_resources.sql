-- ============================================================
-- 修复扫货按钮资源父级（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-26
-- 目标:
-- 1. 将仍被 C5 扫货 2.0 接口使用的 task:scan:list 按钮资源挂到 page_c5_sniping_task_v2
-- 2. 软下线未被代码引用的旧 Buff 任务兼容权限 task:buff:list
-- 3. 清理旧 Buff 任务兼容权限的角色授权，避免发布校验出现孤儿资源
-- 幂等策略:
-- - 按稳定 resource_key / permission_code 定位资源，不依赖自增 id
-- - 父级通过 page_c5_sniping_task_v2 动态查询，重复执行结果一致
-- - 软下线和授权清理重复执行无副作用
-- 回滚思路:
-- - 如需恢复 task:buff:list，可新增 migration 将其挂到有效页面并重新授权
-- - 如需调整 task:scan:list 归属，可新增 migration 更新 parent_resource_id
-- ============================================================

begin;

with parent_page as (
    select id
    from public.resource_draft
    where resource_key = 'page_c5_sniping_task_v2'
      and del_flag = 0
    order by id
    limit 1
)
update public.resource_draft rd
set parent_resource_id = parent_page.id,
    status = 1,
    del_flag = 0,
    updated_by = 'flyway',
    updated_at = now(),
    remark = case
        when rd.remark like '%已由 V2026.04.26.006 挂载到 C5扫货2.0%' then rd.remark
        when rd.remark = '' then '已由 V2026.04.26.006 挂载到 C5扫货2.0'
        else rd.remark || '；已由 V2026.04.26.006 挂载到 C5扫货2.0'
    end
from parent_page
where rd.resource_key = 'button_task_scan_list'
  and rd.permission_code = 'task:scan:list'
  and (rd.parent_resource_id is distinct from parent_page.id
       or rd.status <> 1
       or rd.del_flag <> 0
       or rd.updated_by <> 'flyway'
       or rd.remark not like '%已由 V2026.04.26.006 挂载到 C5扫货2.0%');

with parent_page as (
    select id
    from public.resource_published
    where resource_key = 'page_c5_sniping_task_v2'
      and del_flag = 0
    order by id
    limit 1
)
update public.resource_published rp
set parent_resource_id = parent_page.id,
    status = 1,
    del_flag = 0,
    updated_by = 'flyway',
    updated_at = now(),
    remark = case
        when rp.remark like '%已由 V2026.04.26.006 挂载到 C5扫货2.0%' then rp.remark
        when rp.remark = '' then '已由 V2026.04.26.006 挂载到 C5扫货2.0'
        else rp.remark || '；已由 V2026.04.26.006 挂载到 C5扫货2.0'
    end
from parent_page
where rp.resource_key = 'button_task_scan_list'
  and rp.permission_code = 'task:scan:list'
  and (rp.parent_resource_id is distinct from parent_page.id
       or rp.status <> 1
       or rp.del_flag <> 0
       or rp.updated_by <> 'flyway'
       or rp.remark not like '%已由 V2026.04.26.006 挂载到 C5扫货2.0%');

with retired_draft_resource as (
    select id
    from public.resource_draft
    where resource_key = 'button_task_buff_list'
       or permission_code = 'task:buff:list'
)
delete from public.role_resource_draft rrd
using retired_draft_resource rdr
where rrd.resource_id = rdr.id;

with retired_published_resource as (
    select id
    from public.resource_published
    where resource_key = 'button_task_buff_list'
       or permission_code = 'task:buff:list'
)
delete from public.role_resource_published rrp
using retired_published_resource rpr
where rrp.resource_id = rpr.id;

update public.resource_draft
set status = 0,
    del_flag = 1,
    updated_by = 'flyway',
    updated_at = now(),
    remark = case
        when remark like '%已由 V2026.04.26.006 下线%' then remark
        when remark = '' then '已由 V2026.04.26.006 下线'
        else remark || '；已由 V2026.04.26.006 下线'
    end
where (resource_key = 'button_task_buff_list'
       or permission_code = 'task:buff:list')
  and (status <> 0
       or del_flag <> 1
       or updated_by <> 'flyway'
       or remark not like '%已由 V2026.04.26.006 下线%');

update public.resource_published
set status = 0,
    del_flag = 1,
    updated_by = 'flyway',
    updated_at = now(),
    remark = case
        when remark like '%已由 V2026.04.26.006 下线%' then remark
        when remark = '' then '已由 V2026.04.26.006 下线'
        else remark || '；已由 V2026.04.26.006 下线'
    end
where (resource_key = 'button_task_buff_list'
       or permission_code = 'task:buff:list')
  and (status <> 0
       or del_flag <> 1
       or updated_by <> 'flyway'
       or remark not like '%已由 V2026.04.26.006 下线%');

insert into public.role_resource_draft (
    role_id,
    resource_id,
    del_flag,
    created_by,
    created_at,
    updated_by,
    updated_at
)
select sr.role_id,
       rd.id,
       0,
       'flyway',
       now(),
       'flyway',
       now()
from public.sys_role sr
join public.resource_draft rd
    on rd.resource_key = 'button_task_scan_list'
   and rd.permission_code = 'task:scan:list'
   and rd.del_flag = 0
where sr.role_key = 'admin'
  and sr.del_flag = 0
  and not exists (
      select 1
      from public.role_resource_draft rrd
      where rrd.role_id = sr.role_id
        and rrd.resource_id = rd.id
  );

insert into public.role_resource_published (
    role_id,
    resource_id,
    del_flag,
    created_by,
    created_at,
    updated_by,
    updated_at
)
select sr.role_id,
       rp.id,
       0,
       'flyway',
       now(),
       'flyway',
       now()
from public.sys_role sr
join public.resource_published rp
    on rp.resource_key = 'button_task_scan_list'
   and rp.permission_code = 'task:scan:list'
   and rp.del_flag = 0
where sr.role_key = 'admin'
  and sr.del_flag = 0
  and not exists (
      select 1
      from public.role_resource_published rrp
      where rrp.role_id = sr.role_id
        and rrp.resource_id = rp.id
  );

commit;

-- 复核 SQL：
-- select resource_key, parent_resource_id, permission_code, status, del_flag from public.resource_published where resource_key in ('button_task_scan_list', 'button_task_buff_list');
-- select child.resource_key, parent.resource_key as parent_key from public.resource_published child left join public.resource_published parent on parent.id = child.parent_resource_id where child.resource_key = 'button_task_scan_list';
