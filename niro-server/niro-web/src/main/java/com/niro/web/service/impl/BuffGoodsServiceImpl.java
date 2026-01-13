package com.niro.web.service.impl;

import java.util.ArrayList;
import java.util.List;

import cn.hutool.core.collection.CollUtil;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.dto.BuffGoodsDTO;
/**
 * <p>
 * 商品表 服务实现类
 * </p>
 *
 * @author liyl
 * @since 2025-12-22
 */
import com.niro.web.dto.BuffGoodsSimpleDTO;
import com.niro.web.dto.param.GoodsQueryParam;
import com.niro.web.entity.BuffGoods;
import com.niro.web.mapper.BuffGoodsMapper;
import com.niro.web.service.BuffGoodsService;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;

// ...

import com.niro.web.service.BuffGoodsCategoryService;
import com.niro.web.mapper.BuffScanTaskMapper;
import com.niro.web.entity.BuffScanTask;
import com.niro.web.entity.BuffGoodsCategory;
import com.niro.web.enums.TaskTypeEnum;
import com.niro.core.constant.BuffConstant;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import com.niro.core.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class BuffGoodsServiceImpl extends ServiceImpl<BuffGoodsMapper, BuffGoods> implements BuffGoodsService {

    private final BuffGoodsCategoryService buffGoodsCategoryService;
    private final BuffScanTaskMapper buffScanTaskMapper;

    @Override
    public Page<BuffGoodsDTO> queryGoodsPage(Page<BuffGoods> page, GoodsQueryParam param) {
        // 处理分类过滤
        List<Long> categoryIds = null;
        if (param.getCategoryId() != null) {
            categoryIds = buffGoodsCategoryService.getChildCategoryIds(param.getCategoryId());
            if (CollUtil.isEmpty(categoryIds)) {
                // 如果分类不存在，或者没有ID，则返回空页（或者抛异常，这里选择返回空）
                // 为了简单，构建一个不匹配的条件，或者直接返回
                // 这里选择让查询查不到
                categoryIds = new ArrayList<>();
                categoryIds.add(-1L);
            }
        }

        // 执行分页查询 (使用 lambdaQuery 链式调用)
        Page<BuffGoods> goodsPage = this.lambdaQuery()
                .eq(param.getGoodsId() != null, BuffGoods::getGoodsId, param.getGoodsId())
                .eq(StrUtil.isNotBlank(param.getExterior()), BuffGoods::getExterior, param.getExterior())
                .in(CollUtil.isNotEmpty(categoryIds), BuffGoods::getCategoryId, categoryIds)
                .orderByDesc(BuffGoods::getUpdateTime)
                .page(page);
        // 转换为DTO对象
        List<BuffGoodsDTO> dtoList = BeanUtil.copyToList(goodsPage.getRecords(), BuffGoodsDTO.class);

        // 构造返回结果
        Page<BuffGoodsDTO> dtoPage = new Page<>();
        dtoPage.setRecords(dtoList);
        dtoPage.setCurrent(goodsPage.getCurrent());
        dtoPage.setSize(goodsPage.getSize());
        dtoPage.setTotal(goodsPage.getTotal());
        dtoPage.setPages(goodsPage.getPages());

        return dtoPage;
    }

    @Override
    public List<BuffGoodsSimpleDTO> getSimpleList(String keyword) {
        List<BuffGoods> goodsList = this.lambdaQuery()
                .select(BuffGoods::getGoodsId, BuffGoods::getName)
                .like(StrUtil.isNotBlank(keyword), BuffGoods::getName, keyword)
                .last("LIMIT 50") // 限制返回条数，防止全量拉取
                .list();
        return BeanUtil.copyToList(goodsList, BuffGoodsSimpleDTO.class);
                
    }

    @Override
    public void syncCategoryGoods(Long categoryId) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        // 权限校验：仅管理员可触发
        if (!BuffConstant.ADMIN_USER_ID.equals(currentUserId)) {
            throw new BusinessException("仅管理员可触发分类同步任务");
        }

        BuffGoodsCategory category = buffGoodsCategoryService.getById(categoryId);
        if (category == null) {
            throw new BusinessException("分类不存在");
        }

        // 检查是否已有该分类的同步任务正在运行
        boolean isRunning = buffScanTaskMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BuffScanTask>()
                        .eq(BuffScanTask::getTaskType, TaskTypeEnum.SYNC_CATEGORY_GOODS.getCode())
                        .eq(BuffScanTask::getGoodsId, categoryId)
                        .eq(BuffScanTask::getStatus, 4) // 执行中
        ) > 0;

        if (isRunning) {
            throw new BusinessException("该分类的同步任务正在执行中，请勿重复触发");
        }

        // 创建系统任务
        BuffScanTask task = new BuffScanTask();
        task.setName("同步分类-" + category.getName());
        task.setUserId(currentUserId);
        task.setGoodsId(categoryId); // 借用 goodsId 存放 categoryId
        task.setTaskType(TaskTypeEnum.SYNC_CATEGORY_GOODS.getCode());
        task.setStatus(1); // 待运行，由扫描器接管
        
        buffScanTaskMapper.insert(task);
    }
}