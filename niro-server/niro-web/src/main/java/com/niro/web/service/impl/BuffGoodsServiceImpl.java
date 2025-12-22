package com.niro.web.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.dto.BuffGoodsDTO;
import com.niro.web.entity.BuffGoods;
import com.niro.web.mapper.BuffGoodsMapper;
import com.niro.web.service.BuffGoodsService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 商品表 服务实现类
 * </p>
 *
 * @author liyl
 * @since 2025-12-22
 */
@Service
public class BuffGoodsServiceImpl extends ServiceImpl<BuffGoodsMapper, BuffGoods> implements BuffGoodsService {

    @Override
    public Page<BuffGoodsDTO> queryGoodsPage(Page<BuffGoods> page) {
        // 执行分页查询
        Page<BuffGoods> goodsPage = this.page(page);

        // 转换为DTO对象
        List<BuffGoodsDTO> dtoList = goodsPage.getRecords().stream().map(goods -> {
            BuffGoodsDTO dto = new BuffGoodsDTO();
            BeanUtils.copyProperties(goods, dto);
            return dto;
        }).collect(Collectors.toList());

        // 构造返回结果
        Page<BuffGoodsDTO> dtoPage = new Page<>();
        dtoPage.setRecords(dtoList);
        dtoPage.setCurrent(goodsPage.getCurrent());
        dtoPage.setSize(goodsPage.getSize());
        dtoPage.setTotal(goodsPage.getTotal());
        dtoPage.setPages(goodsPage.getPages());

        return dtoPage;
    }
}