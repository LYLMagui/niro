-- ============================================================
-- RBAC P1 - PostgreSQL 数据修复脚本
-- 日期: 2026-03-15
-- 范围: sys_role / sys_menu / sys_user_role / sys_role_menu
-- 原则:
-- 1. 保留现有 5 表模型，不新增复杂权限表
-- 2. 不使用数据库外键，统一使用逻辑关联
-- 3. 先清理脏数据，再补最小约束和索引
-- 4. 脚本按“执行前核查 -> 修复 -> 执行后复核”顺序使用
-- ============================================================

-- ============================================================
-- A. 执行前核查 SQL
-- 用途: 先确认当前库状态，再决定是否执行 B 段修复
-- ============================================================

-- A1. 基础表数量
select 'sys_user' as table_name, count(*) as row_count from public.sys_user
union all
select 'sys_role', count(*) from public.sys_role
union all
select 'sys_menu', count(*) from public.sys_menu
union all
select 'sys_user_role', count(*) from public.sys_user_role
union all
select 'sys_role_menu', count(*) from public.sys_role_menu;

-- A2. 角色编码重复检查
select role_key, count(*) as duplicate_count
from public.sys_role
where del_flag = 0
group by role_key
having count(*) > 1;

-- A3. 角色菜单孤儿数据检查
select rm.role_id, rm.menu_id
from public.sys_role_menu rm
left join public.sys_role r on r.role_id = rm.role_id
left join public.sys_menu m on m.id = rm.menu_id
where r.role_id is null
   or m.id is null
order by rm.role_id, rm.menu_id;

-- A4. 用户角色孤儿数据检查
select 'missing_user' as issue_type, ur.user_id, ur.role_id
from public.sys_user_role ur
left join public.sys_user u on u.id = ur.user_id
where u.id is null
union all
select 'missing_role' as issue_type, ur.user_id, ur.role_id
from public.sys_user_role ur
left join public.sys_role r on r.role_id = ur.role_id
where r.role_id is null
order by issue_type, user_id, role_id;

-- A5. 菜单关键字段缺口检查
select id, parent_id, title, name, path, type, permission
from public.sys_menu
where (name is null or btrim(name) = '')
   or (type = 1 and (permission is null or btrim(permission) = ''))
order by id;

-- A6. 角色状态值检查
select role_id, role_name, role_key, status, del_flag
from public.sys_role
where status is null
   or status not in (0, 1)
order by role_id;

-- ============================================================
-- B. P1 修复 SQL
-- 说明:
-- 1. 建议先在测试库执行
-- 2. 建议整体放在事务内执行
-- 3. 若线上已有同名索引/约束, 请先核对命名是否冲突
-- ============================================================

begin;

-- B1. 清理 sys_role_menu 孤儿记录
delete from public.sys_role_menu rm
where not exists (
    select 1
    from public.sys_menu m
    where m.id = rm.menu_id
)
or not exists (
    select 1
    from public.sys_role r
    where r.role_id = rm.role_id
);

-- B2. 清理 sys_user_role 孤儿记录
delete from public.sys_user_role ur
where not exists (
    select 1
    from public.sys_user u
    where u.id = ur.user_id
)
or not exists (
    select 1
    from public.sys_role r
    where r.role_id = ur.role_id
);

-- B3. 统一角色状态语义
-- 约定: 1 = 正常, 0 = 停用
update public.sys_role
set status = 1
where status is null;

-- 如果历史库出现非 0/1 状态, 默认收敛为 1
update public.sys_role
set status = 1
where status not in (0, 1);

-- B4. 补齐缺失菜单字段
update public.sys_menu
set permission = 'system:dashboard:view'
where id = 1
  and type = 1
  and (permission is null or btrim(permission) = '');

update public.sys_menu
set permission = 'task:inventory:view'
where id = 11
  and type = 1
  and (permission is null or btrim(permission) = '');

update public.sys_menu
set name = 'ProfitStatistics'
where id = 12
  and (name is null or btrim(name) = '');

-- B5. 确保默认角色 user 可用于注册分配
-- 这里只做存在性校验用的兜底插入，不新增复杂字段
insert into public.sys_role (
    role_name,
    role_key,
    role_sort,
    data_scope,
    status,
    del_flag,
    create_by,
    create_time,
    update_by,
    update_time,
    remark
)
select
    '普通用户',
    'user',
    2,
    '1',
    1,
    0,
    'system',
    now(),
    'system',
    now(),
    'RBAC P1 初始化普通用户角色'
where not exists (
    select 1
    from public.sys_role
    where role_key = 'user'
      and del_flag = 0
);

-- B6. 为 role_key 增加最小唯一约束保护
do $$
begin
    if not exists (
        select 1
        from pg_indexes
        where schemaname = 'public'
          and indexname = 'uk_sys_role_role_key_active'
    ) then
        execute '
            create unique index uk_sys_role_role_key_active
            on public.sys_role (role_key)
            where del_flag = 0
        ';
    end if;
end
$$;

-- B7. 补充逻辑关联查询索引
create index if not exists idx_sys_user_role_role_id
    on public.sys_user_role (role_id);

create index if not exists idx_sys_role_menu_role_id
    on public.sys_role_menu (role_id);

create index if not exists idx_sys_role_menu_menu_id
    on public.sys_role_menu (menu_id);

-- B8. 修正普通用户菜单授权
-- 本轮只授权基础可用页面，避免过度复杂
delete from public.sys_role_menu
where role_id = (
    select role_id
    from public.sys_role
    where role_key = 'user'
      and del_flag = 0
    order by role_id
    limit 1
);

insert into public.sys_role_menu (role_id, menu_id)
select r.role_id, m.id
from public.sys_role r
join public.sys_menu m on m.id in (1, 2, 3, 4, 6, 11, 12, 14)
where r.role_key = 'user'
  and r.del_flag = 0
  and m.del_flag = 0
on conflict do nothing;

commit;

-- ============================================================
-- C. 执行后复核 SQL
-- ============================================================

-- C1. 确认 role_key 唯一索引已创建
select indexname, indexdef
from pg_indexes
where schemaname = 'public'
  and tablename = 'sys_role'
  and indexname = 'uk_sys_role_role_key_active';

-- C2. 确认孤儿数据已清理
select count(*) as orphan_role_menu_count
from public.sys_role_menu rm
left join public.sys_role r on r.role_id = rm.role_id
left join public.sys_menu m on m.id = rm.menu_id
where r.role_id is null
   or m.id is null;

select count(*) as orphan_user_role_count
from public.sys_user_role ur
left join public.sys_user u on u.id = ur.user_id
left join public.sys_role r on r.role_id = ur.role_id
where u.id is null
   or r.role_id is null;

-- C3. 确认角色状态语义已收敛
select role_id, role_name, role_key, status
from public.sys_role
order by role_id;

-- C4. 确认关键菜单字段已补齐
select id, title, name, path, type, permission
from public.sys_menu
where id in (1, 11, 12)
order by id;

-- C5. 确认普通用户授权结果
select r.role_key, m.id, m.title, m.path, m.permission
from public.sys_role_menu rm
join public.sys_role r on r.role_id = rm.role_id
join public.sys_menu m on m.id = rm.menu_id
where r.role_key = 'user'
order by m.id;
