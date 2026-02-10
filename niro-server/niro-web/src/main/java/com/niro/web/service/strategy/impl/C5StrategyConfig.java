package com.niro.web.service.strategy.impl;

import lombok.Getter;
import lombok.Setter;

/**
 * C5 交易策略配置
 */
@Getter
@Setter
public class C5StrategyConfig {
    // 默认锚定第2个阶梯 (Index 1: 次低价)
    private int anchorTierIndex = 1;

    /**
     * 安全边际 (Safe Margin)
     * <p>
     * 0.01-0.02 (1%-2%): 高流动性通货 (如钥匙、红线)
     * 0.03-0.05 (3%-5%): [默认] 热门饰品，平衡成交率与抗跌
     * 0.08+ (8%+): 低流动性/高价值饰品，深水防套
     * </p>
     */
    private double safeMargin = 0.03;
    private int minConcurrency = 5;
}
