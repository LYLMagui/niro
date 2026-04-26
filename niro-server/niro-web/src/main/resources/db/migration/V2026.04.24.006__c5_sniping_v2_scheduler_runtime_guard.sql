-- ============================================================
-- C5 扫货 2.0 调度租约与账号运行态保护（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-24
-- 目标:
-- 1. 给 c5_sniping_task_v2 补齐跨实例调度 lease 字段，支持多实例安全抢占与租约过期重试
-- 2. 新增 c5_sniping_account_runtime_v2，支撑账号级并发配置、在途下单数限制与冷却控制
-- 3. 给 c5_sniping_buy_attempt_v2 补齐 INIT 自动超时与恢复核验计数字段，并补齐调度扫描索引
-- 幂等策略:
-- - 新增字段使用 alter table ... add column if not exists，重复执行不会重复添加字段
-- - 新表使用 create table if not exists，索引使用 create index if not exists，重复执行不会重复创建同名对象
-- - PostgreSQL 不支持 add constraint if not exists，检查约束使用 do $$ ... if not exists ... $$ 块保护
-- - 仅追加字段、表、约束、注释与索引，不修改历史 migration，不删除既有对象
-- 回滚思路:
-- - 如需回滚，可在后续 migration 中 drop index if exists 删除新增索引，软停用账号运行态配置
-- - 字段和表删除属于破坏性变更，应先观察兼容窗口后再通过后续 migration 执行
-- ============================================================

begin;

alter table public.c5_sniping_task_v2
    add column if not exists lease_owner varchar(128) not null default '',
    add column if not exists lease_until timestamp;

comment on column public.c5_sniping_task_v2.lease_owner is '调度租约持有者实例标识';
comment on column public.c5_sniping_task_v2.lease_until is '调度租约过期时间';

create table if not exists public.c5_sniping_account_runtime_v2 (
    id bigint generated always as identity primary key,
    account_id bigint not null default 0,
    concurrency_limit integer not null default 1,
    max_in_flight_attempts integer not null default 1,
    cooldown_until timestamp,
    cooldown_reason varchar(500) not null default '',
    create_time timestamp not null default now(),
    update_time timestamp not null default now(),
    constraint chk_c5_sniping_account_runtime_v2_concurrency_limit check (concurrency_limit >= 1),
    constraint chk_c5_sniping_account_runtime_v2_max_in_flight_attempts check (max_in_flight_attempts >= 1)
);

comment on table public.c5_sniping_account_runtime_v2 is 'C5扫货2.0账号运行态配置与冷却表';
comment on column public.c5_sniping_account_runtime_v2.id is '主键';
comment on column public.c5_sniping_account_runtime_v2.account_id is '账号ID';
comment on column public.c5_sniping_account_runtime_v2.concurrency_limit is '账号级并发执行上限';
comment on column public.c5_sniping_account_runtime_v2.max_in_flight_attempts is '账号级最大在途下单尝试数';
comment on column public.c5_sniping_account_runtime_v2.cooldown_until is '账号冷却截止时间';
comment on column public.c5_sniping_account_runtime_v2.cooldown_reason is '账号冷却原因';
comment on column public.c5_sniping_account_runtime_v2.create_time is '创建时间';
comment on column public.c5_sniping_account_runtime_v2.update_time is '更新时间';

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_c5_sniping_account_runtime_v2_concurrency_limit'
          and conrelid = 'public.c5_sniping_account_runtime_v2'::regclass
    ) then
        alter table public.c5_sniping_account_runtime_v2
            add constraint chk_c5_sniping_account_runtime_v2_concurrency_limit check (concurrency_limit >= 1);
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_c5_sniping_account_runtime_v2_max_in_flight_attempts'
          and conrelid = 'public.c5_sniping_account_runtime_v2'::regclass
    ) then
        alter table public.c5_sniping_account_runtime_v2
            add constraint chk_c5_sniping_account_runtime_v2_max_in_flight_attempts check (max_in_flight_attempts >= 1);
    end if;
end $$;

create unique index if not exists uk_c5_sniping_account_runtime_v2_account_id
    on public.c5_sniping_account_runtime_v2 (account_id);

create index if not exists idx_c5_sniping_account_runtime_v2_cooldown_until
    on public.c5_sniping_account_runtime_v2 (cooldown_until);

alter table public.c5_sniping_buy_attempt_v2
    add column if not exists init_expire_at timestamp,
    add column if not exists recovery_attempt_count integer not null default 0;

comment on column public.c5_sniping_buy_attempt_v2.init_expire_at is 'INIT 状态自动超时处理时间';
comment on column public.c5_sniping_buy_attempt_v2.recovery_attempt_count is '启动恢复或补偿核验次数';

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_c5_sniping_buy_attempt_v2_recovery_attempt_count'
          and conrelid = 'public.c5_sniping_buy_attempt_v2'::regclass
    ) then
        alter table public.c5_sniping_buy_attempt_v2
            add constraint chk_c5_sniping_buy_attempt_v2_recovery_attempt_count check (recovery_attempt_count >= 0);
    end if;
end $$;

create index if not exists idx_c5_sniping_task_v2_ready_lease_schedule
    on public.c5_sniping_task_v2 (task_status, next_scan_at, lease_until, priority desc)
    where del_flag = 0;

create index if not exists idx_c5_sniping_buy_attempt_v2_init_expire
    on public.c5_sniping_buy_attempt_v2 (attempt_status, init_expire_at)
    where attempt_status = 'INIT' and slot_released = false;

commit;
