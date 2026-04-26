-- ============================================================
-- 新权限系统一期 V2 补充 migration（PostgreSQL · 追加式）
-- 日期: 2026-04-24
-- 目标:
-- 1. 为新权限资源表补充页面 / 按钮一致性约束，避免草稿与发布态出现脏数据
-- 2. 为按钮权限码增加唯一保护，满足发布前“权限码不重复”的设计要求
-- 3. 注入一期试点资源与 admin 默认授权，保证新权限工作台首次可用
-- 幂等说明:
-- - 约束按名称检查后再创建
-- - 唯一索引使用 if not exists
-- - 种子数据按 resource_key / role_id + resource_id 做 not exists 保护
-- 回滚思路:
-- - 后续如需回滚，可单独新增 migration 删除种子数据，并废弃或移除本次新增约束 / 索引
-- ============================================================

begin;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_resource_draft_page_key_by_type'
    ) then
        alter table public.resource_draft
            add constraint chk_resource_draft_page_key_by_type
                check (
                    (resource_type = 'BUTTON' and page_key = '')
                    or (resource_type in ('PAGE', 'MENU') and page_key <> '')
                );
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_resource_draft_permission_code_by_type'
    ) then
        alter table public.resource_draft
            add constraint chk_resource_draft_permission_code_by_type
                check (
                    (resource_type = 'BUTTON' and permission_code <> '')
                    or (resource_type in ('PAGE', 'MENU') and permission_code = '')
                );
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_resource_published_page_key_by_type'
    ) then
        alter table public.resource_published
            add constraint chk_resource_published_page_key_by_type
                check (
                    (resource_type = 'BUTTON' and page_key = '')
                    or (resource_type in ('PAGE', 'MENU') and page_key <> '')
                );
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_resource_published_permission_code_by_type'
    ) then
        alter table public.resource_published
            add constraint chk_resource_published_permission_code_by_type
                check (
                    (resource_type = 'BUTTON' and permission_code <> '')
                    or (resource_type in ('PAGE', 'MENU') and permission_code = '')
                );
    end if;
end $$;

create unique index if not exists uk_resource_draft_button_permission_code
    on public.resource_draft (permission_code)
    where resource_type = 'BUTTON' and permission_code <> '';

create unique index if not exists uk_resource_published_button_permission_code
    on public.resource_published (permission_code)
    where resource_type = 'BUTTON' and permission_code <> '';

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
    v.resource_key,
    v.resource_type,
    v.parent_resource_id,
    v.page_key,
    v.title,
    v.icon,
    v.sort_order,
    v.hidden,
    v.permission_code,
    v.button_group,
    v.remark,
    v.status,
    v.created_by,
    v.created_at,
    v.updated_by,
    v.updated_at,
    v.del_flag
from (
    values
        ('permission_manage_new', 'PAGE', 0, 'PermissionManageNew', '新权限管理', 'control', 10, false, '', '', '新权限系统一期试点页面', 1, 'flyway', now(), 'flyway', now(), 0),
        ('invite_code_manage_new', 'PAGE', 0, 'InviteCodeManageNew', '邀请码管理', 'qrcode', 20, false, '', '', '新权限系统一期试点页面', 1, 'flyway', now(), 'flyway', now(), 0),
        ('logs_new', 'PAGE', 0, 'LogsNew', '全链路日志', 'file-search', 30, false, '', '', '新权限系统一期试点页面', 1, 'flyway', now(), 'flyway', now(), 0)
) as v(
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
where not exists (
    select 1
    from public.resource_draft r
    where r.resource_key = v.resource_key
);

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
    v.resource_key,
    v.resource_type,
    v.parent_resource_id,
    v.page_key,
    v.title,
    v.icon,
    v.sort_order,
    v.hidden,
    v.permission_code,
    v.button_group,
    v.remark,
    v.status,
    v.created_by,
    v.created_at,
    v.updated_by,
    v.updated_at,
    v.del_flag
from (
    values
        ('permission_manage_new', 'PAGE', 0, 'PermissionManageNew', '新权限管理', 'control', 10, false, '', '', '新权限系统一期试点页面', 1, 'flyway', now(), 'flyway', now(), 0),
        ('invite_code_manage_new', 'PAGE', 0, 'InviteCodeManageNew', '邀请码管理', 'qrcode', 20, false, '', '', '新权限系统一期试点页面', 1, 'flyway', now(), 'flyway', now(), 0),
        ('logs_new', 'PAGE', 0, 'LogsNew', '全链路日志', 'file-search', 30, false, '', '', '新权限系统一期试点页面', 1, 'flyway', now(), 'flyway', now(), 0)
) as v(
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
where not exists (
    select 1
    from public.resource_published r
    where r.resource_key = v.resource_key
);

with admin_role as (
    select role_id
    from public.sys_role
    where role_key = 'admin'
      and del_flag = 0
    limit 1
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
    rd.id,
    'flyway',
    now(),
    'flyway',
    now(),
    0
from admin_role ar
join public.resource_draft rd
    on rd.resource_key in ('permission_manage_new', 'invite_code_manage_new', 'logs_new')
where not exists (
    select 1
    from public.role_resource_draft rrd
    where rrd.role_id = ar.role_id
      and rrd.resource_id = rd.id
);

with admin_role as (
    select role_id
    from public.sys_role
    where role_key = 'admin'
      and del_flag = 0
    limit 1
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
    rp.id,
    'flyway',
    now(),
    'flyway',
    now(),
    0
from admin_role ar
join public.resource_published rp
    on rp.resource_key in ('permission_manage_new', 'invite_code_manage_new', 'logs_new')
where not exists (
    select 1
    from public.role_resource_published rrp
    where rrp.role_id = ar.role_id
      and rrp.resource_id = rp.id
);

commit;
