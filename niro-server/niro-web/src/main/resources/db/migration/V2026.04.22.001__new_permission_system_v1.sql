-- ============================================================
-- 新权限系统一期 V1（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-22
-- 目标:
-- 1. 建立新权限系统一期的四张核心表：resource_draft、role_resource_draft、resource_published、role_resource_published
-- 2. 提供草稿 / 已发布双态模型，支持保存并发布
-- 3. 复用旧 sys_role 作为角色主体，不引入用户/角色新体系
-- 幂等说明:
-- - 仅新增表、索引与注释
-- - 所有对象使用 if not exists 或先判断再创建
-- - 不修改历史 migration，不使用外键，不引入发布批次/回滚体系
-- 回滚思路:
-- - 本文件仅新增对象；如需回滚，可在后续单独 migration 中软弃用或删除新增对象
-- ============================================================

begin;

create table if not exists public.resource_draft (
    id bigint generated always as identity primary key,
    resource_key varchar(128) not null default '',
    resource_type varchar(16) not null default '',
    parent_resource_id bigint not null default 0,
    page_key varchar(128) not null default '',
    title varchar(128) not null default '',
    icon varchar(128) not null default '',
    sort_order integer not null default 0,
    hidden boolean not null default false,
    permission_code varchar(128) not null default '',
    button_group varchar(128) not null default '',
    remark text not null default '',
    status integer not null default 1,
    created_by varchar(64) not null default '',
    created_at timestamp not null default now(),
    updated_by varchar(64) not null default '',
    updated_at timestamp not null default now(),
    del_flag integer not null default 0,
    constraint uk_resource_draft_resource_key unique (resource_key),
    constraint chk_resource_draft_type check (resource_type in ('PAGE', 'MENU', 'BUTTON')),
    constraint chk_resource_draft_status check (status in (0, 1)),
    constraint chk_resource_draft_del_flag check (del_flag in (0, 1))
);

comment on table public.resource_draft is '新权限系统资源草稿表';
comment on column public.resource_draft.id is '主键';
comment on column public.resource_draft.resource_key is '资源唯一键';
comment on column public.resource_draft.resource_type is '资源类型（PAGE/MENU/BUTTON）';
comment on column public.resource_draft.parent_resource_id is '父资源ID';
comment on column public.resource_draft.page_key is '前端页面注册表键';
comment on column public.resource_draft.title is '资源标题';
comment on column public.resource_draft.icon is '图标';
comment on column public.resource_draft.sort_order is '排序';
comment on column public.resource_draft.hidden is '是否隐藏';
comment on column public.resource_draft.permission_code is '权限码';
comment on column public.resource_draft.button_group is '按钮分组';
comment on column public.resource_draft.remark is '备注';
comment on column public.resource_draft.status is '状态（1正常 0停用）';
comment on column public.resource_draft.created_by is '创建者';
comment on column public.resource_draft.created_at is '创建时间';
comment on column public.resource_draft.updated_by is '更新者';
comment on column public.resource_draft.updated_at is '更新时间';
comment on column public.resource_draft.del_flag is '删除标识（0正常 1删除）';

create index if not exists idx_resource_draft_parent_resource_id
    on public.resource_draft (parent_resource_id);
create index if not exists idx_resource_draft_page_key
    on public.resource_draft (page_key);
create index if not exists idx_resource_draft_type_status
    on public.resource_draft (resource_type, status);

create table if not exists public.role_resource_draft (
    id bigint generated always as identity primary key,
    role_id bigint not null default 0,
    resource_id bigint not null default 0,
    created_by varchar(64) not null default '',
    created_at timestamp not null default now(),
    updated_by varchar(64) not null default '',
    updated_at timestamp not null default now(),
    del_flag integer not null default 0,
    constraint uk_role_resource_draft unique (role_id, resource_id),
    constraint chk_role_resource_draft_del_flag check (del_flag in (0, 1))
);

comment on table public.role_resource_draft is '新权限系统角色资源草稿表';
comment on column public.role_resource_draft.id is '主键';
comment on column public.role_resource_draft.role_id is '角色ID，复用 sys_role';
comment on column public.role_resource_draft.resource_id is '资源ID';
comment on column public.role_resource_draft.created_by is '创建者';
comment on column public.role_resource_draft.created_at is '创建时间';
comment on column public.role_resource_draft.updated_by is '更新者';
comment on column public.role_resource_draft.updated_at is '更新时间';
comment on column public.role_resource_draft.del_flag is '删除标识（0正常 1删除）';

create index if not exists idx_role_resource_draft_role_id
    on public.role_resource_draft (role_id);
create index if not exists idx_role_resource_draft_resource_id
    on public.role_resource_draft (resource_id);

create table if not exists public.resource_published (
    id bigint generated always as identity primary key,
    resource_key varchar(128) not null default '',
    resource_type varchar(16) not null default '',
    parent_resource_id bigint not null default 0,
    page_key varchar(128) not null default '',
    title varchar(128) not null default '',
    icon varchar(128) not null default '',
    sort_order integer not null default 0,
    hidden boolean not null default false,
    permission_code varchar(128) not null default '',
    button_group varchar(128) not null default '',
    remark text not null default '',
    status integer not null default 1,
    created_by varchar(64) not null default '',
    created_at timestamp not null default now(),
    updated_by varchar(64) not null default '',
    updated_at timestamp not null default now(),
    del_flag integer not null default 0,
    constraint uk_resource_published_resource_key unique (resource_key),
    constraint chk_resource_published_type check (resource_type in ('PAGE', 'MENU', 'BUTTON')),
    constraint chk_resource_published_status check (status in (0, 1)),
    constraint chk_resource_published_del_flag check (del_flag in (0, 1))
);

comment on table public.resource_published is '新权限系统资源已发布表';
comment on column public.resource_published.id is '主键';
comment on column public.resource_published.resource_key is '资源唯一键';
comment on column public.resource_published.resource_type is '资源类型（PAGE/MENU/BUTTON）';
comment on column public.resource_published.parent_resource_id is '父资源ID';
comment on column public.resource_published.page_key is '前端页面注册表键';
comment on column public.resource_published.title is '资源标题';
comment on column public.resource_published.icon is '图标';
comment on column public.resource_published.sort_order is '排序';
comment on column public.resource_published.hidden is '是否隐藏';
comment on column public.resource_published.permission_code is '权限码';
comment on column public.resource_published.button_group is '按钮分组';
comment on column public.resource_published.remark is '备注';
comment on column public.resource_published.status is '状态（1正常 0停用）';
comment on column public.resource_published.created_by is '创建者';
comment on column public.resource_published.created_at is '创建时间';
comment on column public.resource_published.updated_by is '更新者';
comment on column public.resource_published.updated_at is '更新时间';
comment on column public.resource_published.del_flag is '删除标识（0正常 1删除）';

create index if not exists idx_resource_published_parent_resource_id
    on public.resource_published (parent_resource_id);
create index if not exists idx_resource_published_page_key
    on public.resource_published (page_key);
create index if not exists idx_resource_published_type_status
    on public.resource_published (resource_type, status);

create table if not exists public.role_resource_published (
    id bigint generated always as identity primary key,
    role_id bigint not null default 0,
    resource_id bigint not null default 0,
    created_by varchar(64) not null default '',
    created_at timestamp not null default now(),
    updated_by varchar(64) not null default '',
    updated_at timestamp not null default now(),
    del_flag integer not null default 0,
    constraint uk_role_resource_published unique (role_id, resource_id),
    constraint chk_role_resource_published_del_flag check (del_flag in (0, 1))
);

comment on table public.role_resource_published is '新权限系统角色资源已发布表';
comment on column public.role_resource_published.id is '主键';
comment on column public.role_resource_published.role_id is '角色ID，复用 sys_role';
comment on column public.role_resource_published.resource_id is '资源ID';
comment on column public.role_resource_published.created_by is '创建者';
comment on column public.role_resource_published.created_at is '创建时间';
comment on column public.role_resource_published.updated_by is '更新者';
comment on column public.role_resource_published.updated_at is '更新时间';
comment on column public.role_resource_published.del_flag is '删除标识（0正常 1删除）';

create index if not exists idx_role_resource_published_role_id
    on public.role_resource_published (role_id);
create index if not exists idx_role_resource_published_resource_id
    on public.role_resource_published (resource_id);

commit;
