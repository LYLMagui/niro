-- ============================================================
-- cs2_goods 商品选择链路重构迁移脚本
-- 日期: 2026-04-20
-- 范围: unbox_record / buff_scan_task
-- 说明:
-- 1. 本脚本只做字段语义切换与索引/注释修正
-- 2. 请在新版服务启动前执行
-- 3. 若目标库已完成部分迁移，请按实际库状态裁剪执行
-- ============================================================

begin;

alter table public.unbox_record
  rename column goods_id to box_goods_id;

alter index if exists public.idx_unbox_record_goods_id
  rename to idx_unbox_record_box_goods_id;

comment on column public.unbox_record.box_goods_id is '箱子商品id，对应cs2_goods表id';

alter table public.buff_scan_task
  rename column goods_id to cs2_goods_id;

comment on column public.buff_scan_task.cs2_goods_id is '任务目标商品id，对应cs2_goods表id';

create index if not exists idx_buff_scan_task_cs2_goods_id
  on public.buff_scan_task (cs2_goods_id);

commit;
