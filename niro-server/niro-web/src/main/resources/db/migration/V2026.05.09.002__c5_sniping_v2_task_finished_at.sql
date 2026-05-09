-- ============================================================
-- C5 扫货 2.0 任务完成时间字段
-- 日期: 2026-05-09
-- 目标:
-- 1. 给 c5_sniping_task_v2 增加任务级完成时间字段 finished_at
-- 2. 支持前端任务列表展示创建时间和完成时间
-- 幂等策略:
-- - 新增字段使用 add column if not exists，重复执行不会重复添加字段
-- - 字段注释可重复执行并覆盖为当前语义
-- 回滚思路:
-- - 如需回滚，可通过后续 migration 删除 finished_at 字段；字段仅用于展示，不影响任务运行主链路
-- ============================================================

begin;

alter table if exists public.c5_sniping_task_v2
    add column if not exists finished_at timestamp;

comment on column public.c5_sniping_task_v2.finished_at is '任务完成时间';

-- 复核 SQL（由执行人按需手动运行）:
-- select column_name, data_type from information_schema.columns where table_schema = 'public' and table_name = 'c5_sniping_task_v2' and column_name = 'finished_at';

commit;
