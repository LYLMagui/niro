-- ============================================================
-- 调整 C5 扫货任务同商品运行约束（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-27
-- 目标:
-- 1. 允许同账号同商品存在多个 READY / DRAFT / 历史任务
-- 2. 仅限制同账号同商品同时最多一个 RUNNING 任务
-- 幂等策略:
-- - drop index if exists 可重复执行
-- - create unique index if not exists 防止重复创建
-- 回滚思路:
-- - 如需回滚，可新增 migration 删除 RUNNING 唯一索引，并恢复 READY/RUNNING 唯一索引
-- - 回滚前需先清理同账号同商品的多个 READY 任务，否则恢复旧索引会失败
-- ============================================================

begin;

drop index if exists public.uk_c5_sniping_task_v2_enabled_account_goods;

create unique index if not exists uk_c5_sniping_task_v2_running_account_goods
    on public.c5_sniping_task_v2 (account_id, cs2_goods_id)
    where del_flag = 0 and task_status = 'RUNNING';

commit;
