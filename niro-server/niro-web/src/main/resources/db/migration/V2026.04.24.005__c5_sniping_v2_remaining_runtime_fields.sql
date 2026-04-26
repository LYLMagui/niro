-- ============================================================
-- C5 扫货 2.0 剩余运行态字段补齐（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-24
-- 目标:
-- 1. 给 c5_sniping_task_v2 补齐人工停用请求与下一次可扫描时间字段，支持安全停用、单轮占槽调度和公平轮询
-- 2. 给 c5_sniping_buy_attempt_v2 补齐在途金额与远端核验时间字段，支持账号级在途约束和恢复扫描
-- 3. 补齐 READY 调度、运行中停用请求与账号在途约束扫描所需索引
-- 幂等策略:
-- - 新增字段使用 alter table ... add column if not exists，重复执行不会重复添加字段
-- - 索引使用 create index if not exists，重复执行不会重复创建同名索引
-- - 仅追加字段、注释与索引，不修改历史 migration，不删除既有对象
-- 回滚思路:
-- - 如需回滚，可在后续 migration 中 drop index if exists 删除新增索引，并按兼容窗口评估是否 drop column
-- - 破坏性删除字段不在本 migration 中执行
-- ============================================================

begin;

alter table public.c5_sniping_task_v2
    add column if not exists stop_requested boolean not null default false,
    add column if not exists stop_requested_at timestamp,
    add column if not exists next_scan_at timestamp not null default now();

comment on column public.c5_sniping_task_v2.stop_requested is '人工停用请求标记，运行循环在安全点自然收尾';
comment on column public.c5_sniping_task_v2.stop_requested_at is '人工停用请求时间';
comment on column public.c5_sniping_task_v2.next_scan_at is '下一次可扫描时间，用于单轮占槽调度和公平轮询';

alter table public.c5_sniping_buy_attempt_v2
    add column if not exists in_flight_amount numeric(18, 2) not null default 0,
    add column if not exists remote_checked_at timestamp;

comment on column public.c5_sniping_buy_attempt_v2.in_flight_amount is '本次尝试在途金额，用于账号级在途金额估算';
comment on column public.c5_sniping_buy_attempt_v2.remote_checked_at is '恢复或同步时最近一次远端订单状态核验时间';

create index if not exists idx_c5_sniping_task_v2_ready_schedule
    on public.c5_sniping_task_v2 (account_id, task_status, next_scan_at, priority desc);

create index if not exists idx_c5_sniping_task_v2_stop_requested_running
    on public.c5_sniping_task_v2 (stop_requested, task_status)
    where stop_requested = true and task_status = 'RUNNING';

create index if not exists idx_c5_sniping_buy_attempt_v2_account_in_flight
    on public.c5_sniping_buy_attempt_v2 (account_id, attempt_status, slot_reserved, slot_released);

commit;
