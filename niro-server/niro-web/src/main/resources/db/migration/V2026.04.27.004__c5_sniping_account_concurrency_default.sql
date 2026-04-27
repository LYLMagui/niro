-- ============================================================
-- 调整 C5 扫货账号并发默认值（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-27
-- 目标:
-- 1. 将 c5_sniping_account_runtime_v2.concurrency_limit 的数据库默认值从 1 调整为 5
-- 幂等策略:
-- - alter column set default 可重复执行
-- 回滚思路:
-- - 如需回滚，可新增 migration 将默认值改回 1
-- - 本脚本不改写既有记录，避免误覆盖显式配置为 1 的账号
-- ============================================================

begin;

alter table public.c5_sniping_account_runtime_v2
    alter column concurrency_limit set default 5;


commit;
