package com.niro.web.scheduler;

import com.niro.web.service.BuffScanTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 分类采集与任务自愈守护进程
 * 负责监控数据库中运行中的任务是否在 Redis 队列中活跃，若丢失则自动恢复
 * <p>
 * 已迁移至 XXL-JOB：categoryTaskAutoDetectJobHandler
 * 保留此类仅作为代码参考，不再使用 @Scheduled 定时执行
 * </p>
 *
 * @author niro
 * @since 2026-01-18
 * @deprecated 已迁移到 XXL-JOB，使用 CategoryTaskJobHandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Deprecated
public class CategoryTaskMonitor {

    private final BuffScanTaskService buffScanTaskService;

    /**
     * 任务自愈检查方法
     * 原 @Scheduled(fixedRate = 60000) 已移除，迁移至 XXL-JOB
     * Cron 表达式：0 * * * * ?
     */
    public void checkTasks() {
        log.info("[CategoryTaskMonitor] 开始执行任务自愈与分片健康检查...");
        try {
            buffScanTaskService.reEnqueueRunningTasks();
        } catch (Exception e) {
            log.error("[CategoryTaskMonitor] 任务自愈检查异常", e);
        }
        log.info("[CategoryTaskMonitor] 任务自愈与分片健康检查完成");
    }
}
