package com.niro.web.manager;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.C5SnipingBuyAttemptV2;
import com.niro.web.enums.C5SnipingBuyAttemptV2StatusEnum;
import com.niro.web.mapper.C5SnipingBuyAttemptV2Mapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * C5 扫货 2.0 下单尝试数据库访问管理器。
 */
@Service
public class C5SnipingBuyAttemptV2MapperManager extends ServiceImpl<C5SnipingBuyAttemptV2Mapper, C5SnipingBuyAttemptV2> {

    /**
     * 分页查询任务下单尝试明细。
     *
     * @param taskId 任务 ID
     * @param page 当前页
     * @param pageSize 每页数量
     * @return 下单尝试分页
     */
    public Page<C5SnipingBuyAttemptV2> pageByTaskId(Long taskId, long page, long pageSize) {
        return this.lambdaQuery()
                .eq(C5SnipingBuyAttemptV2::getTaskId, taskId)
                .orderByDesc(C5SnipingBuyAttemptV2::getCreatedAt)
                .page(new Page<>(page, pageSize));
    }

    /**
     * 幂等创建下单尝试记录。
     *
     * @param attempt 下单尝试记录
     * @return true 表示创建成功，false 表示命中唯一约束
     */
    public boolean saveInitAttemptIfAbsent(C5SnipingBuyAttemptV2 attempt) {
        try {
            attempt.setAttemptStatus(C5SnipingBuyAttemptV2StatusEnum.INIT);
            attempt.setCreatedAt(LocalDateTime.now());
            return this.save(attempt);
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    /**
     * 更新下单尝试结果。
     *
     * @param id 下单尝试 ID
     * @param status 尝试状态
     * @param orderRecordId 订单记录 ID
     * @param failureCode 失败编码
     * @param failureMessage 失败信息
     * @return 是否更新成功
     */
    public boolean finishAttempt(Long id, C5SnipingBuyAttemptV2StatusEnum status, Long orderRecordId,
                                 String failureCode, String failureMessage) {
        return this.lambdaUpdate()
                .eq(C5SnipingBuyAttemptV2::getId, id)
                .eq(C5SnipingBuyAttemptV2::getAttemptStatus, C5SnipingBuyAttemptV2StatusEnum.INIT)
                .set(C5SnipingBuyAttemptV2::getAttemptStatus, status)
                .set(orderRecordId != null, C5SnipingBuyAttemptV2::getOrderRecordId, orderRecordId)
                .set(failureCode != null, C5SnipingBuyAttemptV2::getFailureCode, failureCode)
                .set(failureMessage != null, C5SnipingBuyAttemptV2::getFailureMessage, failureMessage)
                .set(C5SnipingBuyAttemptV2::getFinishedAt, LocalDateTime.now())
                .update();
    }

    /**
     * 按需标记预占名额已结算。
     *
     * @param id 下单尝试 ID
     * @return 是否从未结算状态更新为已结算
     */
    public boolean markSlotReleasedIfNeeded(Long id) {
        return this.lambdaUpdate()
                .eq(C5SnipingBuyAttemptV2::getId, id)
                .eq(C5SnipingBuyAttemptV2::getSlotReserved, true)
                .eq(C5SnipingBuyAttemptV2::getSlotReleased, false)
                .set(C5SnipingBuyAttemptV2::getSlotReleased, true)
                .update();
    }

    /**
     * 查询任务下未结算的预占尝试。
     *
     * @param taskId 任务 ID
     * @return 未结算预占尝试列表
     */
    public List<C5SnipingBuyAttemptV2> listUnsettledReservedAttempts(Long taskId) {
        return this.lambdaQuery()
                .eq(C5SnipingBuyAttemptV2::getTaskId, taskId)
                .eq(C5SnipingBuyAttemptV2::getSlotReserved, true)
                .eq(C5SnipingBuyAttemptV2::getSlotReleased, false)
                .list();
    }

    /**
     * 查询已过期的 INIT 下单尝试，兼容历史 initExpireAt 为空的长期 INIT 数据。
     *
     * @param now 当前时间
     * @param fallbackCreatedBefore initExpireAt 为空时的创建时间兜底阈值
     * @param limit 查询数量上限
     * @return 已过期 INIT 下单尝试列表
     */
    public List<C5SnipingBuyAttemptV2> listExpiredInitAttempts(LocalDateTime now, LocalDateTime fallbackCreatedBefore, int limit) {
        int safeLimit = Math.max(1, limit);
        return this.lambdaQuery()
                .eq(C5SnipingBuyAttemptV2::getAttemptStatus, C5SnipingBuyAttemptV2StatusEnum.INIT)
                .and(wrapper -> wrapper.le(C5SnipingBuyAttemptV2::getInitExpireAt, now)
                        .or(nested -> nested.isNull(C5SnipingBuyAttemptV2::getInitExpireAt)
                                .le(C5SnipingBuyAttemptV2::getCreatedAt, fallbackCreatedBefore)))
                .orderByAsc(C5SnipingBuyAttemptV2::getInitExpireAt)
                .last("limit " + safeLimit)
                .list();
    }

    /**
     * 查询已终态但预占名额尚未结算的下单尝试。
     *
     * @param limit 查询数量上限
     * @return 已终态未结算预占尝试列表
     */
    public List<C5SnipingBuyAttemptV2> listUnsettledTerminalReservedAttempts(int limit) {
        int safeLimit = Math.max(1, limit);
        return this.lambdaQuery()
                .eq(C5SnipingBuyAttemptV2::getSlotReserved, true)
                .eq(C5SnipingBuyAttemptV2::getSlotReleased, false)
                .ne(C5SnipingBuyAttemptV2::getAttemptStatus, C5SnipingBuyAttemptV2StatusEnum.INIT)
                .orderByAsc(C5SnipingBuyAttemptV2::getFinishedAt)
                .orderByAsc(C5SnipingBuyAttemptV2::getCreatedAt)
                .last("limit " + safeLimit)
                .list();
    }

    /**
     * 将过期 INIT 下单尝试标记为失败。
     *
     * @param id 下单尝试 ID
     * @param failureCode 失败编码
     * @param failureMessage 失败信息
     * @return 是否从 INIT 状态更新为失败
     */
    public boolean markExpiredInitAttemptFailed(Long id, String failureCode, String failureMessage) {
        LocalDateTime now = LocalDateTime.now();
        return this.lambdaUpdate()
                .eq(C5SnipingBuyAttemptV2::getId, id)
                .eq(C5SnipingBuyAttemptV2::getAttemptStatus, C5SnipingBuyAttemptV2StatusEnum.INIT)
                .set(C5SnipingBuyAttemptV2::getAttemptStatus, C5SnipingBuyAttemptV2StatusEnum.FAILED)
                .set(C5SnipingBuyAttemptV2::getFailureCode, failureCode)
                .set(C5SnipingBuyAttemptV2::getFailureMessage, failureMessage)
                .set(C5SnipingBuyAttemptV2::getFinishedAt, now)
                .set(C5SnipingBuyAttemptV2::getUpdateTime, now)
                .setSql("recovery_attempt_count = coalesce(recovery_attempt_count, 0) + 1")
                .update();
    }

    /**
     * 增加恢复核验次数并刷新远端核验时间。
     *
     * @param id 下单尝试 ID
     * @return 是否更新成功
     */
    public boolean incrementRecoveryAttemptCount(Long id) {
        LocalDateTime now = LocalDateTime.now();
        return this.lambdaUpdate()
                .eq(C5SnipingBuyAttemptV2::getId, id)
                .set(C5SnipingBuyAttemptV2::getRemoteCheckedAt, now)
                .set(C5SnipingBuyAttemptV2::getUpdateTime, now)
                .setSql("recovery_attempt_count = coalesce(recovery_attempt_count, 0) + 1")
                .update();
    }

    /**
     * 统计账号当前未释放的在途下单金额。
     *
     * @param accountId 账号 ID
     * @return 在途金额
     */
    public BigDecimal sumInFlightAmount(Long accountId) {
        BigDecimal amount = this.baseMapper.sumInFlightAmount(accountId);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    /**
     * 统计账号当前未释放的在途下单尝试数。
     *
     * @param accountId 账号 ID
     * @return 在途下单尝试数
     */
    public int countInFlightAttempts(Long accountId) {
        if (accountId == null) {
            return 0;
        }
        return this.baseMapper.countInFlightAttempts(accountId);
    }

    /**
     * 标记 attempt 已完成远端核验。
     *
     * @param id attempt ID
     * @return 是否更新成功
     */
    public boolean markRemoteChecked(Long id) {
        LocalDateTime now = LocalDateTime.now();
        return this.lambdaUpdate()
                .eq(C5SnipingBuyAttemptV2::getId, id)
                .set(C5SnipingBuyAttemptV2::getRemoteCheckedAt, now)
                .set(C5SnipingBuyAttemptV2::getUpdateTime, now)
                .update();
    }
}
