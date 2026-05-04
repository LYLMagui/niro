-- ============================================================
-- C5 市场价格快照表（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-30
-- 目标:
-- 1. 新增 c5_market_price_snapshot 最新价格快照表
-- 2. 按 app_id + market_hash_name + range_type + wear_min + wear_max 保证快照唯一
-- 3. 补齐刷新扫描、商品名称查询索引和状态/区间检查约束
-- 幂等策略:
-- - 新表使用 create table if not exists
-- - 索引使用 create unique index if not exists / create index if not exists
-- - 检查约束通过 pg_constraint 判重后追加
-- 回滚思路:
-- - 如需回滚，可新增 migration 将 refresh_enabled 置为 false，并让业务停止读写快照表
-- - 表数据为行情缓存，可按后续 migration 软停用或清理
-- ============================================================

begin;

create table if not exists public.c5_market_price_snapshot (
    id bigint generated always as identity primary key,
    app_id integer not null default 730,
    market_hash_name varchar(255) not null default '',
    range_type varchar(32) not null default 'WEAR',
    wear_min numeric(12, 8) not null default 0,
    wear_max numeric(12, 8) not null default 1.00000000,
    page_num integer not null default 1,
    page_size integer not null default 50,
    lowest_price numeric(12, 2) not null default 0,
    avg_price numeric(12, 2) not null default 0,
    sample_count integer not null default 0,
    has_more boolean not null default false,
    listings_json jsonb not null default '[]'::jsonb,
    refresh_enabled boolean not null default true,
    refresh_priority integer not null default 0,
    refresh_interval_seconds integer not null default 300,
    next_refresh_time timestamp not null default now(),
    last_fetch_time timestamp not null default '1970-01-01 00:00:00',
    last_success_time timestamp not null default '1970-01-01 00:00:00',
    last_request_time timestamp not null default '1970-01-01 00:00:00',
    status varchar(32) not null default 'PENDING',
    fetch_count bigint not null default 0,
    fail_count integer not null default 0,
    last_error_message text not null default '',
    last_fetch_account_id bigint not null default 0,
    refresh_start_time timestamp not null default '1970-01-01 00:00:00',
    create_time timestamp not null default now(),
    update_time timestamp not null default now()
);

comment on table public.c5_market_price_snapshot is 'C5市场价格最新快照表';
comment on column public.c5_market_price_snapshot.id is '主键ID';
comment on column public.c5_market_price_snapshot.app_id is 'Steam应用ID，CS2固定730';
comment on column public.c5_market_price_snapshot.market_hash_name is 'Steam市场Hash名称';
comment on column public.c5_market_price_snapshot.range_type is '区间类型，ALL不筛磨损，WEAR指定磨损区间';
comment on column public.c5_market_price_snapshot.wear_min is '归一化查询最小磨损，ALL固定0';
comment on column public.c5_market_price_snapshot.wear_max is '归一化查询最大磨损，ALL固定1';
comment on column public.c5_market_price_snapshot.page_num is 'C5查询页码，首期固定1';
comment on column public.c5_market_price_snapshot.page_size is 'C5查询页大小，首期固定50';
comment on column public.c5_market_price_snapshot.lowest_price is '当前快照最低价';
comment on column public.c5_market_price_snapshot.avg_price is '当前快照样本算术平均价';
comment on column public.c5_market_price_snapshot.sample_count is '当前快照样本数量';
comment on column public.c5_market_price_snapshot.has_more is 'C5是否还有更多数据';
comment on column public.c5_market_price_snapshot.listings_json is '精简挂单列表JSON，最多50条';
comment on column public.c5_market_price_snapshot.refresh_enabled is '是否启用刷新';
comment on column public.c5_market_price_snapshot.refresh_priority is '临时刷新优先级，数字越大越优先';
comment on column public.c5_market_price_snapshot.refresh_interval_seconds is '目标刷新间隔秒数，默认300秒';
comment on column public.c5_market_price_snapshot.next_refresh_time is '下次应刷新时间';
comment on column public.c5_market_price_snapshot.last_fetch_time is '最近一次尝试刷新时间';
comment on column public.c5_market_price_snapshot.last_success_time is '最近一次成功刷新时间';
comment on column public.c5_market_price_snapshot.last_request_time is '最近一次被前端或业务关注的时间';
comment on column public.c5_market_price_snapshot.status is '刷新状态';
comment on column public.c5_market_price_snapshot.fetch_count is '累计刷新次数';
comment on column public.c5_market_price_snapshot.fail_count is '连续失败次数';
comment on column public.c5_market_price_snapshot.last_error_message is '最近失败原因摘要';
comment on column public.c5_market_price_snapshot.last_fetch_account_id is '最近执行刷新所使用的系统C5市场查询账号ID，首期无账号表时为0';
comment on column public.c5_market_price_snapshot.refresh_start_time is '最近一次开始刷新时间，用于识别卡住的刷新任务';
comment on column public.c5_market_price_snapshot.create_time is '创建时间';
comment on column public.c5_market_price_snapshot.update_time is '更新时间';

create unique index if not exists uk_c5_market_price_snapshot_query
    on public.c5_market_price_snapshot (app_id, market_hash_name, range_type, wear_min, wear_max);

create index if not exists idx_c5_market_price_snapshot_refresh
    on public.c5_market_price_snapshot (refresh_enabled, next_refresh_time, refresh_priority desc, last_request_time desc, id);

create index if not exists idx_c5_market_price_snapshot_hash_name
    on public.c5_market_price_snapshot (app_id, market_hash_name);

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_c5_market_price_snapshot_range_type'
          and conrelid = 'public.c5_market_price_snapshot'::regclass
    ) then
        alter table public.c5_market_price_snapshot
            add constraint chk_c5_market_price_snapshot_range_type check (range_type in ('ALL', 'WEAR'));
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_c5_market_price_snapshot_status'
          and conrelid = 'public.c5_market_price_snapshot'::regclass
    ) then
        alter table public.c5_market_price_snapshot
            add constraint chk_c5_market_price_snapshot_status check (status in ('PENDING', 'REFRESHING', 'SUCCESS', 'FAILED', 'DISABLED'));
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_c5_market_price_snapshot_wear'
          and conrelid = 'public.c5_market_price_snapshot'::regclass
    ) then
        alter table public.c5_market_price_snapshot
            add constraint chk_c5_market_price_snapshot_wear check (wear_min >= 0 and wear_max <= 1 and wear_min <= wear_max);
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_c5_market_price_snapshot_all_range'
          and conrelid = 'public.c5_market_price_snapshot'::regclass
    ) then
        alter table public.c5_market_price_snapshot
            add constraint chk_c5_market_price_snapshot_all_range check (
                (range_type = 'ALL' and wear_min = 0 and wear_max = 1)
                or range_type = 'WEAR'
            );
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_c5_market_price_snapshot_page'
          and conrelid = 'public.c5_market_price_snapshot'::regclass
    ) then
        alter table public.c5_market_price_snapshot
            add constraint chk_c5_market_price_snapshot_page check (page_num > 0 and page_size > 0);
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_c5_market_price_snapshot_price'
          and conrelid = 'public.c5_market_price_snapshot'::regclass
    ) then
        alter table public.c5_market_price_snapshot
            add constraint chk_c5_market_price_snapshot_price check (lowest_price >= 0 and avg_price >= 0);
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_c5_market_price_snapshot_refresh_interval'
          and conrelid = 'public.c5_market_price_snapshot'::regclass
    ) then
        alter table public.c5_market_price_snapshot
            add constraint chk_c5_market_price_snapshot_refresh_interval check (refresh_interval_seconds > 0);
    end if;
end $$;

-- 复核 SQL：
-- select count(*) from public.c5_market_price_snapshot;
-- select app_id, market_hash_name, range_type, wear_min, wear_max, status from public.c5_market_price_snapshot limit 10;

commit;
