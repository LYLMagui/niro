package com.niro.web.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.core.constant.BuffConstant;
import com.niro.core.exception.BusinessException;
import com.niro.core.util.Assert;
import com.niro.web.dto.BuffScanTaskDTO;
import com.niro.web.dto.param.BuffScanTaskParam;
import com.niro.web.dto.param.TaskQueryParam;
import com.niro.web.entity.BuffGoods;
import com.niro.web.entity.BuffScanTask;
import com.niro.web.enums.TaskTypeEnum;
import com.niro.web.mapper.BuffScanTaskMapper;
import com.niro.web.service.BuffGoodsService;
import com.niro.web.service.BuffGoodsCategoryService;
import com.niro.web.service.BuffScanTaskService;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;

/**
 * 扫货任务服务实现类
 *
 * @author liyl
 * @since 2025-12-24
 */
@Service
@RequiredArgsConstructor
public class BuffScanTaskServiceImpl extends ServiceImpl<BuffScanTaskMapper, BuffScanTask> implements BuffScanTaskService {

    private final BuffGoodsService buffGoodsService;
    private final BuffGoodsCategoryService buffGoodsCategoryService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveTask(BuffScanTaskParam param) {
        validateParam(param);
        Long currentUserId = StpUtil.getLoginIdAsLong();
        BuffScanTask task = BeanUtil.copyProperties(param, BuffScanTask.class);

        if (TaskTypeEnum.isSystemTask(param.getTaskType())) {
            // 权限校验：仅管理员可创建系统任务
            if (!BuffConstant.ADMIN_USER_ID.equals(currentUserId)) {
                throw new BusinessException("权限不足：仅管理员可创建系统任务");
            }
            // 系统任务唯一性校验：禁止创建多个相同类型的系统任务
            long count = this.lambdaQuery()
                    .eq(BuffScanTask::getTaskType, param.getTaskType())
                    .count();
            if (count > 0) {
                throw new BusinessException("系统任务【" + TaskTypeEnum.getDescByCode(param.getTaskType()) + "】已存在，不可重复创建");
            }
            // 系统任务不需要关联商品，手动设置任务名
            task.setName(TaskTypeEnum.getDescByCode(param.getTaskType()));
        } else {
            // 校验商品是否存在
            BuffGoods goods = buffGoodsService.lambdaQuery()
                    .eq(BuffGoods::getGoodsId, param.getGoodsId())
                    .one();
            Assert.validateNull(goods, "商品不存在");
            // 默认任务名为商品名
            task.setName(goods.getName());
        }

        // 默认停止
        task.setStatus(0);
        task.setSuccessCount(0);
        task.setUserId(currentUserId);

        this.save(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTask(BuffScanTaskParam param) {
        Assert.validateNull(param.getId(), "任务ID不能为空");
        validateParam(param);
        Long currentUserId = StpUtil.getLoginIdAsLong();
        BuffScanTask task = this.getById(param.getId());
        Assert.validateNull(task, "任务不存在");

        // 权限校验：非管理员只能修改自己的任务
        if (!BuffConstant.ADMIN_USER_ID.equals(currentUserId) && !task.getUserId().equals(currentUserId)) {
            throw new BusinessException("权限不足：无法修改他人的任务");
        }

        // 仅允许修改配置字段，不允许修改 goodsId
        task.setMaxPrice(param.getMaxPrice());
        task.setMinPaintwear(param.getMinPaintwear());
        task.setMaxPaintwear(param.getMaxPaintwear());
        task.setBuyCount(param.getBuyCount());
        task.setCronExpression(param.getCronExpression());
        task.setDurationMinutes(param.getDurationMinutes());
        task.setScanInterval(param.getScanInterval());
        
        // 如果修改了任务类型，且改为系统任务，需要校验唯一性
        if (param.getTaskType() != null && !param.getTaskType().equals(task.getTaskType())) {
            if (TaskTypeEnum.isSystemTask(param.getTaskType())) {
                // 仅管理员可修改为系统任务
                if (!BuffConstant.ADMIN_USER_ID.equals(currentUserId)) {
                    throw new BusinessException("权限不足：仅管理员可操作系统任务");
                }
                long count = this.lambdaQuery()
                        .eq(BuffScanTask::getTaskType, param.getTaskType())
                        .ne(BuffScanTask::getId, task.getId())
                        .count();
                if (count > 0) {
                    throw new BusinessException("系统任务【" + TaskTypeEnum.getDescByCode(param.getTaskType()) + "】已存在，不可重复创建/修改");
                }
                task.setName(TaskTypeEnum.getDescByCode(param.getTaskType()));
            }
            task.setTaskType(param.getTaskType());
        }
        
        task.setMinProfit(param.getMinProfit());
        
        this.updateById(task);
    }

    private void validateParam(BuffScanTaskParam param) {
        if (param.getTaskType() == null) {
            throw new BusinessException("任务类型不能为空");
        }

        if (TaskTypeEnum.isSystemTask(param.getTaskType())) {
            // 系统任务不需要校验 goodsId、maxPrice 和 scanInterval 限制
            return;
        }

        // 普通任务校验
        // 如果设置了时间范围，则验证范围
        if (param.getScanIntervalMin() != null || param.getScanIntervalMax() != null) {
            if (param.getScanIntervalMin() != null && param.getScanIntervalMin() < 15) {
                throw new BusinessException("最小扫描间隔不能低于15秒");
            }
            if (param.getScanIntervalMax() != null && param.getScanIntervalMin() != null && param.getScanIntervalMax() < param.getScanIntervalMin()) {
                throw new BusinessException("最大扫描间隔不能小于最小扫描间隔");
            }
        } else if (param.getScanInterval() != null && param.getScanInterval() < 15) {
            // 如果只设置了固定间隔，则验证固定间隔
            throw new BusinessException("普通扫描任务的间隔不能低于15秒");
        }

        if (param.getGoodsId() == null) {
            throw new BusinessException("非系统任务下，商品ID不能为空");
        }

        if (TaskTypeEnum.SNIPING.getCode().equals(param.getTaskType())) {
            if (param.getMaxPrice() == null) {
                throw new BusinessException("炼金扫货模式下，最高价格不能为空");
            }
        } else if (TaskTypeEnum.FLIPPING.getCode().equals(param.getTaskType())) {
            if (param.getMinProfit() == null) {
                throw new BusinessException("站内倒卖模式下，最小预期利润不能为空");
            }
        }
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        BuffScanTask task = this.getById(id);
        Assert.validateNull(task, "任务不存在");

        // 权限校验：非管理员只能操作自己的任务
        if (!BuffConstant.ADMIN_USER_ID.equals(currentUserId) && !task.getUserId().equals(currentUserId)) {
            throw new BusinessException("权限不足：无法操作他人的任务");
        }

        task.setStatus(status);
        this.updateById(task);
    }

    @Override
    public Page<BuffScanTaskDTO> pageTask(TaskQueryParam param) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        Page<BuffScanTask> page = new Page<>(param.getPage(), param.getPageSize());
        
        // 查询任务：普通用户仅查看自己的任务，管理员查看所有
        Page<BuffScanTask> taskPage = this.lambdaQuery()
                .eq(!BuffConstant.ADMIN_USER_ID.equals(currentUserId), BuffScanTask::getUserId, currentUserId)
                .eq(param.getStatus() != null, BuffScanTask::getStatus, param.getStatus())
                .like(cn.hutool.core.util.StrUtil.isNotBlank(param.getKeyword()), BuffScanTask::getName, param.getKeyword())
                .orderByDesc(BuffScanTask::getCreateTime)
                .page(page);
        
        // 转换 DTO
        List<BuffScanTaskDTO> dtoList = BeanUtil.copyToList(taskPage.getRecords(), BuffScanTaskDTO.class);
        
        // 补充商品信息
        if (CollUtil.isNotEmpty(dtoList)) {
            Set<Long> goodsIds = dtoList.stream()
                    .map(BuffScanTaskDTO::getGoodsId)
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());
            
            if (CollUtil.isNotEmpty(goodsIds)) {
                List<BuffGoods> goodsList = buffGoodsService.lambdaQuery()
                        .in(BuffGoods::getGoodsId, goodsIds)
                        .list();
                Map<Long, BuffGoods> goodsMap = goodsList.stream()
                        .collect(Collectors.toMap(BuffGoods::getGoodsId, g -> g));
                
                dtoList.forEach(dto -> {
                    if (dto.getGoodsId() != null && goodsMap.containsKey(dto.getGoodsId())) {
                        BuffGoods g = goodsMap.get(dto.getGoodsId());
                        dto.setGoodsIconUrl(g.getIconUrl());
                        dto.setMarketHashName(g.getMarketHashName());
                    }
                });
            }
        }
        
        Page<BuffScanTaskDTO> resultPage = new Page<>(taskPage.getCurrent(), taskPage.getSize(), taskPage.getTotal());
        resultPage.setRecords(dtoList);
        return resultPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long id) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        BuffScanTask task = this.getById(id);
        Assert.validateNull(task, "任务不存在");

        // 权限校验：非管理员只能删除自己的任务
        if (!BuffConstant.ADMIN_USER_ID.equals(currentUserId) && !task.getUserId().equals(currentUserId)) {
            throw new BusinessException("权限不足：无法删除他人的任务");
        }

        if (TaskTypeEnum.isSystemTask(task.getTaskType())) {
            throw new BusinessException("系统任务【" + TaskTypeEnum.getDescByCode(task.getTaskType()) + "】禁止删除");
        }

        this.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncCategoryGoods(Long categoryId) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        // 权限校验：仅管理员可触发同步
        if (!BuffConstant.ADMIN_USER_ID.equals(currentUserId)) {
            throw new BusinessException("权限不足：仅管理员可触发同步任务");
        }

        // 校验分类是否存在
        com.niro.web.entity.BuffGoodsCategory category = buffGoodsCategoryService.getById(categoryId);
        Assert.validateNull(category, "分类不存在");

        // 检查是否已有该分类的同步任务
        BuffScanTask existingTask = this.lambdaQuery()
                .eq(BuffScanTask::getTaskType, TaskTypeEnum.SYNC_CATEGORY_GOODS.getCode())
                .eq(BuffScanTask::getGoodsId, categoryId)
                .one();

        if (existingTask != null) {
            // 如果任务存在，将其状态设置为运行中
            existingTask.setStatus(1);
            this.updateById(existingTask);
        } else {
            // 创建新任务
            BuffScanTask task = new BuffScanTask();
            task.setName("同步分类: " + category.getName());
            task.setTaskType(TaskTypeEnum.SYNC_CATEGORY_GOODS.getCode());
            task.setGoodsId(categoryId); // 使用 goodsId 存储 categoryId
            task.setUserId(currentUserId);
            task.setStatus(1); // 立即运行
            task.setSuccessCount(0);
            this.save(task);
        }
    }
}
