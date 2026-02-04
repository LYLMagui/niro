package com.niro.web.manager;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.core.constant.GlobalConstant;
import com.niro.web.entity.TradeOrderRecord;
import com.niro.web.mapper.TradeOrderRecordMapper;
import org.springframework.stereotype.Service;

/**
 *
 *
 * @author liyl
 * @date 2026/2/4
 */
@Service
public class TradeOrderRecordManagerMapper extends ServiceImpl<TradeOrderRecordMapper, TradeOrderRecord> {

    public Long countSuccess(Long taskId) {
        return this.lambdaQuery().eq(TradeOrderRecord::getTaskId, taskId)
                .eq(TradeOrderRecord::getStatus, GlobalConstant.YES)
                .count();
    }

}
