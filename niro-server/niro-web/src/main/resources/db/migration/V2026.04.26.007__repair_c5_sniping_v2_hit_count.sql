/*
 * 变更日期：2026-04-26
 * 目标：修复 C5 扫货 2.0 命中记录已落库但任务/运行实例 hit_count 未及时累加的历史数据。
 * 幂等策略：每次按 c5_sniping_hit_record_v2 聚合结果直接覆盖 task/run 的 hit_count，重复执行结果一致。
 * 回滚思路：本脚本只按明细表重算冗余计数字段；如需回滚，可重新按命中明细聚合覆盖，或由业务重新刷新统计。
 */

begin;

with task_hit_stats as (
    select
        task_id,
        count(*)::integer as hit_count
    from public.c5_sniping_hit_record_v2
    where task_id > 0
    group by task_id
)
update public.c5_sniping_task_v2 task
set
    hit_count = stats.hit_count,
    version = task.version + 1,
    update_time = now()
from task_hit_stats stats
where task.id = stats.task_id
  and task.hit_count is distinct from stats.hit_count;

update public.c5_sniping_task_v2 task
set
    hit_count = 0,
    version = task.version + 1,
    update_time = now()
where task.hit_count <> 0
  and not exists (
      select 1
      from public.c5_sniping_hit_record_v2 hit
      where hit.task_id = task.id
  );

with run_hit_stats as (
    select
        run_id,
        count(*)::integer as hit_count
    from public.c5_sniping_hit_record_v2
    where run_id > 0
    group by run_id
)
update public.c5_sniping_task_run_v2 run
set
    hit_count = stats.hit_count,
    update_time = now()
from run_hit_stats stats
where run.id = stats.run_id
  and run.hit_count is distinct from stats.hit_count;

update public.c5_sniping_task_run_v2 run
set
    hit_count = 0,
    update_time = now()
where run.hit_count <> 0
  and not exists (
      select 1
      from public.c5_sniping_hit_record_v2 hit
      where hit.run_id = run.id
  );

commit;

select
    task_id,
    count(*)::integer as actual_hit_count
from public.c5_sniping_hit_record_v2
where task_id > 0
group by task_id
order by task_id;
