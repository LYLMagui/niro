package com.niro.web.service.strategy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.niro.core.exception.BusinessException;
import com.niro.web.enums.PlatformEnum;

/**
 * 平台策略工厂
 */
@Component
public class PlatformStrategyFactory {

    private final Map<PlatformEnum, IPlatformStrategy> strategyMap = new ConcurrentHashMap<>();

    /**
     * 构造函数注入所有策略实现
     */
    public PlatformStrategyFactory(List<IPlatformStrategy> strategies) {
        this.strategyMap.putAll(strategies.stream()
                .collect(Collectors.toMap(IPlatformStrategy::getPlatform, Function.identity())));
    }

    /**
     * 获取策略
     *
     * @param platform 平台枚举
     * @return 策略实现
     */
    public IPlatformStrategy getStrategy(PlatformEnum platform) {
        IPlatformStrategy strategy = strategyMap.get(platform);
        if (strategy == null) {
            throw new BusinessException("不支持的平台策略: " + platform);
        }
        return strategy;
    }
}
