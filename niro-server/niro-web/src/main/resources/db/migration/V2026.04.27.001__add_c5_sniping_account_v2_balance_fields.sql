-- ============================================================
-- C5 扫货账号余额 V2 字段（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-27
-- 目标:
-- 1. 适配 C5 /merchant/account/v2/balance 响应字段
-- 2. 为 c5_sniping_account 补齐保证金余额、秒到账余额与秒到账保证金
-- 3. 继续复用 balance 作为 C5 可用余额，pending_balance 作为交易待结算余额
-- 幂等策略:
-- - 新字段使用 add column if not exists
-- - 新约束通过 pg_constraint 判重后追加
-- 回滚思路:
-- - 如需回滚，可新增 migration 软弃用新增字段，业务回读 balance / pending_balance
-- ============================================================

begin;

alter table public.c5_sniping_account
    add column if not exists deposit_amount numeric(12, 2) not null default 0,
    add column if not exists credit_money numeric(12, 2) not null default 0,
    add column if not exists credit_deposit numeric(12, 2) not null default 0;

comment on column public.c5_sniping_account.balance is 'C5可用余额，对应余额V2 moneyAmount';
comment on column public.c5_sniping_account.pending_balance is '交易待结算余额，对应余额V2 tradeSettleAmount';
comment on column public.c5_sniping_account.deposit_amount is '保证金余额，对应余额V2 depositAmount';
comment on column public.c5_sniping_account.credit_money is '秒到账余额，对应余额V2 creditMoney';
comment on column public.c5_sniping_account.credit_deposit is '秒到账保证金，对应余额V2 creditDeposit';

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_c5_sniping_account_deposit_amount'
          and conrelid = 'public.c5_sniping_account'::regclass
    ) then
        alter table public.c5_sniping_account
            add constraint chk_c5_sniping_account_deposit_amount check (deposit_amount >= 0);
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_c5_sniping_account_credit_money'
          and conrelid = 'public.c5_sniping_account'::regclass
    ) then
        alter table public.c5_sniping_account
            add constraint chk_c5_sniping_account_credit_money check (credit_money >= 0);
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_c5_sniping_account_credit_deposit'
          and conrelid = 'public.c5_sniping_account'::regclass
    ) then
        alter table public.c5_sniping_account
            add constraint chk_c5_sniping_account_credit_deposit check (credit_deposit >= 0);
    end if;
end $$;

-- 复核 SQL：
-- select balance, pending_balance, deposit_amount, credit_money, credit_deposit from public.c5_sniping_account limit 10;

commit;
