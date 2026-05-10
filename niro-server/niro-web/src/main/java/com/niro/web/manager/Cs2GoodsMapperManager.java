package com.niro.web.manager;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.Cs2Goods;
import com.niro.web.mapper.Cs2GoodsMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CS2 商品数据库访问层
 */
@Service
public class Cs2GoodsMapperManager extends ServiceImpl<Cs2GoodsMapper, Cs2Goods> {

    public List<Cs2Goods> listUnboxCaseOptions(String keyword) {
        return this.lambdaQuery()
                .select(Cs2Goods::getId,
                        Cs2Goods::getDisplayName,
                        Cs2Goods::getBaseDisplayName,
                        Cs2Goods::getMarketHashName,
                        Cs2Goods::getItemType,
                        Cs2Goods::getWeaponType,
                        Cs2Goods::getRarity,
                        Cs2Goods::getExteriorName,
                        Cs2Goods::getHasExterior,
                        Cs2Goods::getImageUrl)
                .eq(Cs2Goods::getEnabled, true)
                .eq(Cs2Goods::getItemType, "case")
                .and(StrUtil.isNotBlank(keyword), wrapper -> wrapper
                        .like(Cs2Goods::getDisplayName, keyword)
                        .or()
                        .like(Cs2Goods::getBaseDisplayName, keyword)
                        .or()
                        .like(Cs2Goods::getMarketHashName, keyword))
                .orderByDesc(Cs2Goods::getUpdatedAt)
                .last("limit 50")
                .list();
    }

    public List<Cs2Goods> listUnboxItemOptions(String keyword) {
        return this.lambdaQuery()
                .select(Cs2Goods::getId,
                        Cs2Goods::getDisplayName,
                        Cs2Goods::getBaseDisplayName,
                        Cs2Goods::getMarketHashName,
                        Cs2Goods::getBaseName,
                        Cs2Goods::getItemType,
                        Cs2Goods::getWeaponType,
                        Cs2Goods::getRarity,
                        Cs2Goods::getExteriorName,
                        Cs2Goods::getHasExterior,
                        Cs2Goods::getImageUrl)
                .eq(Cs2Goods::getEnabled, true)
                .in(Cs2Goods::getItemType, "knife", "weapon_skin", "glove")
                .and(StrUtil.isNotBlank(keyword), wrapper -> wrapper
                        .like(Cs2Goods::getDisplayName, keyword)
                        .or()
                        .like(Cs2Goods::getBaseDisplayName, keyword)
                        .or()
                        .like(Cs2Goods::getMarketHashName, keyword)
                        .or()
                        .like(Cs2Goods::getBaseName, keyword))
                .orderByDesc(Cs2Goods::getUpdatedAt)
                .last("limit 200")
                .list();
    }

    public Map<String, Long> mapUnboxItemIdsByDisplayName(List<String> displayNames) {
        if (CollUtil.isEmpty(displayNames)) {
            return Map.of();
        }
        return this.lambdaQuery()
                .select(Cs2Goods::getId, Cs2Goods::getDisplayName)
                .eq(Cs2Goods::getEnabled, true)
                .in(Cs2Goods::getItemType, "knife", "weapon_skin", "glove")
                .in(Cs2Goods::getDisplayName, displayNames)
                .list()
                .stream()
                .collect(Collectors.toMap(Cs2Goods::getDisplayName, Cs2Goods::getId, (first, ignored) -> first));
    }

    public List<Cs2Goods> listC5TaskOptions(String keyword) {
        return this.lambdaQuery()
                .select(Cs2Goods::getId,
                        Cs2Goods::getDisplayName,
                        Cs2Goods::getBaseDisplayName,
                        Cs2Goods::getMarketHashName,
                        Cs2Goods::getItemType,
                        Cs2Goods::getWeaponType,
                        Cs2Goods::getRarity,
                        Cs2Goods::getExteriorName,
                        Cs2Goods::getHasExterior,
                        Cs2Goods::getImageUrl)
                .eq(Cs2Goods::getEnabled, true)
                .and(StrUtil.isNotBlank(keyword), wrapper -> wrapper
                        .like(Cs2Goods::getDisplayName, keyword)
                        .or()
                        .like(Cs2Goods::getBaseDisplayName, keyword)
                        .or()
                        .like(Cs2Goods::getMarketHashName, keyword))
                .orderByDesc(Cs2Goods::getUpdatedAt)
                .last("limit 50")
                .list();
    }

    public Cs2Goods getEnabledById(Long id) {
        return this.lambdaQuery()
                .eq(Cs2Goods::getId, id)
                .eq(Cs2Goods::getEnabled, true)
                .one();
    }
}
