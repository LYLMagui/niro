package com.niro.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.niro.web.dto.Cs2GoodsOptionDTO;
import com.niro.web.entity.Cs2Goods;

import java.util.List;

/**
 * CS2 商品服务
 */
public interface Cs2GoodsService extends IService<Cs2Goods> {

    List<Cs2GoodsOptionDTO> listUnboxCaseOptions(String keyword);

    List<Cs2GoodsOptionDTO> listC5TaskOptions(String keyword);
}
