-- ============================================================
-- 删除 buff_scan_task 深度锚点配置列
-- 日期: 2026-04-21
-- 目标:
-- 1. 移除 buff_scan_task 的 safety_margin、ladder_step、extra_config 三列
-- 2. 配合后端下线 C5 扫货的深度锚点算法（由 task.max_price 作为扫货过滤上限）
--
-- 幂等说明:
-- - 三列均使用 drop column if exists 保护，重复执行安全
--
-- 回滚思路:
-- - 如需回滚，另开新 migration 重新 add column 并回填默认值
-- - 不直接修改或回滚本 migration
-- ============================================================

begin;

alter table public.buff_scan_task drop column if exists safety_margin;
alter table public.buff_scan_task drop column if exists ladder_step;
alter table public.buff_scan_task drop column if exists extra_config;

commit;

-- ============================================================
-- 执行后复核（可选）
-- ============================================================
-- select column_name
-- from information_schema.columns
-- where table_schema = 'public'
--   and table_name = 'buff_scan_task'
--   and column_name in ('safety_margin', 'ladder_step', 'extra_config');
