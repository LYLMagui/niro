package com.niro.web.service;

import java.util.List;

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
     *
     * @param param 参数
     */
    void saveTask(BuffScanTaskParam param);

    /**
     * 更新任务
     *
     * @param param 参数
     */
    void updateTask(BuffScanTaskParam param);

    /**
     * 更新任务状态
     *
     * @param id     任务ID
     * @param status 状态
     */
    void updateStatus(Long id, Integer status);

    /**
     * Python 端任务状态回调
     *
     * @param task 任务信息
     */
    void taskCallback(BuffScanTask task);

    /**
     * 同步任务进度
     * 根据订单表统计实际成功数量，并更新任务进度。若达到购买目标则自动停止任务。
     *
     * @param taskId 任务ID
     */
    void syncTaskProgress(Long taskId);

    /**
     * 分页查询任务
     *
     * @param param 查询参数
     * @return 分页结果
     */
    Page<BuffScanTaskDTO> pageTask(TaskQueryParam param);

    /**
     * 删除任务
     *
     * @param id 任务ID
     */
    void deleteTask(Long id);

    /**
     * 同步分类商品数据 (创建一次性同步任务)
     *
     * @param categoryId 分类ID
     */
    void syncCategoryGoods(Long categoryId);

    /**
     * 重新推送所有运行中的任务到队列 (自愈机制)
     */
    void reEnqueueRunningTasks();

    /**
     * 获取所有下单模式的任务列表
     *
     * @param cs2GoodsId CS2商品ID(可选，用于过滤相同商品的下单任务)
     * @return 任务列表
     */
    List<BuffScanTask> listTradeTasks(Long cs2GoodsId);
}
