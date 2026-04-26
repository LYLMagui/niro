-- ============================================================
-- C5 扫货 2.0 V1（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-24
-- 目标:
-- 1. 建立 C5 扫货 2.0 的四张核心表：c5_sniping_task_v2、c5_sniping_task_run_v2、c5_sniping_hit_record_v2、c5_sniping_buy_attempt_v2
-- 2. 将任务定义、运行实例、命中明细与下单尝试拆表，替代旧 buff_scan_task 的运行态职责
-- 3. 落地账号维度唯一启用、同账号同 listing 唯一尝试与 BUY_COUNT 防超买所需字段
-- 幂等说明:
-- - 仅新增表、索引与注释
-- - 所有对象使用 if not exists 或 drop/create index if exists 的安全写法
-- - 不修改历史 migration，不引入外键，避免影响现有主数据与旧链路
-- 回滚思路:
-- - 本文件仅新增对象；如需回滚，可在后续 migration 中软弃用或删除新增对象
-- ============================================================

begin;

create table if not exists public.c5_sniping_task_v2 (
    id bigint generated always as identity primary key,
    user_id bigint not null default 0,
    account_id bigint not null default 0,
    cs2_goods_id bigint not null default 0,
    name varchar(128) not null default '',
    max_price numeric(12, 2) not null default 0,
    min_paintwear numeric(8, 6),
    max_paintwear numeric(8, 6),
    stop_mode varchar(32) not null default 'BUY_COUNT',
    target_buy_count integer not null default 0,
    balance_guard_mode varchar(32) not null default '',
    reserve_balance numeric(12, 2),
    priority integer not null default 0,
    scan_interval_ms bigint not null default 1000,
    task_status varchar(32) not null default 'DRAFT',
    latest_run_id bigint,
    success_buy_count integer not null default 0,
    reserved_buy_count integer not null default 0,
    hit_count integer not null default 0,
    last_error_message varchar(500) not null default '',
    version integer not null default 0,
    del_flag integer not null default 0,
    create_time timestamp not null default now(),
    update_time timestamp not null default now(),
    constraint chk_c5_sniping_task_v2_stop_mode check (stop_mode in ('BUY_COUNT', 'BALANCE_GUARD')),
    constraint chk_c5_sniping_task_v2_balance_guard_mode check (balance_guard_mode in ('', 'MAX_PRICE', 'RESERVE_BALANCE')),
    constraint chk_c5_sniping_task_v2_status check (task_status in ('DRAFT', 'READY', 'RUNNING', 'STOPPED', 'COMPLETED', 'ERROR')),
    constraint chk_c5_sniping_task_v2_del_flag check (del_flag in (0, 1)),
    constraint chk_c5_sniping_task_v2_target_buy_count check (target_buy_count >= 0),
    constraint chk_c5_sniping_task_v2_success_buy_count check (success_buy_count >= 0),
    constraint chk_c5_sniping_task_v2_reserved_buy_count check (reserved_buy_count >= 0),
    constraint chk_c5_sniping_task_v2_hit_count check (hit_count >= 0),
    constraint chk_c5_sniping_task_v2_priority check (priority >= 0),
    constraint chk_c5_sniping_task_v2_scan_interval_ms check (scan_interval_ms >= 200),
    constraint chk_c5_sniping_task_v2_paintwear_range check (
        (min_paintwear is null or (min_paintwear >= 0 and min_paintwear <= 1))
        and (max_paintwear is null or (max_paintwear >= 0 and max_paintwear <= 1))
        and (min_paintwear is null or max_paintwear is null or min_paintwear <= max_paintwear)
    )
);

comment on table public.c5_sniping_task_v2 is 'C5扫货2.0任务定义表';
comment on column public.c5_sniping_task_v2.user_id is '创建用户ID';
comment on column public.c5_sniping_task_v2.account_id is '绑定的C5账号ID';
comment on column public.c5_sniping_task_v2.cs2_goods_id is '目标商品ID，对应cs2_goods.id';
comment on column public.c5_sniping_task_v2.name is '任务名称';
comment on column public.c5_sniping_task_v2.max_price is '目标最高价格';
comment on column public.c5_sniping_task_v2.min_paintwear is '最小磨损';
comment on column public.c5_sniping_task_v2.max_paintwear is '最大磨损';
comment on column public.c5_sniping_task_v2.stop_mode is '停止模式(BUY_COUNT/BALANCE_GUARD)';
comment on column public.c5_sniping_task_v2.target_buy_count is '目标购买数量';
comment on column public.c5_sniping_task_v2.balance_guard_mode is '余额保护模式(MAX_PRICE/RESERVE_BALANCE)';
comment on column public.c5_sniping_task_v2.reserve_balance is '保底余额阈值';
comment on column public.c5_sniping_task_v2.priority is '调度优先级，越大越高';
comment on column public.c5_sniping_task_v2.scan_interval_ms is '扫描间隔毫秒';
comment on column public.c5_sniping_task_v2.task_status is '任务状态';
comment on column public.c5_sniping_task_v2.latest_run_id is '最近一次运行实例ID';
comment on column public.c5_sniping_task_v2.success_buy_count is '累计成功购买数';
comment on column public.c5_sniping_task_v2.reserved_buy_count is '已预占但未最终确认的购买名额';
comment on column public.c5_sniping_task_v2.hit_count is '累计命中数';
comment on column public.c5_sniping_task_v2.last_error_message is '最近一次错误信息';
comment on column public.c5_sniping_task_v2.version is '乐观锁版本号';
comment on column public.c5_sniping_task_v2.del_flag is '删除标识';

create index if not exists idx_c5_sniping_task_v2_user_id on public.c5_sniping_task_v2 (user_id);
create index if not exists idx_c5_sniping_task_v2_account_priority on public.c5_sniping_task_v2 (account_id, task_status, priority desc, update_time asc);
create index if not exists idx_c5_sniping_task_v2_goods_id on public.c5_sniping_task_v2 (cs2_goods_id);
create index if not exists idx_c5_sniping_task_v2_latest_run_id on public.c5_sniping_task_v2 (latest_run_id);
drop index if exists public.uk_c5_sniping_task_v2_enabled_account_goods;
create unique index if not exists uk_c5_sniping_task_v2_enabled_account_goods
    on public.c5_sniping_task_v2 (account_id, cs2_goods_id)
    where del_flag = 0 and task_status in ('READY', 'RUNNING');

create table if not exists public.c5_sniping_task_run_v2 (
    id bigint generated always as identity primary key,
    task_id bigint not null default 0,
    run_status varchar(32) not null default 'RUNNING',
    stop_reason varchar(64) not null default '',
    started_at timestamp not null default now(),
    finished_at timestamp,
    retry_count integer not null default 0,
    consecutive_error_count integer not null default 0,
    hit_count integer not null default 0,
    buy_attempt_count integer not null default 0,
    buy_success_count integer not null default 0,
    last_error_message varchar(500) not null default '',
    create_time timestamp not null default now(),
    update_time timestamp not null default now(),
    constraint chk_c5_sniping_task_run_v2_status check (run_status in ('RUNNING', 'STOPPED', 'COMPLETED', 'ERROR')),
    constraint chk_c5_sniping_task_run_v2_retry_count check (retry_count >= 0),
    constraint chk_c5_sniping_task_run_v2_consecutive_error_count check (consecutive_error_count >= 0),
    constraint chk_c5_sniping_task_run_v2_hit_count check (hit_count >= 0),
    constraint chk_c5_sniping_task_run_v2_buy_attempt_count check (buy_attempt_count >= 0),
    constraint chk_c5_sniping_task_run_v2_buy_success_count check (buy_success_count >= 0)
);

comment on table public.c5_sniping_task_run_v2 is 'C5扫货2.0运行实例表';
comment on column public.c5_sniping_task_run_v2.task_id is '任务ID';
comment on column public.c5_sniping_task_run_v2.run_status is '运行状态';
comment on column public.c5_sniping_task_run_v2.stop_reason is '停止原因';
comment on column public.c5_sniping_task_run_v2.started_at is '开始时间';
comment on column public.c5_sniping_task_run_v2.finished_at is '结束时间';
comment on column public.c5_sniping_task_run_v2.retry_count is '累计重试次数';
comment on column public.c5_sniping_task_run_v2.consecutive_error_count is '连续错误次数';
comment on column public.c5_sniping_task_run_v2.hit_count is '本次运行命中数';
comment on column public.c5_sniping_task_run_v2.buy_attempt_count is '本次运行下单尝试数';
comment on column public.c5_sniping_task_run_v2.buy_success_count is '本次运行成功下单数';
comment on column public.c5_sniping_task_run_v2.last_error_message is '最近一次错误信息';

create index if not exists idx_c5_sniping_task_run_v2_task_id on public.c5_sniping_task_run_v2 (task_id, started_at desc);
drop index if exists public.uk_c5_sniping_task_run_v2_running_task;
create unique index if not exists uk_c5_sniping_task_run_v2_running_task
    on public.c5_sniping_task_run_v2 (task_id)
    where run_status = 'RUNNING';

create table if not exists public.c5_sniping_hit_record_v2 (
    id bigint generated always as identity primary key,
    task_id bigint not null default 0,
    run_id bigint not null default 0,
    account_id bigint not null default 0,
    listing_id varchar(64) not null default '',
    listing_price numeric(12, 2) not null default 0,
    paintwear numeric(8, 6),
    decision_result varchar(64) not null default '',
    item_snapshot_json jsonb,
    hit_at timestamp not null default now(),
    create_time timestamp not null default now()
);

comment on table public.c5_sniping_hit_record_v2 is 'C5扫货2.0命中明细表';
comment on column public.c5_sniping_hit_record_v2.task_id is '任务ID';
comment on column public.c5_sniping_hit_record_v2.run_id is '运行实例ID';
comment on column public.c5_sniping_hit_record_v2.account_id is '账号ID';
comment on column public.c5_sniping_hit_record_v2.listing_id is '平台商品listing/product ID';
comment on column public.c5_sniping_hit_record_v2.listing_price is '命中价格';
comment on column public.c5_sniping_hit_record_v2.paintwear is '命中磨损';
comment on column public.c5_sniping_hit_record_v2.decision_result is '命中后的处理结果';
comment on column public.c5_sniping_hit_record_v2.item_snapshot_json is '命中商品快照';
comment on column public.c5_sniping_hit_record_v2.hit_at is '命中时间';

create index if not exists idx_c5_sniping_hit_record_v2_task_run on public.c5_sniping_hit_record_v2 (task_id, run_id, hit_at desc);
create index if not exists idx_c5_sniping_hit_record_v2_listing_id on public.c5_sniping_hit_record_v2 (listing_id);

create table if not exists public.c5_sniping_buy_attempt_v2 (
    id bigint generated always as identity primary key,
    task_id bigint not null default 0,
    run_id bigint not null default 0,
    hit_record_id bigint not null default 0,
    account_id bigint not null default 0,
    listing_id varchar(64) not null default '',
    idempotency_key varchar(128) not null default '',
    attempt_status varchar(32) not null default 'INIT',
    order_record_id bigint,
    failure_code varchar(64) not null default '',
    failure_message varchar(500) not null default '',
    created_at timestamp not null default now(),
    finished_at timestamp,
    update_time timestamp not null default now(),
    constraint chk_c5_sniping_buy_attempt_v2_status check (attempt_status in ('INIT', 'SUCCESS', 'FAILED', 'SKIPPED'))
);

comment on table public.c5_sniping_buy_attempt_v2 is 'C5扫货2.0下单尝试表';
comment on column public.c5_sniping_buy_attempt_v2.task_id is '任务ID';
comment on column public.c5_sniping_buy_attempt_v2.run_id is '运行实例ID';
comment on column public.c5_sniping_buy_attempt_v2.hit_record_id is '命中明细ID';
comment on column public.c5_sniping_buy_attempt_v2.account_id is '账号ID';
comment on column public.c5_sniping_buy_attempt_v2.listing_id is '平台商品listing/product ID';
comment on column public.c5_sniping_buy_attempt_v2.idempotency_key is '幂等键';
comment on column public.c5_sniping_buy_attempt_v2.attempt_status is '尝试状态';
comment on column public.c5_sniping_buy_attempt_v2.order_record_id is '关联订单记录ID';
comment on column public.c5_sniping_buy_attempt_v2.failure_code is '失败码';
comment on column public.c5_sniping_buy_attempt_v2.failure_message is '失败信息';

create index if not exists idx_c5_sniping_buy_attempt_v2_task_run on public.c5_sniping_buy_attempt_v2 (task_id, run_id, created_at desc);
drop index if exists public.uk_c5_sniping_buy_attempt_v2_account_listing;
create unique index if not exists uk_c5_sniping_buy_attempt_v2_account_listing
    on public.c5_sniping_buy_attempt_v2 (account_id, listing_id);
create unique index if not exists uk_c5_sniping_buy_attempt_v2_idempotency_key
    on public.c5_sniping_buy_attempt_v2 (idempotency_key);

commit;
