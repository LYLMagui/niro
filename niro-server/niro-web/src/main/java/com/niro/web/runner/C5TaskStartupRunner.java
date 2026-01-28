package com.niro.web.runner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.niro.web.entity.BuffScanTask;
import com.niro.web.enums.PlatformEnum;
import com.niro.web.enums.TaskStatusEnum;
import com.niro.web.service.BuffScanTaskService;
import com.niro.web.service.strategy.impl.C5TradeStrategyImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * C5 任务启动自愈
 * 应用启动时，自动重新注册 C5 平台的运行中任务
 *
 * @author niro
 * @since 2026-01-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class C5TaskStartupRunner implements ApplicationRunner {

    private final BuffScanTaskService buffScanTaskService;
    private final C5TradeStrategyImpl c5TradeStrategy;

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始扫描 C5 平台运行中任务...");

        List<BuffScanTask> runningTasks = buffScanTaskService.list(
                new LambdaQueryWrapper<BuffScanTask>()
                        .eq(BuffScanTask::getPlatform, PlatformEnum.C5.name())
                        .eq(BuffScanTask::getStatus, TaskStatusEnum.RUNNING.getCode())
        );

        if (runningTasks.isEmpty()) {
            log.info("C5 平台无运行中任务需要恢复");
            return;
        }

        log.info("发现 {} 个 C5 任务需要恢复，开始重新注册...", runningTasks.size());

        for (BuffScanTask task : runningTasks) {
            try {
                // 直接调用策略入口，内部会调用 C5TaskScheduler.start
                c5TradeStrategy.handleTask(task);
                log.info("任务 [{}] {} 恢复成功", task.getId(), task.getName());
            } catch (Exception e) {
                log.error("任务 [{}] 恢复失败", task.getId(), e);
            }
        }
    }
}
