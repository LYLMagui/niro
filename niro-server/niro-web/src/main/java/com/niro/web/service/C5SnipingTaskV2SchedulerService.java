package com.niro.web.service;

/**
 * C5 扫货 2.0 调度服务。
 * <p>
 * 负责服务启动恢复、按账号并发上限选择 READY 任务，以及启动任务运行循环。
 * </p>
 */
public interface C5SnipingTaskV2SchedulerService {

    /**
     * 恢复 READY/RUNNING 任务到安全可调度状态。
     */
    void recoverTasksOnStartup();

    /**
     * 扫描并调度 READY 任务。
     */
    void scheduleReadyTasks();
}
