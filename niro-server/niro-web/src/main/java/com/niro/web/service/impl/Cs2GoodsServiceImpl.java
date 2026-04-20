package com.niro.web.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.dto.Cs2GoodsOptionDTO;
import com.niro.web.entity.Cs2Goods;
import com.niro.web.manager.Cs2GoodsMapperManager;
import com.niro.web.mapper.Cs2GoodsMapper;
import com.niro.web.service.Cs2GoodsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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
    public List<Cs2GoodsOptionDTO> listC5TaskOptions(String keyword) {
        return BeanUtil.copyToList(cs2GoodsMapperManager.listC5TaskOptions(keyword), Cs2GoodsOptionDTO.class);
    }
}
