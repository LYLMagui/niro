-- ============================================================
-- cs2_goods 商品选择链路重构迁移脚本
-- 日期: 2026-04-20
-- 范围: unbox_record / buff_scan_task
-- 说明:
-- 1. 本脚本只做字段语义切换与索引/注释修正
-- 2. 请在新版服务启动前执行
-- 3. 幂等：rename column 通过 information_schema 检查
-- ============================================================

begin;

do $$
begin
    if exists (
        select 1
        from information_schema.columns
        where table_schema = 'public'
          and table_name = 'unbox_record'
          and column_name = 'goods_id'
    ) then
        execute 'alter table public.unbox_record rename column goods_id to box_goods_id';
    end if;
end
$$;

alter index if exists public.idx_unbox_record_goods_id
  rename to idx_unbox_record_box_goods_id;

comment on column public.unbox_record.box_goods_id is '箱子商品id，对应cs2_goods表id';

do $$
begin
    if exists (
        select 1
        from information_schema.columns
        where table_schema = 'public'
          and table_name = 'buff_scan_task'
          and column_name = 'goods_id'
    ) then
        execute 'alter table public.buff_scan_task rename column goods_id to cs2_goods_id';
    end if;
end
$$;

comment on column public.buff_scan_task.cs2_goods_id is '任务目标商品id，对应cs2_goods表id';

create index if not exists idx_buff_scan_task_cs2_goods_id
  on public.buff_scan_task (cs2_goods_id);

commit;
