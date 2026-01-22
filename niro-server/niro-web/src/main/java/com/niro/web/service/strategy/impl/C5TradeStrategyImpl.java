package com.niro.web.service.strategy.impl;

import com.niro.web.entity.BuffAccount;
import com.niro.web.entity.BuffScanTask;
import com.niro.web.enums.PlatformEnum;
import com.niro.web.service.strategy.IPlatformStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * C5 平台策略实现 (占位)
 */
@Slf4j
@Component
public class C5TradeStrategyImpl implements IPlatformStrategy {

    @Override
    public PlatformEnum getPlatform() {
        return PlatformEnum.C5;
    }

    @Override
    public void handleTask(BuffScanTask task) {
        log.info("[C5] 收到任务启动请求: {}", task.getName());
        // TODO: 调用 C5 SDK 创建任务
    }

    @Override
    public void syncAccountBalance(BuffAccount account) {
        log.info("[C5] 收到余额同步请求: {}", account.getAccountName());
        // TODO: 调用 C5 SDK 查询余额
    }
}
