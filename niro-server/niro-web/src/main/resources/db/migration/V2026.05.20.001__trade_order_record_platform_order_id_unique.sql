-- ==========================================
-- 变更日期: 2026-05-20
-- 目标: 给 trade_order_record (platform, order_id) 增加部分唯一索引，
--       防止订单上报并发竞态导致重复入库。
-- 幂等策略: CREATE UNIQUE INDEX IF NOT EXISTS
-- 回滚思路: DROP INDEX IF EXISTS uk_trade_order_record_platform_order_id;
-- 注意: 若现有数据存在 (platform, order_id) 重复行，本 migration 会失败。
--       请先执行下方诊断查询定位重复，再按业务规则保留一条软删其余。
--       诊断 SQL（仅参考，不在本 migration 内执行）:
--       SELECT platform, order_id, COUNT(*) FROM trade_order_record
--         WHERE order_id <> '' AND order_id IS NOT NULL AND is_deleted = 0
--         GROUP BY platform, order_id HAVING COUNT(*) > 1;
-- ==========================================

CREATE UNIQUE INDEX IF NOT EXISTS uk_trade_order_record_platform_order_id
    ON trade_order_record (platform, order_id)
    WHERE order_id <> '' AND order_id IS NOT NULL AND is_deleted = 0;
