package com.niro.web.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.dto.BuffScanTaskDTO;
import com.niro.web.dto.param.BuffScanTaskParam;
import com.niro.web.dto.param.TaskQueryParam;
import com.niro.web.entity.BuffGoods;
import com.niro.web.entity.BuffScanTask;
import com.niro.web.mapper.BuffScanTaskMapper;
import com.niro.web.service.BuffGoodsService;
import com.niro.web.service.BuffScanTaskService;
import com.niro.core.util.Assert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 扫货任务服务实现类
 *
 * @author liyl
 * @since 2025-12-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuffScanTaskServiceImpl extends ServiceImpl<BuffScanTaskMapper, BuffScanTask> implements BuffScanTaskService {

    private final BuffGoodsService buffGoodsService;

    @Override
    public void saveTask(BuffScanTaskParam param) {
        // 校验商品是否存在
        BuffGoods goods = buffGoodsService.lambdaQuery().eq(BuffGoods::getGoodsId, param.getGoodsId()).one();
        Assert.validateNull(goods, "商品不存在");

        BuffScanTask task = BeanUtil.copyProperties(param, BuffScanTask.class);
        task.setName(goods.getName()); // 默认任务名为商品名
        task.setStatus(0); // 默认停止
        task.setSuccessCount(0);
        // 获取当前登录用户ID
        task.setUserId(StpUtil.getLoginIdAsLong());
        
        this.save(task);
    }

    @Override
    public void updateTask(BuffScanTaskParam param) {
        Assert.validateNull(param.getId(), "任务ID不能为空");
        BuffScanTask task = this.getById(param.getId());
        Assert.validateNull(task, "任务不存在");

        // 仅允许修改配置字段，不允许修改 goodsId
        task.setMaxPrice(param.getMaxPrice());
        task.setMinPaintwear(param.getMinPaintwear());
        task.setMaxPaintwear(param.getMaxPaintwear());
        task.setBuyCount(param.getBuyCount());
        
        this.updateById(task);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        BuffScanTask task = this.getById(id);
        Assert.validateNull(task, "任务不存在");
        task.setStatus(status);
        this.updateById(task);
    }

    @Override
    public Page<BuffScanTaskDTO> pageTask(TaskQueryParam param) {
        Page<BuffScanTask> page = new Page<>(param.getPage(), param.getPageSize());
        
        // 查询任务
        Page<BuffScanTask> taskPage = this.lambdaQuery()
                .eq(param.getStatus() != null, BuffScanTask::getStatus, param.getStatus())
                .like(StrUtil.isNotBlank(param.getKeyword()), BuffScanTask::getName, param.getKeyword())
                .orderByDesc(BuffScanTask::getCreateTime)
                .page(page);
                
        if (CollUtil.isEmpty(taskPage.getRecords())) {
            return new Page<>(param.getPage(), param.getPageSize());
        }

        // 转换为DTO
        List<BuffScanTaskDTO> dtoList = BeanUtil.copyToList(taskPage.getRecords(), BuffScanTaskDTO.class);

        // 填充商品信息 (图标等)
        Set<Long> goodsIds = dtoList.stream().map(BuffScanTaskDTO::getGoodsId).collect(Collectors.toSet());
        if (CollUtil.isNotEmpty(goodsIds)) {
            List<BuffGoods> goodsList = buffGoodsService.lambdaQuery()
                    .in(BuffGoods::getGoodsId, goodsIds)
                    .list();
            Map<Long, BuffGoods> goodsMap = goodsList.stream()
                    .collect(Collectors.toMap(BuffGoods::getGoodsId, g -> g));
            
            for (BuffScanTaskDTO dto : dtoList) {
                BuffGoods goods = goodsMap.get(dto.getGoodsId());
                if (goods != null) {
                    dto.setGoodsName(goods.getName());
                    dto.setGoodsIconUrl(goods.getIconUrl());
                }
            }
        }

        Page<BuffScanTaskDTO> resultPage = new Page<>();
        resultPage.setRecords(dtoList);
        resultPage.setTotal(taskPage.getTotal());
        resultPage.setCurrent(taskPage.getCurrent());
        resultPage.setSize(taskPage.getSize());
        resultPage.setPages(taskPage.getPages());
        
        return resultPage;
    }
}
