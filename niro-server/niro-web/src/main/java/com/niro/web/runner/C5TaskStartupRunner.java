package com.niro.web.runner;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.niro.web.entity.BuffScanTask;
import com.niro.web.enums.PlatformEnum;
import com.niro.web.enums.TaskStatusEnum;
import com.niro.web.service.BuffScanTaskService;
import com.niro.web.service.strategy.impl.C5TradeStrategyImpl;

import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * C5任务启动运行器
 * 系统启动时恢复运行中的任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class C5TaskStartupRunner implements ApplicationRunner {

    private final BuffScanTaskService buffScanTaskService;
    private final C5TradeStrategyImpl c5TradeStrategy;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("C5TaskStartupRunner starting...");
        
        // 查询所有状态为RUNNING或SCHEDULED的C5任务
        List<BuffScanTask> runningTasks = buffScanTaskService.lambdaQuery()
                .eq(BuffScanTask::getPlatform, PlatformEnum.C5.getCode())
                .in(BuffScanTask::getStatus, TaskStatusEnum.RUNNING.getCode(), TaskStatusEnum.SCHEDULED.getCode())
                .list();

        if (CollUtil.isEmpty(runningTasks)) {
            log.info("No running C5 tasks found.");
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
