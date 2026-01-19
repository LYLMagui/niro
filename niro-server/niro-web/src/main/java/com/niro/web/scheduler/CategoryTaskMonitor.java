package com.niro.web.scheduler;

import com.niro.web.service.BuffScanTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 分类采集与任务自愈守护进程
 * 负责监控数据库中运行中的任务是否在 Redis 队列中活跃，若丢失则自动恢复
 * 
 * @author niro
 * @since 2026-01-18
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryTaskMonitor {

    private final BuffScanTaskService buffScanTaskService;

    /**
     * 每 1 分钟执行一次检查 (v2.7.2 缩短频率以提升任务自愈响应速度)
     */
    @Scheduled(fixedRate = 60000)
    public void checkTasks() {
        log.info("🔍 [CategoryTaskMonitor] 开始执行任务自愈与分片健康检查...");
        try {
            buffScanTaskService.reEnqueueRunningTasks();
        } catch (Exception e) {
            log.error("❌ [CategoryTaskMonitor] 任务自愈检查异常", e);
        }
        log.info("✅ [CategoryTaskMonitor] 任务自愈与分片健康检查完成");
    }
}
