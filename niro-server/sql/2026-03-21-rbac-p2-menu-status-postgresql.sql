-- ============================================================
-- RBAC P2 - sys_menu.status 字段补齐脚本（PostgreSQL）
-- 日期: 2026-03-21
-- 目标:
-- 1. 为 sys_menu 增加 status 字段（1=正常, 0=停用）
-- 2. 清洗历史空值并补充约束
-- 3. 脚本可重复执行
-- ============================================================

begin;

alter table public.sys_menu
    add column if not exists status smallint default 1;

update public.sys_menu
set status = 1
where status is null;

alter table public.sys_menu
    alter column status set default 1;

alter table public.sys_menu
    alter column status set not null;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'ck_sys_menu_status'
          and conrelid = 'public.sys_menu'::regclass
    ) then
        alter table public.sys_menu
            add constraint ck_sys_menu_status check (status in (0, 1));
    end if;
end
$$;

create index if not exists idx_sys_menu_status
    on public.sys_menu (status);

commit;

