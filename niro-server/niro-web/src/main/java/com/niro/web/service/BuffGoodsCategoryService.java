package com.niro.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.niro.web.dto.BuffGoodsCategoryDTO;
import com.niro.web.entity.BuffGoodsCategory;

import java.util.List;

/**
 * 商品分类服务接口
 *
 * @author liyl
 * @since 2025-12-23
 */
public interface BuffGoodsCategoryService extends IService<BuffGoodsCategory> {

    /**
     * 获取分类树
     * @return 分类树列表
     */
    List<BuffGoodsCategoryDTO> getCategoryTree();

    /**
     * 获取指定分类及其子分类的所有ID
     * @param categoryId 分类ID
     * @return ID列表
     */
    List<Long> getChildCategoryIds(Long categoryId);
}
