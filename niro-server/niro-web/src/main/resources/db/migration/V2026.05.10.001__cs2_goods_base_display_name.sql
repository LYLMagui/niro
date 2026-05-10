/*
 * 变更日期：2026-05-10
 * 目标：为 CS2 商品保存不带外观的展示名称，用于商品选择列表展示。
 * 幂等策略：add column if not exists；历史数据按 display_name 去除外观后缀回填，重复执行结果一致。
 * 回滚思路：如需回滚，可在确认无业务依赖后 drop column public.cs2_goods.base_display_name。
 */
begin;

alter table if exists public.cs2_goods
    add column if not exists base_display_name varchar(255) not null default '';

comment on column public.cs2_goods.base_display_name is '不带外观的展示名称';

update public.cs2_goods
set base_display_name = trim(regexp_replace(
        display_name,
        '[[:space:]]*[（(](崭新出厂|略有磨损|久经沙场|破损不堪|战痕累累|Factory New|Minimal Wear|Field-Tested|Well-Worn|Battle-Scarred)[）)]$',
        ''
    )),
    updated_at = now()
where base_display_name = ''
  and display_name <> '';

commit;
