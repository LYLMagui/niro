package com.niro.web.service.strategy;

import com.niro.web.entity.BuffAccount;
import com.niro.web.entity.BuffScanTask;
import com.niro.web.enums.PlatformEnum;

/**
 * 平台策略接口
 * <p>
 * 用于隔离不同平台的业务逻辑（如任务处理、账号同步等）。
 */
public interface IPlatformStrategy {

    /**
     * 处理任务启动逻辑
     * <p>
     * Buff: 推送 Redis 队列
     * C5: 调用 SDK 创建任务
     *
     * @param task 任务实体
     */
    void handleTask(BuffScanTask task);

    /**
     * 同步账号余额和状态
     * <p>
     * Buff: 通过 Cookie 调用接口
     * C5: 调用 SDK 查询余额
     *
     * @param account 账号实体
     */
    void syncAccountBalance(BuffAccount account);

    /**
     * 获取平台代码
     *
     * @return 平台枚举
     */
    PlatformEnum getPlatform();
}
