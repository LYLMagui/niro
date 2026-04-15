package com.niro.web.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.dto.BuffGoodsDTO;
import com.niro.web.dto.BuffGoodsSimpleDTO;
import com.niro.web.dto.param.GoodsQueryParam;
import com.niro.web.entity.BuffGoods;
import com.niro.web.entity.BuffGoodsCategory;
import com.niro.web.mapper.BuffGoodsMapper;
import com.niro.web.service.BuffGoodsCategoryService;
import com.niro.web.service.BuffGoodsService;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;

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
                .select(BuffGoods::getId, BuffGoods::getGoodsId, BuffGoods::getName, BuffGoods::getCategoryId)
                .like(StrUtil.isNotBlank(keyword), BuffGoods::getName, keyword)
                .last("LIMIT 50") // 限制返回条数，防止全量拉取
                .list();

        if (CollUtil.isEmpty(goodsList)) {
            return new ArrayList<>();
        }

        // 批量查询分类信息
        List<Long> categoryIds = goodsList.stream()
                .map(BuffGoods::getCategoryId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();

        Map<Long, String> parentCategoryMap = new java.util.HashMap<>();
        if (CollUtil.isNotEmpty(categoryIds)) {
            List<BuffGoodsCategory> categories = buffGoodsCategoryService.listByIds(categoryIds);

            // 收集所有父级ID
            List<Long> parentIds = categories.stream()
                    .map(BuffGoodsCategory::getParentId)
                    .filter(id -> id != null && id > 0)
                    .distinct()
                    .toList();

            Map<Long, String> parentNameMap = new java.util.HashMap<>();
            if (CollUtil.isNotEmpty(parentIds)) {
                List<BuffGoodsCategory> parents = buffGoodsCategoryService.listByIds(parentIds);
                for (BuffGoodsCategory parent : parents) {
                    parentNameMap.put(parent.getId(), parent.getName());
                }
            }

            // 建立 categoryId -> parentCategoryName 映射
            for (BuffGoodsCategory category : categories) {
                String parentName = parentNameMap.get(category.getParentId());
                if (parentName != null) {
                    parentCategoryMap.put(category.getId(), parentName);
                }
            }
        }

        List<BuffGoodsSimpleDTO> dtoList = BeanUtil.copyToList(goodsList, BuffGoodsSimpleDTO.class);
        for (int i = 0; i < dtoList.size(); i++) {
            BuffGoods goods = goodsList.get(i);
            BuffGoodsSimpleDTO dto = dtoList.get(i);
            if (goods.getCategoryId() != null) {
                dto.setParentCategoryName(parentCategoryMap.get(goods.getCategoryId()));
            }
        }

        return dtoList;
    }
}