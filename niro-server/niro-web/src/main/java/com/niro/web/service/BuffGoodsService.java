package com.niro.web.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.niro.web.dto.BuffGoodsDTO;
import com.niro.web.dto.BuffGoodsSimpleDTO;
import com.niro.web.dto.param.GoodsQueryParam;
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
     * @param param 查询参数
     * @return
     */
    Page<BuffGoodsDTO> queryGoodsPage(Page<BuffGoods> page, GoodsQueryParam param);

    /**
     * 获取商品简单列表 (仅ID和名称，支持模糊搜索，默认限制50条)
     * @param keyword 搜索关键词
     * @return
     */
    List<BuffGoodsSimpleDTO> getSimpleList(String keyword);
}