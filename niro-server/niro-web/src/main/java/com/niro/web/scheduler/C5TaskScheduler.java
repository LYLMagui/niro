package com.niro.web.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import com.niro.core.util.Assert;
import com.niro.web.entity.BuffScanTask;
import com.niro.web.enums.TaskStatusEnum;
import com.niro.web.mapper.BuffScanTaskMapper;
import com.niro.web.service.UserBuffSettingsService;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * C5 任务调度器
 * <p>
 * 负责 C5 平台任务的调度管理，支持 Cron 表达式和固定间隔执行。
 * 使用虚拟线程执行实际业务逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class C5TaskScheduler {

    private final TaskScheduler taskScheduler;
    private final BuffScanTaskMapper buffScanTaskMapper;
    private final UserBuffSettingsService userBuffSettingsService;

    /**
     * 任务存储容器: Map<TaskId, ScheduledFuture>
     */
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

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

        // 1. 如果任务已在运行，先停止
        if (scheduledTasks.containsKey(taskId)) {
            stop(taskId);
        }

        log.info("开始调度 C5 任务: {} (ID: {})", task.getName(), taskId);

        // 2. 构建任务执行体
        C5TaskRunner runner = new C5TaskRunner(task, businessLogic);

        // 3. 根据调度策略提交任务
        ScheduledFuture<?> future;
        if (StrUtil.isNotBlank(task.getCronExpression())) {
            // Cron 调度
            future = taskScheduler.schedule(runner, new CronTrigger(task.getCronExpression()));
            log.info("任务 [{}] 已注册 Cron 调度: {}", taskId, task.getCronExpression());
        } else {
            // 固定间隔调度 (默认 1 秒或从任务配置读取)
            int intervalSeconds = task.getScanInterval() != null && task.getScanInterval() > 0 
                    ? task.getScanInterval() 
                    : 1; // 默认为 1 秒
            
            // 使用 scheduleWithFixedDelay 确保上一次执行完再等 N 秒，避免堆积
            future = taskScheduler.scheduleWithFixedDelay(runner, Duration.ofSeconds(intervalSeconds));
            log.info("任务 [{}] 已注册固定间隔调度: {} 秒", taskId, intervalSeconds);
        }

        // 4. 存入 Map
        scheduledTasks.put(taskId, future);

        // 5. 更新状态为运行中
        BuffScanTask update = new BuffScanTask();
        update.setId(taskId);
        update.setStatus(TaskStatusEnum.RUNNING.getCode());
        buffScanTaskMapper.updateById(update);

        // 6. 处理自动停止 (Duration)
        if (task.getDurationMinutes() != null && task.getDurationMinutes() > 0) {
            Date endTime = Date.from(Instant.now().plus(Duration.ofMinutes(task.getDurationMinutes())));
            taskScheduler.schedule(() -> {
                log.info("任务 [{}] 此时已达运行时长限制，自动停止", taskId);
                stop(taskId);
            }, endTime);
        }
    }

    /**
     * 停止任务
     *
     * @param taskId 任务ID
     */
    public void stop(Long taskId) {
        stopWithStatus(taskId, TaskStatusEnum.STOPPED);
    }

    /**
     * 完成任务
     *
     * @param taskId 任务ID
     */
    public void complete(Long taskId) {
        stopWithStatus(taskId, TaskStatusEnum.COMPLETED);
    }

    private void stopWithStatus(Long taskId, TaskStatusEnum status) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null) {
            future.cancel(true); // mayInterruptIfRunning = true
            log.info("任务 [{}] 停止调度, 状态: {}", taskId, status.getDescription());

            // 更新数据库状态
            BuffScanTask update = new BuffScanTask();
            update.setId(taskId);
            update.setStatus(status.getCode());
            buffScanTaskMapper.updateById(update);
        }
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
                }
            }
        }
    }
}
