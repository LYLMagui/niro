package com.niro.web.manager;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.core.util.Assert;
import com.niro.web.constant.C5SnipingAccountRuntimeConstants;
import com.niro.web.entity.C5SnipingAccountRuntimeV2;
import com.niro.web.mapper.C5SnipingAccountRuntimeV2Mapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * C5 扫货 2.0 账号运行态数据库访问管理器。
 */
@Service
public class C5SnipingAccountRuntimeV2MapperManager extends ServiceImpl<C5SnipingAccountRuntimeV2Mapper, C5SnipingAccountRuntimeV2> {


    /**
     * 查询或初始化账号运行态配置。
     *
     * @param accountId 账号 ID
     * @return 账号运行态配置
     */
    public C5SnipingAccountRuntimeV2 getOrCreateByAccountId(Long accountId) {
        C5SnipingAccountRuntimeV2 runtime = this.lambdaQuery()
                .eq(C5SnipingAccountRuntimeV2::getAccountId, accountId)
                .one();
        if (runtime != null) {
            return runtime;
        }

        LocalDateTime now = LocalDateTime.now();
        C5SnipingAccountRuntimeV2 newRuntime = new C5SnipingAccountRuntimeV2();
        newRuntime.setAccountId(accountId);
        newRuntime.setConcurrencyLimit(C5SnipingAccountRuntimeConstants.DEFAULT_CONCURRENCY_LIMIT);
        newRuntime.setMaxInFlightAttempts(C5SnipingAccountRuntimeConstants.DEFAULT_MAX_IN_FLIGHT_ATTEMPTS);
        newRuntime.setCooldownReason("");
        newRuntime.setCreateTime(now);
        newRuntime.setUpdateTime(now);
        try {
            this.save(newRuntime);
            return newRuntime;
        } catch (DuplicateKeyException e) {
            C5SnipingAccountRuntimeV2 existingRuntime = this.lambdaQuery()
                    .eq(C5SnipingAccountRuntimeV2::getAccountId, accountId)
                    .one();
            Assert.notNull(existingRuntime, "账号运行态配置初始化冲突，请重试");
            return existingRuntime;
        }
    }

    /**
     * 按账号 ID 批量查询运行态配置。
     *
     * @param accountIds 账号 ID 集合
     * @return 账号运行态配置列表
     */
    public List<C5SnipingAccountRuntimeV2> listByAccountIds(Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Collections.emptyList();
        }
        return this.lambdaQuery()
                .in(C5SnipingAccountRuntimeV2::getAccountId, accountIds)
                .list();
    }

    /**
     * 保存账号运行态配置。
     *
     * @param accountId 账号 ID
     * @param concurrencyLimit 并发上限
     * @param maxInFlightAttempts 最大在途下单尝试数
     * @return 账号运行态配置
     */
    public C5SnipingAccountRuntimeV2 saveAccountRuntime(Long accountId, Integer concurrencyLimit, Integer maxInFlightAttempts) {
        Assert.notNull(accountId, "账号ID不能为空");
        int safeConcurrencyLimit = concurrencyLimit == null ? C5SnipingAccountRuntimeConstants.DEFAULT_CONCURRENCY_LIMIT : concurrencyLimit;
        int safeMaxInFlightAttempts = maxInFlightAttempts == null ? C5SnipingAccountRuntimeConstants.DEFAULT_MAX_IN_FLIGHT_ATTEMPTS : maxInFlightAttempts;
        Assert.isTrue(safeConcurrencyLimit >= 1, "并发上限必须大于等于1");
        Assert.isTrue(safeMaxInFlightAttempts >= 1, "最大在途下单数必须大于等于1");

        C5SnipingAccountRuntimeV2 runtime = getOrCreateByAccountId(accountId);
        runtime.setConcurrencyLimit(safeConcurrencyLimit);
        runtime.setMaxInFlightAttempts(safeMaxInFlightAttempts);
        runtime.setUpdateTime(LocalDateTime.now());
        boolean updated = this.updateById(runtime);
        Assert.isTrue(updated, "账号运行态配置保存失败");
        return runtime;
    }

    /**
     * 判断账号当前是否处于冷却期。
     *
     * @param accountId 账号 ID
     * @param now 当前时间
     * @return 是否冷却中
     */
    public boolean isCoolingDown(Long accountId, LocalDateTime now) {
        C5SnipingAccountRuntimeV2 runtime = getOrCreateByAccountId(accountId);
        return runtime.getCooldownUntil() != null && runtime.getCooldownUntil().isAfter(now);
    }

    /**
     * 写入账号短冷却信息。
     *
     * @param accountId 账号 ID
     * @param cooldownUntil 冷却截止时间
     * @param reason 冷却原因
     * @return 是否更新成功
     */
    public boolean coolDown(Long accountId, LocalDateTime cooldownUntil, String reason) {
        getOrCreateByAccountId(accountId);
        return this.lambdaUpdate()
                .eq(C5SnipingAccountRuntimeV2::getAccountId, accountId)
                .set(C5SnipingAccountRuntimeV2::getCooldownUntil, cooldownUntil)
                .set(C5SnipingAccountRuntimeV2::getCooldownReason, StrUtil.maxLength(StrUtil.blankToDefault(reason, ""), 500))
                .set(C5SnipingAccountRuntimeV2::getUpdateTime, LocalDateTime.now())
                .update();
    }

    /**
     * 解析账号在途下单数上限。
     *
     * @param accountId 账号 ID
     * @return 在途下单数上限
     */
    public int resolveMaxInFlightAttempts(Long accountId) {
        C5SnipingAccountRuntimeV2 runtime = getOrCreateByAccountId(accountId);
        if (runtime.getMaxInFlightAttempts() == null || runtime.getMaxInFlightAttempts() < 1) {
            return C5SnipingAccountRuntimeConstants.DEFAULT_MAX_IN_FLIGHT_ATTEMPTS;
        }
        return runtime.getMaxInFlightAttempts();
    }
}
