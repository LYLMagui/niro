package com.niro.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niro.web.entity.TradeOrderRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 交易订单记录 Mapper 接口
 *
 * @author niro
 * @since 2026-01-22
 */
@Mapper
public interface TradeOrderRecordMapper extends BaseMapper<TradeOrderRecord> {
}
