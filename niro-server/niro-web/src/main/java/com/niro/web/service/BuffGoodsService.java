package com.niro.web.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.niro.web.dto.BuffGoodsDTO;
import com.niro.web.entity.BuffGoods;

/**
 * <p>
 * 商品表 服务类
 * </p>
 *
 * @author liyl
 * @since 2025-12-22
 */
public interface BuffGoodsService extends IService<BuffGoods> {

    /**
     * 分页查询商品列表
     * @param page 分页参数
     * @return
     */
    Page<BuffGoodsDTO> queryGoodsPage(Page<BuffGoods> page);
}