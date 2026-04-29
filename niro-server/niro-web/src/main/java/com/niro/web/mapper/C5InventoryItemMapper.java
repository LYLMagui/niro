package com.niro.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niro.web.dto.C5InventoryAggregateQueryDTO;
import com.niro.web.entity.C5InventoryItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * C5 库存快照 Mapper。
 */
@Mapper
public interface C5InventoryItemMapper extends BaseMapper<C5InventoryItem> {

    @Select("""
            <script>
            select count(1)
            from (
                select 1
                from c5_inventory_item
                where user_id = #{query.userId}
                  and inventory_status in ('IN_STOCK', 'LISTING')
                  <if test='query.accountId != null'>
                    and account_id = #{query.accountId}
                  </if>
                  <if test='query.keyword != null and query.keyword != ""'>
                    and (name like concat('%', #{query.keyword}, '%') or market_hash_name like concat('%', #{query.keyword}, '%'))
                  </if>
                  <if test='query.status == "tradable"'>
                    and inventory_status = 'IN_STOCK' and if_tradable = true
                  </if>
                  <if test='query.status == "cooldown"'>
                    and inventory_status = 'IN_STOCK' and if_tradable = false and tradable_time is not null and tradable_time != ''
                  </if>
                  <if test='query.status == "selling"'>
                    and inventory_status = 'LISTING'
                  </if>
                group by account_id, coalesce(market_hash_name, name), coalesce(exterior_name, ''), if_tradable
            ) grouped_inventory
            </script>
            """)
    long countAggregated(@Param("query") C5InventoryAggregateQueryDTO query);

    @Select("""
            <script>
            select *
            from (
                select distinct on (account_id, coalesce(market_hash_name, name), coalesce(exterior_name, ''), if_tradable)
                    *,
                    count(1) over (partition by account_id, coalesce(market_hash_name, name), coalesce(exterior_name, ''), if_tradable) as quantity
                from c5_inventory_item
                where user_id = #{query.userId}
                  and inventory_status in ('IN_STOCK', 'LISTING')
                  <if test='query.accountId != null'>
                    and account_id = #{query.accountId}
                  </if>
                  <if test='query.keyword != null and query.keyword != ""'>
                    and (name like concat('%', #{query.keyword}, '%') or market_hash_name like concat('%', #{query.keyword}, '%'))
                  </if>
                  <if test='query.status == "tradable"'>
                    and inventory_status = 'IN_STOCK' and if_tradable = true
                  </if>
                  <if test='query.status == "cooldown"'>
                    and inventory_status = 'IN_STOCK' and if_tradable = false and tradable_time is not null and tradable_time != ''
                  </if>
                  <if test='query.status == "selling"'>
                    and inventory_status = 'LISTING'
                  </if>
                order by account_id, coalesce(market_hash_name, name), coalesce(exterior_name, ''), if_tradable, last_sync_time desc, update_time desc
            ) aggregated_inventory
            order by last_sync_time desc, update_time desc
            limit #{query.pageSize} offset #{query.offset}
            </script>
            """)
    List<C5InventoryItem> listAggregatedPage(@Param("query") C5InventoryAggregateQueryDTO query);

    @Select("""
            <script>
            select coalesce(sum(coalesce(price, 0)), 0)
            from c5_inventory_item
            where user_id = #{query.userId}
              and inventory_status in ('IN_STOCK', 'LISTING')
              <if test='query.accountId != null'>
                and account_id = #{query.accountId}
              </if>
              <if test='query.keyword != null and query.keyword != ""'>
                and (name like concat('%', #{query.keyword}, '%') or market_hash_name like concat('%', #{query.keyword}, '%'))
              </if>
            </script>
            """)
    BigDecimal sumActiveInventoryValue(@Param("query") C5InventoryAggregateQueryDTO query);
}
