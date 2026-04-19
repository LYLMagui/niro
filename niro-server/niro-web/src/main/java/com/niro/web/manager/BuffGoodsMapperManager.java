package com.niro.web.manager;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.BuffGoods;
import com.niro.web.enums.ExteriorEnum;
import com.niro.web.mapper.BuffGoodsMapper;
import org.springframework.stereotype.Service;

/**
 *
 *
 * @author liyl
 * @date 2026/2/4
 */
@Service
public class BuffGoodsMapperManager extends ServiceImpl<BuffGoodsMapper, BuffGoods> {

    public BuffGoods findByShortName(String shortName, Integer exterior) {
        String normalizedShortName = StrUtil.trim(shortName);
        if (StrUtil.isBlank(normalizedShortName)) {
            return null;
        }

        String exteriorValue = resolveExteriorValue(exterior);
        BuffGoods goods = findByField(BuffGoods::getShortName, normalizedShortName, exteriorValue);
        if (goods != null || StrUtil.isBlank(exteriorValue)) {
            return goods;
        }
        return findByField(BuffGoods::getShortName, normalizedShortName, null);
    }

    public BuffGoods findByWeaponName(String weaponName, Integer exterior) {
        String normalizedName = StrUtil.trim(weaponName);
        if (StrUtil.isBlank(normalizedName)) {
            return null;
        }

        String exteriorValue = resolveExteriorValue(exterior);
        BuffGoods goods = findExact(normalizedName, exteriorValue);
        if (goods != null || StrUtil.isBlank(exteriorValue)) {
            return goods;
        }
        return findExact(normalizedName, null);
    }

    private BuffGoods findExact(String weaponName, String exteriorValue) {
        BuffGoods goods = findByField(BuffGoods::getName, weaponName, exteriorValue);
        if (goods != null) {
            return goods;
        }

        goods = findByField(BuffGoods::getShortName, weaponName, exteriorValue);
        if (goods != null) {
            return goods;
        }

        goods = findByField(BuffGoods::getInternalName, weaponName, exteriorValue);
        if (goods != null) {
            return goods;
        }

        goods = findByMarketHashName(weaponName, exteriorValue);
        if (goods != null) {
            return goods;
        }

        return this.lambdaQuery()
                .eq(StrUtil.isNotBlank(exteriorValue), BuffGoods::getExterior, exteriorValue)
                .like(BuffGoods::getName, weaponName)
                .last("limit 1")
                .one();
    }

    private BuffGoods findByField(SFunction<BuffGoods, String> field, String value, String exteriorValue) {
        return this.lambdaQuery()
                .eq(StrUtil.isNotBlank(exteriorValue), BuffGoods::getExterior, exteriorValue)
                .eq(field, value)
                .last("limit 1")
                .one();
    }

    private BuffGoods findByMarketHashName(String weaponName, String exteriorValue) {
        BuffGoods goods = this.lambdaQuery()
                .eq(StrUtil.isNotBlank(exteriorValue), BuffGoods::getExterior, exteriorValue)
                .eq(BuffGoods::getMarketHashName, weaponName)
                .last("limit 1")
                .one();
        if (goods != null || !weaponName.matches("^[\\x00-\\x7F]+$")) {
            return goods;
        }

        return this.lambdaQuery()
                .eq(StrUtil.isNotBlank(exteriorValue), BuffGoods::getExterior, exteriorValue)
                .apply("lower(market_hash_name) = {0}", weaponName.toLowerCase())
                .last("limit 1")
                .one();
    }

    private String resolveExteriorValue(Integer exterior) {
        if (exterior == null) {
            return null;
        }
        return switch (exterior) {
            case 0 -> ExteriorEnum.FACTORY_NEW.getValue();
            case 1 -> ExteriorEnum.MINIMAL_WEAR.getValue();
            case 2 -> ExteriorEnum.FIELD_TESTED.getValue();
            case 3 -> ExteriorEnum.WELL_WORN.getValue();
            case 4 -> ExteriorEnum.BATTLE_SCARRED.getValue();
            default -> null;
        };
    }
}
