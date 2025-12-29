package com.niro.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.niro.web.entity.BuffPriceHistory;

import java.math.BigDecimal;

/**
 * 商品价格历史服务类
 *
 * @author liyl
 * @date 2025-12-29
 */
public interface BuffPriceHistoryService extends IService<BuffPriceHistory> {

    /**
     * 获取商品的近期平均价格 (用于风控)
     * @param goodsId 商品ID
     * @param hours 最近小时数
     * @return 平均价格
     */
    BigDecimal getAveragePrice(Long goodsId, int hours);
}
