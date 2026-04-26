-- ============================================================
-- C5 扫货 2.0 运行实例状态索引补齐（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-24
-- 目标:
-- 1. 补齐设计文档要求的 c5_sniping_task_run_v2(task_id, run_status) 普通索引
-- 2. 支持按任务与运行状态查询运行实例，避免仅依赖 task_id + started_at 索引或 RUNNING 部分唯一索引
-- 幂等策略:
-- - 使用 create index if not exists，重复执行不会重复创建同名索引
-- - 不修改历史 migration，不引入外键，不改表结构与既有数据
-- 回滚思路:
-- - 如需回滚，可在后续 migration 中 drop index if exists public.idx_c5_sniping_task_run_v2_task_status
-- ============================================================

begin;

create index if not exists idx_c5_sniping_task_run_v2_task_status
    on public.c5_sniping_task_run_v2 (task_id, run_status);

commit;
