package com.niro.web.manager;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.C5SnipingTaskRunV2;
import com.niro.web.enums.C5SnipingTaskRunV2StatusEnum;
import com.niro.web.mapper.C5SnipingTaskRunV2Mapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * C5 扫货 2.0 运行实例数据库访问管理器。
 */
@Service
public class C5SnipingTaskRunV2MapperManager extends ServiceImpl<C5SnipingTaskRunV2Mapper, C5SnipingTaskRunV2> {

    /**
     * 查询任务最近一次运行实例。
     *
     * @param taskId 任务 ID
     * @return 最近运行实例
     */
    public C5SnipingTaskRunV2 findLatestByTaskId(Long taskId) {
        return this.lambdaQuery()
                .eq(C5SnipingTaskRunV2::getTaskId, taskId)
                .orderByDesc(C5SnipingTaskRunV2::getStartedAt)
                .last("limit 1")
                .one();
    }

    /**
     * 收尾运行实例。
     *
     * @param runId 运行实例 ID
     * @param status 目标运行状态
     * @param reason 停止原因
     * @param errorMessage 错误信息
     * @return 是否更新成功
     */
    public boolean finishRun(Long runId, C5SnipingTaskRunV2StatusEnum status, String reason, String errorMessage) {
        return this.lambdaUpdate()
                .eq(C5SnipingTaskRunV2::getId, runId)
                .set(C5SnipingTaskRunV2::getRunStatus, status)
                .set(C5SnipingTaskRunV2::getStopReason, reason == null ? "" : reason)
                .set(C5SnipingTaskRunV2::getLastErrorMessage, errorMessage == null ? "" : errorMessage)
                .setSql("finished_at = now()")
                .update();
    }

    /**
     * 增加运行实例命中数量。
     *
     * @param runId 运行实例 ID
     * @param count 增量
     * @return 是否更新成功
     */
    public boolean incrementHitCount(Long runId, int count) {
        if (count <= 0) {
            return true;
        }
        return this.lambdaUpdate()
                .eq(C5SnipingTaskRunV2::getId, runId)
                .setSql("hit_count = hit_count + " + count)
                .update();
    }

    /**
     * 增加运行实例下单尝试数量。
     *
     * @param runId 运行实例 ID
     * @return 是否更新成功
     */
    public boolean incrementAttemptCount(Long runId) {
        return this.lambdaUpdate()
                .eq(C5SnipingTaskRunV2::getId, runId)
                .setSql("buy_attempt_count = buy_attempt_count + 1")
                .update();
    }

    /**
     * 增加运行实例成功购买数量并清空连续错误。
     *
     * @param runId 运行实例 ID
     * @return 是否更新成功
     */
    public boolean incrementSuccessCount(Long runId) {
        return this.lambdaUpdate()
                .eq(C5SnipingTaskRunV2::getId, runId)
                .setSql("buy_success_count = buy_success_count + 1")
                .set(C5SnipingTaskRunV2::getConsecutiveErrorCount, 0)
                .set(C5SnipingTaskRunV2::getLastErrorMessage, "")
                .update();
    }

    /**
     * 标记单轮扫描异常。
     *
     * @param runId 运行实例 ID
     * @param errorMessage 错误信息
     * @return 是否更新成功
     */
    public boolean markCycleError(Long runId, String errorMessage) {
        return this.lambdaUpdate()
                .eq(C5SnipingTaskRunV2::getId, runId)
                .setSql("retry_count = retry_count + 1")
                .setSql("consecutive_error_count = consecutive_error_count + 1")
                .set(C5SnipingTaskRunV2::getLastErrorMessage, errorMessage == null ? "" : errorMessage)
                .update();
    }

    /**
     * 创建运行实例。
     *
     * @param taskId 任务 ID
     * @return 运行实例
     */
    public C5SnipingTaskRunV2 createRun(Long taskId) {
        C5SnipingTaskRunV2 latestRun = findLatestByTaskId(taskId);
        C5SnipingTaskRunV2 run = new C5SnipingTaskRunV2();
        run.setTaskId(taskId);
        run.setRunStatus(C5SnipingTaskRunV2StatusEnum.RUNNING);
        run.setStopReason("");
        run.setStartedAt(LocalDateTime.now());
        run.setRetryCount(latestRun == null || latestRun.getRetryCount() == null ? 0 : latestRun.getRetryCount());
        run.setConsecutiveErrorCount(latestRun == null || latestRun.getConsecutiveErrorCount() == null ? 0 : latestRun.getConsecutiveErrorCount());
        run.setHitCount(0);
        run.setBuyAttemptCount(0);
        run.setBuySuccessCount(0);
        run.setLastErrorMessage("");
        this.save(run);
        return run;
    }

    /**
     * 查询任务当前运行中实例。
     *
     * @param taskId 任务 ID
     * @return 运行中实例
     */
    public C5SnipingTaskRunV2 findRunningByTaskId(Long taskId) {
        return this.lambdaQuery()
                .eq(C5SnipingTaskRunV2::getTaskId, taskId)
                .eq(C5SnipingTaskRunV2::getRunStatus, C5SnipingTaskRunV2StatusEnum.RUNNING)
                .orderByDesc(C5SnipingTaskRunV2::getStartedAt)
                .last("limit 1")
                .one();
    }

    /**
     * 收尾任务的运行中实例。
     *
     * @param taskId 任务 ID
     * @param status 目标运行状态
     * @param reason 停止原因
     * @param errorMessage 错误信息
     * @return 是否更新成功
     */
    public boolean finishRunningByTaskId(Long taskId, C5SnipingTaskRunV2StatusEnum status, String reason, String errorMessage) {
        return this.lambdaUpdate()
                .eq(C5SnipingTaskRunV2::getTaskId, taskId)
                .eq(C5SnipingTaskRunV2::getRunStatus, C5SnipingTaskRunV2StatusEnum.RUNNING)
                .set(C5SnipingTaskRunV2::getRunStatus, status)
                .set(C5SnipingTaskRunV2::getStopReason, reason == null ? "" : reason)
                .set(C5SnipingTaskRunV2::getLastErrorMessage, errorMessage == null ? "" : errorMessage)
                .setSql("finished_at = now()")
                .update();
    }

    /**
     * 查询运行中实例列表。
     *
     * @return 运行中实例列表
     */
    public List<C5SnipingTaskRunV2> listRunningRuns() {
        return this.lambdaQuery()
                .eq(C5SnipingTaskRunV2::getRunStatus, C5SnipingTaskRunV2StatusEnum.RUNNING)
                .list();
    }
}
