-- ============================================================
-- C5 库存上架状态扩展
-- 日期: 2026-04-28
-- 目标:
-- 1. 为 c5_inventory_item.inventory_status 增加 LISTING 状态
-- 2. 允许上架成功后的库存快照从 IN_STOCK 流转为 LISTING
-- 幂等策略:
-- - 先删除同名 check constraint，再按新状态集合重建
-- - comment on column 可重复执行
-- 回滚思路:
-- - 如需回滚，先将 LISTING 数据回写为 IN_STOCK 或 REMOVED，再新增 migration 恢复旧约束
-- ============================================================

begin;

alter table public.c5_inventory_item
    drop constraint if exists chk_c5_inventory_item_inventory_status;

alter table public.c5_inventory_item
    add constraint chk_c5_inventory_item_inventory_status
        check (inventory_status in ('IN_STOCK', 'REMOVED', 'LISTING'));

comment on column public.c5_inventory_item.inventory_status is '库存状态，IN_STOCK在库，REMOVED已移除，LISTING已提交上架';

-- 复核 SQL：
-- select inventory_status, count(*) from public.c5_inventory_item group by inventory_status;

commit;
