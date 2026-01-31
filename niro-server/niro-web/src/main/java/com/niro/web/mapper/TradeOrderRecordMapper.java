package com.niro.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niro.web.entity.TradeOrderRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 交易订单记录 Mapper 接口
 *
 * @author niro
 * @since 2026-01-22
 */
@Mapper
public interface TradeOrderRecordMapper extends BaseMapper<TradeOrderRecord> {

    @Select("select count(*) from trade_order_record where task_id = #{taskId} and status = 1")
    Long countSuccess(@Param("taskId") Long taskId);
}
