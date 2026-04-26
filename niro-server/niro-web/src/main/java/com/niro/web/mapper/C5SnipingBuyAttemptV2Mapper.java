package com.niro.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niro.web.entity.C5SnipingBuyAttemptV2;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface C5SnipingBuyAttemptV2Mapper extends BaseMapper<C5SnipingBuyAttemptV2> {

    /**
     * 统计账号当前未完成外部下单的在途金额。
     *
     * @param accountId 账号 ID
     * @return 在途金额
     */
    @Select("select coalesce(sum(in_flight_amount), 0) from c5_sniping_buy_attempt_v2 where account_id = #{accountId} and attempt_status = 'INIT'")
    BigDecimal sumInFlightAmount(@Param("accountId") Long accountId);

    /**
     * 统计账号当前未完成外部下单的在途尝试数。
     *
     * @param accountId 账号 ID
     * @return 在途下单尝试数
     */
    @Select("select count(1) from c5_sniping_buy_attempt_v2 where account_id = #{accountId} and attempt_status = 'INIT'")
    int countInFlightAttempts(@Param("accountId") Long accountId);
}
