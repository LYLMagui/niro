/*
 * 变更日期: 2026-05-11
 * 目标: 为开箱记录明细保存 CS2 商品 ID，支持编辑时按已选商品回显名称。
 * 幂等策略: 字段和索引用 if not exists 创建，历史数据按 weapon_name 尽力回填。
 * 回滚思路: 如需回滚，可新增 migration 删除 idx_unbox_record_item_cs2_goods_id 和 cs2_goods_id 字段。
 */
begin;

alter table public.unbox_record_item
    add column if not exists cs2_goods_id bigint not null default 0;

comment on column public.unbox_record_item.cs2_goods_id is '饰品商品id，对应cs2_goods表id，0表示未匹配';

update public.unbox_record_item item
set cs2_goods_id = goods.id
from public.cs2_goods goods
where item.cs2_goods_id = 0
  and item.weapon_name <> ''
  and goods.display_name = item.weapon_name
  and goods.item_type in ('knife', 'weapon_skin', 'glove')
  and goods.enabled = true;

create index if not exists idx_unbox_record_item_cs2_goods_id
    on public.unbox_record_item (cs2_goods_id);

commit;
