package com.niro.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niro.web.entity.BuffPriceHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品价格历史 Mapper 接口
 *
 * @author liyl
 * @date 2025-12-29
 */
@Mapper
public interface BuffPriceHistoryMapper extends BaseMapper<BuffPriceHistory> {
}
