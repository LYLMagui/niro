package com.niro.web.manager;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.core.constant.GlobalConstant;
import com.niro.web.entity.TradeOrderRecord;
import com.niro.web.enums.OrderStatusEnum;
import com.niro.web.enums.PlatformEnum;
import com.niro.web.mapper.TradeOrderRecordMapper;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 *
 *
 * @author liyl
 * @date 2026/2/4
 */
@Service
public class TradeOrderRecordMapperManager extends ServiceImpl<TradeOrderRecordMapper, TradeOrderRecord> {

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

    public Map<Long, Integer> countSuccessByTaskIds(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> validTaskIds = taskIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (validTaskIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows = this.baseMapper.countSuccessByTaskIds(validTaskIds, OrderStatusEnum.SUCCESS.getCode());
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Integer> result = new HashMap<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object taskIdValue = row.get("taskId");
            Object successCountValue = row.get("successCount");
            if (!(taskIdValue instanceof Number taskIdNumber) || !(successCountValue instanceof Number successCountNumber)) {
                continue;
            }
            result.put(taskIdNumber.longValue(), successCountNumber.intValue());
        }
        return result;
    }

    public TradeOrderRecord getByOrderId(String orderId) {
        return this.lambdaQuery().eq(TradeOrderRecord::getOrderId,orderId).one();
    }

    public List<TradeOrderRecord> listSuccessfulPurchaseRecords(Long userId, String keyword,
                                                                LocalDateTime startDateTime, LocalDateTime endDateTime) {
        return this.lambdaQuery()
                .eq(TradeOrderRecord::getUserId, userId)
                .eq(TradeOrderRecord::getStatus, OrderStatusEnum.SUCCESS.getCode())
                .like(StrUtil.isNotBlank(keyword), TradeOrderRecord::getGoodsName, keyword)
                .ge(startDateTime != null, TradeOrderRecord::getCreateTime, startDateTime)
                .le(endDateTime != null, TradeOrderRecord::getCreateTime, endDateTime)
                .orderByAsc(TradeOrderRecord::getCreateTime)
                .list();
    }
}
