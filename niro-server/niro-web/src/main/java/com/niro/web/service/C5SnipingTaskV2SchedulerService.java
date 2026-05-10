package com.niro.web.service;

import com.niro.web.entity.C5SnipingTaskV2;

/**
 * C5 扫货 2.0 调度服务。
 * <p>
 * 负责服务启动恢复 RUNNING 任务，以及启动任务本地持续运行循环。
 * </p>
 */
public interface C5SnipingTaskV2SchedulerService {

    /**
     * 恢复 RUNNING 任务的本地循环。
     */
    void recoverTasksOnStartup();

    /**
     * 兜底扫描并启动 RUNNING 任务。
     */
    void scheduleReadyTasks();

    /**
     * 启动任务本地运行循环。
     *
     * @param task 任务定义
     */
    void startTaskAsync(C5SnipingTaskV2 task);
}
