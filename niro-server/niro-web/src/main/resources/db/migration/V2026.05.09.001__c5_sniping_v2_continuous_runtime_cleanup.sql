-- ============================================================
-- C5 扫货 2.0 持续运行模型数据库清理（PostgreSQL · 追加式 migration）
-- 日期: 2026-05-09
-- 目标:
-- 1. 清理 c5_sniping_task_run_v2 中 run_status = 'ERROR' 的历史异常运行实例
-- 2. 将历史 READY 任务迁移为 STOPPED，避免上线后旧任务自动进入扫描
-- 3. 移除持续运行模型不再使用的 run 实例依赖字段、索引和运行实例表
-- 4. 为命中明细和下单尝试补齐去 run_id 后的任务维度查询索引
-- 幂等策略:
-- - 数据清理和 READY 状态迁移使用 do $$ 块判断表与字段存在后再执行
-- - 索引和表使用 drop if exists / create index if not exists
-- - 字段删除使用 alter table if exists ... drop column if exists
-- 回滚思路:
-- - 如需回滚，可通过后续 migration 重建 c5_sniping_task_run_v2 表与 run_id / latest_run_id / next_scan_at / lease 字段
-- - READY -> STOPPED 属于上线保护性状态迁移，回滚时需由业务按任务启用入口重新启用任务，不建议批量恢复 READY
-- ============================================================

begin;

do $$
declare
    v_table regclass;
begin
    v_table := to_regclass('public.c5_sniping_task_run_v2');

    if v_table is not null
       and exists (
           select 1
           from pg_attribute
           where attrelid = v_table
             and attname = 'run_status'
             and not attisdropped
       ) then
        execute 'delete from public.c5_sniping_task_run_v2 where run_status = ''ERROR''';
    end if;
end $$;

do $$
declare
    v_table regclass;
    v_has_update_time boolean;
begin
    v_table := to_regclass('public.c5_sniping_task_v2');

    if v_table is not null
       and exists (
           select 1
           from pg_attribute
           where attrelid = v_table
             and attname = 'task_status'
             and not attisdropped
       ) then
        select exists (
            select 1
            from pg_attribute
            where attrelid = v_table
              and attname = 'update_time'
              and not attisdropped
        ) into v_has_update_time;

        if v_has_update_time then
            execute 'update public.c5_sniping_task_v2 set task_status = ''STOPPED'', update_time = now() where task_status = ''READY''';
        else
            execute 'update public.c5_sniping_task_v2 set task_status = ''STOPPED'' where task_status = ''READY''';
        end if;
    end if;
end $$;

-- 先删除依赖废弃字段或废弃运行实例表的索引。
drop index if exists public.idx_c5_sniping_task_v2_latest_run_id;
drop index if exists public.idx_c5_sniping_task_v2_ready_schedule;
drop index if exists public.idx_c5_sniping_task_v2_ready_lease_schedule;
drop index if exists public.idx_c5_sniping_hit_record_v2_task_run;
drop index if exists public.idx_c5_sniping_buy_attempt_v2_task_run;
drop index if exists public.idx_c5_sniping_task_run_v2_task_id;
drop index if exists public.idx_c5_sniping_task_run_v2_task_status;
drop index if exists public.uk_c5_sniping_task_run_v2_running_task;

-- 移除任务表中仅服务于 run 实例、单轮调度或跨实例 lease 的废弃字段。
alter table if exists public.c5_sniping_task_v2
    drop column if exists latest_run_id,
    drop column if exists next_scan_at,
    drop column if exists lease_owner,
    drop column if exists lease_until;

-- 收窄任务状态约束，持续运行模型不再使用 READY。
do $$
begin
    if to_regclass('public.c5_sniping_task_v2') is not null then
        alter table public.c5_sniping_task_v2 drop constraint if exists chk_c5_sniping_task_v2_status;
        alter table public.c5_sniping_task_v2
            add constraint chk_c5_sniping_task_v2_status
            check (task_status in ('DRAFT', 'RUNNING', 'STOPPED', 'COMPLETED', 'ERROR'));
    end if;
end $$;

-- 命中明细和下单尝试不再挂靠运行实例。
alter table if exists public.c5_sniping_hit_record_v2
    drop column if exists run_id;

alter table if exists public.c5_sniping_buy_attempt_v2
    drop column if exists run_id;

-- 去除 run_id 后保留任务维度明细查询能力。
do $$
declare
    v_table regclass;
begin
    v_table := to_regclass('public.c5_sniping_hit_record_v2');

    if v_table is not null
       and exists (
           select 1
           from pg_attribute
           where attrelid = v_table
             and attname = 'task_id'
             and not attisdropped
       )
       and exists (
           select 1
           from pg_attribute
           where attrelid = v_table
             and attname = 'hit_at'
             and not attisdropped
       ) then
        execute 'create index if not exists idx_c5_sniping_hit_record_v2_task_hit_at on public.c5_sniping_hit_record_v2 (task_id, hit_at desc)';
    end if;
end $$;

do $$
declare
    v_table regclass;
begin
    v_table := to_regclass('public.c5_sniping_buy_attempt_v2');

    if v_table is not null
       and exists (
           select 1
           from pg_attribute
           where attrelid = v_table
             and attname = 'task_id'
             and not attisdropped
       )
       and exists (
           select 1
           from pg_attribute
           where attrelid = v_table
             and attname = 'created_at'
             and not attisdropped
       ) then
        execute 'create index if not exists idx_c5_sniping_buy_attempt_v2_task_created_at on public.c5_sniping_buy_attempt_v2 (task_id, created_at desc)';
    end if;
end $$;

-- 持续运行模型下不再保留运行实例表。
drop table if exists public.c5_sniping_task_run_v2;

-- 复核 SQL（由执行人按需手动运行）:
-- select count(*) from public.c5_sniping_task_v2 where task_status = 'READY';
-- select to_regclass('public.c5_sniping_task_run_v2');

commit;
