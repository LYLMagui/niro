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
import lombok.RequiredArgsConstructor;

// ...

@Service
@RequiredArgsConstructor
public class BuffGoodsServiceImpl extends ServiceImpl<BuffGoodsMapper, BuffGoods> implements BuffGoodsService {

    private final BuffGoodsCategoryService buffGoodsCategoryService;

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
}