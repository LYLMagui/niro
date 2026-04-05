package com.niro.web.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import com.niro.core.util.Assert;
import com.niro.web.entity.BuffScanTask;
import com.niro.web.enums.TaskStatusEnum;
import com.niro.web.mapper.BuffScanTaskMapper;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * C5 任务调度器
 * <p>
 * 负责 C5 平台任务的调度管理，支持 Cron 表达式和固定间隔执行。
 * 使用虚拟线程执行实际业务逻辑。
 * <p>
 * 重构说明 (2026-01-29):
 * 引入 Supervisor-Worker 模式：
 * 1. cronFutures (Supervisor): 负责定时唤醒，生命周期常驻。
 * 2. runningFutures (Worker): 负责实际的高频扫描，生命周期由 Duration 控制。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class C5TaskScheduler {

    private final TaskScheduler taskScheduler;
    private final BuffScanTaskMapper buffScanTaskMapper;

    /**
     * Cron 调度句柄 (Supervisor)
     * 负责按 Cron 表达式触发 startSession
     */
    private final Map<Long, ScheduledFuture<?>> cronFutures = new ConcurrentHashMap<>();

    /**
     * 运行中任务句柄 (Worker)
     * 负责执行具体的扫描/交易逻辑 (Interval/FixedDelay)
     */
    private final Map<Long, ScheduledFuture<?>> runningFutures = new ConcurrentHashMap<>();

    /**
     * 任务回调逻辑存储
     * 用于在 Work-Rest-Loop 循环中重启任务时获取业务逻辑
     */
    private final Map<Long, Consumer<BuffScanTask>> taskCallbacks = new ConcurrentHashMap<>();

    /**
     * 启动任务
     *
     * @param task          任务实体
     * @param businessLogic 业务逻辑回调
     */
    public TaskStatusEnum start(BuffScanTask task, Consumer<BuffScanTask> businessLogic) {
        Assert.notNull(task, "任务不能为空");
        Assert.notNull(businessLogic, "业务逻辑回调不能为空");
        Long taskId = task.getId();

        // 先彻底停止旧任务，防止状态不一致，再重新注册回调
        stopWithoutRemovingCallback(taskId);
        taskCallbacks.put(taskId, businessLogic);

        log.info("开始调度 C5 任务: {} (ID: {})", task.getName(), taskId);

        // 3. 判断调度模式
        String cron = task.getCronExpression();
        boolean isLoopMode = isImmediateOrBlank(cron);

        if (StrUtil.isNotBlank(cron) && !isLoopMode) {
            // --- Cron 模式 (Supervisor) ---
            try {
                ScheduledFuture<?> future = taskScheduler.schedule(
                        () -> startSession(taskId),
                        new CronTrigger(cron.trim())
                );
                cronFutures.put(taskId, future);
                updateTaskStatus(taskId, TaskStatusEnum.SCHEDULED);
                log.info("任务 [{}] 已注册 Cron 调度: {}", taskId, cron);
                return TaskStatusEnum.SCHEDULED;
            } catch (Exception e) {
                log.error("Cron 表达式非法: {}", cron, e);
                throw e;
            }
        }

        // --- 直连模式 ---
        // 无 Cron 或 "立即执行"占位符，先持久化为 RUNNING，再启动 Session
        if (isLoopMode && StrUtil.isNotBlank(cron)) {
            log.info("任务 [{}] 检测到立即执行标识 ({})，转为 Direct Start 模式", taskId, cron);
        }
        updateTaskStatus(taskId, TaskStatusEnum.RUNNING);
        startSession(taskId);
        return TaskStatusEnum.RUNNING;
    }

    /**
     * 判断是否为立即执行或空 Cron (循环模式)
     */
    private boolean isImmediateOrBlank(String cron) {
        if (StrUtil.isBlank(cron)) {
            return true;
        }
        String trimmed = cron.trim();
        return "* * * * * ?".equals(trimmed) || "* * * * * *".equals(trimmed);
    }

    /**
     * 启动一次运行会话 (Worker)
     * 被 Cron 触发、直接调用或 Rest 结束后的重启
     */
    private void startSession(Long taskId) {
        // --- 1. 获取回调 ---
        Consumer<BuffScanTask> businessLogic = taskCallbacks.get(taskId);
        if (businessLogic == null) {
            log.error("任务 [{}] 缺少业务回调，无法启动会话", taskId);
            stop(taskId);
            return;
        }

        // --- 2. 原子启动 (Atomic Start) ---
        // 使用 compute 确保并发安全：取消旧任务，校验状态，启动新任务
        ScheduledFuture<?> newFuture = runningFutures.compute(taskId, (k, existingFuture) -> {
            if (existingFuture != null) {
                existingFuture.cancel(true);
            }

            // 校验数据库最新状态
            BuffScanTask dbTask = buffScanTaskMapper.selectById(taskId);
            if (dbTask == null) {
                return null;
            }
            
            if (dbTask.getStatus() == TaskStatusEnum.STOPPED.getCode()
                    || dbTask.getStatus() == TaskStatusEnum.COMPLETED.getCode()
                    || dbTask.getStatus() == TaskStatusEnum.ERROR.getCode()) {
                log.info("任务 [{}] 当前状态不可启动，忽略本次会话启动", taskId);
                return null;
            }

            log.info("任务 [{}] 会话启动... (状态: Running)", taskId);

            // 构建 Runner
            C5TaskRunner runner = new C5TaskRunner(dbTask, businessLogic);
            ScheduledFuture<?> future;

            // 注册调度策略
            if (dbTask.getScanInterval() != null && dbTask.getScanInterval() > 0) {
                future = taskScheduler.scheduleWithFixedDelay(runner, Duration.ofSeconds(dbTask.getScanInterval()));
                log.info("任务 [{}] 会话执行策略: FixedDelay {}s", taskId, dbTask.getScanInterval());
            } else if (dbTask.getScanIntervalMin() != null && dbTask.getScanIntervalMax() != null) {
                int min = Math.max(0, dbTask.getScanIntervalMin());
                int max = Math.max(min, dbTask.getScanIntervalMax());
                Trigger trigger = triggerContext -> {
                    Instant lastCompletion = triggerContext.lastCompletion();
                    if (lastCompletion == null) {
                        return Instant.now();
                    }
                    int delay = RandomUtil.randomInt(min, max + 1);
                    return lastCompletion.plus(Duration.ofSeconds(delay));
                };
                future = taskScheduler.schedule(runner, trigger);
                log.info("任务 [{}] 会话执行策略: Random {}-{}s", taskId, min, max);
            } else {
                future = taskScheduler.scheduleWithFixedDelay(runner, Duration.ofSeconds(1));
                log.info("任务 [{}] 会话执行策略: Default FixedDelay 1s", taskId);
            }
            return future;
        });

        // --- 3. 后置处理 ---
        if (newFuture == null) {
            // 启动失败（状态校验未通过），确保清理句柄，但不要覆盖已落地状态
            BuffScanTask check = buffScanTaskMapper.selectById(taskId);
            if (check == null) {
                stopWithoutRemovingCallback(taskId);
                taskCallbacks.remove(taskId);
                return;
            }
            if (check.getStatus() == TaskStatusEnum.STOPPED.getCode()) {
                stop(taskId);
                return;
            }
            if (check.getStatus() == TaskStatusEnum.COMPLETED.getCode()
                    || check.getStatus() == TaskStatusEnum.ERROR.getCode()) {
                stopWithoutRemovingCallback(taskId);
                taskCallbacks.remove(taskId);
            }
            return;
        }

        updateTaskStatus(taskId, TaskStatusEnum.RUNNING);

        // --- 4. 处理 Duration (自动暂停/休息) ---
        // 需重新获取配置中的 duration (虽有微小时间差，但可接受)
        BuffScanTask currentTask = buffScanTaskMapper.selectById(taskId);
        if (currentTask != null && currentTask.getDurationMinutes() != null && currentTask.getDurationMinutes() > 0) {
            Instant endTime = Instant.now().plus(Duration.ofMinutes(currentTask.getDurationMinutes()));
            taskScheduler.schedule(() -> {
                log.info("任务 [{}] 运行时长已达 ({} min)，结束当前会话", taskId, currentTask.getDurationMinutes());
                stopSession(taskId, newFuture);
            }, endTime);
        }
    }

    /**
     * 停止当前会话 (Worker Stop)
     * 处理 Work-Rest-Loop 逻辑
     */
    private void stopSession(Long taskId, ScheduledFuture<?> sessionFuture) {
        // 乐观移除：仅当 Map 中的 Future 与当前会话一致时才移除
        if (runningFutures.remove(taskId, sessionFuture)) {
            sessionFuture.cancel(true);

            // 获取最新配置以检查 RestPeriod
            BuffScanTask task = buffScanTaskMapper.selectById(taskId);
            if (task == null) {
                stop(taskId);
                return;
            }

            // 检查是否需要进入休息模式 (Work-Rest-Loop)
            // 条件：配置了休息时间 且 是循环模式 (无 Cron 或 占位符)
            boolean isLoopMode = isImmediateOrBlank(task.getCronExpression());
            
            if (task.getRestPeriod() != null && task.getRestPeriod() > 0 && isLoopMode) {
                log.info("任务 [{}] 进入休息模式 (Resting: {} min) -> 状态: SCHEDULED", taskId, task.getRestPeriod());
                updateTaskStatus(taskId, TaskStatusEnum.SCHEDULED);
                
                // 调度下一次启动
                taskScheduler.schedule(
                    () -> startSession(taskId),
                    Instant.now().plus(Duration.ofMinutes(task.getRestPeriod()))
                );
            } else if (cronFutures.containsKey(taskId)) {
                // 有 Cron 调度，回归等待 Cron 触发
                log.info("任务 [{}] 会话结束，等待下一次 Cron 触发 -> 状态: SCHEDULED", taskId);
                updateTaskStatus(taskId, TaskStatusEnum.SCHEDULED);
            } else {
                // 无 Cron 且无 Rest，彻底结束
                log.info("任务 [{}] 会话结束，无后续计划 -> 状态: STOPPED", taskId);
                updateTaskStatus(taskId, TaskStatusEnum.STOPPED);
                // 彻底停止时移除回调
                taskCallbacks.remove(taskId);
            }
        } else {
            log.warn("任务 [{}] 停止会话失败: Future 不匹配 (可能是并发启动导致的旧会话)", taskId);
        }
    }

    /**
     * 内部停止逻辑，不移除 Callback (用于 start 方法中的清理)
     */
    private void stopWithoutRemovingCallback(Long taskId) {
        ScheduledFuture<?> cron = cronFutures.remove(taskId);
        if (cron != null) cron.cancel(true);

        ScheduledFuture<?> running = runningFutures.remove(taskId);
        if (running != null) running.cancel(true);
    }

    /**
     * 彻底停止任务 (Supervisor + Worker Stop)
     * 移除回调，清理所有句柄
     */
    public void stop(Long taskId) {
        stopWithoutRemovingCallback(taskId);
        taskCallbacks.remove(taskId); // 移除回调

        log.info("任务 [{}] 已停止调度", taskId);
        updateTaskStatus(taskId, TaskStatusEnum.STOPPED);
    }

    /**
     * 完成任务
     */
    public void complete(Long taskId) {
        log.info("任务 [{}] 目标已达成，正在持久化完成状态...", taskId);

        // 1. Persistence First: 先更新 DB，确保状态落地
        // 必须在停止 Future 之前执行，否则当前线程被中断会导致 JDBC 抛出 InterruptedException/SQLException
        BuffScanTask update = new BuffScanTask();
        update.setId(taskId);
        update.setStatus(TaskStatusEnum.COMPLETED.getCode());
        update.setFinishTime(java.time.LocalDateTime.now());
        buffScanTaskMapper.updateById(update);

        // 2. Clean Up: 清理内存资源
        taskCallbacks.remove(taskId); // 移除回调
        stopWithoutRemovingCallback(taskId); // 停止调度

        log.info("任务 [{}] 已完成并停止调度", taskId);
    }

    private void updateTaskStatus(Long taskId, TaskStatusEnum status) {
        BuffScanTask update = new BuffScanTask();
        update.setId(taskId);
        update.setStatus(status.getCode());
        buffScanTaskMapper.updateById(update);
    }

    /**
     * C5 任务执行体 (支持中断响应)
     */
    private class C5TaskRunner implements Runnable {
        private final BuffScanTask task;
        private final Consumer<BuffScanTask> businessLogic;

        public C5TaskRunner(BuffScanTask task, Consumer<BuffScanTask> businessLogic) {
            this.task = task;
            this.businessLogic = businessLogic;
        }

        @Override
        public void run() {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            try {
                businessLogic.accept(task);
            } catch (Exception e) {
                if (e instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                    log.warn("任务 [{}] 执行被中断", task.getId());
                    Thread.currentThread().interrupt();
                } else {
                    log.error("任务 [{}] 执行异常", task.getId(), e);
                    updateLastError(task.getId(), e.getMessage());
                }
            }
        }
    }

    private void updateLastError(Long taskId, String errorMsg) {
        BuffScanTask update = new BuffScanTask();
        update.setId(taskId);
        update.setLastError(StrUtil.maxLength(errorMsg, 500));
        buffScanTaskMapper.updateById(update);
    }
}
