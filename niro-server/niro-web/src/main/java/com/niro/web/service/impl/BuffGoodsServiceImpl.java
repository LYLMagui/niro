package com.niro.web.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.dto.BuffGoodsDTO;
import com.niro.web.dto.param.GoodsQueryParam;
import com.niro.web.entity.BuffGoods;
import com.niro.web.mapper.BuffGoodsMapper;
import com.niro.web.service.BuffGoodsService;

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
import com.niro.web.dto.BuffGoodsSimpleDTO;

// ...

@Service
public class BuffGoodsServiceImpl extends ServiceImpl<BuffGoodsMapper, BuffGoods> implements BuffGoodsService {

    @Override
    public Page<BuffGoodsDTO> queryGoodsPage(Page<BuffGoods> page, GoodsQueryParam param) {
        // 执行分页查询 (使用 lambdaQuery 链式调用)
        Page<BuffGoods> goodsPage = this.lambdaQuery()
                .eq(param.getGoodsId() != null, BuffGoods::getGoodsId, param.getGoodsId())
                .eq(StrUtil.isNotBlank(param.getExterior()), BuffGoods::getExterior, param.getExterior())
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
        return this.lambdaQuery()
                .select(BuffGoods::getGoodsId, BuffGoods::getName)
                .like(StrUtil.isNotBlank(keyword), BuffGoods::getName, keyword)
                .last("LIMIT 50") // 限制返回条数，防止全量拉取
                .list()
                .stream()
                .map(goods -> {
                    BuffGoodsSimpleDTO dto = new BuffGoodsSimpleDTO();
                    dto.setGoodsId(goods.getGoodsId());
                    dto.setName(goods.getName());
                    return dto;
                })
                .collect(Collectors.toList());
    }
}