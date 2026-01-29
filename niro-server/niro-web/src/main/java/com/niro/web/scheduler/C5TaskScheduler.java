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
     * 负责执行具体的扫描/交易逻辑 (Interval/FixedRate)
     */
    private final Map<Long, ScheduledFuture<?>> runningFutures = new ConcurrentHashMap<>();

    /**
     * 启动任务
     *
     * @param task          任务实体
     * @param businessLogic 业务逻辑回调
     */
    public void start(BuffScanTask task, Consumer<BuffScanTask> businessLogic) {
        Assert.notNull(task, "任务不能为空");
        Assert.notNull(businessLogic, "业务逻辑回调不能为空");
        Long taskId = task.getId();

        // 1. 先彻底停止旧任务，防止状态不一致
        stop(taskId);

        log.info("开始调度 C5 任务: {} (ID: {})", task.getName(), taskId);

        // 2. 判断调度模式
        if (StrUtil.isNotBlank(task.getCronExpression())) {
            // --- Cron 模式 (Supervisor) ---
            try {
                ScheduledFuture<?> future = taskScheduler.schedule(
                        () -> startSession(task, businessLogic),
                        new CronTrigger(task.getCronExpression())
                );
                cronFutures.put(taskId, future);
                log.info("任务 [{}] 已注册 Cron 调度: {}", taskId, task.getCronExpression());

                // 初始状态置为 SCHEDULED (等待 Cron 触发)
                updateTaskStatus(taskId, TaskStatusEnum.SCHEDULED);
            } catch (Exception e) {
                log.error("Cron 表达式非法: {}", task.getCronExpression(), e);
                updateTaskStatus(taskId, TaskStatusEnum.ERROR);
            }
        } else {
            // --- 直连模式 ---
            // 无 Cron，直接启动 Session
            startSession(task, businessLogic);
        }
    }

    /**
     * 启动一次运行会话 (Worker)
     * 被 Cron 触发或直接调用
     */
    private void startSession(BuffScanTask task, Consumer<BuffScanTask> businessLogic) {
        Long taskId = task.getId();

        // --- 入口守卫：校验数据库最新状态 ---
        BuffScanTask dbTask = buffScanTaskMapper.selectById(taskId);
        if (dbTask == null || (dbTask.getStatus() != TaskStatusEnum.RUNNING.getCode() 
                && dbTask.getStatus() != TaskStatusEnum.SCHEDULED.getCode())) {
            log.info("任务 [{}] 已处于非调度状态 ({})，忽略本次会话启动", taskId, dbTask != null ? dbTask.getStatus() : "NULL");
            // 清理可能残余的句柄
            stop(taskId);
            return;
        }

        log.info("任务 [{}] 会话启动... (最新扫描间隔: {}s)", taskId, dbTask.getScanInterval());

        // 清理旧的运行句柄 (如果 Cron 触发频率高于 Duration，会发生这种情况)
        ScheduledFuture<?> oldFuture = runningFutures.remove(taskId);
        if (oldFuture != null) {
            oldFuture.cancel(true);
        }

        // 构建 Runner (必须使用最新的 dbTask 配置)
        C5TaskRunner runner = new C5TaskRunner(dbTask, businessLogic);
        ScheduledFuture<?> future;

        // 注册 Interval 调度
        if (dbTask.getScanInterval() != null && dbTask.getScanInterval() > 0) {
            // Fixed Rate
            future = taskScheduler.scheduleAtFixedRate(runner, Duration.ofSeconds(dbTask.getScanInterval()));
            log.info("任务 [{}] 会话执行策略: FixedRate {}s", taskId, dbTask.getScanInterval());
        } else if (dbTask.getScanIntervalMin() != null && dbTask.getScanIntervalMax() != null) {
            // Random Range
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
            // Default
            future = taskScheduler.scheduleAtFixedRate(runner, Duration.ofSeconds(1));
            log.info("任务 [{}] 会话执行策略: Default 1s", taskId);
        }

        runningFutures.put(taskId, future);
        updateTaskStatus(taskId, TaskStatusEnum.RUNNING);

        // 处理 Duration (自动暂停)
        if (dbTask.getDurationMinutes() != null && dbTask.getDurationMinutes() > 0) {
            Instant endTime = Instant.now().plus(Duration.ofMinutes(dbTask.getDurationMinutes()));
            final ScheduledFuture<?> sessionFuture = future;
            taskScheduler.schedule(() -> {
                log.info("任务 [{}] 运行时长已达 ({} min)，自动暂停会话", taskId, dbTask.getDurationMinutes());
                stopSession(taskId, sessionFuture);
            }, endTime);
        }
    }

    /**
     * 停止当前会话 (Worker Stop)
     * 如果有 Cron，回退到 SCHEDULED；否则 STOPPED
     */
    private void stopSession(Long taskId, ScheduledFuture<?> sessionFuture) {
        // 乐观移除：仅当 Map 中的 Future 与当前会话一致时才移除
        // 防止误杀新启动的会话 (Race Condition Fix)
        if (runningFutures.remove(taskId, sessionFuture)) {
            sessionFuture.cancel(true);

            if (cronFutures.containsKey(taskId)) {
                // 还有 Cron 调度，说明是暂时休息
                log.info("任务 [{}] 会话结束，进入休眠 (SCHEDULED)", taskId);
                updateTaskStatus(taskId, TaskStatusEnum.SCHEDULED);
            } else {
                // 没有 Cron，说明是彻底停止
                log.info("任务 [{}] 会话结束，彻底停止 (STOPPED)", taskId);
                updateTaskStatus(taskId, TaskStatusEnum.STOPPED);
            }
        }
    }

    /**
     * 彻底停止任务 (Supervisor + Worker Stop)
     */
    public void stop(Long taskId) {
        // 1. 取消 Cron
        ScheduledFuture<?> cron = cronFutures.remove(taskId);
        if (cron != null) {
            cron.cancel(true);
        }

        // 2. 取消 Running
        ScheduledFuture<?> running = runningFutures.remove(taskId);
        if (running != null) {
            running.cancel(true);
        }

        log.info("任务 [{}] 已停止调度", taskId);
        updateTaskStatus(taskId, TaskStatusEnum.STOPPED);
    }

    /**
     * 完成任务
     */
    public void complete(Long taskId) {
        // 逻辑同 stop，但状态为 COMPLETED
        ScheduledFuture<?> cron = cronFutures.remove(taskId);
        if (cron != null) cron.cancel(true);

        ScheduledFuture<?> running = runningFutures.remove(taskId);
        if (running != null) running.cancel(true);

        log.info("任务 [{}] 已完成", taskId);
        updateTaskStatus(taskId, TaskStatusEnum.COMPLETED);
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
                // 执行注入的业务逻辑
                businessLogic.accept(task);
            } catch (Exception e) {
                // 如果是中断异常，则不打印错误堆栈
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
        update.setLastError(StrUtil.maxLength(errorMsg, 500)); // 限制长度
        buffScanTaskMapper.updateById(update);
    }
}
