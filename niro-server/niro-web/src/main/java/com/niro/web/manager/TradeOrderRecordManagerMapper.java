package com.niro.web.manager;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.core.constant.GlobalConstant;
import com.niro.web.entity.TradeOrderRecord;
import com.niro.web.enums.OrderStatusEnum;
import com.niro.web.enums.PlatformEnum;
import com.niro.web.mapper.TradeOrderRecordMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 *
 *
 * @author liyl
 * @date 2026/2/4
 */
@Service
public class TradeOrderRecordManagerMapper extends ServiceImpl<TradeOrderRecordMapper, TradeOrderRecord> {

    /**
     * 查询活跃的C5订单 (状态为 SUCCESS，且在指定时间之后创建)
     * 遵循 MyBatis-Plus Lambda Query 规范，通过 PlatformEnum 和 OrderStatusEnum 消除魔法值
     */
    public List<TradeOrderRecord> selectActiveC5Orders(LocalDateTime since) {
        return this.lambdaQuery()
                .eq(TradeOrderRecord::getPlatform, PlatformEnum.C5.getCode())
                .eq(TradeOrderRecord::getStatus, OrderStatusEnum.SUCCESS.getCode())
                .ge(TradeOrderRecord::getCreateTime, since)
                .list();
    }

    public Long countSuccess(Long taskId) {
        return this.lambdaQuery().eq(TradeOrderRecord::getTaskId, taskId)
                .eq(TradeOrderRecord::getStatus, GlobalConstant.YES)
                .count();
    }

}
