package com.niro.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niro.web.entity.TradeOrderRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 交易订单记录 Mapper 接口
 *
 * @author niro
 * @since 2026-01-22
 */
@Mapper
public interface TradeOrderRecordMapper extends BaseMapper<TradeOrderRecord> {

    @Select("""
            <script>
            SELECT task_id AS taskId, COUNT(*) AS successCount
            FROM trade_order_record
            WHERE is_deleted = 0
              AND status IN
              <foreach item='status' collection='statuses' open='(' separator=',' close=')'>
                #{status}
              </foreach>
              AND task_id IN
              <foreach item='taskId' collection='taskIds' open='(' separator=',' close=')'>
                #{taskId}
              </foreach>
            GROUP BY task_id
            </script>
            """)
    List<Map<String, Object>> countSuccessByTaskIds(@Param("taskIds") List<Long> taskIds,
                                                    @Param("statuses") List<Integer> statuses);
}
