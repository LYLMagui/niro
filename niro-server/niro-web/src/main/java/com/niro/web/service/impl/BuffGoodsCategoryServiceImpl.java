package com.niro.web.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.dto.BuffGoodsCategoryDTO;
import com.niro.web.entity.BuffGoodsCategory;
import com.niro.web.mapper.BuffGoodsCategoryMapper;
import com.niro.web.service.BuffGoodsCategoryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import java.util.Collections;

/**
 * 商品分类服务实现类
 *
 * @author liyl
 * @since 2025-12-23
 */
@Service
public class BuffGoodsCategoryServiceImpl extends ServiceImpl<BuffGoodsCategoryMapper, BuffGoodsCategory> implements BuffGoodsCategoryService {

    @Override
    public List<BuffGoodsCategoryDTO> getCategoryTree() {
        // 1. 查询所有分类
        List<BuffGoodsCategory> allCategories = this.list();
        if (CollUtil.isEmpty(allCategories)) {
            return new ArrayList<>();
        }

        // 2. 转换为 DTO
        List<BuffGoodsCategoryDTO> allDTOs = BeanUtil.copyToList(allCategories, BuffGoodsCategoryDTO.class);

        // 3. 构建树形结构
        // 3.1 找出所有一级分类 (parentId = 0 或 null)
        List<BuffGoodsCategoryDTO> rootNodes = allDTOs.stream()
                .filter(node -> node.getParentId() == null || node.getParentId() == 0)
                .collect(Collectors.toList());

        // 3.2 按 parentId 分组所有子节点 (排除 null 和 0)
        Map<Long, List<BuffGoodsCategoryDTO>> childrenMap = allDTOs.stream()
                .filter(node -> node.getParentId() != null && node.getParentId() != 0)
                .collect(Collectors.groupingBy(BuffGoodsCategoryDTO::getParentId));

        // 3.3 递归(或循环)填充子节点
        for (BuffGoodsCategoryDTO root : rootNodes) {
            fillChildren(root, childrenMap);
        }

        return rootNodes;
    }

    @Override
    public List<Long> getChildCategoryIds(Long categoryId) {
        if (categoryId == null) {
            return Collections.emptyList();
        }
        
        // 1. 查找当前分类
        BuffGoodsCategory category = this.getById(categoryId);
        if (category == null) {
            return Collections.emptyList();
        }
        
        List<Long> ids = new ArrayList<>();
        ids.add(categoryId);
        
        // 2. 如果是父分类(parentId=0 或 null)，查找其所有子分类
        if (category.getParentId() == null || category.getParentId() == 0) {
            List<BuffGoodsCategory> children = this.lambdaQuery()
                    .eq(BuffGoodsCategory::getParentId, categoryId)
                    .list();
            if (CollUtil.isNotEmpty(children)) {
                List<Long> childIds = children.stream()
                        .map(BuffGoodsCategory::getId)
                        .collect(Collectors.toList());
                ids.addAll(childIds);
            }
        }
        
        return ids;
    }

    private void fillChildren(BuffGoodsCategoryDTO parent, Map<Long, List<BuffGoodsCategoryDTO>> childrenMap) {
        List<BuffGoodsCategoryDTO> children = childrenMap.get(parent.getId());
        if (CollUtil.isNotEmpty(children)) {
            parent.setChildren(children);
            // 如果有多级，可以继续递归
            // for (BuffGoodsCategoryDTO child : children) {
            //     fillChildren(child, childrenMap);
            // }
        }
    }
}
