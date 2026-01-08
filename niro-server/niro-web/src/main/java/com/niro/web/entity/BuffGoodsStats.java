package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 饰品市场行情统计雷达表
 *
 * @author liyl
 * @date 2026/01/08
 */
@Data
@TableName("buff_goods_stats")
public class BuffGoodsStats {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * BUFF商品唯一标识ID
     */
    private Long goodsId;
    /**
     * 过去7天成交均价
     */
    private BigDecimal avgPrice7d;
    /**
     * 过去24小时成交均价
     */
    private BigDecimal avgPrice24h;
    /**
     * 当前最高求购价格
     */
    private BigDecimal buyMaxPrice;
    /**
     * 当前在售数量
     */
    private Integer sellNum;
    /**
     * 流动性评分(0-100，分数越高变现越快)
     */
    private Integer liquidityScore;
    /**
     * 最后统计时间
     */
    private LocalDateTime updateTime;
}
