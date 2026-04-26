-- ============================================================
-- C5 扫货账号补充账号级凭据字段（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-26
-- 目标:
-- 1. 为 c5_sniping_account 补充账号级 C5 AppKey 与 Steam 交易链接
-- 2. 从 user_platform_setting 回填历史用户级配置，支撑 C5 扫货 2.0 改为账号级配置
-- 幂等策略:
-- - 使用 add column if not exists，重复执行不报错
-- - 字段注释重复执行结果一致
-- - 仅在账号字段为空时回填历史用户级配置，避免覆盖用户已维护的账号级配置
-- 回滚思路:
-- - 如需回滚，可新增 migration 软弃用这两个字段并让业务临时回读 user_platform_setting
-- ============================================================

begin;

alter table public.c5_sniping_account
    add column if not exists c5_app_key varchar(256) not null default '',
    add column if not exists steam_trade_url varchar(1024) not null default '';

comment on column public.c5_sniping_account.c5_app_key is '账号级C5 AppKey';
comment on column public.c5_sniping_account.steam_trade_url is '账号级Steam交易链接';

update public.c5_sniping_account ca
set c5_app_key = coalesce(ups.c5_app_key, ''),
    steam_trade_url = coalesce(ups.steam_trade_url, ''),
    status = case
        when coalesce(ups.c5_app_key, '') = '' or coalesce(ups.steam_trade_url, '') = '' then 'INVALID'
        else ca.status
    end,
    warning_msg = case
        when coalesce(ups.c5_app_key, '') = '' or coalesce(ups.steam_trade_url, '') = '' then '账号 C5 配置不完整'
        else ca.warning_msg
    end,
    update_time = now()
from public.user_platform_setting ups
where ups.user_id = ca.user_id
  and ca.del_flag = 0
  and ca.c5_app_key = ''
  and ca.steam_trade_url = '';

update public.c5_sniping_account ca
set status = 'INVALID',
    warning_msg = '账号 C5 配置不完整',
    update_time = now()
where ca.del_flag = 0
  and (ca.c5_app_key = '' or ca.steam_trade_url = '')
  and (ca.status <> 'INVALID' or ca.warning_msg <> '账号 C5 配置不完整');

commit;

-- 复核 SQL：
-- select count(*) from public.c5_sniping_account where c5_app_key = '' or steam_trade_url = '';
