-- ============================================================
-- C5 扫货 2.0 下单尝试回写与预占归属字段补齐（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-24
-- 目标:
-- 1. 给 c5_sniping_buy_attempt_v2 补齐 out_trade_no，用于按系统内部请求流水号关联 C5 下单响应与订单同步
-- 2. 给 c5_sniping_buy_attempt_v2 补齐 slot_reserved、slot_released，用于 attempt 级购买名额预占归属与恢复
-- 3. 补齐 out_trade_no 非空值唯一索引和预占恢复扫描索引
-- 幂等策略:
-- - 新增字段使用 alter table ... add column if not exists，重复执行不会重复添加字段
-- - 索引使用 create index if not exists / create unique index if not exists，重复执行不会重复创建同名索引
-- - out_trade_no 唯一索引使用部分索引 where out_trade_no <> ''，避免历史默认空串冲突
-- 回滚思路:
-- - 如需回滚，可在后续 migration 中 drop index if exists 删除新增索引，并按兼容窗口评估是否 drop column
-- - 破坏性删除字段不在本 migration 中执行
-- ============================================================

begin;

alter table public.c5_sniping_buy_attempt_v2
    add column if not exists out_trade_no varchar(64) not null default '',
    add column if not exists slot_reserved boolean not null default false,
    add column if not exists slot_released boolean not null default false;

comment on column public.c5_sniping_buy_attempt_v2.out_trade_no is '系统内部请求流水号，用于关联C5下单响应与订单同步';
comment on column public.c5_sniping_buy_attempt_v2.slot_reserved is '是否已为本次尝试预占购买名额';
comment on column public.c5_sniping_buy_attempt_v2.slot_released is '预占名额是否已释放或结算';

create unique index if not exists uk_c5_sniping_buy_attempt_v2_out_trade_no
    on public.c5_sniping_buy_attempt_v2 (out_trade_no)
    where out_trade_no <> '';

create index if not exists idx_c5_sniping_buy_attempt_v2_slot_recovery
    on public.c5_sniping_buy_attempt_v2 (task_id, attempt_status, slot_reserved, slot_released);

commit;
