/*
 * 变更日期：2026-05-09
 * 目标：为 C5 扫货 2.0 命中记录保存下单失败原因，供命中明细返回和排查。
 * 幂等策略：add column if not exists；字段默认空串，重复执行不改变已有数据。
 * 回滚思路：如需回滚，可在确认无业务依赖后 drop column public.c5_sniping_hit_record_v2.buy_failure_reason。
 */
begin;

alter table if exists public.c5_sniping_hit_record_v2
    add column if not exists buy_failure_reason text not null default '';

comment on column public.c5_sniping_hit_record_v2.buy_failure_reason is '下单失败原因';

commit;
