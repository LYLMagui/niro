package com.niro.web.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.dto.Cs2GoodsOptionDTO;
import com.niro.web.entity.Cs2Goods;
import com.niro.web.manager.Cs2GoodsMapperManager;
import com.niro.web.mapper.Cs2GoodsMapper;
import com.niro.web.service.Cs2GoodsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CS2 商品服务实现
 */
@Service
@RequiredArgsConstructor
public class Cs2GoodsServiceImpl extends ServiceImpl<Cs2GoodsMapper, Cs2Goods> implements Cs2GoodsService {

    private final Cs2GoodsMapperManager cs2GoodsMapperManager;

    @Override
    public List<Cs2GoodsOptionDTO> listUnboxCaseOptions(String keyword) {
        return BeanUtil.copyToList(cs2GoodsMapperManager.listUnboxCaseOptions(keyword), Cs2GoodsOptionDTO.class);
    }

    @Override
    public List<Cs2GoodsOptionDTO> listUnboxItemOptions(String keyword) {
        return cs2GoodsMapperManager.listUnboxItemOptions(keyword).stream()
                .filter(item -> StrUtil.isNotBlank(item.getBaseDisplayName()))
                .collect(Collectors.toMap(
                        Cs2Goods::getBaseDisplayName,
                        Function.identity(),
                        (current, ignored) -> current,
                        LinkedHashMap::new))
                .values()
                .stream()
                .limit(50)
                .map(this::toBaseGoodsOption)
                .toList();
    }

    @Override
    public List<Cs2GoodsOptionDTO> listC5TaskOptions(String keyword) {
        return BeanUtil.copyToList(cs2GoodsMapperManager.listC5TaskOptions(keyword), Cs2GoodsOptionDTO.class);
    }

    private Cs2GoodsOptionDTO toBaseGoodsOption(Cs2Goods goods) {
        Cs2GoodsOptionDTO option = BeanUtil.copyProperties(goods, Cs2GoodsOptionDTO.class);
        option.setDisplayName(goods.getBaseDisplayName());
        option.setMarketHashName(goods.getBaseName());
        option.setExteriorName(null);
        return option;
    }
}
