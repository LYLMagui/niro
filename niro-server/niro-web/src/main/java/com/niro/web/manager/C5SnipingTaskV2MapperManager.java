package com.niro.web.manager;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.C5SnipingTaskV2;
import com.niro.web.enums.C5SnipingTaskV2StatusEnum;
import com.niro.web.mapper.C5SnipingTaskV2Mapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * C5 扫货 2.0 任务数据库访问管理器。
 */
@Service
public class C5SnipingTaskV2MapperManager extends ServiceImpl<C5SnipingTaskV2Mapper, C5SnipingTaskV2> {

    /**
     * 批量查询当前用户任务并按任务 ID 映射。
     *
     * @param userId 用户 ID
     * @param taskIds 任务 ID 集合
     * @return 任务 ID 到任务实体的映射
     */
    public Map<Long, C5SnipingTaskV2> mapByUserIdAndIds(Long userId, Collection<Long> taskIds) {
        if (userId == null || taskIds == null || taskIds.isEmpty()) {
            return Map.of();
        }
        return this.lambdaQuery()
                .eq(C5SnipingTaskV2::getUserId, userId)
                .in(C5SnipingTaskV2::getId, taskIds)
                .eq(C5SnipingTaskV2::getDelFlag, 0)
                .list()
                .stream()
                .collect(Collectors.toMap(C5SnipingTaskV2::getId, Function.identity(), (left, right) -> left));
    }

    /**
     * 判断账号是否仍被未删除任务引用。
     *
     * @param accountId 账号 ID
     * @return 是否存在未删除任务引用
     */
    public boolean existsActiveTaskByAccount(Long accountId) {
        return this.lambdaQuery()
                .eq(C5SnipingTaskV2::getAccountId, accountId)
                .eq(C5SnipingTaskV2::getDelFlag, 0)
                .count() > 0;
    }

    /**
     * 查询账号绑定的未删除任务引用。
     *
     * @param accountIds 账号 ID 集合
     * @return 未删除任务列表
     */
    public List<C5SnipingTaskV2> listActiveTasksByAccountIds(Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Collections.emptyList();
        }
        return this.lambdaQuery()
                .in(C5SnipingTaskV2::getAccountId, accountIds)
                .eq(C5SnipingTaskV2::getDelFlag, 0)
                .orderByDesc(C5SnipingTaskV2::getUpdateTime)
                .list();
    }

    /**
     * 分页查询当前用户任务。
     *
     * @param userId 用户 ID
     * @param keyword 任务关键字
     * @param taskStatus 任务状态
     * @param accountId 账号 ID
     * @param page 当前页
     * @param pageSize 每页数量
     * @return 任务分页
     */
    public Page<C5SnipingTaskV2> pageTasks(Long userId, String keyword, String taskStatus, Long accountId, long page, long pageSize) {
        return this.lambdaQuery()
                .eq(C5SnipingTaskV2::getUserId, userId)
                .eq(accountId != null, C5SnipingTaskV2::getAccountId, accountId)
                .eq(taskStatus != null && !taskStatus.isBlank(), C5SnipingTaskV2::getTaskStatus, taskStatus)
                .and(keyword != null && !keyword.isBlank(), wrapper -> wrapper.like(C5SnipingTaskV2::getName, keyword))
                .eq(C5SnipingTaskV2::getDelFlag, 0)
                .orderByDesc(C5SnipingTaskV2::getCreateTime)
                .orderByDesc(C5SnipingTaskV2::getId)
                .page(new Page<>(page, pageSize));
    }

    /**
     * 查询当前运行中的未删除任务。
     *
     * @return RUNNING 任务列表
     */
    public List<C5SnipingTaskV2> listRunningTasks() {
        return this.lambdaQuery()
                .eq(C5SnipingTaskV2::getTaskStatus, C5SnipingTaskV2StatusEnum.RUNNING)
                .eq(C5SnipingTaskV2::getDelFlag, 0)
                .orderByDesc(C5SnipingTaskV2::getPriority)
                .orderByAsc(C5SnipingTaskV2::getUpdateTime)
                .list();
    }

    /**
     * 统计账号下运行中任务数量。
     *
     * @param accountId 账号 ID
     * @return 运行中任务数
     */
    public long countRunningTasksByAccount(Long accountId) {
        return this.lambdaQuery()
                .eq(C5SnipingTaskV2::getAccountId, accountId)
                .eq(C5SnipingTaskV2::getTaskStatus, C5SnipingTaskV2StatusEnum.RUNNING)
                .eq(C5SnipingTaskV2::getDelFlag, 0)
                .count();
    }

    /**
     * 查询服务启动时需要恢复本地循环的任务。
     *
     * @return RUNNING 的未删除任务
     */
    public List<C5SnipingTaskV2> listRecoverableTasks() {
        return listRunningTasks();
    }

    /**
     * 按条件更新任务状态。
     *
     * @param taskId 任务 ID
     * @param fromStatus 原状态，为空时不限制
     * @param toStatus 目标状态
     * @return 是否更新成功
     */
    public boolean markTaskStatus(Long taskId, C5SnipingTaskV2StatusEnum fromStatus, C5SnipingTaskV2StatusEnum toStatus) {
        return this.lambdaUpdate()
                .eq(C5SnipingTaskV2::getId, taskId)
                .eq(fromStatus != null, C5SnipingTaskV2::getTaskStatus, fromStatus)
                .set(C5SnipingTaskV2::getTaskStatus, toStatus)
                .set(C5SnipingTaskV2::getFinishedAt, C5SnipingTaskV2StatusEnum.COMPLETED.equals(toStatus) ? LocalDateTime.now() : null)
                .set(C5SnipingTaskV2::getLastErrorMessage, "")
                .setSql("version = version + 1")
                .update();
    }

    /**
     * 请求运行中任务在安全点停止。
     *
     * @param taskId 任务 ID
     * @return 是否更新成功
     */
    public boolean requestStop(Long taskId) {
        return this.lambdaUpdate()
                .eq(C5SnipingTaskV2::getId, taskId)
                .eq(C5SnipingTaskV2::getTaskStatus, C5SnipingTaskV2StatusEnum.RUNNING)
                .set(C5SnipingTaskV2::getStopRequested, true)
                .set(C5SnipingTaskV2::getStopRequestedAt, LocalDateTime.now())
                .setSql("version = version + 1")
                .update();
    }

    /**
     * 清理任务停止请求字段。
     *
     * @param taskId 任务 ID
     * @return 是否更新成功
     */
    public boolean clearStopRequest(Long taskId) {
        return this.lambdaUpdate()
                .eq(C5SnipingTaskV2::getId, taskId)
                .set(C5SnipingTaskV2::getStopRequested, false)
                .set(C5SnipingTaskV2::getStopRequestedAt, null)
                .setSql("version = version + 1")
                .update();
    }

    /**
     * 按停止请求将运行中任务切为已停止。
     *
     * @param taskId 任务 ID
     * @return 是否更新成功
     */
    public boolean markStoppedByRequest(Long taskId) {
        return this.lambdaUpdate()
                .eq(C5SnipingTaskV2::getId, taskId)
                .eq(C5SnipingTaskV2::getTaskStatus, C5SnipingTaskV2StatusEnum.RUNNING)
                .set(C5SnipingTaskV2::getTaskStatus, C5SnipingTaskV2StatusEnum.STOPPED)
                .set(C5SnipingTaskV2::getStopRequested, false)
                .set(C5SnipingTaskV2::getStopRequestedAt, null)
                .set(C5SnipingTaskV2::getLastErrorMessage, "")
                .setSql("version = version + 1")
                .update();
    }

    /**
     * 启用任务并直接进入运行中。
     *
     * @param taskId 任务 ID
     * @param fromStatuses 允许的原状态
     * @return 是否更新成功
     */
    public boolean enableTask(Long taskId, List<C5SnipingTaskV2StatusEnum> fromStatuses) {
        return this.lambdaUpdate()
                .eq(C5SnipingTaskV2::getId, taskId)
                .in(fromStatuses != null && !fromStatuses.isEmpty(), C5SnipingTaskV2::getTaskStatus, fromStatuses)
                .set(C5SnipingTaskV2::getTaskStatus, C5SnipingTaskV2StatusEnum.RUNNING)
                .set(C5SnipingTaskV2::getStopRequested, false)
                .set(C5SnipingTaskV2::getStopRequestedAt, null)
                .set(C5SnipingTaskV2::getFinishedAt, null)
                .set(C5SnipingTaskV2::getLastErrorMessage, "")
                .setSql("version = version + 1")
                .update();
    }

    /**
     * 按多状态条件更新任务状态。
     *
     * @param taskId 任务 ID
     * @param fromStatuses 允许的原状态
     * @param toStatus 目标状态
     * @param errorMessage 错误信息
     * @return 是否更新成功
     */
    public boolean markTaskStatus(Long taskId, List<C5SnipingTaskV2StatusEnum> fromStatuses,
                                  C5SnipingTaskV2StatusEnum toStatus, String errorMessage) {
        return this.lambdaUpdate()
                .eq(C5SnipingTaskV2::getId, taskId)
                .in(fromStatuses != null && !fromStatuses.isEmpty(), C5SnipingTaskV2::getTaskStatus, fromStatuses)
                .set(C5SnipingTaskV2::getTaskStatus, toStatus)
                .set(C5SnipingTaskV2::getFinishedAt, C5SnipingTaskV2StatusEnum.COMPLETED.equals(toStatus) ? LocalDateTime.now() : null)
                .set(C5SnipingTaskV2::getLastErrorMessage, errorMessage == null ? "" : errorMessage)
                .setSql("version = version + 1")
                .update();
    }

    /**
     * 按条件标记任务运行中。
     *
     * @param taskId 任务 ID
     * @return 是否更新成功
     */
    public boolean markRunning(Long taskId) {
        return this.lambdaUpdate()
                .eq(C5SnipingTaskV2::getId, taskId)
                .eq(C5SnipingTaskV2::getTaskStatus, C5SnipingTaskV2StatusEnum.RUNNING)
                .eq(C5SnipingTaskV2::getDelFlag, 0)
                .set(C5SnipingTaskV2::getStopRequested, false)
                .set(C5SnipingTaskV2::getStopRequestedAt, null)
                .set(C5SnipingTaskV2::getLastErrorMessage, "")
                .setSql("version = version + 1")
                .update();
    }

    /**
     * 更新任务最近错误，不改变运行状态。
     *
     * @param taskId 任务 ID
     * @param errorMessage 错误信息
     * @return 是否更新成功
     */
    public boolean updateLastError(Long taskId, String errorMessage) {
        return this.lambdaUpdate()
                .eq(C5SnipingTaskV2::getId, taskId)
                .eq(C5SnipingTaskV2::getTaskStatus, C5SnipingTaskV2StatusEnum.RUNNING)
                .set(C5SnipingTaskV2::getLastErrorMessage, errorMessage == null ? "" : errorMessage)
                .setSql("version = version + 1")
                .update();
    }

    /**
     * 清空任务最近错误。
     *
     * @param taskId 任务 ID
     * @return 是否更新成功
     */
    public boolean clearLastError(Long taskId) {
        return updateLastError(taskId, "");
    }

    /**
     * 预占购买名额，防止 BUY_COUNT 模式超买。
     *
     * @param taskId 任务 ID
     * @return 是否预占成功
     */
    public boolean reserveBuySlot(Long taskId) {
        return this.lambdaUpdate()
                .eq(C5SnipingTaskV2::getId, taskId)
                .eq(C5SnipingTaskV2::getTaskStatus, C5SnipingTaskV2StatusEnum.RUNNING)
                .apply("success_buy_count + reserved_buy_count < target_buy_count")
                .setSql("reserved_buy_count = reserved_buy_count + 1")
                .setSql("version = version + 1")
                .update();
    }

    /**
     * 释放已预占的购买名额。
     *
     * @param taskId 任务 ID
     * @return 是否释放成功
     */
    public boolean releaseBuySlot(Long taskId) {
        return this.lambdaUpdate()
                .eq(C5SnipingTaskV2::getId, taskId)
                .gt(C5SnipingTaskV2::getReservedBuyCount, 0)
                .setSql("reserved_buy_count = greatest(reserved_buy_count - 1, 0)")
                .setSql("version = version + 1")
                .update();
    }

    /**
     * 确认购买成功并回收预占名额。
     *
     * @param taskId 任务 ID
     * @return 是否确认成功
     */
    public boolean confirmBuySuccess(Long taskId) {
        return this.lambdaUpdate()
                .eq(C5SnipingTaskV2::getId, taskId)
                .gt(C5SnipingTaskV2::getReservedBuyCount, 0)
                .setSql("reserved_buy_count = greatest(reserved_buy_count - 1, 0)")
                .setSql("success_buy_count = success_buy_count + 1")
                .setSql("version = version + 1")
                .update();
    }

    /**
     * 增加任务命中数量。
     *
     * @param taskId 任务 ID
     * @param count 增量
     * @return 是否更新成功
     */
    public boolean incrementHitCount(Long taskId, int count) {
        if (count <= 0) {
            return true;
        }
        return this.lambdaUpdate()
                .eq(C5SnipingTaskV2::getId, taskId)
                .setSql("hit_count = hit_count + " + count)
                .setSql("version = version + 1")
                .update();
    }

    /**
     * 停用时回收任务残留预占名额。
     *
     * @param taskId 任务 ID
     * @return 是否更新成功
     */
    public boolean clearReservedBuyCount(Long taskId) {
        return this.lambdaUpdate()
                .eq(C5SnipingTaskV2::getId, taskId)
                .set(C5SnipingTaskV2::getReservedBuyCount, 0)
                .setSql("version = version + 1")
                .update();
    }

    /**
     * 对无未结算 attempt 的任务清理残留预占计数。
     *
     * @param taskId 任务 ID
     * @return 是否更新成功
     */
    public boolean clearReservedBuyCountIfNoUnsettledAttempt(Long taskId) {
        return this.lambdaUpdate()
                .eq(C5SnipingTaskV2::getId, taskId)
                .apply("not exists (select 1 from c5_sniping_buy_attempt_v2 a where a.task_id = c5_sniping_task_v2.id and a.slot_reserved = true and a.slot_released = false)")
                .set(C5SnipingTaskV2::getReservedBuyCount, 0)
                .setSql("version = version + 1")
                .update();
    }
}
