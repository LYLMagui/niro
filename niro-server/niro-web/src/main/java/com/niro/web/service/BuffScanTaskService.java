package com.niro.web.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.niro.web.dto.BuffScanTaskDTO;
import com.niro.web.dto.param.BuffScanTaskParam;
import com.niro.web.dto.param.TaskQueryParam;
import com.niro.web.entity.BuffScanTask;

/**
 * 扫货任务服务接口
 *
 * @author liyl
 * @since 2025-12-24
 */
public interface BuffScanTaskService extends IService<BuffScanTask> {

    /**
     * 新增任务
     * @param param 参数
     */
    void saveTask(BuffScanTaskParam param);

    /**
     * 更新任务
     * @param param 参数
     */
    void updateTask(BuffScanTaskParam param);

    /**
     * 更新任务状态
     * @param id 任务ID
     * @param status 状态
     */
    void updateStatus(Long id, Integer status);

    /**
     * 分页查询任务
     * @param param 查询参数
     * @return 分页结果
     */
    Page<BuffScanTaskDTO> pageTask(TaskQueryParam param);

    /**
     * 删除任务
     * @param id 任务ID
     */
    void deleteTask(Long id);
}
