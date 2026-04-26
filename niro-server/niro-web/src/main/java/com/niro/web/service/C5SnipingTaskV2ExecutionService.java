package com.niro.web.service;

import com.niro.web.entity.C5SnipingTaskRunV2;
import com.niro.web.entity.C5SnipingTaskV2;
import com.niro.web.service.impl.C5SnipingTaskV2ExecutionResult;

/**
 * C5 扫货 2.0 执行服务。
 * <p>
 * 负责单轮扫描、命中写入、幂等下单尝试与停止条件判断。
 * </p>
 */
public interface C5SnipingTaskV2ExecutionService {

    /**
     * 执行一轮任务扫描与下单尝试。
     *
     * @param task 任务定义
     * @param run 运行实例
     * @return 单轮执行结果
     */
    C5SnipingTaskV2ExecutionResult executeOneCycle(C5SnipingTaskV2 task, C5SnipingTaskRunV2 run);
}
