package com.niro.web.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.BuffPriceHistory;
import com.niro.web.mapper.BuffPriceHistoryMapper;
import com.niro.web.service.BuffPriceHistoryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品价格历史服务实现类
 *
 * @author liyl
 * @date 2025-12-29
 */
@Service
public class BuffPriceHistoryServiceImpl extends ServiceImpl<BuffPriceHistoryMapper, BuffPriceHistory> implements BuffPriceHistoryService {

    @Override
    public BigDecimal getAveragePrice(Long goodsId, int hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);
        
        List<BuffPriceHistory> historyList = this.lambdaQuery()
                .eq(BuffPriceHistory::getGoodsId, goodsId)
                .ge(BuffPriceHistory::getRecordTime, startTime)
                .select(BuffPriceHistory::getPrice)
                .list();

        if (historyList.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = historyList.stream()
                .map(BuffPriceHistory::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return total.divide(BigDecimal.valueOf(historyList.size()), 2, RoundingMode.HALF_UP);
    }
}
