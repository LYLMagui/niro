package com.niro.web.jobhandler;

import com.niro.web.service.BuffScanTaskService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 分类采集与任务自愈 XXL-JOB 任务处理器
 * 负责监控数据库中运行中的任务是否在 Redis 队列中活跃，若丢失则自动恢复
 *
 * @author niro
 * @since 2026-02-10
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryTaskJobHandler {

    private final BuffScanTaskService buffScanTaskService;

    /**
     * 任务自愈与分片健康检查
     * 建议调度配置：每 1 分钟执行一次（Cron: 0 * * * * ?）
     */
    @XxlJob("categoryTaskAutoDetectJobHandler")
    public void categoryTaskAutoDetectJobHandler() {
        XxlJobHelper.log("🔍 [CategoryTaskJobHandler] 开始执行任务自愈与分片健康检查...");
        log.info("🔍 [CategoryTaskJobHandler] 开始执行任务自愈与分片健康检查...");

        try {
            buffScanTaskService.reEnqueueRunningTasks();
            XxlJobHelper.log("✅ 任务自愈检查执行完成");
            log.info("✅ [CategoryTaskJobHandler] 任务自愈与分片健康检查完成");
        } catch (Exception e) {
            XxlJobHelper.log("❌ 任务自愈检查异常: " + e.getMessage());
            log.error("❌ [CategoryTaskJobHandler] 任务自愈检查异常", e);
            XxlJobHelper.handleFail("任务自愈检查异常: " + e.getMessage());
        }
    }
}
